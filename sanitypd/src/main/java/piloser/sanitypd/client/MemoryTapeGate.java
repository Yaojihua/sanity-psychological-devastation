package piloser.sanitypd.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.item.ItemRegistry;

/**
 * The two conditions a memory tape has to satisfy before it plays.
 *
 * <h2>1. The end poem has to have been watched</h2>
 * The flag is vanilla's own {@code seenCredits}, read on the server (it is private and never synced) and
 * pushed here by {@code net.PoemSeenPacket}. Nothing here re-derives it: the third voice unlocked itself with
 * a per-save marker file plus that same vanilla flag, and a second definition of "the poem was seen" is
 * exactly the kind of duplicate that drifts.
 *
 * <h2>2. The fragments have to be watched in story order</h2>
 * Doubt, Longing, Bewilderment, The Name - the four fragments in the order the item registry declares. A
 * fragment may be watched once the one before it has been watched <b>through</b>, which is what
 * {@link MemoryTapeRecord} already records for the "can be skipped" rule: one file per fragment, written when
 * the tape reaches full white. So the order rule needs no storage of its own.
 *
 * <h2>3. Which fragments were watched is answered to the server</h2>
 * Whether this save has watched all four fragments is decided on the server, out of a mask this client has
 * to send it: a server cannot read the marker files, and they are what "watched" means. The client used to
 * send that mask at one moment only - when a tape reached its end - so a save whose four fragments were
 * already watched before that report existed told the server nothing, and the hidden fragment was never
 * handed over. The server announces the allowance on join and on respawn, which is a moment the client is
 * known to be connected and to know which world it is in, so that announcement is now answered with the
 * mask as well.
 *
 * <p>A second mask travels with it: which <b>hidden</b> fragments this save has watched through. The word
 * triggers do not all share one prerequisite - the vessel's is "the inner void's fragment was watched" - and
 * those markers are client-side files too, so the same report answers for both masks, always together.
 *
 * <h2>How a refusal is shown</h2>
 * In the action bar, above the hotbar - the same place and the same single line vanilla uses to say a bed
 * cannot be slept in. The wording, one language key per line.
 */
@OnlyIn(Dist.CLIENT)
public final class MemoryTapeGate
{
    /** "You must witness a certain story first." */
    public static final String NEED_POEM = "gui.sanitypd.memory.need_poem";

    /** "Your knowledge of It is not yet complete..." */
    public static final String OUT_OF_ORDER = "gui.sanitypd.memory.out_of_order";

    /** What the server last said about vanilla's poem flag. False until told otherwise. */
    private static boolean s_poemSeen;

    /**
     * What the server last said about the hidden fragment's allowance: true while this save has not spent it.
     *
     * <p>Kept false until told otherwise, so a client that never hears from a server behaves exactly as it did
     * before the allowance existed.
     *
     * <p>Reported as well as stored. The client does not act on it - the server decides whether the hidden
     * fragment is handed over, and it says so through {@link #consumeExcuse()} - but the announcement is the
     * moment the client is known to be connected, so it is also when the client answers with its watched
     * fragments.
     */
    private static boolean s_hiddenAllowance;

    /**
     * One-shot excuse for the current command: set by the server half when it hands the fragment over, and
     * consumed by the client half of that same command so it does not close the game.
     *
     * <p>In single player both halves run in one process, which is what makes this exact rather than a race:
     * the server half runs first and the client half of the same command sees the flag already set. It is the
     * <b>only</b> thing that may keep the client quiet about the name: the client half runs only for the
     * player sitting at this computer, and for him the server half has already decided.
     */
    private static boolean s_excused;

    private MemoryTapeGate() {}

    /**
     * The server's announcement of the allowance, answered with the fragments this save has watched through.
     *
     * <p>Sent on join and on respawn, so the server's copy of the mask is refreshed in every session instead
     * of only when a tape happens to be watched to its end. That difference is what decides whether the hidden
     * fragment is ever handed over in a save whose four fragments were watched in an earlier session.
     */
    public static void onServerAllowance(boolean available)
    {
        setHiddenAllowance(available);
        reportWatchedMask();
    }

    /** Tells the server which story <b>and</b> hidden fragments this save has watched through. */
    public static void reportWatchedMask()
    {
        piloser.sanitypd.net.PacketHandler.CHANNEL_INSTANCE.sendToServer(
                new piloser.sanitypd.net.MemoryProgressPacket(MemoryTapeRecord.watchedMask(),
                        MemoryTapeRecord.watchedHiddenMask()));
    }

    /** Told by the server (see net.HiddenAllowancePacket). */
    public static void setHiddenAllowance(boolean available)
    {
        if (available != s_hiddenAllowance)
            SanityMod.LOGGER.info("[MEMORY] the hidden fragment's allowance is now {}",
                    available ? "available" : "spent");

        s_hiddenAllowance = available;
    }

    /**
     * Whether the hidden fragment may still be earned here, as far as the client has been told.
     *
     * <p>Carried for the log line the diagnostic side prints next to the mask it reads back from the server:
     * "the allowance is still available, but the server does not have the mask" is the whole of the failure
     * this pairing exists to make visible.
     */
    public static boolean hiddenAllowance()
    {
        return s_hiddenAllowance;
    }

    /** Records that this command's name was paid for with the fragment, not with a crash. */
    public static void excuseThisCommand()
    {
        s_excused = true;
    }

    /**
     * Forgets any excuse left over from an earlier command.
     *
     * <p>Called once at the start of every hint command, because the excuse is only ever consumed by the client
     * half of the <b>same</b> command - and in the one case where it is set, that half never runs: the server
     * half hands the fragment over and returns before the client dispatch. Without this, the flag from the
     * first attempt would still be armed on the second one, and the second attempt would be quietly let
     * through instead of ending the way it must.
     */
    public static void clearExcuse()
    {
        s_excused = false;
    }

    /** Reads and clears the one-shot excuse. */
    public static boolean consumeExcuse()
    {
        boolean excused = s_excused;
        s_excused = false;

        return excused;
    }

    /**
     * Called from the packet handler whenever the server's answer differs from the last one it sent.
     */
    public static void setPoemSeen(boolean seen)
    {
        if (seen != s_poemSeen)
            SanityMod.LOGGER.info("[MEMORY] the end poem has {} been watched (server record)", seen ? "now" : "not");

        s_poemSeen = seen;
    }

    /** Whether the end poem has been watched, as far as the client has been told. */
    public static boolean poemSeen()
    {
        return s_poemSeen;
    }

    /** The fragment that has to be watched through first, or null when this one is first or unknown. */
    public static String previousOf(String scriptKey)
    {
        return ItemRegistry.previousFragmentScriptKey(scriptKey);
    }

    /** Whether this fragment may be watched now: it is the first, or the one before it has been watched. */
    public static boolean mayWatch(String scriptKey)
    {
        String previous = previousOf(scriptKey);

        return previous == null || MemoryTapeRecord.hasPlayed(previous);
    }

    /** Refuses in the action bar, the way vanilla refuses a bed. */
    public static void notice(String langKey)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null)
            mc.player.displayClientMessage(Component.translatable(langKey), true);
    }
}
