package piloser.sanityprobe;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.HashMap;
import java.util.Map;

/**
 * "Command layer" counters for crawler movement: mixins increment them at method entry and
 * every {@link #WINDOW} navigation ticks a single summary line is logged.
 *
 * <p>Each counter only records a fact; a zero value points at the link that never ran:
 * <ul>
 *   <li>{@code navTicks} - {@code PathNavigation#tick()} calls (zero: the AI tick chain never reached navigation)</li>
 *   <li>{@code moveCalls} - {@code MoveControl#setWantedPosition} calls (zero: nothing ever ordered a move)</li>
 *   <li>{@code moveTicks}/{@code moveTicksWanted} - {@code MoveControl#tick()} calls, and how many of them had a wanted position (zero: move control not ticked / command overwritten to WAIT)</li>
 *   <li>{@code navStops} - {@code PathNavigation#stop()} calls (high value: something keeps clearing the path)</li>
 *   <li>{@code navMoveTo} - {@code moveTo} calls, with the success count in parentheses (zero: pathing never started)</li>
 * </ul>
 */
public final class PathTrace
{
    private PathTrace() {}

    private static final int WINDOW = 200;

    private static int s_navTicks, s_moveCalls, s_moveTicks, s_moveTicksWanted, s_navStops, s_navMoveTo, s_navMoveToOk;
    private static String s_lastStopCaller = "-";
    private static String s_lastMoveToCaller = "-";
    private static double s_lastWantedX, s_lastWantedY, s_lastWantedZ, s_lastSpeedMod;
    private static int s_lines;

    private static final int MAX_LINES = 10;

    private static void tickWindow()
    {
        if (++s_navTicks == 0) return;   // no-op placeholder: the real window check happens in maybeFlush
    }

    public static void navTick(Mob mob)
    {
        s_navTicks++;
        maybeFlush(mob);
    }

    public static void navStop(Mob mob)
    {
        s_navStops++;
        s_lastStopCaller = caller();
    }

    public static void navMoveTo(Mob mob, double x, double y, double z, double speed, boolean ok)
    {
        s_navMoveTo++;
        if (ok) s_navMoveToOk++;
        s_lastMoveToCaller = caller();
    }

    public static void moveSetWanted(Mob mob, double x, double y, double z, double speed)
    {
        s_moveCalls++;
        s_lastWantedX = x; s_lastWantedY = y; s_lastWantedZ = z; s_lastSpeedMod = speed;
    }

    public static void moveTick(Mob mob, double wx, double wy, double wz, double speedMod)
    {
        s_moveTicks++;
        if (s_moveCalls > 0) s_moveTicksWanted++;
    }

    private static void maybeFlush(Mob mob)
    {
        if (s_navTicks % WINDOW != 0)
            return;
        if (s_lines++ >= MAX_LINES)
            return;
        try
        {
            double dist = -1;
            if (mob.getTarget() != null) dist = Math.sqrt(mob.distanceToSqr(mob.getTarget()));
            ProbeLog.log("AI-27-CMD", "window=" + WINDOW + "t"
                    + " navTicks=" + s_navTicks
                    + " navStops=" + s_navStops
                    + " navMoveTo=" + s_navMoveTo + "(ok=" + s_navMoveToOk + ")"
                    + " moveCalls=" + s_moveCalls
                    + " moveTicks=" + s_moveTicks + "(wanted=" + s_moveTicksWanted + ")"
                    + " | lastWanted=" + String.format("%.2f,%.2f,%.2f", s_lastWantedX, s_lastWantedY, s_lastWantedZ)
                    + " mobPos=" + String.format("%.2f,%.2f,%.2f", mob.getX(), mob.getY(), mob.getZ())
                    + " speedMod=" + String.format("%.3f", s_lastSpeedMod)
                    + " dist=" + (dist < 0 ? "-" : String.format("%.2f", dist))
                    + " | lastStop=" + s_lastStopCaller
                    + " | lastMoveTo=" + s_lastMoveToCaller);
        }
        catch (Throwable t)
        {
            ProbeLog.log("AI-27-CMD", "flush error: " + t);
        }
    }

    /** First 3 useful stack frames; skips this probe, the JDK, the Forge event bus and Sponge. */
    private static String caller()
    {
        try
        {
            StringBuilder sb = new StringBuilder();
            int taken = 0;
            for (StackTraceElement el : new Throwable().getStackTrace())
            {
                String cn = el.getClassName();
                if (cn.startsWith("piloser.sanityprobe")) continue;
                if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")) continue;
                if (cn.startsWith("net.minecraftforge.eventbus")) continue;
                if (cn.startsWith("net.minecraftforge.common.ForgeHooks")) continue;
                if (cn.startsWith("org.spongepowered")) continue;
                sb.append(cn.substring(cn.lastIndexOf('.') + 1)).append('#').append(el.getMethodName()).append(' ');
                if (++taken >= 3) break;
            }
            return sb.length() == 0 ? "?" : sb.toString().trim();
        }
        catch (Throwable ignored) {}
        return "?";
    }
}
