package piloser.sanitypd.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Ground navigation for the screaming crawler: it pins the waypoint arrival radius into a range
 * where the mob can actually walk.
 *
 * <p>Vanilla {@code GroundPathNavigation} sets {@code maxDistanceToWaypoint} to
 * {@code bbWidth * bbWidth * 3.0f}. The crawler is 0.92 wide, giving 2.27 blocks, which is larger
 * than the 1-block waypoint spacing, so every waypoint counts as already reached and the mob stands
 * still.
 *
 * <p>Note that {@code PathNavigation#followThePath()} writes {@code maxDistanceToWaypoint} directly
 * as a field on every tick, recomputing it as {@code bbWidth / 2} for mobs wider than 0.75 (0.46 for
 * this mob). It is not a setter call, so overriding the getter has no effect. Raising the radius
 * above the fixed 0.5 distance between the waypoint and the mob is not a safe fix either:
 * {@code advance()} would then fire before the mob truly arrives and could skip waypoints. Widening
 * the radius was tried and did not make the crawler move, so the constant stays at the vanilla
 * initial value. This class only affects movement and changes no values or mechanics.
 */
public class CrawlerPathNavigation extends GroundPathNavigation
{
    /**
     * Waypoint arrival radius.
     *
     * <p>Waypoints are 1 block apart, so the radius must stay clearly below 1 to avoid skipping
     * them. 0.5 is the vanilla {@code PathNavigation} initial value and is stable for mobs of this
     * size.
     *
     * <p>A larger radius (0.6) was tried and reverted: it did not make the crawler move, and a
     * radius above the fixed 0.5 distance to the waypoint lets {@code advance()} fire before the mob
     * truly arrives.
     */
    public static final float WAYPOINT_RADIUS = 0.5f;

    public CrawlerPathNavigation(Mob mob, Level level)
    {
        super(mob, level);
        // GroundPathNavigation's constructor sets this to bbWidth^2 * 3, so it must be pinned back
        // after super(...) runs
        this.maxDistanceToWaypoint = WAYPOINT_RADIUS;
    }

    @Override
    protected Path createPath(java.util.Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy,
                              float maxDistanceToWaypoint)
    {
        // This call chain can also reset it, so pin the value again to be safe
        this.maxDistanceToWaypoint = WAYPOINT_RADIUS;
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, maxDistanceToWaypoint);
    }
}
