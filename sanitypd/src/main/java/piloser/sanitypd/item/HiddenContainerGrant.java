package piloser.sanitypd.item;

import net.minecraft.server.level.ServerPlayer;

/**
 * The one-time allowance behind the hidden fragment of the vessel.
 *
 * <h2>What it decides</h2>
 * Once the inner void's fragment has been watched through, a player who adds an inner line that is <b>nothing
 * but</b> "容器" / "vessel" (trimmed, case-insensitive) is handed this fragment instead of the line: the
 * command is cancelled silently, nothing is stored, and once the allowance is spent the same text is an
 * ordinary line again.
 *
 * <h2>Where the rule itself lives</h2>
 * In {@link HiddenWordGrant}, together with the own-name, macaron and inner-void triggers; this class is only
 * the vessel's name for it, so the four cannot drift apart. The words it accepts are written out there as
 * literals, and its prerequisite - "the inner void's fragment was watched" - is a named method on
 * {@link HiddenMemoryGrant}, so there is exactly one definition of that condition.
 */
public final class HiddenContainerGrant
{
    private HiddenContainerGrant() {}

    /** This trigger, for callers that want the shared view of it. */
    public static HiddenWordGrant.Trigger trigger()
    {
        return HiddenWordGrant.Trigger.CONTAINER;
    }

    /** Whether this text is nothing but the vessel's name. */
    public static boolean mentionsContainer(ServerPlayer player, String text)
    {
        return text != null && trigger().matches(player, text);
    }

    /**
     * Spends the allowance and hands the fragment over, when this player is entitled to it.
     *
     * @return {@code true} when the fragment was given and the command must end without storing the text
     */
    public static boolean tryGrant(ServerPlayer player, String text)
    {
        return HiddenWordGrant.tryGrant(player, text, trigger());
    }

    /**
     * The same decision with the poem flag supplied by the caller.
     *
     * @see HiddenWordGrant#tryGrant(ServerPlayer, String, HiddenWordGrant.Trigger, boolean)
     */
    public static boolean tryGrant(ServerPlayer player, String text, boolean poemSeen)
    {
        return HiddenWordGrant.tryGrant(player, text, trigger(), poemSeen);
    }

    /** Whether this player may still earn the fragment in this save. */
    public static boolean allowanceAvailable(ServerPlayer player)
    {
        return HiddenWordGrant.allowanceAvailable(player, trigger());
    }

    /** Whether this player has already spent the allowance, for the probe and the logs. */
    public static boolean alreadySpent(ServerPlayer player)
    {
        return HiddenWordGrant.alreadySpent(player, trigger());
    }
}
