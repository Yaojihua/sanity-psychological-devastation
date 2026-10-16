package piloser.sanitypd.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import piloser.sanitypd.SanityMod;

/**
 * The one-time allowance behind the hidden memory fragment.
 *
 * <h2>What it decides</h2>
 * Speaking the hidden name normally ends in a crash or in lethal damage - and a pocket full of macarons can
 * buy a player out of that. This check runs <b>before both</b>: a player who has watched all four story
 * fragments through, and who has never spent this save's allowance, receives the hidden fragment instead of
 * any punishment at all.
 *
 * <h2>Per player, per save - and why the flags live where they do</h2>
 * Both flags are written into the player's own persistent data ({@code playerdata/} inside the save), so the
 * allowance is not inherited by a new world, is not shared between players in one world, and behaves the same
 * on a dedicated server. Nothing is added to the mod's capabilities, so no save structure changes.
 *
 * <p>The "watched all four" half comes from the client: the watch markers are per-save files on the client
 * (see {@code client.MemoryTapeRecord}), which a server cannot read. The client reports the mask as it
 * changes, and the server keeps the last value it was told.
 */
public final class HiddenMemoryGrant
{
    /** Player-data key: this save's allowance has been spent. */
    private static final String GRANTED = "sanitypd_hidden_tape_granted";

    /** Player-data key: bitmask of the story fragments the client has reported watching. */
    private static final String MASK = "sanitypd_memory_watched";

    /**
     * Player-data key: bitmask of the <b>hidden</b> fragments the client has reported watching.
     *
     * <p>A second mask rather than more bits in the first one: the story mask's meaning is fixed by its own
     * comment - one bit per entry of {@code ItemRegistry.MEMORY_FRAGMENT_ORDER} - and the hidden fragments are
     * deliberately not in that order. Widening the story mask would have made its {@link #ALL} constant stop
     * meaning "the story is finished", which every allowance and one gate read.
     */
    private static final String HIDDEN_MASK = "sanitypd_hidden_watched";

    /** All four story fragments watched: one bit per entry of {@code ItemRegistry.MEMORY_FRAGMENT_ORDER}. */
    public static final int ALL = 0b1111;

    /**
     * Every bit of {@link #HIDDEN_MASK}: one per entry of {@code ItemRegistry.HIDDEN_FRAGMENT_ORDER}.
     *
     * <p>Computed rather than written out, because a hand-written constant is exactly the trap {@link #ALL}'s
     * own comment warns about: add a fragment, forget to widen the constant, and the new fragment's bit is
     * silently dropped on the way in.
     */
    public static final int ALL_HIDDEN = (1 << ItemRegistry.HIDDEN_FRAGMENT_ORDER.size()) - 1;

    /**
     * Every hidden fragment <b>except the vessel's</b>: the mask half of "the eight fragments other than the
     * vessel's", which is when the void lifts its limits on the player's understanding (see
     * {@code client.VoidUnbound}).
     *
     * <p>Written as "all of them minus one named bit" rather than as a literal, so that the vessel is excluded
     * by <b>what it is</b> and not by the position it happens to hold in the registry order.
     */
    public static final int ALL_HIDDEN_EXCEPT_VESSEL = ALL_HIDDEN
            & ~(1 << ItemRegistry.HIDDEN_FRAGMENT_ORDER.indexOf(ItemRegistry.LOST_MEMORY_FRAGMENT_CONTAINER));

    /** Language key of the line the player is shown when the fragment is handed over. */
    public static final String GRANTED_KEY = "gui.sanitypd.memory.hidden_granted";

    private HiddenMemoryGrant() {}

    /** The client reporting which story fragments it has watched through in this save. */
    public static void remember(ServerPlayer player, int mask)
    {
        player.getPersistentData().putInt(MASK, mask & ALL);
    }

    /** The client reporting which hidden fragments it has watched through in this save. */
    public static void rememberHidden(ServerPlayer player, int mask)
    {
        player.getPersistentData().putInt(HIDDEN_MASK, mask & ALL_HIDDEN);
    }

    /** Whether the client has reported this hidden fragment watched through in this save. */
    public static boolean hiddenFragmentWatched(ServerPlayer player, RegistryObject<Item> fragment)
    {
        int bit = ItemRegistry.HIDDEN_FRAGMENT_ORDER.indexOf(fragment);

        return bit >= 0 && (player.getPersistentData().getInt(HIDDEN_MASK) & (1 << bit)) != 0;
    }

    /**
     * Whether the inner void's fragment has been watched through in this save - the vessel trigger's
     * prerequisite ("after reading the void memory fragment").
     *
     * <p>A named method rather than a bit expression at the call site, so that this condition has exactly one
     * definition, next to the mask it reads.
     */
    public static boolean voidFragmentWatched(ServerPlayer player)
    {
        return hiddenFragmentWatched(player, ItemRegistry.LOST_MEMORY_FRAGMENT_VOID);
    }

    /**
     * Spends the allowance and hands over the hidden fragment, when this player is entitled to it.
     *
     * @return {@code true} when the fragment was given and the caller must not punish or spare anybody
     */
    public static boolean tryGrant(ServerPlayer player)
    {
        CompoundTag data = player.getPersistentData();

        if (data.getBoolean(GRANTED) || (data.getInt(MASK) & ALL) != ALL)
            return false;

        data.putBoolean(GRANTED, true);

        ItemStack stack = new ItemStack(ItemRegistry.LOST_MEMORY_FRAGMENT_HIDDEN.get());

        if (!player.getInventory().add(stack))
            player.drop(stack, false);

        player.displayClientMessage(Component.translatable(GRANTED_KEY), false);

        // The client half of the hidden name has to know at once: it is the half that closes the game, and the
        // allowance it just spent is the only reason this command did not end there.
        piloser.sanitypd.net.PacketHandler.sendHiddenAllowance(player, false);

        SanityMod.LOGGER.info("[MEMORY] the hidden fragment was handed to {} - this save's allowance is spent",
                player.getGameProfile().getName());

        return true;
    }

    /** Whether this player may still earn the fragment in this save. */
    public static boolean allowanceAvailable(ServerPlayer player)
    {
        return !player.getPersistentData().getBoolean(GRANTED);
    }

    /** Whether this player has already spent the allowance, for the probe and the logs. */
    public static boolean alreadySpent(ServerPlayer player)
    {
        return player.getPersistentData().getBoolean(GRANTED);
    }

    /**
     * Whether the client has reported <b>all four</b> story fragments watched through in this save.
     *
     * <p>Exposed because it is not only this fragment's prerequisite: the inner void's word trigger is gated
 * on exactly the same condition (the requirement), and having one definition of "the story is
     * finished" is what stops the two from drifting apart.
     */
    public static boolean allFragmentsWatched(ServerPlayer player)
    {
        return (player.getPersistentData().getInt(MASK) & ALL) == ALL;
    }
}
