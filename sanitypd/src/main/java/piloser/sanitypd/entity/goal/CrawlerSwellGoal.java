package piloser.sanitypd.entity.goal;

import piloser.sanitypd.entity.ScreamingCrawler;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Self-destruct fuse goal for the screaming crawler, using the same checks as the creeper's
 * {@code SwellGoal}.
 *
 * <p>Ignition conditions, all required:
 * <ul>
 *   <li>there is an attack target and it is alive</li>
 *   <li>distance to the target is {@code <= 9.0} squared, i.e. 3 blocks
 *       (vanilla writes {@code distanceToSqr < 9.0})</li>
 *   <li>the target is visible ({@code getSensing().hasLineOfSight})</li>
 * </ul>
 * When satisfied the fuse advances by one per tick, otherwise it falls back by one.
 *
 * <p>{@link #tick()} must not reset the fuse merely because the target is null. Combined with the
 * renewal condition in {@link #canUse()} that would deadlock: with no target the goal never starts,
 * but if it is entered another way (renewal, or ignition by a command) the first tick would push the
 * fuse back to -1, so the renewal path could never run to completion and the fuse would never reach 30.
 * The correct vanilla {@code SwellGoal} semantics are used instead: leave the fuse alone when there
 * is no target, and only push it back when a target exists but is too far away or not visible.
 */
public class CrawlerSwellGoal extends Goal
{
    private final ScreamingCrawler m_crawler;

    public CrawlerSwellGoal(ScreamingCrawler crawler)
    {
        this.m_crawler = crawler;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        net.minecraft.world.entity.LivingEntity target = m_crawler.getTarget();
        // Do not add `m_crawler.getSwellDir() > 0 ||` to this condition. It was tried as a renewal
        // rule and caused the crawler to stand still at 3-7 blocks: once the fuse was lit and the
        // target stayed within 7 blocks (see the > 49.0 check in tick()), the goal renewed forever and
        // held Goal.Flag.MOVE, while start() calls navigation.stop() and never moves. The lower
        // priority MeleeAttackGoal was starved as a result.
        //
        // The current form matches vanilla SwellGoal#canUse() exactly: ignite only when the target is
        // close, visible and valid. Once the target leaves the 3 block radius, canContinueToUse()
        // returns false, stop() resets the fuse to -1 and releases MOVE, so MeleeAttackGoal can chase
        // again - the vanilla creeper chase/ignite rhythm.
        return isValidTarget(target) && m_crawler.distanceToSqr(target) < 9.0
                && m_crawler.getSensing().hasLineOfSight(target);
    }

    /**
     * Whether the target is valid: alive, not carrying inner immunity, and not a player whose sanity has
     * climbed back above {@code ScreamingCrawler#TRACKING_CANCEL_SANITY} (75%).
     *
     * <p>A player with {@code sanitypd:inner_immunity} must not be chased and blown up by the crawler;
     * that effect is defined as "the crawler will not deliberately explode".
     *
     * <p>The sanity gate is checked here as well as in {@code InnerEntity#setTarget} so the fuse can never
     * be lit against a recovered player, even if a target was assigned by some other path.
     */
    private boolean isValidTarget(net.minecraft.world.entity.LivingEntity target)
    {
        if (target == null || !target.isAlive())
            return false;

        if (target instanceof net.minecraft.world.entity.player.Player player
                && player.hasEffect(piloser.sanitypd.effect.EffectRegistry.INNER_IMMUNITY.get()))
            return false;

        return !piloser.sanitypd.entity.InnerEntity.isTrackingCancelled(target);
    }

    /**
     * Renewal check equivalent to vanilla {@code SwellGoal#canContinueToUse()}.
     *
     * <p>Vanilla returns {@code this.target != null && this.creeper.getSwellDir() > 0;}. This class
     * does not override that method, so the default implementation calls {@link #canUse()} again.
     * If {@link #canUse()} also renewed on a lit fuse, the goal would never release
     * {@code Goal.Flag.MOVE} and would starve the lower priority walk goals such as
     * MeleeAttackGoal and StrollGoal.
     *
     * <p>Ending the goal as soon as the target is no longer valid keeps that from happening;
     * {@link #stop()} resets the fuse to -1.
     */
    @Override
    public boolean canContinueToUse()
    {
        return isValidTarget(m_crawler.getTarget()) && m_crawler.getSwellDir() > 0;
    }

    @Override
    public void start()
    {
        m_crawler.getNavigation().stop();
    }

    @Override
    public void stop()
    {
        m_crawler.setSwellDir(-1);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        net.minecraft.world.entity.LivingEntity target = m_crawler.getTarget();

        // No valid target (including a player who drank the stabilizer) - push the fuse back, do not explode.
        // Note that the fuse itself accumulates in the entity tick (ScreamingCrawler#tick does
        // m_swell += dir), so returning here is not enough: setSwellDir(-1) is required, otherwise the
        // fuse keeps counting to 30 and detonates anyway.
        if (!isValidTarget(target))
        {
            m_crawler.setSwellDir(-1);
            return;
        }

        if (m_crawler.distanceToSqr(target) > 49.0)
            m_crawler.setSwellDir(-1);
        else if (!m_crawler.getSensing().hasLineOfSight(target))
            m_crawler.setSwellDir(-1);
        else
            m_crawler.setSwellDir(1);
    }
}
