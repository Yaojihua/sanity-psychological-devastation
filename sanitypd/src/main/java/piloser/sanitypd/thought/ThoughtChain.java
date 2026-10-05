package piloser.sanitypd.thought;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import piloser.sanitypd.net.ThoughtChainPacket;

/**
 * Default {@link IThoughtChain} implementation: an eighteen-slot container plus the six counts the client
 * keeps for tooltips.
 *
 * <p>Slot contents are written one entry per non-empty slot ({@code Items} list, {@code Slot} byte plus the
 * stack), rather than through {@code ContainerHelper}: that helper wants a raw {@code NonNullList} here, and
 * writing the list ourselves keeps slots stable when the row size changes (missing slots read as empty).
 * Everything else - the category
 * rule, the one-of-each rule - belongs to the slot class and is deliberately not duplicated here.
 *
 * <p>Writing any slot notifies the container listener, which is the single place that pushes fresh counts
 * to the owning client. Nothing polls: the screen, the tooltip and JEI all read whatever the last push
 * said, and pushes happen exactly when the contents change - with one exception, the load itself, which is
 * not a change and is skipped by {@link #m_loading}.
 */
public class ThoughtChain implements IThoughtChain
{
    private final SimpleContainer m_container = new SimpleContainer(SIZE);

    /** Client-side mirror of the server's counts; unused (and never authoritative) on the server. */
    private final int[] m_counts = new int[ThoughtType.values().length];

    /**
     * Active mindsets, oldest first. Kept on both sides: the server recomputes it, the client is handed the
     * result. Storing ids (not constants) is what makes the order survive a restart.
     */
    private final java.util.List<String> m_activeOrder = new java.util.ArrayList<>();

    /** Client-side mirror of which items sit in the chain; unused (and never authoritative) on the server. */
    private final java.util.List<String> m_equippedIds = new java.util.ArrayList<>();

    /** The owning player, bound when the capability is attached. */
    @Nullable
    private Player m_owner;

    /**
     * True while {@link #deserializeNBT} writes the saved slots, so the container listener does not mistake a
     * load for a change.
     *
     * <p>This guard is not an optimisation. A player's capabilities are deserialized while the player entity
     * is being read from the save, which happens <b>before</b> the server attaches that player's connection;
     * a push from there dereferences a null connection and the join dies with {@code Loading entity NBT}
     * (first seen 2026-10-02: a world made by 1.2.7, opened by 1.4.3). Loading is not a change, and the counts
     * still reach the client: the join and respawn handlers push them once the player is live (see
     * {@code EventHandler#syncThoughtChain}).
     */
    private boolean m_loading;

    /** Tag holding every non-empty slot of the chain. */
    private static final String ITEMS_TAG = "Items";

    /** Tag holding one stack's slot index inside {@link #ITEMS_TAG}. */
    private static final String SLOT_TAG = "Slot";

    /** Tag holding the active mindset ids, oldest first. */
    private static final String ORDER_TAG = "Active";

    public ThoughtChain()
    {
        // Fired by every setItem/removeItem; the only place counts are pushed from.
        m_container.addListener(container -> pushCounts());
    }

    /** Binds the owning player. Called once, right after the capability is attached. */
    public void setOwner(Player owner)
    {
        m_owner = owner;
    }

    @Override
    public Container container()
    {
        return m_container;
    }

    /**
     * Whether this instance belongs to the server's copy of the player, and therefore holds the truth.
     *
     * <p>Deliberately not {@code level.isClientSide}: during capability attachment and during the death
     * copy the level may not be reachable yet, while the entity class already tells us which side we are
     * on (the client's player is a {@code LocalPlayer}, never a {@code ServerPlayer}).
     */
    private boolean authoritative()
    {
        return m_owner instanceof ServerPlayer;
    }

    @Override
    public int countOf(ThoughtType type)
    {
        if (!authoritative())
            return m_counts[type.ordinal()];

        int total = 0;

        for (int slot = 0; slot < SIZE; slot++)
        {
            ItemStack stack = m_container.getItem(slot);

            if (stack.getItem() instanceof ThoughtItem thought && thought.types().contains(type))
                total += stack.getCount();
        }

        return total;
    }

    @Override
    public int[] counts()
    {
        ThoughtType[] types = ThoughtType.values();
        int[] out = new int[types.length];

        for (int i = 0; i < types.length; i++)
            out[i] = countOf(types[i]);

        return out;
    }

    @Override
    public boolean holds(net.minecraft.world.item.Item item)
    {
        if (item == null)
            return false;

        if (!authoritative())
            return m_equippedIds.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());

        for (int slot = 0; slot < SIZE; slot++)
        {
            if (m_container.getItem(slot).is(item))
                return true;
        }

        return false;
    }

    @Override
    public java.util.List<String> equippedItemIds()
    {
        if (!authoritative())
            return java.util.List.copyOf(m_equippedIds);

        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();

        for (int slot = 0; slot < SIZE; slot++)
        {
            net.minecraft.world.item.ItemStack stack = m_container.getItem(slot);

            if (!stack.isEmpty())
                ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }

        return java.util.List.copyOf(ids);
    }

    @Override
    public void applyEquippedItemIds(java.util.List<String> ids)
    {
        m_equippedIds.clear();
        m_equippedIds.addAll(ids);
    }

    @Override
    public java.util.List<String> activeMindsetIds()
    {
        return java.util.Collections.unmodifiableList(m_activeOrder);
    }

    @Override
    public void applyActiveMindsetIds(java.util.List<String> ids)
    {
        m_activeOrder.clear();
        m_activeOrder.addAll(ids);
    }

    /**
     * Recomputes the activation order from the counts.
     *
     * <p>Survivors keep their old relative position, then newcomers are appended in registration order. That
     * is exactly the owner's rule: the first thought that reaches a threshold takes the first slot, later ones
     * follow, and a mindset that turns off leaves no hole because the list is rebuilt from survivors.
     *
     * <p>Registration order is the tie-break for mindsets that become active in the same recomputation, which
     * makes the result deterministic instead of depending on hash or scan order.
     */
    private void recomputeActiveOrder()
    {
        java.util.List<String> next = new java.util.ArrayList<>();

        for (String id : m_activeOrder)
        {
            if (isActive(id))
                next.add(id);
        }

        for (Mindset mindset : Mindsets.all())
        {
            if (isActive(mindset.id()) && !next.contains(mindset.id()))
                next.add(mindset.id());
        }

        m_activeOrder.clear();
        m_activeOrder.addAll(next);
    }

    /** Whether a mindset currently meets its threshold; unknown ids count as inactive. */
    private boolean isActive(String id)
    {
        return Mindsets.byId(id).filter(m -> countOf(m.type()) >= m.threshold()).isPresent();
    }

    @Override
    public void applyCounts(int[] counts)
    {
        for (int i = 0; i < m_counts.length && i < counts.length; i++)
            m_counts[i] = counts[i];
    }

    @Override
    public void copyFrom(IThoughtChain other)
    {
        Container source = other.container();

        for (int slot = 0; slot < SIZE; slot++)
            m_container.setItem(slot, source.getItem(slot).copy());

        // The activation order is part of the chain's state rather than something to re-derive, so it travels
        // with the contents: without this, respawning would reshuffle the third row of a player who already
        // had several mindsets active.
        applyActiveMindsetIds(other.activeMindsetIds());
    }

    /** Pushes the counts to the owning client; a no-op on the client itself and while the save is loading. */
    private void pushCounts()
    {
        // Nothing changed - the slots are being read back from the save - and the owner is not reachable yet.
        if (m_loading)
            return;

        if (m_owner instanceof ServerPlayer serverPlayer)
        {
            // Order first: the client is about to be handed both, and the screen reads the order while the
            // tooltip reads the counts.
            recomputeActiveOrder();
            ThoughtChainPacket.send(serverPlayer, this);
        }
    }

    /**
     * Persisted by the capability provider.
     *
     * <p>Only non-empty slots are written, each with its own {@code Slot} index, so the format survives a row
     * being resized and never depends on how many slots exist today.
     */
    public void serializeNBT(CompoundTag tag)
    {
        ListTag items = new ListTag();

        for (int slot = 0; slot < SIZE; slot++)
        {
            ItemStack stack = m_container.getItem(slot);

            if (stack.isEmpty())
                continue;

            CompoundTag entry = new CompoundTag();
            entry.putByte(SLOT_TAG, (byte) slot);
            stack.save(entry);
            items.add(entry);
        }

        tag.put(ITEMS_TAG, items);

        // The activation order is state, not a derivation: two mindsets can be driven by counts that changed
        // in the same tick, so re-deriving it after a restart could silently reorder the third row.
        net.minecraft.nbt.ListTag order = new net.minecraft.nbt.ListTag();

        for (String id : m_activeOrder)
            order.add(net.minecraft.nbt.StringTag.valueOf(id));

        tag.put(ORDER_TAG, order);
    }

    /** Counterpart of {@link #serializeNBT}; a slot the save does not mention simply stays empty. */
    public void deserializeNBT(CompoundTag tag)
    {
        // Every setItem below notifies the container listener. A load must not be pushed as a change: the
        // owner is a server player without a connection at this point (see m_loading).
        m_loading = true;

        try
        {
            m_container.clearContent();

            ListTag items = tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND);

            for (int i = 0; i < items.size(); i++)
            {
                CompoundTag entry = items.getCompound(i);
                int slot = entry.getByte(SLOT_TAG) & 255;

                if (slot < SIZE)
                    m_container.setItem(slot, ItemStack.of(entry));
            }

            m_activeOrder.clear();
            net.minecraft.nbt.ListTag order = tag.getList(ORDER_TAG, Tag.TAG_STRING);

            for (int i = 0; i < order.size(); i++)
                m_activeOrder.add(order.getString(i));
        }
        finally
        {
            m_loading = false;
        }
    }
}
