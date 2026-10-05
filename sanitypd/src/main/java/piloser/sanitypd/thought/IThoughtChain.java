package piloser.sanitypd.thought;

import net.minecraft.world.Container;

/**
 * A player's thought chain: the eighteen slots behind the "Thought Chain" screen.
 *
 * <h2>Why a player-only capability instead of a field on sanity</h2>
 * The chain is <b>player data</b>: it must survive death, never drop, never be visible to another player,
 * and be readable by the client for tooltips. The sanity capability is attached to <b>every living
 * entity</b>, is synced as a four-float packet and is rebuilt on respawn; hanging eighteen item stacks on
 * it would tax every mob in the world for no reason. So the chain gets its own capability, attached to
 * players only, on which {@code PlayerEvent.Clone} copies the contents across death.
 *
 * <h2>Two rows, one container</h2>
 * Slots {@code 0..8} are the worldview row and {@code 9..17} the methodology row, in that order, because
 * that is the order the screen lays them out. Nothing else about the layout is stored: the slot rules
 * (category, one of each thought) live in the slot class, so a change there cannot desynchronise from a
 * change here.
 *
 * <h2>Counts travel separately from the items</h2>
 * Slot contents are synced by the container protocol while the screen is open, so the client always sees
 * what is <b>in</b> the chain. It does <b>not</b> see the chain when the screen is closed, and the tooltip
 * needs the six per-type counts wherever the item is (inventory, a chest, JEI). Those six numbers are
 * therefore pushed by {@link #countOf} consumers through {@code ThoughtChainPacket}: a few bytes per
 * change, instead of eighteen item stacks per sync.
 */
public interface IThoughtChain
{
    /** Slots per row. */
    int ROW_SIZE = 9;

    /** Total slots: the worldview row followed by the methodology row. */
    int SIZE = ROW_SIZE * 2;

    /** The backing container, {@link #SIZE} slots. */
    Container container();

    /** Index of the first slot of a category's row. */
    static int rowStart(ThoughtCategory category)
    {
        return category == ThoughtCategory.WORLDVIEW ? 0 : ROW_SIZE;
    }

    /**
     * How many thoughts of a type the chain currently holds.
     *
     * <p>On the server this scans the container (the authoritative answer). On the client it returns the
     * last counts pushed by the server, because the client cannot see the chain while the screen is shut.
     */
    int countOf(ThoughtType type);

    /** The six counts in {@link ThoughtType} order, for the server to send. */
    int[] counts();

    /** Client side: adopt counts sent by the server. */
    void applyCounts(int[] counts);

    /** Copies another chain's contents into this one (used when the owner respawns). */
    void copyFrom(IThoughtChain other);

    /**
     * Ids of the mindsets that are active right now, in <b>activation order</b>: the one that turned on first
     * comes first, and the list closes its gaps when one turns off.
     *
     * <p>Order is not derivable from the counts - two mindsets can be driven by counts that changed in the
     * same tick - so the server maintains the list and sends it to the client along with the counts. Every
     * entry is an id, so the list survives a restart and stays readable if a mindset is ever renamed away.
     */
    java.util.List<String> activeMindsetIds();

    /** Client side: adopt the activation order sent by the server. */
    void applyActiveMindsetIds(java.util.List<String> ids);

    /**
     * Whether <b>this</b> item is in the chain right now.
     *
     * <p>Deliberately separate from {@link #countOf}: the tier follows the type count, but "not equipped"
     * follows the item. Two thoughts can feed the same type, so counting alone would let an unequipped sibling
     * show an effect it is not granting.
     */
    boolean holds(net.minecraft.world.item.Item item);

    /**
     * Registry names of the items currently in the chain, for the client.
     *
     * <p>Ids rather than references: this value crosses the network, and an id survives a rename on either
     * side. The order is the slot order, deduplicated.
     */
    java.util.List<String> equippedItemIds();

    /** Client side: adopt the equipped-item set sent by the server. */
    void applyEquippedItemIds(java.util.List<String> ids);
}
