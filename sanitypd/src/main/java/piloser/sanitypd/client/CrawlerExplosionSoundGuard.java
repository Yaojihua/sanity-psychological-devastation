package piloser.sanitypd.client;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.sound.SoundRegistry;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Suppresses the vanilla creeper explosion sound when the screaming crawler detonates.
 *
 * <h2>Why this indirection is needed</h2>
 * The vanilla {@code SoundEvents.GENERIC_EXPLODE} (volume 4.0) is played on the <b>client</b>:
 * <pre>
 *   ClientPacketListener.handleExplosion
 *     -> Explosion.finalizeExplosion(true)
 *        -> if (this.level.isClientSide)
 *              this.level.playLocalSound(x, y, z, GENERIC_EXPLODE, BLOCKS, 4.0F, ...);
 * </pre>
 * It is played by the client itself after receiving {@code ClientboundExplodePacket}, so no server
 * side event can intercept it. That packet does not say who caused the explosion either, so the
 * client cannot tell directly - a mark-and-match approach is the only option.
 *
 * <h2>The mark</h2>
 * {@code ScreamingCrawler.explodeCrawler()} plays {@link SoundRegistry#SCREAMING_CRAWLER_EXPLODE} via
 * {@code level().playSound(...)} <b>before</b> the explosion, so the client receives our sound packet
 * first and the explosion packet second (same tick, same position, TCP preserves order). Then:
 * <ol>
 *   <li>our own explosion sound is heard -> its position and tick are recorded as a pending mark;</li>
 *   <li>the vanilla {@code GENERIC_EXPLODE} is heard next and matches a mark exactly
 *       -> it is identified as the crawler's blast and dropped with {@code PlaySoundEvent#setSound(null)},
 *       <b>and that mark is consumed</b>.</li>
 * </ol>
 *
 * <h2>Three safety invariants: vanilla creepers must never be affected</h2>
 * <ol>
 *   <li><b>A mark must exist before anything can match</b>: with no crawler detonation in flight,
 *       every {@code GENERIC_EXPLODE} is passed through untouched, so vanilla creepers, TNT, beds and
 *       end crystals are all unaffected.</li>
 *   <li><b>A mark is consumed on use</b> (removed right after the match): one crawler detonation can
 *       suppress at most one vanilla explosion sound. Even if a vanilla creeper detonates at the same
 *       moment, its sound is not swallowed because the mark has already been spent.</li>
 *   <li><b>Tight window, very short distance</b>: the mark must be within
 *       {@link #MATCH_WINDOW_TICKS} ticks (0.5 seconds) and
 *       {@link #MATCH_RADIUS_SQR} squared blocks (0.5 blocks), while the real offset is only 0.0625
 *       blocks (our sound is at {@code (x,y,z)}, the vanilla one at {@code (x, y+0.0625, z)}). A vanilla
 *       creeper would have to detonate in the same block within that same window.</li>
 * </ol>
 * The whole chain prefers false negatives over false positives: if a mark never arrives (dropped or
 * reordered packet) nothing is suppressed at all, and the worst case is that the crawler's vanilla
 * explosion sound is still audible.
 *
 * <h2>Why {@code setSound(null)} and not {@code setCanceled(true)}</h2>
 * Disassembling {@code SoundEngine.play} shows this at the very start:
 * <pre>
 *    9: invokestatic ForgeHooksClient.playSound(SoundEngine, SoundInstance) -> SoundInstance
 *   13: aload_1
 *   14: ifnull 667          &lt;- a null return skips playback entirely
 * </pre>
 * So {@code setSound(null)} is the path that reliably takes effect.
 */
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CrawlerExplosionSoundGuard
{
    /**
     * Match window in ticks (0.5 seconds).
     *
     * <p>Originally 3 ticks, since both packets are sent in the same tick and TCP preserves order, so
     * they should arrive within 0 to 1 tick. It was widened to 0.5 seconds while diagnosing a report
     * of our explosion sound being inaudible, one possible cause being a failed match.
     *
     * <p>Safety is unaffected: false positives are prevented by the mark having to be within
     * {@link #MATCH_RADIUS_SQR} and by <b>consuming the mark on use</b> (one detonation suppresses at
     * most one sound), not by a narrow window. A vanilla creeper would still have to detonate in the
     * same block within the same 0.5 seconds.
     */
    private static final int MATCH_WINDOW_TICKS = 10;
    /** Squared match distance: 0.5 blocks. The real offset is only 0.0625 blocks, so this is generous. */
    private static final double MATCH_RADIUS_SQR = 0.25d;

    /** Pending crawler detonation marks: {x, y, z, tick}. Removed on match; one mark suppresses one sound. */
    private static final List<double[]> PENDING_MARKS = new ArrayList<>();

    private static long s_tick;

    private CrawlerExplosionSoundGuard() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event)
    {
        try
        {
            SoundInstance sound = event.getSound();

            if (sound == null)
                return;

            ResourceLocation location = sound.getLocation();

            if (location == null)
                return;

            // (1) Our own explosion sound -> record a pending mark
            if (location.equals(SoundRegistry.SCREAMING_CRAWLER_EXPLODE.get().getLocation()))
            {
                PENDING_MARKS.add(new double[] { sound.getX(), sound.getY(), sound.getZ(), s_tick });
                return;
            }

            // (2) Vanilla explosion sound: suppress only on an exact, unexpired mark match, and consume
            //     that mark immediately. Without a mark it passes through untouched, so vanilla creepers,
            //     TNT and beds are all unaffected.
            if (location.equals(SoundEvents.GENERIC_EXPLODE.getLocation()))
            {
                for (int i = 0; i < PENDING_MARKS.size(); i++)
                {
                    double[] mark = PENDING_MARKS.get(i);

                    if (s_tick - (long)mark[3] > MATCH_WINDOW_TICKS)
                        continue;

                    double dx = mark[0] - sound.getX();
                    double dy = mark[1] - sound.getY();
                    double dz = mark[2] - sound.getZ();

                    if (dx * dx + dy * dy + dz * dz > MATCH_RADIUS_SQR)
                        continue;

                    // Match: drop this sound and spend the mark (one detonation affects at most one sound)
                    PENDING_MARKS.remove(i);
                    event.setSound(null);
                    return;
                }
            }
        }
        catch (Throwable ignored)
        {
            // Client-side presentation only; it must never be allowed to affect the game
        }
    }

    /** Periodically drops expired marks so the list cannot grow without bound. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        s_tick++;

        if (s_tick % 10 == 0 && !PENDING_MARKS.isEmpty())
            PENDING_MARKS.removeIf(mark -> s_tick - (long)mark[3] > MATCH_WINDOW_TICKS);
    }
}
