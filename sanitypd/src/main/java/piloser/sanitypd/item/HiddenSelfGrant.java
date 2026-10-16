package piloser.sanitypd.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The one-time allowance behind the hidden fragment of the player's own name.
 *
 * <h2>What it decides</h2>
 * After the end poem has been watched, a player who adds an inner line that is <b>nothing but their own game
 * name</b> is handed this fragment instead of the line: the command is cancelled silently - no success
 * message, and the text never reaches the custom hint file. Once the allowance is spent the very same text is
 * an ordinary line again, which is why this branch decides nothing else.
 *
 * <h2>Where the rule itself lives</h2>
 * In {@link HiddenWordGrant}, together with the macaron trigger: this class is only the name of one trigger
 * plus the vocabulary the rest of the mod already calls it by. Kept because both the wiring gate and the
 * handover notes name it, and because "the player's own name" deserves a name of its own.
 */
public final class HiddenSelfGrant
{
    private HiddenSelfGrant() {}

    /** This trigger, for callers that want the shared view of it. */
    public static HiddenWordGrant.Trigger trigger()
    {
        return HiddenWordGrant.Trigger.OWN_NAME;
    }

    /**
     * Whether this text is nothing but the sender's own game name.
     *
     * @see HiddenWordGrant.Trigger#matches(ServerPlayer, String)
     */
    public static boolean mentionsOwnName(Entity sender, String text)
    {
        return sender instanceof ServerPlayer player && text != null && trigger().matches(player, text);
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
