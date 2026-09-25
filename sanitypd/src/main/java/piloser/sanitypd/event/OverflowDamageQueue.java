package piloser.sanitypd.event;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.combat.SanityCombat;
import piloser.sanitypd.damage.SanityDamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Deferred queue for the real-damage overflow of psychic damage.
 *
 * <h2>Why the damage must be delayed</h2>
 * The overflow used to be applied synchronously, nested inside {@code SanityCombat.onLivingHurt}:
 * the outer {@code player.hurt(psychic)} triggered the overflow, which nested another
 * {@code hurt(crawler_explosion)} that dropped health to zero and called {@code die()} with the
 * crawler explosion source. When that nested call returned, the tail of the outer
 * {@code LivingEntity.hurt} saw {@code isDeadOrDying()} and called {@code die()} a second time with
 * the psychic source, producing a second, generic death message that also overwrote the death
 * screen. Mania deaths never had this problem because they do not nest a hurt call.
 *
 * <h2>Fix</h2>
 * Apply the overflow real damage once at the end of the tick, the same approach used by
 * {@link CrawlerExplosionHandler}. The outer psychic {@code hurt()} then resolves while health is
 * still above zero, so its tail does not call {@code die()}, and the target dies exactly once with
 * the correct message. This also fixes a second symptom of the same root cause: killing an inner mob
 * with psychic damage triggered death twice, which could run {@code LivingDropsEvent} twice.
 *
 * <p>This handler runs at {@link EventPriority#LOWEST}, after {@link CrawlerExplosionHandler}, so a
 * crawler explosion still completes "drain sanity, queue overflow, resolve overflow" within one tick
 * and the delay is not perceptible.
 */
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OverflowDamageQueue
{
    /** One pending overflow hit. {@code forcedPsychicOverflow} always uses the psychic_overflow type (inner mob branch). */
    private record Entry(LivingEntity target, float amount, Entity attacker, boolean forcedPsychicOverflow) {}

    private static final List<Entry> QUEUE = new ArrayList<>();

    private OverflowDamageQueue() {}

    /**
     * Queues the overflow part of a psychic hit for <b>normal mobs and players</b>. The damage type
     * is chosen when the queue is resolved, through {@link SanityCombat#overflowTypeFor} (attacker is
     * a Screaming Crawler means {@code crawler_explosion}, otherwise {@code psychic_overflow}).
     */
    public static void enqueue(LivingEntity target, float overflow, Entity attacker)
    {
        if (target == null || overflow <= 0.0f)
            return;

        QUEUE.add(new Entry(target, overflow, attacker, false));
    }

    /**
     * Queues overflow that always uses the {@code psychic_overflow} type, for the <b>inner mob</b>
     * branch: they have no sanity, so the whole psychic hit becomes real damage and the type does not
     * follow the crawler death message.
     */
    public static void enqueuePsychicOverflow(LivingEntity target, float overflow, Entity attacker)
    {
        if (target == null || overflow <= 0.0f)
            return;

        QUEUE.add(new Entry(target, overflow, attacker, true));
    }

    /** Resolves the whole queue once at the end of the tick. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty())
            return;

        // Snapshot before clearing: if resolving an entry produces new psychic damage (for example an
        // overflow kill that chains into another crawler), those new entries stay queued for the next
        // tick instead of mutating the list being iterated.
        List<Entry> snapshot = new ArrayList<>(QUEUE);
        QUEUE.clear();

        for (Entry entry : snapshot)
        {
            LivingEntity target = entry.target();

            // The target died earlier in this tick from another hit: do not hit it again
            if (target == null || !target.isAlive())
                continue;

            if (entry.forcedPsychicOverflow())
                SanityDamageTypes.dealPsychicOverflow(target, entry.amount(), entry.attacker());
            else
                SanityCombat.applyOverflowDamage(target, entry.amount(), entry.attacker());
        }
    }
}
