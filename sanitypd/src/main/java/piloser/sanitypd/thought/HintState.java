package piloser.sanitypd.thought;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.player.Player;

/**
 * The server's copy of "a non-mild inner line is on screen right now", reported by the client
 * ({@code HintStateReporter} -> {@code HintStatePacket}).
 *
 * <h2>Why this is not saved and not a capability field</h2>
 * It describes what is being <b>drawn this moment</b>, so it is worthless one tick later: persisting it
 * would let a player quit mid-line and come back with an attack bonus he is not seeing, and putting it in
 * the sanity capability would put it into the sync packet and the save for no gain. A map that is dropped
 * when the player logs out is the whole requirement.
 *
 * <h2>Why it expires on its own</h2>
 * A log line is cleared by the client on its own timer, and a lost or late packet must not leave the bonus
 * stuck on, so the reported state is only trusted for {@link #MAX_AGE_TICKS}. The client also re-sends the
 * value when the world changes, so the "stuck" case needs both a lost packet and a quiet client to happen.
 */
public final class HintState
{
    /** How long a report is trusted, in ticks. Comfortably longer than the gap between re-sends. */
    public static final int MAX_AGE_TICKS = 200;

    /** Per player: whether a non-mild line is on screen, and the game time it was reported at. */
    private static final Map<UUID, long[]> REPORTS = new ConcurrentHashMap<>();

    private HintState() {}

    /** Server side: records what the client just reported. */
    public static void set(Player player, boolean active)
    {
        if (player == null)
            return;

        REPORTS.put(player.getUUID(), new long[] { active ? 1L : 0L, player.level().getGameTime() });
    }

    /** Whether a non-mild inner line is currently being drawn for this player. */
    public static boolean isNonMildHintOnScreen(Player player)
    {
        if (player == null)
            return false;

        long[] report = REPORTS.get(player.getUUID());

        if (report == null || report[0] == 0L)
            return false;

        return player.level().getGameTime() - report[1] <= MAX_AGE_TICKS;
    }

    /** Drops a player's report; called when he logs out so the map cannot grow without bound. */
    public static void forget(Player player)
    {
        if (player != null)
            REPORTS.remove(player.getUUID());
    }
}
