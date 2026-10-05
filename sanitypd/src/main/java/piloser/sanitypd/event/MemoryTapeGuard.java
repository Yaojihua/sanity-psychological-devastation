package piloser.sanitypd.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import piloser.sanitypd.SanityMod;

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
        if (event.phase != TickEvent.Phase.END || WATCHING.isEmpty())
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
}
