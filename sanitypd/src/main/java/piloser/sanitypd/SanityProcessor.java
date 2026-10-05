package piloser.sanitypd;

import piloser.sanitypd.capability.*;
import piloser.sanitypd.config.*;
import piloser.sanitypd.entity.InnerEntity;
import piloser.sanitypd.entity.InnerEntitySpawner;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.net.InnerEntityCapImplPacket;
import piloser.sanitypd.net.PacketHandler;
import piloser.sanitypd.net.SanityPacket;
import piloser.sanitypd.passive.*;
import piloser.sanitypd.util.MathHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import piloser.sanitypd.thought.ThoughtChainProvider;
import piloser.sanitypd.thought.ThoughtItem;
import piloser.sanitypd.thought.ThoughtType;
import piloser.sanitypd.thought.MindsetState;
import piloser.sanitypd.thought.Mindsets;
import piloser.sanitypd.thought.MindsetAttributes;
import piloser.sanitypd.thought.ThoughtEffects;
import piloser.sanitypd.thought.MadnessCombat;
import piloser.sanitypd.thought.HintState;
import piloser.sanitypd.combat.SanityCombat;
import piloser.sanitypd.effect.EffectRegistry;

public final class SanityProcessor
{
    // The garland durability timer is stored per player (see Sanity#getGarlandTimer).
    private static final RandomSource RAND = RandomSource.create();

    public static final int MAX_GARLAND_TIMER = 60;
    public static final float SANITY_TARGET_THRESHOLD = .87f; // compared against getMadness() (0..1 madness)
    /**
     * Recalculation interval in ticks for the expensive passive sources: 10 = once per 0.5s.
     * Smaller reacts faster but costs more CPU; these sources change slowly, so 10 is plenty.
     */
    public static final int PASSIVE_SCAN_INTERVAL = 10;
    public static final List<IPassiveSanitySource> PASSIVE_SANITY_SOURCES = Arrays.asList(
            new Passive(),
            new InWaterOrRain(),
            new Hungry(),
            new EnderManAnger(),
            new Pet(),
            new Monster(),
            new Darkness(),
            new Lightness(),
            new PassiveBlocks(),
            new PlayerCompany(),
            new Jukebox(),
            new BlockStuck(),
            new DirtPath()
    );

    private SanityProcessor() {}

    private static float calcPassive(ServerPlayer player, ISanity sanity)
    {
        ResourceLocation dim = player.level().dimension().location();
        float passive = 0;

        // Nature Affinity: one factor for the four behaviours it names. Applying it per source is the same
        // number as applying it to their sum once (multiplication distributes over addition), and it keeps the
        // expensive sources' cached sum usable as-is.
        float affinity = 1f + natureAffinityBonus(player);

        // Cheap sources (attribute / effect / position checks) are still evaluated every tick.
        for (IPassiveSanitySource pss : PASSIVE_SANITY_SOURCES)
        {
            if (pss.isExpensive())
                continue;

            float val = pss.get(player, sanity, dim);
            float scaled = val * getSanityMultiplier(player, val);

            if (pss.isNatureSoothed() && val > 0f)
                scaled *= affinity;

            passive += scaled;
        }

        // Expensive sources (entity scans, line-of-sight rays, per-block volume scans) are throttled
        // and reuse a cache: the numbers are identical, just up to PASSIVE_SCAN_INTERVAL ticks stale.
        passive += calcExpensivePassive(player, sanity, dim, affinity);

        int garlandTimer = sanity instanceof Sanity s0 ? s0.getGarlandTimer() : 0;
        garlandTimer--;
        ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);
        if (headItem.is(ItemRegistry.GARLAND.get()))
        {
            // TODO: unhardcode
            // Garland: grants 0.005 sanity points per tick (positive value = sanity gain).
            // Wearing it is one of the four behaviours Nature Affinity boosts, so the same factor applies.
            passive += .005 * ConfigProxy.getPosMul(dim) * affinity;
            if (garlandTimer <= 0)
                wearGarland(player, headItem, sanity, player.isInWaterOrRain() ? 2 : 1);
        }
        if (garlandTimer <= 0)
            garlandTimer = MAX_GARLAND_TIMER;
            if (sanity instanceof Sanity s1)
                s1.setGarlandTimer(garlandTimer);

        return passive;
    }

    /**
     * Sum of the expensive passive sources, with a throttling cache.
     *
     * <p>These sources read slowly-changing world state (nearby monsters or pets, a campfire
     * underfoot), so a cached value equals a fresh scan, only up to
     * {@link #PASSIVE_SCAN_INTERVAL} ticks stale.
     */
    private static float calcExpensivePassive(ServerPlayer player, ISanity sanity, ResourceLocation dim, float affinity)
    {
        if (!(sanity instanceof Sanity s))
            return sumExpensivePassive(player, sanity, dim, affinity);

        if (s.getPassiveScanTtl() > 0)
        {
            s.setPassiveScanTtl(s.getPassiveScanTtl() - 1);
            return s.getPassiveScanCache();
        }

        float sum = sumExpensivePassive(player, sanity, dim, affinity);
        s.setPassiveScanCache(sum);
        s.setPassiveScanTtl(PASSIVE_SCAN_INTERVAL);
        return sum;
    }

    private static float sumExpensivePassive(ServerPlayer player, ISanity sanity, ResourceLocation dim, float affinity)
    {
        float sum = 0;

        for (IPassiveSanitySource pss : PASSIVE_SANITY_SOURCES)
        {
            if (!pss.isExpensive())
                continue;

            float val = pss.get(player, sanity, dim);
            float scaled = val * getSanityMultiplier(player, val);

            if (pss.isNatureSoothed() && val > 0f)
                scaled *= affinity;

            sum += scaled;
        }

        return sum;
    }

    private static void shareSanity(ServerPlayer player, Sanity cap)
    {
        if (cap.getDirty())
        {
            SanityPacket packet = new SanityPacket(cap);
            PacketHandler.CHANNEL_INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
			cap.setDirty(false);
        }
    }

    private static void handlePlayerAte(ServerPlayer player, ItemStack itemStack)
    {
        handleActiveSourceForPlayer(
                player,
                ActiveSanitySources.EATING,
                ConfigProxy::getEatingCooldown,
                dim -> itemStack.getFoodProperties(player).getNutrition() * ConfigProxy.getEating(dim));
    }

    /**
     * Wears the garland, at a quarter rate while the composure mindset is active.
     *
     * <p>"75% less wear" cannot be expressed as a whole point per wear tick without rounding: the base wear is
     * 1 (2 in rain), and three quarters of one point is not a durability value. So the saving is kept as a
     * fraction in the capability and paid out a whole point at a time, which makes the total exact over any
     * number of ticks - and keeps rain from being rounded up to a full point every time.
     *
     * <p>The credit is dropped when the mindset is not active, so putting the thought back cannot release a
     * banked fraction.
     */
    private static void wearGarland(ServerPlayer player, ItemStack garland, ISanity sanity, int amount)
    {
        if (!(sanity instanceof Sanity cap) || !MindsetState.isActive(player, Mindsets.COMPOSURE))
        {
            if (sanity instanceof Sanity idle)
                idle.setGarlandWearCredit(0f);

            garland.hurtAndBreak(amount, player, ent -> {});
            return;
        }

        float credit = cap.getGarlandWearCredit() + amount * 0.25f;
        int whole = (int) credit;
        cap.setGarlandWearCredit(credit - whole);

        if (whole > 0)
            garland.hurtAndBreak(whole, player, ent -> {});
    }

    public static float getGarlandMultiplier(ServerPlayer player)
    {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ItemRegistry.GARLAND.get()) ? .92f : 1.0f;
    }

    /**
     * The "Nature Affinity" bonus for a player: 0 unless that thought sits in his chain and its composure
     * count has reached a tier, otherwise 0.20 / 0.40 / 0.50 for 1 / 3 / 5 thoughts.
     *
     * <p>Read from the chain's contents, which are authoritative on the server. The tiers are the same ones the
     * tooltip shows, so they are stated once here rather than repeated at each call site.
     *
     * <p>An item that carries no type (a tag entry added by a datapack) cannot reach a tier and is skipped
     * instead of being cast: the chain accepts it, but only {@code ThoughtItem} declares an axis to count.
     */
    private static float natureAffinityBonus(ServerPlayer player)
    {
        return ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_NATURE_AFFINITY.get());
    }

    public static float getSanityMultiplier(ServerPlayer player, float value)
    {
        ResourceLocation dim = player.level().dimension().location();
        return value >= 0 ? ConfigProxy.getPosMul(dim) : ConfigProxy.getNegMul(dim) * getGarlandMultiplier(player);
    }

    public static void addSanity(@NotNull ISanity sanity, float value, @NotNull ServerPlayer player)
    {
        if (value == 0.0f)
            return;

        float change = value * getSanityMultiplier(player, value);

        // Depersonalization: a positive change may not take the player above their recovery ceiling. Losses
        // are untouched on purpose, and so is a value that already sits above the ceiling (equipping the
        // thought does not take sanity away, it only stops recovery).
        if (change > 0.0f)
            change = clampToRecoveryCeiling(sanity, player, change);

        if (change == 0.0f)
            return;

        sanity.setSanity(sanity.getSanity() + change);
    }

    /**
     * Caps a positive sanity change at the player's recovery ceiling.
     *
     * @return what may actually be applied: {@code 0} when the player is already at or above the ceiling
     */
    public static float clampToRecoveryCeiling(@NotNull ISanity sanity, @NotNull ServerPlayer player, float positive)
    {
        float ceiling = sanity.getMaxSanity() * ThoughtEffects.recoveryCeilingFraction(player);

        return Math.min(positive, Math.max(0.0f, ceiling - sanity.getSanity()));
    }

    public static void tickPlayer(final ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dim = player.level().dimension().location();

            // Composure mindset, refreshed once a tick so the capability never has to look the chain up in
            // getMaxSanity() (which runs several times per tick) or inside the damage maths.
            if (s instanceof Sanity sanityCap)
            {
                boolean composure = MindsetState.isActive(player, Mindsets.COMPOSURE);
                sanityCap.setSanityCapBonus(composure ? 20f : 0f);

                // Read after the cap is refreshed, so the "+20 points" counts towards the same half the screen
                // and the HUD show. 0.15 is the flat bonus; the line above is what keeps the two consistent.
                boolean aboveHalf = sanityCap.getSanity() > sanityCap.getMaxSanity() * 0.5f;
                sanityCap.setPsychicResistBonus(composure && aboveHalf ? 0.15f : 0f);

                // Attributes go through vanilla, so every other mod and the client's own movement agree with
                // the server about the numbers. The call is idempotent, which is what makes it safe per tick.
                boolean aboveEighty = sanityCap.getSanity() > sanityCap.getMaxSanity() * 0.8f;
                boolean garlandWorn = player.getItemBySlot(EquipmentSlot.HEAD).is(ItemRegistry.GARLAND.get());

                // Lucid Elation is granted by its own item rather than by a mindset, so it is read from the
                // chain and is zero unless the item is in it, the sanity condition holds and a tier is reached.
                boolean aboveSixty = sanityCap.getSanity()
                        > sanityCap.getMaxSanity() * ThoughtEffects.sanityThreshold(ItemRegistry.THOUGHT_LUCID_ELATION.get());
                float lucid = aboveSixty
                        ? ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_LUCID_ELATION.get())
                        : 0f;

                MindsetAttributes.refresh(player, composure, aboveEighty, garlandWorn, lucid);
                refreshMadness(player, sanityCap);
            }

            float passive = calcPassive(player, s);
            float snapshot = s.getSanity();
            // Passive is premultiplied, so it does not go through SanityProcessor#addSanity - but a positive
            // gain still has to respect the recovery ceiling (Depersonalization), so it takes the same clamp.
            // The amount reported to the HUD is what was actually applied, not what was intended: at the
            // ceiling the "you are recovering" arrow must go away, otherwise the screen promises a gain the
            // maths refuses.
            float applied = passive > 0f ? clampToRecoveryCeiling(s, player, passive) : passive;
            s.setSanity(s.getSanity() + applied);
            if (s instanceof IPassiveSanity ps)
            {
                ps.setPassiveIncrease(snapshot != s.getSanity() ? applied : 0);
            }
            if (s instanceof IPersistentSanity ps)
            {
                int[] cds = ps.getActiveSourcesCooldowns();
                for (int i = 0; i < cds.length; ++i)
                    cds[i] = Mth.clamp(cds[i] - 1, 0, Integer.MAX_VALUE);

                Map<Integer, Integer> itemCds = ps.getItemCooldowns();
                for (Iterator<Map.Entry<Integer, Integer>> it = itemCds.entrySet().iterator(); it.hasNext();)
                {
                    Map.Entry<Integer, Integer> entry = it.next();
                    itemCds.put(entry.getKey(), itemCds.get(entry.getKey()) - 1);
                    if (itemCds.get(entry.getKey()) <= 0)
                        it.remove();
                }

                Map<Integer, Integer> brokenBlocksCds = ps.getBrokenBlocksCooldowns();
                for (Iterator<Map.Entry<Integer, Integer>> it = brokenBlocksCds.entrySet().iterator(); it.hasNext();)
                {
                    Map.Entry<Integer, Integer> entry = it.next();
                    brokenBlocksCds.put(entry.getKey(), brokenBlocksCds.get(entry.getKey()) - 1);
                    if (brokenBlocksCds.get(entry.getKey()) <= 0)
                        it.remove();
                }
            }
            if (s instanceof Sanity)
                shareSanity(player, (Sanity)s);
        });
        InnerEntitySpawner.trySpawnForPlayer(player);
    }

    /**
     * Refreshes everything the madness mindset and its five thoughts grant, and applies the mindset's own
     * sanity drain.
     *
     * <h2>Why the resistance is added rather than compared</h2>
     * The madness bonus and the composure bonus share the one field the capability keeps for it, and the
     * owner's rule is that unstipulated effects stack - so an active madness mindset adds its 20% on top of
     * a composure mindset's 15%. (In practice the two need ten thoughts of two types at once, so this is
     * mostly a guard against a future bug; adding is also the answer that cannot silently swallow one of
     * them.)
     *
     * <h2>Why the drain is not routed through the normal multiplier</h2>
     * The owner's wording is "equal to the darkness rate", and the darkness rate is whatever
     * {@code sanity.passive.darkness} is set to. Reading that one value here - rather than hard-coding a
     * number - is what keeps the two equal when a player edits the config. It is applied with the same
     * negative multiplier every other sanity source uses, so standing in the dark with the mindset active
     * costs both rates.
     */
    private static void refreshMadness(final ServerPlayer player, final Sanity sanityCap)
    {
        boolean madness = MindsetState.isActive(player, Mindsets.MADNESS);
        boolean madnessLow = madness && sanityCap.getSanity() < sanityCap.getMaxSanity() * MindsetAttributes.MADNESS_LOW;

        // Shared field with the composure mindset, so the two bonuses add instead of overwriting each other.
        float resist = (madness ? MindsetAttributes.MADNESS_RESIST : 0f)
                + (MindsetState.isActive(player, Mindsets.COMPOSURE)
                        && sanityCap.getSanity() > sanityCap.getMaxSanity() * 0.5f ? 0.15f : 0f);
        sanityCap.setPsychicResistBonus(Math.min(resist, 1f));

        // Command Hallucination: the client reports whether a non-mild inner line is on screen, because that
        // is decided by the client's own draw path (see HintState / HintStatePacket).
        float hallucination = HintState.isNonMildHintOnScreen(player)
                ? ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_COMMAND_HALLUCINATION.get())
                : 0f;

        // Psychomotor Agitation: very low sanity, or the mania damage already biting.
        boolean maniaBiting = sanityCap.getManiaTicks() > SanityCombat.MANIA_GRACE_TICKS
                && !player.hasEffect(piloser.sanitypd.effect.EffectRegistry.MANIA_IMMUNITY.get());
        boolean agitated = sanityCap.getSanity() < sanityCap.getMaxSanity() * ThoughtEffects.AGITATION_SANITY
                || maniaBiting;
        float agitation = agitated
                ? ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_PSYCHOMOTOR_AGITATION.get())
                : 0f;

        // Fight or Flight: one of two mutually exclusive states, or nothing at all above its threshold.
        float[] fight = MadnessCombat.fightOrFlight(player, ThoughtEffects.isEquipped(
                player, ItemRegistry.THOUGHT_FIGHT_OR_FLIGHT.get())
                && sanityCap.getSanity() < sanityCap.getMaxSanity() * ThoughtEffects.FIGHT_OR_FLIGHT_SANITY);
        float fightAttack = fight == null ? 0f : fight[0];
        float fightSpeed = fight == null ? 0f : fight[1];

        // Identification with the Aggressor is applied on the hit itself (it only pays out against the one
        // entity that struck the player), so no attribute modifier is passed for it here.
        // Instrumental Aggression is not madness-gated either: it pays out whenever it is in the chain.
        float instrumental = ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_INSTRUMENTAL_AGGRESSION.get())
                ? ThoughtEffects.INSTRUMENTAL_AGGRESSION_BONUS
                : 0f;

        // Irritability: it raises the attack bonus the mania *effect* grants, so it pays out only while that
        // effect is on the player. Gating on the effect (rather than on the madness mindset or the mania
        // window) is the owner's wording: "the mania status effect's attack bonus is raised".
        float irritability = player.hasEffect(piloser.sanitypd.effect.EffectRegistry.MANIA.get())
                ? ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_IRRITABILITY.get())
                : 0f;

        MindsetAttributes.refreshMadness(player, madness, madnessLow, hallucination, 0f, fightAttack, fightSpeed,
                agitation, instrumental, irritability);

        if (madness && sanityCap.getSanity() < sanityCap.getMaxSanity() * MindsetAttributes.MADNESS_ABOVE)
        {
            ResourceLocation dim = player.level().dimension().location();
            float drain = ConfigProxy.getDarkness(dim);

            if (drain < 0f)
                sanityCap.setSanity(sanityCap.getSanity() + drain * ConfigProxy.getNegMul(dim)
                        * getGarlandMultiplier(player));
        }
    }

    public static void tickLevel(final ServerLevel level)
    {
        for (Entity ent : level.getEntities().getAll())
        {
            if (ent instanceof InnerEntity ie)
            {
                ie.getCapability(InnerEntityCapImplProvider.CAP).ifPresent(iec ->
                {
                    if (iec instanceof InnerEntityCapImpl ieci)
                    {
                        // Also sync when the target player changed but hasTarget() did not.
                        if (ieci.hasTarget() != (ie.getTarget() != null) || !Objects.equals(ieci.getPlayerTargetUUID(), ie.getTarget() instanceof ServerPlayer sp ? sp.getUUID() : null))
                        {
                            ieci.setHasTarget(ie.getTarget() != null);
                            ieci.setPlayerTargetUUID(ie.getTarget() instanceof ServerPlayer sp ? sp.getUUID() : null);
                        }

                        if (ieci.getDirty())
                        {
                            InnerEntityCapImplPacket packet = new InnerEntityCapImplPacket(ieci);
                            packet.m_id = ent.getId();
                            PacketHandler.CHANNEL_INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> ent), packet);
                            ieci.setDirty(false); // must clear dirty after sending, or the same packet is resent every tick
                        }
                    }
                });
            }
        }
    }

    public static List<Player> getInsanePlayersInArea(final Level levelIn, BlockPos center, int blockRadius)
    {
        if (levelIn == null || center == null)
            return null;
        List<Player> list = new ArrayList<Player>();
        for (Player player : levelIn.getEntitiesOfClass(
                Player.class,
                new AABB(center.offset(blockRadius, blockRadius, blockRadius), center.offset(-blockRadius, -blockRadius, -blockRadius))))
        {
            player.getCapability(SanityProvider.CAP).ifPresent(s ->
            {
                if (s.getMadness() >= SANITY_TARGET_THRESHOLD)
                    list.add(player);
            });
        }
        return list;
    }

    public static Player getMostInsanePlayer(final Level levelIn)
    {
        return getMostInsanePlayer(levelIn, SANITY_TARGET_THRESHOLD);
    }

    public static Player getMostInsanePlayer(final Level levelIn, float sanityThreshold)
    {
        if (levelIn == null)
            return null;

        Player toReturn = null;
        float lowestSanity = Float.MAX_VALUE;
        for (Player player : levelIn.players())
        {
            if (player.isCreative() || player.isSpectator())
                continue;
            ISanity s = player.getCapability(SanityProvider.CAP).orElse(null);
            if (s == null)
                continue;
            float sanity = s.getSanity();
            if (s.getMadness() >= sanityThreshold && sanity < lowestSanity)
            {
                lowestSanity = sanity;
                toReturn = player;
            }
        }
        return toReturn;
    }

    public static void handleActiveSourceForPlayer(
            ServerPlayer player,
            int id,
            Function<ResourceLocation, Integer> cdSupplier,
            Function<ResourceLocation, Float> sanitySupplier)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            int cd = cdSupplier.apply(dimLoc);

            if (s instanceof IPersistentSanity ps && cd > 0.0f)
            {
                int timePassed = cd - ps.getActiveSourcesCooldowns()[id];
                addSanity(s, sanitySupplier.apply(dimLoc) * MathHelper.clampNorm((float) timePassed / cd), player);
//                s.setSanity(s.getSanity() + sanitySupplier.apply(dimLoc) * MathHelper.clampNorm((float) timePassed / cd));
                ps.getActiveSourcesCooldowns()[id] = cd;
            }
            else
                addSanity(s, sanitySupplier.apply(dimLoc), player);
//                s.setSanity(s.getSanity() + sanitySupplier.apply(dimLoc));
        });
    }

    public static void handlePlayerSlept(ServerLevel level)
    {
        for (ServerPlayer player : level.players())
        {
            if (player.isCreative() || player.isSpectator())
                continue;

            SanityProcessor.handleActiveSourceForPlayer(player, ActiveSanitySources.SLEEPING,
                    ConfigProxy::getSleepingCooldown, dim -> sleepSanityFor(player, dim));
        }
    }

    /**
     * How much sanity one sleep restores for this player, before any per-dimension multipliers.
     *
     * <p>Public so a self-check can assert the number without going through {@code handlePlayerSlept}, whose
     * loop only visits players in the level's player list - a fake player is never in it, and the check would
     * read a flat zero for reasons that have nothing to do with the thought (measured 2026-10-03).
     *
     * <p>Sleep Debt cuts it by its penalty. The reduction multiplies the amount rather than adding a second
     * negative source, so the cooldown and the "was that a real sleep" bookkeeping are untouched - only the
     * number the player gets is smaller.
     */
    public static float sleepSanityFor(ServerPlayer player, ResourceLocation dim)
    {
        float amount = ConfigProxy.getSleeping(dim);

        return ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_SLEEP_DEBT.get())
                ? amount * (1f - ThoughtEffects.SLEEP_DEBT_PENALTY)
                : amount;
    }

    public static void handlePlayerHurt(ServerPlayer player, float amount)
    {
        if (player == null || player.isCreative() || player.isSpectator() || amount <= 0)
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            addSanity(s, amount * ConfigProxy.getHurtRatio(dimLoc), player);
//            s.setSanity(s.getSanity() + amount * ConfigProxy.getHurtRatio(player.level.dimension().location()));
        });
    }

    /**
     * The "you hit a friendly creature" penalty.
     *
     * <p>The parameter is a {@link LivingEntity} rather than an {@code Animal} because "friendly" is wider
     * than vanilla's Animal: villagers and wandering traders (both {@code AbstractVillager}) are peaceful
     * too, and the Law of the Jungle thought names friendly creatures rather than animals. See
     * {@code EventHandler#onLivingHurtAnimal}, which decides what reaches this method.
     */
    public static void handlePlayerHurtAnimal(ServerPlayer player, LivingEntity target, float amount)
    {
        if (player == null || player.isCreative() || player.isSpectator() || amount <= 0)
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            // Law of the Jungle: while sanity is above half, this penalty is waived by the tier the chain has
            // reached (20 / 40 / 60%). The penalty is a signed value, so the waiver multiplies it instead of
            // subtracting from it - and a player who does not hold the thought is multiplied by exactly 1.
            float waived = s.getSanity() > s.getMaxSanity() * ThoughtEffects.sanityThreshold(ItemRegistry.THOUGHT_LAW_OF_THE_JUNGLE.get())
                    ? ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_LAW_OF_THE_JUNGLE.get())
                    : 0f;

            addSanity(s, amount * ConfigProxy.getAnimalHurtRatio(player.level().dimension().location()) * (target.isBaby() ? 2.0f : 1.0f) * (1f - waived), player);
        });
    }

    public static void handlePlayerPetDeath(ServerPlayer player, TamableAnimal pet)
    {
        // The player passed by this event already IS the pet owner; an extra
        // pet.isOwnedBy(player) check would prevent this penalty from ever firing.
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            addSanity(s, ConfigProxy.getPetDeath(player.level().dimension().location()), player);
//            s.setSanity(s.getSanity() + ConfigProxy.getPetDeath(player.level.dimension().location()));
        });
    }

    public static void handlePlayerEnderManAngered(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            if (s instanceof IPersistentSanity ps && ps.getEnderManAngerTimer() <= 0)
            {
                ps.setEnderManAngerTimer(100);
            }
        });
    }

    public static void handlePlayerGotAdvancement(ServerPlayer player, Advancement adv)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        if (adv.getDisplay() == null || !adv.getDisplay().shouldAnnounceChat())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            addSanity(s, ConfigProxy.getAdvancement(dimLoc), player);
//            s.setSanity(s.getSanity() + ConfigProxy.getAdvancement(player.level.dimension().location()));
        });
    }

    public static void handlePlayerBredAnimals(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.BREEDING_ANIMALS, ConfigProxy::getAnimalBreedingCooldown, ConfigProxy::getAnimalBreeding);
    }

    public static void handlePlayerTradedWithVillager(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.VILLAGER_TRADE, ConfigProxy::getVillagerTradeCooldown, ConfigProxy::getVillagerTrade);
    }

    public static void handlePlayerUsedShears(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.SHEARING, ConfigProxy::getShearingCooldown, ConfigProxy::getShearing);
    }

    public static void handlePlayerSpawnedChicken(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.SPAWNING_BABY_CHICKEN, ConfigProxy::getBabyChickenSpawningCooldown, ConfigProxy::getBabyChickenSpawning);
    }

    public static void handlePlayerUsedItem(ServerPlayer player, ItemStack itemStack)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            if (s instanceof IPersistentSanity ps)
            {
                ResourceLocation dim = player.level().dimension().location();

                for (ConfigItem citem : ConfigProxy.getItems(dim))
                {
                    if (!itemStack.is(ForgeRegistries.ITEMS.getValue(citem.m_name)))
                        continue;

                    if (!ConfigProxy.getIdToItemCat(dim).containsKey(citem.m_cat))
                    {
                        SanityMod.LOGGER.warn("player " + player.getDisplayName().getString() + " used " + citem.m_name + " from category " + citem.m_cat + ", but no such category is present");
                        return;
                    }

                    ConfigItemCategory cat = ConfigProxy.getIdToItemCat(dim).get(citem.m_cat);
                    if (cat.m_cd <= 0)
                    {
                        addSanity(s, citem.m_sanity, player);
                        return;
                    }

                    Map<Integer, Integer> itemCds = ps.getItemCooldowns();

                    if (!itemCds.containsKey(citem.m_cat) || itemCds.get(citem.m_cat) <= 0)
                    {
                        addSanity(s, citem.m_sanity, player);
                    }
                    else
                    {
                        int timePassed = cat.m_cd - itemCds.get(citem.m_cat);
                        addSanity(s, citem.m_sanity * MathHelper.clampNorm((float)timePassed / cat.m_cd), player);
                    }

                    itemCds.put(citem.m_cat, cat.m_cd);

                    return;
                }
            }

            if (itemStack.isEdible())
                handlePlayerAte(player, itemStack);
        });
    }

    public static void handlePlayerFishedItem(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(
                player,
                ActiveSanitySources.FISHING,
                ConfigProxy::getFishingCooldown,
                ConfigProxy::getFishing
        );
    }

    public static void handlePlayerMinedBlock(ServerPlayer player, BlockPos blockPos, BlockState blockState, Block block, boolean correctTool)
    {
        if (player == null)
            return;

        ServerLevel level = player.serverLevel();
        LevelChunk levelChunk = level.getChunkAt(blockPos);

        if (player.isCreative() || player.isSpectator())
        {
            levelChunk.getCapability(SanityLevelChunkProvider.CAP).ifPresent(sl ->
            {
                sl.getArtificiallyPlacedBlocks().remove(blockPos);
                levelChunk.setUnsaved(true);
            });
            return;
        }

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            if (s instanceof IPersistentSanity ps)
            {
                ResourceLocation dim = level.dimension().location();

                for (ConfigBrokenBlock cbblock : ConfigProxy.getBrokenBlocks(dim))
                {
                    if (!(cbblock.m_isTag && blockState.getTags().anyMatch(tag -> tag.location().equals(cbblock.m_name)) ||
                            block.equals(ForgeRegistries.BLOCKS.getValue(cbblock.m_name))))
                    {
                        continue;
                    }

                    if (cbblock.m_toolRequired && !correctTool)
                        return;

                    if (!ConfigProxy.getIdToBrokenBlockCat(dim).containsKey(cbblock.m_cat))
                    {
                        SanityMod.LOGGER.warn("player " + player.getDisplayName().getString() + " mined " + cbblock.m_name + " from category " + cbblock.m_cat + ", but no such category is present");
                        return;
                    }

                    // skip if artifically placed and block needs to be naturally gend
                    if (cbblock.m_naturallyGend)
                    {
                        AtomicBoolean flag = new AtomicBoolean(false);
                        levelChunk.getCapability(SanityLevelChunkProvider.CAP).ifPresent(sl ->
                        {
                            if (sl.getArtificiallyPlacedBlocks().remove(blockPos))
                            {
                                flag.set(true);
                                levelChunk.setUnsaved(true);
                            }
                        });
                        if (flag.get())
                            return;
                    }

                    ConfigBrokenBlockCategory cat = ConfigProxy.getIdToBrokenBlockCat(dim).get(cbblock.m_cat);
                    if (cat.m_cd <= 0)
                    {
                        addSanity(s, cbblock.m_sanity, player);
                        return;
                    }

                    Map<Integer, Integer> brokenBlockCds = ps.getBrokenBlocksCooldowns();

                    if (!brokenBlockCds.containsKey(cbblock.m_cat) || brokenBlockCds.get(cbblock.m_cat) <= 0)
                    {
                        addSanity(s, cbblock.m_sanity, player);
                    }
                    else
                    {
                        int timePassed = cat.m_cd - brokenBlockCds.get(cbblock.m_cat);
                        addSanity(s, cbblock.m_sanity * MathHelper.clampNorm((float)timePassed / cat.m_cd), player);
                    }

                    brokenBlockCds.put(cbblock.m_cat, cat.m_cd);

                    return;
                }
            }
        });
    }

    public static void handlePlayerTrampledFarmland(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            addSanity(s, ConfigProxy.getFarmlandTrample(player.level().dimension().location()), player);
        });
    }

    public static void handlePlayerPottedFlower(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            handleActiveSourceForPlayer(
                    player,
                    ActiveSanitySources.POTTING_FLOWER,
                    ConfigProxy::getPottingFlowerCooldown,
                    ConfigProxy::getPottingFlower
            );
        });
    }

    public static void handlePlayerChangedDimensions(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            addSanity(s, ConfigProxy.getChangedDimension(player.level().dimension().location()), player);
        });
    }

    public static void handlePlayerStruckByLightning(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            addSanity(s, ConfigProxy.getStruckByLightning(player.level().dimension().location()), player);
        });
    }
}