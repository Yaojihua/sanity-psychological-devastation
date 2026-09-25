package piloser.sanitypd.capability;

import piloser.sanitypd.ActiveSanitySources;
import piloser.sanitypd.util.MathHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class Sanity implements ISanity, IPassiveSanity, IPersistentSanity
{
    /** Save format version: 1 = old 0-1 madness value, 2 = sanity points, 3 = sanity points + psychic resistance. */
    private static final int NBT_VERSION = 3;

    private boolean m_dirty = true;
    private int m_emAngerTimer;
    // A new player, or a client before its first sync, must start fully sane.
    // Version 1 used 0.0 for sane; in the points system 0 means fully insane, so the default is MAX_SANITY.
    private float m_sanityVal = MAX_SANITY;
    private float m_passive;
    private Vec3 m_stuckMultiplier;
    /** Owning entity: players cap at 100 sanity, other mobs cap at their max health. */
    private LivingEntity m_owner;
    /** Set while sanity still has to be filled to the owner's cap; see {@link #setOwner}. */
    private boolean m_pendingInit = true;
    /** Cache for expensive passive sources (entity scans and block cube scans); not persisted or synced. */
    private float m_passiveScanCache;
    /** Remaining lifetime of that cache in ticks; <= 0 means a rescan is due. */
    private int m_passiveScanTtl;
    /** Garland durability timer; not persisted. Kept per instance because a static field would leak between players. */
    private int m_garlandTimer;
    /** Confusion and mania state timers in ticks; persisted with the save. */
    private int m_lowSanityTicks;
    private int m_confusionTicks;
    private int m_maniaTicks;
    /**
     * Psychic resistance (0-1): reduces only the sanity loss from psychic damage, not the overflow
     * true damage. Defaults to 0 and is changed by {@code /sanity resist} or by datapacks and addons;
     * persisted with the save.
     */
    private float m_psychicResistance;

    private final int[] m_cds = new int[ActiveSanitySources.AMOUNT];
    private final Map<Integer, Integer> m_itemCds = new HashMap<>();
    private final Map<Integer, Integer> m_brokenBlocksCds = new HashMap<>();

    public Sanity()
    {
    }

    @Override
    public void serializeNBT(CompoundTag tag)
    {
        // v2: sanity.sanity stores sanity points (0 = insane, MAX_SANITY = sane)
        tag.putFloat("sanity.sanity", m_sanityVal);
        tag.putInt("sanity.version", NBT_VERSION);
        tag.putInt("sanity.low_sanity_ticks", m_lowSanityTicks);
        tag.putInt("sanity.confusion_ticks", m_confusionTicks);
        tag.putInt("sanity.mania_ticks", m_maniaTicks);
        tag.putInt("sanity.ender_man_anger_timer", m_emAngerTimer);
        // Added in v3: old saves have no such key, so getFloat returns 0 (no reduction), same as before.
        tag.putFloat("sanity.psychic_resistance", m_psychicResistance);

        // TODO: do lazy serialization instead
        tag.putInt("sanity.sleeping", m_cds[ActiveSanitySources.SLEEPING]);
        tag.putInt("sanity.baby_chicken_spawn", m_cds[ActiveSanitySources.SPAWNING_BABY_CHICKEN]);
        tag.putInt("sanity.animal_breeding", m_cds[ActiveSanitySources.BREEDING_ANIMALS]);
        tag.putInt("sanity.villager_trade", m_cds[ActiveSanitySources.VILLAGER_TRADE]);
        tag.putInt("sanity.shearing", m_cds[ActiveSanitySources.SHEARING]);
        tag.putInt("sanity.eating", m_cds[ActiveSanitySources.EATING]);
        tag.putInt("sanity.fishing", m_cds[ActiveSanitySources.FISHING]);
        tag.putInt("sanity.potting_flower", m_cds[ActiveSanitySources.POTTING_FLOWER]);

        serializeItemCds(tag);
        serializeBrokenBlocksCds(tag);
    }

    @Override
    public void deserializeNBT(CompoundTag tag)
    {
        // No version key means an old save holding a 0-1 madness value; convert it to sanity points.
        if (tag.getInt("sanity.version") < NBT_VERSION)
            setSanity((1f - tag.getFloat("sanity.sanity")) * getMaxSanity());
        else
            setSanity(tag.getFloat("sanity.sanity"));
        setEnderManAngerTimer(tag.getInt("sanity.ender_man_anger_timer"));
        m_lowSanityTicks = tag.getInt("sanity.low_sanity_ticks");
        m_confusionTicks = tag.getInt("sanity.confusion_ticks");
        m_maniaTicks = tag.getInt("sanity.mania_ticks");
        // v3: an old save lacks this key, so it reads back as 0 (no reduction), matching the new default.
        setPsychicResistance(tag.getFloat("sanity.psychic_resistance"));

        m_cds[ActiveSanitySources.SLEEPING] = tag.getInt("sanity.sleeping");
        m_cds[ActiveSanitySources.SPAWNING_BABY_CHICKEN] = tag.getInt("sanity.baby_chicken_spawn");
        m_cds[ActiveSanitySources.BREEDING_ANIMALS] = tag.getInt("sanity.animal_breeding");
        m_cds[ActiveSanitySources.VILLAGER_TRADE] = tag.getInt("sanity.villager_trade");
        m_cds[ActiveSanitySources.SHEARING] = tag.getInt("sanity.shearing");
        m_cds[ActiveSanitySources.EATING] = tag.getInt("sanity.eating");
        m_cds[ActiveSanitySources.FISHING] = tag.getInt("sanity.fishing");
        m_cds[ActiveSanitySources.POTTING_FLOWER] = tag.getInt("sanity.potting_flower");

        deserializeItemCds(tag);
        deserializeBrokenBlocksCooldowns(tag);
    }

    /**
     * Server to client sync. The client needs:
     * <ul>
     *   <li>sanity points and the passive delta for the HUD brain texture and the corner arrow;</li>
     *   <li>the mania timer for the red screen edge filter warning that mania damage is about to start.</li>
     * </ul>
     */
    public void serialize(FriendlyByteBuf buf)
    {
        buf.writeFloat(m_sanityVal);
        buf.writeFloat(m_passive);
        buf.writeInt(m_maniaTicks);
        buf.writeFloat(m_psychicResistance);
    }

    public void deserialize(FriendlyByteBuf buf)
    {
        m_sanityVal = buf.readFloat();
        m_passive = buf.readFloat();
        m_maniaTicks = buf.readInt();
        setPsychicResistance(buf.readFloat());
        // The server already sent the real values, so the pending cap-based initialization must be
        // cleared: otherwise the next getSanity() call would run ensureInitialized() and overwrite
        // them with a full bar, briefly showing 100 sanity on the client HUD and splash text.
        m_pendingInit = false;
    }

    /**
     * Sanity cap: {@link #MAX_SANITY} (100) for players, max health for every other mob.
     *
     * <p>{@code getAttributes()} must be null-checked: {@code AttachCapabilitiesEvent} fires from the
     * {@code Entity} super constructor ({@code CapabilityProvider.gatherCapabilities()}, with
     * {@code Entity.<init>} on the stack), and at that point {@code LivingEntity}'s attribute map is
     * not assigned yet, so calling {@code getMaxHealth()} throws
     * {@code NullPointerException: ... getAttributes() is null}. Until the map is ready this returns
     * 100; {@link #ensureInitialized()} fills to the real cap once it is.
     */
    @Override
    public float getMaxSanity()
    {
        if (m_owner instanceof Player)
            return MAX_SANITY;
        if (m_owner != null && m_owner.getAttributes() != null)
            return Math.max(1.0f, m_owner.getMaxHealth());
        return MAX_SANITY;
    }

    /**
     * Binds the owning entity.
     *
     * <p>Owner attributes must not be read here (see {@link #getMaxSanity()}): calling
     * {@code getMaxHealth()} during construction would throw an NPE for every entity, flooding the
     * log when bees leave a nest, mobs spawn or chunks load. This only stores the owner; the sanity
     * value is filled later by {@link #ensureInitialized()}.
     */
    public void setOwner(LivingEntity owner)
    {
        m_owner = owner;
        m_pendingInit = true;
        m_dirty = true;
    }

    /**
     * Deferred initialization: fills sanity to the owner's cap, so new mobs spawn fully sane.
     * Does nothing while the attribute map is still unavailable and retries on the next access.
     */
    private void ensureInitialized()
    {
        if (!m_pendingInit)
            return;
        if (m_owner != null && !(m_owner instanceof Player) && m_owner.getAttributes() == null)
            return;
        m_pendingInit = false;
        m_sanityVal = getMaxSanity();
    }

    @Override
    public float getSanity()
    {
        ensureInitialized();
        return Math.min(m_sanityVal, getMaxSanity());
    }

    @Override
    public void setSanity(float value)
    {
        ensureInitialized();
        m_sanityVal = MathHelper.clamp(value, 0.0f, getMaxSanity());
        m_dirty = true;
    }

    @Override
    public float getPassiveIncrease()
    {
        return m_passive;
    }

    @Override
    public void setPassiveIncrease(float value)
    {
        m_passive = value;
        m_dirty = true;
    }

    public boolean getDirty()
    {
        return m_dirty;
    }

    public void setDirty(boolean value)
    {
        m_dirty = value;
    }

    @Override
    public int[] getActiveSourcesCooldowns()
    {
        return m_cds;
    }

    @Override
    public Map<Integer, Integer> getItemCooldowns()
    {
        return m_itemCds;
    }

    @Override
    public Map<Integer, Integer> getBrokenBlocksCooldowns()
    {
        return m_brokenBlocksCds;
    }

    @Override
    public void setEnderManAngerTimer(int value)
    {
        m_emAngerTimer = value;
    }

    @Override
    public int getEnderManAngerTimer()
    {
        return m_emAngerTimer;
    }

    @Override
    public void setStuckMotionMultiplier(Vec3 multiplier)
    {
        m_stuckMultiplier = multiplier;
    }

    @Override
    public Vec3 getStuckMotionMultiplier()
    {
        return m_stuckMultiplier;
    }

    public int getLowSanityTicks()
    {
        return m_lowSanityTicks;
    }

    public void setLowSanityTicks(int value)
    {
        m_lowSanityTicks = value;
    }

    @Override
    public float getPsychicResistance()
    {
        return m_psychicResistance;
    }

    @Override
    public void setPsychicResistance(float value)
    {
        float clamped = MathHelper.clamp(value, 0f, 1f);
        // A resistance change must reach the client so debug tools can read it, so mark dirty.
        if (m_psychicResistance != clamped)
            m_dirty = true;
        m_psychicResistance = clamped;
    }

    public int getConfusionTicks()
    {
        return m_confusionTicks;
    }

    public void setConfusionTicks(int value)
    {
        m_confusionTicks = value;
    }

    public int getManiaTicks()
    {
        return m_maniaTicks;
    }

    public void setManiaTicks(int value)
    {
        // The mania timer must reach the client too (the red edge filter derives its alpha from it),
        // so mark dirty on change. Marking dirty only makes shareSanity() send a packet to players;
        // mobs have no packet path.
        if (m_maniaTicks != value)
            m_dirty = true;
        m_maniaTicks = value;
    }

    public int getGarlandTimer()
    {
        return m_garlandTimer;
    }

    /** @see #m_passiveScanCache */
    public float getPassiveScanCache()
    {
        return m_passiveScanCache;
    }

    public void setPassiveScanCache(float value)
    {
        m_passiveScanCache = value;
    }

    /** @see #m_passiveScanTtl */
    public int getPassiveScanTtl()
    {
        return m_passiveScanTtl;
    }

    public void setPassiveScanTtl(int value)
    {
        m_passiveScanTtl = value;
    }

    public void setGarlandTimer(int value)
    {
        m_garlandTimer = value;
    }

    private void serializeItemCds(CompoundTag tag)
    {
        long[] itemCds = new long[m_itemCds.size()];
        int i = 0;

        for (Map.Entry<Integer, Integer> entry : m_itemCds.entrySet())
        {
            long val = entry.getKey();
            val <<= Long.SIZE / 2;
            val |= entry.getValue();

            itemCds[i] = val;

            i++;
        }

        tag.putLongArray("sanity.item_cooldowns", itemCds);
    }

    private void deserializeItemCds(CompoundTag tag)
    {
        long[] itemCds = tag.getLongArray("sanity.item_cooldowns");
        m_itemCds.clear();

        for (long itemCd : itemCds)
        {
            m_itemCds.put((int)(itemCd >> Long.SIZE / 2), (int)itemCd);
        }
    }

    private void serializeBrokenBlocksCds(CompoundTag tag)
    {
        long[] brokenBlocksCds = new long[m_brokenBlocksCds.size()];
        int i = 0;

        for (Map.Entry<Integer, Integer> entry : m_brokenBlocksCds.entrySet())
        {
            long val = entry.getKey();
            val <<= Long.SIZE / 2;
            val |= entry.getValue();

            brokenBlocksCds[i] = val;

            i++;
        }

        tag.putLongArray("sanity.broken_blocks_cooldowns", brokenBlocksCds);
    }

    private void deserializeBrokenBlocksCooldowns(CompoundTag tag)
    {
        long[] brokenBlocksCds = tag.getLongArray("sanity.broken_blocks_cooldowns");
        m_brokenBlocksCds.clear();

        for (long blockCd : brokenBlocksCds)
        {
            m_brokenBlocksCds.put((int)(blockCd >> Long.SIZE / 2), (int)blockCd); // was previously written into the item cooldown map by mistake
        }
    }
}