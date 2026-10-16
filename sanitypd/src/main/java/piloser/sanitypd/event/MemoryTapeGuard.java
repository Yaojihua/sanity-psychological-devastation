package piloser.sanitypd.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.mixin.MixinServerPlayerSeenCredits;
import piloser.sanitypd.net.PacketHandler;

/**
 * Server half of the memory tape's protection: a player watching the tape takes no damage, like creative
 * mode.
 *
 * <h2>Transient on purpose</h2>
 * A plain map, not saved and not a capability, exactly like the hint state that backs Command
 * Hallucination: it describes "right now", and persisting it would only mean a player could log out
 * invulnerable and come back that way.
 *
 * <h2>The expiry is not decoration</h2>
 * The script runs for about seventy seconds. If a client dies, is killed or loses connection mid-tape, no
 * "I am done" message ever arrives; without the expiry that player would keep the protection forever. The
 * window is deliberately generous so a slow machine finishing the script is never cut off early.
 *
 * <h2>What "like creative" means here</h2>
 * Every source is refused except those the game itself lets through invulnerability - the void and
 * {@code /kill} - which also kill a creative player.
 */
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MemoryTapeGuard
{
    /** Safety window in ticks: the script is roughly 70 s, so 150 s is comfortably past its end. */
    public static final int EXPIRY_TICKS = 20 * 150;

    /** Players currently watching the tape, with the ticks left before the protection lapses on its own. */
    private static final Map<UUID, Integer> WATCHING = new HashMap<>();

    /** Last poem flag pushed to each player, so the packet is sent when it changes and not every tick. */
    private static final Map<UUID, Boolean> POEM_SENT = new HashMap<>();

    /** Counts server ticks up to one second; the poem flag cannot change faster than that. */
    private static int s_poemPoll;

    private MemoryTapeGuard() {}

    /** Called from the packet handler: the client reporting that the tape started or ended. */
    public static void set(ServerPlayer player, boolean watching)
    {
        if (watching)
            WATCHING.put(player.getUUID(), EXPIRY_TICKS);
        else
            WATCHING.remove(player.getUUID());
    }

    /** Whether this player is inside the protection window. */
    public static boolean isWatching(ServerPlayer player)
    {
        return WATCHING.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        tickExpiry();
        tickPoemPush();
    }

    /** Counts the protection windows down; the loop is skipped entirely when nobody is watching. */
    private static void tickExpiry()
    {
        if (WATCHING.isEmpty())
            return;

        Iterator<Map.Entry<UUID, Integer>> it = WATCHING.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;

            if (left <= 0)
                it.remove();
            else
                entry.setValue(left);
        }
    }

    /** Whether vanilla's own record says this player has watched the end poem. */
    public static boolean hasSeenPoem(ServerPlayer player)
    {
        // The flag is private with no getter, so it is read through the accessor mixin the third voice already
        // uses. Never throws: a mixin that failed to apply means "not seen", which is the loud direction - the
        // player is refused and told so, rather than a gate that silently does not exist.
        try
        {
            return ((MixinServerPlayerSeenCredits) player).sanitypd$hasSeenCredits();
        }
        catch (Throwable t)
        {
            SanityMod.LOGGER.warn("[MEMORY] could not read vanilla's poem flag, treating it as not seen: {}",
                    t.toString());

            return false;
        }
    }

    /** Forgets what was last pushed for this player, so the next join always sends the flag again. */
    public static void forget(UUID player)
    {
        POEM_SENT.remove(player);
    }

    /**
     * Pushes the poem flag to every player whose value differs from the last one sent.
     *
     * <p>Polled once a second rather than event driven: vanilla sets {@code seenCredits} while the credits
     * play, and no event announces it. Without this a player who finished the credits would have to reconnect
     * before the tapes opened for them.
     */
    private static void tickPoemPush()
    {
        if (++s_poemPoll < 20)
            return;

        s_poemPoll = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null)
            return;

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            boolean seen = hasSeenPoem(player);

            if (!Boolean.valueOf(seen).equals(POEM_SENT.put(player.getUUID(), seen)))
                PacketHandler.sendPoemSeen(player, seen);
        }
    }

    /**
     * The immunity itself.
     *
     * <p>Refusing the attack before it is processed is what a creative player experiences; cancelling here
     * leaves health, effects and knockback untouched, which is the point of a cutscene.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event)
    {
        if (WATCHING.isEmpty())
            return;
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!isWatching(player))
            return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return;

        event.setCanceled(true);
    }

    /**
     * Releases the protection the moment the watcher dies.
     *
     * <p>The client closes the tape on death and says so, but the server must not depend on that message
     * arriving: a player who lost it would respawn still carrying the tape's immunity until the expiry ran out.
     * Dying inside the tape is a supported outcome - the void and {@code /kill} are deliberately let through -
     * so this is the ordinary path, not an edge case.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
            WATCHING.remove(player.getUUID());
    }
}
