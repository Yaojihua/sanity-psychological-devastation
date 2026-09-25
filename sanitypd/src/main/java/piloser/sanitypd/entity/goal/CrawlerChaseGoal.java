package piloser.sanitypd.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import piloser.sanitypd.entity.ScreamingCrawler;

import java.util.EnumSet;

/**
 * Chase goal for the screaming crawler: <b>self-driven movement plus A\* global routing</b>.
 *
 * <h2>Why not vanilla {@code MeleeAttackGoal}</h2>
 * {@code RandomStrollGoal} path requests do move the crawler, but with {@code MeleeAttackGoal} most
 * sampled ticks showed no displacement at all: the "walk along the path automatically" chain is
 * unreliable. A\* itself is fine, though - only {@code followThePath} is broken.
 *
 * <h2>Approach</h2>
 * <ol>
 *   <li><b>Far away / routing needed</b>: call {@code createPath(target, 0)} to get an <b>A\* path</b>,
 *       then read the waypoints ourselves ({@code getNextNodePos()}) and drive there with
 *       {@code MoveControl}, calling {@code advance()} once a node is reached.</li>
 *   <li><b>Close</b> (&le; 8 blocks): charge straight at the target position (zombie-style, the most
 *       reliable and with no delay).</li>
 *   <li><b>Local obstacle fallback</b>: when blocked ahead, jump / raise the wanted Y / look for a gap
 *       at &plusmn;45 degrees (step-up height is handled by {@link ScreamingCrawler#maxUpStep()} = 1.1).</li>
 * </ol>
 * Global routing comes from A\* ({@code createPath}) while "can it actually move" is guaranteed by
 * driving {@code MoveControl} ourselves, so the broken {@code followThePath} is never used.
 *
 * <h2>Why we advance the path ourselves</h2>
 * {@code followThePath}'s "advance on arrival" reads fields rather than getters and its observable
 * behaviour does not match what happens in game. Here the driver of the movement is also the one that
 * decides arrival and advances the path, with no unverified mechanics involved.
 */
public class CrawlerChaseGoal extends Goal
{
    /** Beyond this squared distance the goal drives movement; closer targets are left to CrawlerSwellGoal / MeleeAttackGoal. */
    private static final double CHASE_MIN_DIST_SQR = 9.0d;      // 3 blocks
    /** At or below this squared distance, charge straight ahead without requesting a path (cheaper and more responsive). */
    private static final double DIRECT_DIST_SQR = 64.0d;      // 8 blocks
    /** Path recompute interval (ticks). */
    private static final int REPATH_INTERVAL = 10;
    /** Distance at which the current waypoint counts as reached. */
    private static final double NODE_REACHED_DIST = 0.9d;
    /** How far up to probe when testing whether the crawler can step up. */
    private static final double STEP_PROBE_UP = 1.15d;
    /** Horizontal distance of the probe used for obstacle checks. */
    private static final double PROBE_AHEAD = 0.8d;
    /** Initial jump velocity (same order as a vanilla mob jumping one block). */
    private static final double JUMP_VELOCITY = 0.42d;

    private final ScreamingCrawler m_crawler;
    private final double m_speed;

    private Path m_path;
    private int m_repathCooldown;
    private double m_lastX, m_lastZ;
    private int m_stuckTicks;

    public CrawlerChaseGoal(ScreamingCrawler crawler, double speed)
    {
        m_crawler = crawler;
        m_speed = speed;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        LivingEntity target = m_crawler.getTarget();
        return target != null && target.isAlive()
                && m_crawler.distanceToSqr(target) > CHASE_MIN_DIST_SQR;
    }

    @Override
    public boolean canContinueToUse()
    {
        return canUse();
    }

    @Override
    public void start()
    {
        m_path = null;
        m_repathCooldown = 0;
        m_stuckTicks = 0;
        m_lastX = m_crawler.getX();
        m_lastZ = m_crawler.getZ();
        // Stop vanilla navigation: it would fight this goal over the MoveControl
        m_crawler.getNavigation().stop();
    }

    @Override
    public void stop()
    {
        m_path = null;
        m_crawler.getNavigation().stop();
        m_crawler.getMoveControl().setWantedPosition(m_crawler.getX(), m_crawler.getY(), m_crawler.getZ(), 0.0d);
    }

    @Override
    public void tick()
    {
        LivingEntity target = m_crawler.getTarget();
        if (target == null)
            return;

        m_crawler.setSpeed((float)m_speed);
        m_crawler.getLookControl().setLookAt(target, 30.0f, 30.0f);

        double distSqr = m_crawler.distanceToSqr(target);
        Vec3 pos = m_crawler.position();

        // ---- Destination: target position up close, A* waypoint when far away ----
        double destX, destY, destZ;

        if (distSqr <= DIRECT_DIST_SQR)
        {
            m_path = null;                       // no path needed up close
            destX = target.getX();
            destY = target.getY();
            destZ = target.getZ();
        }
        else
        {
            if (m_path == null || --m_repathCooldown <= 0)
            {
                m_path = computePath(target);
                m_repathCooldown = REPATH_INTERVAL;
            }

            BlockPos node = null;
            if (m_path != null && !m_path.isDone())
            {
                // Advance waypoints ourselves: close enough to the current node -> advance
                // (does not rely on followThePath)
                BlockPos cur = m_path.getNextNodePos();
                double dx = cur.getX() + 0.5d - m_crawler.getX();
                double dz = cur.getZ() + 0.5d - m_crawler.getZ();
                if (dx * dx + dz * dz <= NODE_REACHED_DIST * NODE_REACHED_DIST)
                {
                    m_path.advance();
                    if (!m_path.isDone())
                        cur = m_path.getNextNodePos();
                }
                if (!m_path.isDone())
                    node = cur;
            }

            if (node == null)
            {
                // No path found (or path finished) -> fall back to charging straight at the target,
                // never stand still
                destX = target.getX();
                destY = target.getY();
                destZ = target.getZ();
            }
            else
            {
                destX = node.getX() + 0.5d;
                destY = node.getY();
                destZ = node.getZ() + 0.5d;
            }
        }

        driveTowards(pos, destX, destY, destZ, target);
    }

    /** Requests an A\* path; returns {@code null} on failure. */
    private Path computePath(LivingEntity target)
    {
        try
        {
            return m_crawler.getNavigation().createPath(target, 0);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** Drives towards (destX,destY,destZ), including local obstacle handling (jump / raise Y / look for a gap). */
    private void driveTowards(Vec3 pos, double destX, double destY, double destZ, LivingEntity target)
    {
        double dx = destX - m_crawler.getX();
        double dz = destZ - m_crawler.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4d)
            return;
        dx /= len;
        dz /= len;

        double wantedY = destY;
        boolean blocked = !canOccupy(pos.x + dx * PROBE_AHEAD, pos.y, pos.z + dz * PROBE_AHEAD);

        if (blocked)
        {
            boolean canStepUp = canOccupy(pos.x + dx * PROBE_AHEAD, pos.y + STEP_PROBE_UP, pos.z + dz * PROBE_AHEAD);
            if (canStepUp)
            {
                // One-block step: raise the wanted Y and give a small jump
                wantedY = Math.max(wantedY, pos.y + STEP_PROBE_UP);
                jumpIfPossible();
            }
            else
            {
                // Taller wall: just try to jump first
                jumpIfPossible();
            }

            // Look for a gap at +-45 degrees (simplified wall following; the A* path usually routes
            // around already, this only handles nearby bumps)
            double[] side = pickSideDirection(pos, dx, dz);
            if (side != null)
            {
                dx = side[0];
                dz = side[1];
            }
        }

        // Stuck detection: force a path recompute after 1 second without displacement (lets A* replan)
        double movedX = m_crawler.getX() - m_lastX;
        double movedZ = m_crawler.getZ() - m_lastZ;
        if (movedX * movedX + movedZ * movedZ < 0.0025d)   // less than 0.05 blocks
        {
            if (++m_stuckTicks > 20)
            {
                m_stuckTicks = 0;
                m_path = null;
                m_repathCooldown = 0;
            }
        }
        else
        {
            m_stuckTicks = 0;
        }
        m_lastX = m_crawler.getX();
        m_lastZ = m_crawler.getZ();

        m_crawler.getMoveControl().setWantedPosition(
                m_crawler.getX() + dx * 4.0d, wantedY, m_crawler.getZ() + dz * 4.0d, m_speed);
    }

    /** Whether the given position (including the crawler's own hitbox) is free. */
    private boolean canOccupy(double x, double y, double z)
    {
        AABB box = m_crawler.getBoundingBox().move(x - m_crawler.getX(), y - m_crawler.getY(), z - m_crawler.getZ());
        return m_crawler.level().noCollision(m_crawler, box);
    }

    /** Jumps once (only while on the ground). */
    private void jumpIfPossible()
    {
        if (!m_crawler.onGround())
            return;
        Vec3 d = m_crawler.getDeltaMovement();
        m_crawler.setDeltaMovement(d.x, JUMP_VELOCITY, d.z);
        m_crawler.hasImpulse = true;
    }

    /** Turns +-45 degrees to either side to find a clear direction; returns null when both are blocked. */
    private double[] pickSideDirection(Vec3 pos, double dx, double dz)
    {
        double best = -1.0d;
        double[] bestDir = null;

        for (double angle : new double[] { Math.PI / 4, -Math.PI / 4 })
        {
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double rx = dx * cos - dz * sin;
            double rz = dx * sin + dz * cos;

            int clear = 0;
            for (int step = 1; step <= 4; step++)
            {
                if (canOccupy(pos.x + rx * step, pos.y, pos.z + rz * step))
                    clear++;
                else
                    break;
            }

            if (clear > best)
            {
                best = clear;
                bestDir = new double[] { rx, rz };
            }
        }

        return best >= 1 ? bestDir : null;
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }
}
