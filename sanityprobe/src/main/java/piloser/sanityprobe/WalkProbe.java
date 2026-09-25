package piloser.sanityprobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import piloser.sanityprobe.mixin.MobAccessor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side walk probe: is the crawler actually walking?
 *
 * <p>Why this must be measured in a real game: a headless dedicated server does not tick
 * entities that have no player nearby (vanilla only ticks entities inside a player's
 * simulation distance), so a "not even the zombies move" comparison run there is
 * invalid evidence and cannot be used to deny a crawler movement bug.
 *
 * <p>Every {@link #INTERVAL} ticks this logs position delta, target, fuse, navigation state
 * and the list of currently running goals - the latter shows directly whether
 * {@code CrawlerSwellGoal} (which holds the MOVE flag) starves the walk goal.
 */
public final class WalkProbe
{
    /** Only crawlers within this radius of a player are sampled. */
    private static final double RADIUS = 64.0d;
    private static final int INTERVAL = 10;

    private static int s_tick;
    private static final Map<Integer, double[]> s_lastPos = new HashMap<>();
    private static final Map<Integer, Integer> s_lastTick = new HashMap<>();
    /** One "suspected fault" verdict per crawler, to avoid log spam. */
    private static final Set<Integer> s_verdictDone = new HashSet<>();
    /** Previous roar cooldown per crawler; detects the 0 -> positive ignition edge. */
    private static final Map<Integer, Integer> s_prevRoarCd = new HashMap<>();
    /** Player UUID -> last "roar heard" game tick seen by the probe (per-player throttle evidence). */
    private static final Map<String, Long> s_lastThrottle = new HashMap<>();

    private WalkProbe() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        // Per-tick path timeline: must run before the interval return below, otherwise only
        // 10-tick granularity is visible.
        tickTimeline(event.getServer());

        if (++s_tick % INTERVAL != 0)
            return;

        try
        {
            for (ServerLevel level : event.getServer().getAllLevels())
                sampleLevel(level);

            // Per-player roar throttle table.
            checkRoarThrottle();
        }
        catch (Throwable t)
        {
            ProbeLog.log("WALK-S", "sampler error: " + t);
        }
    }

    private static void sampleLevel(ServerLevel level)
    {
        Set<Integer> seen = new HashSet<>();

        for (ServerPlayer viewer : level.players())
        {
            AABB area = viewer.getBoundingBox().inflate(RADIUS);

            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area))
            {
                if (!isCrawler(entity) || !seen.add(entity.getId()))
                    continue;

                try
                {
                    sampleCrawler(entity, viewer);
                }
                catch (Throwable t)
                {
                    ProbeLog.log("WALK-S", "crawler " + entity.getId() + " sample error: " + t);
                }
            }
        }
    }

    private static void sampleCrawler(LivingEntity crawler, ServerPlayer viewer)
    {
        int id = crawler.getId();
        double x = crawler.getX(), y = crawler.getY(), z = crawler.getZ();

        double[] last = s_lastPos.get(id);
        Integer lastTick = s_lastTick.get(id);
        int elapsed = lastTick == null ? INTERVAL : Math.max(1, s_tick - lastTick);
        double moved = last == null ? 0.0d : Math.sqrt(
                (x - last[0]) * (x - last[0]) + (y - last[1]) * (y - last[1]) + (z - last[2]) * (z - last[2]));

        s_lastPos.put(id, new double[] { x, y, z });
        s_lastTick.put(id, s_tick);

        LivingEntity target = crawler instanceof Mob mob ? mob.getTarget() : null;
        int swellDir = swellDir(crawler);
        double targetDist = target == null ? -1.0d : Math.sqrt(crawler.distanceToSqr(target));

        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(id);
        sb.append(" pos=").append(ProbeLog.fmt(x)).append(',').append(ProbeLog.fmt(y)).append(',').append(ProbeLog.fmt(z));
        sb.append(" moved/").append(elapsed).append("t=").append(ProbeLog.fmt(moved));
        sb.append(" delta=").append(ProbeLog.vec(crawler.getDeltaMovement()));
        sb.append(" swellDir=").append(swellDir);
        sb.append(" target=").append(target == null ? "null"
                : ProbeLog.safe(() -> target.getType() + "#" + target.getId()) + "@" + ProbeLog.fmt(targetDist));
        sb.append(" viewerDist=").append(ProbeLog.fmt(Math.sqrt(crawler.distanceToSqr(viewer))));
        sb.append(" walkAnimSpeed=").append(ProbeLog.fmt(crawler.walkAnimation.speed()));
        sb.append(" onGround=").append(crawler.onGround());

        // Also report the roar cooldown, to see whether the sound effect really ignites.
        int roarCd = intField(crawler, "m_roarCooldown", -1);
        sb.append(" roarCd=").append(roarCd);

        // Inner immunity: while the observer has this effect the crawler should drop its target
        // and let the fuse fall back.
        net.minecraft.world.effect.MobEffect innerImmunity = SanityReflect.effect("INNER_IMMUNITY");
        boolean viewerInnerImmune = innerImmunity != null && viewer.hasEffect(innerImmunity);
        sb.append(" viewerInnerImmune=").append(viewerInnerImmune);

        if (crawler instanceof Mob mob)
        {
            PathNavigation nav = mob.getNavigation();
            sb.append(" nav=").append(ProbeLog.safe(() -> nav.isInProgress() ? "inProgress" : (nav.isDone() ? "done" : "idle")));
            sb.append(" pathLen=").append(ProbeLog.safe(() -> nav.getPath() == null ? "-1" : String.valueOf(nav.getPath().getNodeCount())));
            sb.append(" nextNode=").append(ProbeLog.safe(() -> nav.getPath() == null ? "-1" : String.valueOf(nav.getPath().getNextNodeIndex())));
            sb.append(" moveWanted=").append(ProbeLog.safe(() -> String.valueOf(mob.getMoveControl().hasWanted())));
            sb.append(" effectiveAi=").append(mob.isEffectiveAi());
            sb.append(" noAi=").append(mob.isNoAi());
            sb.append(" goals=[").append(runningGoals(mob)).append(']');
        }

        ProbeLog.log("WALK-S", sb.toString());

        // AI-27: deep diagnostic only when the crawler has a target yet stands still ("not chasing").
        if (crawler instanceof Mob mobForAi)
            logAi27(mobForAi, target, moved);

        // AI-27-NEWPATH: path identity tracking (who keeps rebuilding the path).
        if (crawler instanceof Mob mobForPath)
            trackPathIdentity(mobForPath, moved, target);

        // CHASE-27: regression watch for the "chase + A* detour" fix.
        if (crawler instanceof Mob mobForChase && target != null)
            logChase27(mobForChase, target);

        // Roar ignition: cooldown 0 -> positive means it just roared on this tick.
        Integer previousRoar = s_prevRoarCd.put(id, roarCd);

        if (roarCd > 0 && (previousRoar == null || previousRoar == 0))
            ProbeLog.log("ROAR-S", "crawler " + id + " roar FIRED (roarCd " + previousRoar + " -> " + roarCd + ")"
                    + " target=" + (target == null ? "null" : ProbeLog.safe(() -> target.getType() + "#" + target.getId()))
                    + " viewerDist=" + ProbeLog.fmt(Math.sqrt(crawler.distanceToSqr(viewer))));

        // Verdict line: printed once per crawler, and only when something looks broken.
        boolean stuck = target != null && moved < 0.05d && ((Mob) crawler).getNavigation().isInProgress();
        boolean frozenBySwell = swellDir > 0 && target == null;
        boolean swellHogging = swellDir > 0 && target != null && targetDist > 3.5d && moved < 0.05d;
        // With inner immunity on the observer the fuse must not stay lit; that feature exists to
        // make the crawler defuse and back off.
        boolean immunityNotRespected = viewerInnerImmune && swellDir > 0;

        if (!s_verdictDone.contains(id) && (stuck || frozenBySwell || swellHogging || immunityNotRespected))
        {
            s_verdictDone.add(id);

            String verdict;
            if (immunityNotRespected)
                verdict = "the observer has the inner-immunity buff but the crawler's fuse is still lit (swellDir>0) -> immunity not respected; report this line";
            else if (frozenBySwell)
                verdict = "fuse is lit (swellDir>0) but there is no target -> if it neither retreats nor moves, check CrawlerSwellGoal#canContinueToUse";
            else if (swellHogging)
                verdict = "target is beyond 3.5 blocks yet the fuse is lit -> CrawlerSwellGoal holds the MOVE flag, MeleeAttackGoal cannot take over -> stands still";
            else
                verdict = "has a target and nav is inProgress, but moved ~0 over 10 ticks -> movement is blocked by another goal";

            ProbeLog.log("WALK-S VERDICT", "id=" + id + " " + verdict + " | goals=[" + runningGoals((Mob) crawler) + "]");
        }
    }

    /** Currently running goals, in priority order - direct evidence of who holds the MOVE flag. */
    private static String runningGoals(Mob mob)
    {
        try
        {
            GoalSelector goalSelector = ((MobAccessor) mob).sanityprobe$getGoalSelector();
            StringBuilder sb = new StringBuilder();
            goalSelector.getRunningGoals().forEach(wrapped ->
                    sb.append(wrapped.getGoal().getClass().getSimpleName()).append(' '));

            return sb.length() == 0 ? "(none)" : sb.toString().trim();
        }
        catch (Throwable t)
        {
            return "<err:" + t.getClass().getSimpleName() + ">";
        }
    }

    /** Reads sanitypd's own {@code getSwellDir()} by reflection: this probe deliberately does not
     * depend on sanitypd at compile time. */
    private static int swellDir(LivingEntity crawler)
    {
        try
        {
            Method method = crawler.getClass().getMethod("getSwellDir");
            Object value = method.invoke(crawler);
            return value instanceof Integer i ? i : -999;
        }
        catch (Throwable t)
        {
            return -999;
        }
    }

    // ==================================================================================
    // AI-27 group: why a crawler that has a target and a path still does not close in.
    //
    // Observation: a crawler that stands still keeps path nextNodeIndex at 0, while one that
    // walks advances the index. So the only question left is why the index never leaves 0.
    //
    // That cannot be measured on a headless server (entities without a nearby player are not
    // ticked there), so this group targets the three candidate causes:
    //   (1) no target at all        -> log target==null and the distance;
    //   (2) waypoint radius too small to reach the next node -> log the real distance to the
    //       next node against maxDistanceToWaypoint;
    //   (3) node type making canCutCorner refuse to advance -> log the next node's BlockPathTypes.
    // The nextNodeIndex history is kept as well, which separates "never advanced" from
    // "advanced and then pushed back to 0".
    // ==================================================================================

    /** Last nextNodeIndex seen for each crawler (tells whether the index ever moved). */
    private static final Map<Integer, Integer> s_prevNextNode = new HashMap<>();
    /** Highest nextNodeIndex ever observed for each crawler. */
    private static final Map<Integer, Integer> s_maxNextNode = new HashMap<>();
    /** Per-crawler AI-27 print counter (log spam cap). */
    private static final Map<Integer, Integer> s_ai27Count = new HashMap<>();

    private static final int AI27_MAX_LINES = 12;

    /**
     * Per-tick path timeline. Recorded once per server tick, i.e. after all AI has run, and
     * run-length encoded so that {@code null -> path -> null} flapping and the duration of each
     * state become visible:
     * <pre>
     *   [AI-27-TL] crawler=214 tl=RUNx37,NULx1,RUNx2,NULx8,RUNx11 | runs=3 nulls=2 curNodes=5 idx=0
     * </pre>
     *
     * <p>Reading it: mostly NUL means the path is cleared while nobody uses it (find the
     * {@code stop()} caller next); RUN throughout with idx stuck at 0 means the path is stable and
     * {@code advance()} has no effect; fast RUN/NUL alternation means something clears the path
     * every tick, with {@code MeleeAttackGoal} stopping on arrival as the prime suspect.
     */
    private static final Map<Integer, StringBuilder> s_timeline = new HashMap<>();
    /** Current run state (RUN/NUL) and its length, per crawler. */
    private static final Map<Integer, String> s_runState = new HashMap<>();
    private static final Map<Integer, Integer> s_runLen = new HashMap<>();
    private static int s_tlCount;
    private static final int TL_MAX_LINES = 8;
    private static final int TL_WINDOW_TICKS = 200;

    private static void tickTimeline(net.minecraft.server.MinecraftServer server)
    {
        try
        {
            if (server.getTickCount() % TL_WINDOW_TICKS == 0)
            {
                if (!s_timeline.isEmpty() && s_tlCount++ < TL_MAX_LINES)
                    flushTimeline();
                s_timeline.clear();
                s_runState.clear();
                s_runLen.clear();
            }

            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels())
                for (net.minecraft.server.level.ServerPlayer viewer : level.players())
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, viewer.getBoundingBox().inflate(48.0d)))
                    {
                        if (!isCrawler(e) || !(e instanceof Mob mob))
                            continue;

                        String state = mob.getNavigation().getPath() == null ? "NUL" : "RUN";
                        int id = mob.getId();
                        StringBuilder tl = s_timeline.get(id);
                        if (tl == null)
                        {
                            tl = new StringBuilder();
                            s_timeline.put(id, tl);
                        }

                        String prevState = s_runState.get(id);
                        int len = s_runLen.getOrDefault(id, 0);
                        if (state.equals(prevState))
                        {
                            s_runLen.put(id, len + 1);
                        }
                        else
                        {
                            if (prevState != null)
                            {
                                if (tl.length() > 0) tl.append(',');
                                tl.append(prevState).append('×').append(len);
                            }
                            s_runState.put(id, state);
                            s_runLen.put(id, 1);
                        }
                    }
        }
        catch (Throwable t)
        {
            ProbeLog.log("AI-27-TL", "timeline error: " + t);
        }
    }

    private static void flushTimeline()
    {
        for (Map.Entry<Integer, StringBuilder> en : s_timeline.entrySet())
        {
            try
            {
                int id = en.getKey();
                StringBuilder tl = new StringBuilder(en.getValue());
                String cur = s_runState.get(id);
                int len = s_runLen.getOrDefault(id, 0);
                if (cur != null)
                {
                    if (tl.length() > 0) tl.append(',');
                    tl.append(cur).append('×').append(len);
                }
                ProbeLog.log("AI-27-TL", "crawler=" + id + " window=" + TL_WINDOW_TICKS
                        + "t timeline=" + tl + "  (RUN=path present NUL=path null)");
            }
            catch (Throwable ignored) {}
        }
    }

    /**
     * Path identity tracking.
     *
     * <p>AI-27 kept seeing {@code nextIdx=0} with {@code maxNextIdxSeen=0} and {@code dNode=0.000},
     * while {@code maxWP} - written only by vanilla {@code PathNavigation#followThePath()} - stayed
     * sane. So that method does run and does advance at the waypoint, yet nextNodeIndex never
     * leaves 0. The remaining explanation is a repeatedly replaced {@code Path}: a fresh path
     * always starts at nextNodeIndex 0.
     *
     * <p>This group therefore records whether the path changed and who changed it:
     * <ul>
     *   <li>{@link #fingerprint(Path)}: node count plus first and last node coordinates;</li>
     *   <li>each change logs {@code [AI-27-NEWPATH]} with the first stack frame from
     *       {@code net.minecraft} or this mod, naming the code that rebuilt the path;</li>
     *   <li>whether the mob moved meanwhile: a path rebuilt while the mob stands still is not
     *       driven by target movement.</li>
     * </ul>
     */
    private static final Map<Integer, String> s_pathFingerprint = new HashMap<>();
    private static final Map<Integer, Integer> s_pathChanges = new HashMap<>();
    private static final Map<Integer, Integer> s_newPathLines = new HashMap<>();

    private static final int NEWPATH_MAX_LINES = 6;

    /** Path fingerprint: node count plus first and last node. Any change means a new Path. */
    private static String fingerprint(Path p)
    {
        if (p == null || p.getNodeCount() == 0)
            return "null";
        Node first = p.getNode(0);
        Node last = p.getNode(p.getNodeCount() - 1);
        return p.getNodeCount() + ":" + first.x + "," + first.y + "," + first.z
                + "->" + last.x + "," + last.y + "," + last.z;
    }

    /** First stack frame that reveals who is rebuilding the path. */
    private static String callerOf()
    {
        try
        {
            StringBuilder sb = new StringBuilder();
            int taken = 0;
            for (StackTraceElement el : new Throwable().getStackTrace())
            {
                String cn = el.getClassName();
                if (cn.startsWith("piloser.sanityprobe"))
                    continue;
                if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun."))
                    continue;
                if (cn.startsWith("net.minecraftforge.eventbus"))
                    continue;   // the Forge event bus eats several frames; do not let it take a slot
                if (cn.startsWith("net.minecraftforge.common.ForgeHooks"))
                    continue;
                sb.append(cn.substring(cn.lastIndexOf('.') + 1)).append('#').append(el.getMethodName()).append(' ');
                if (++taken >= 4)
                    break;
            }
            return sb.length() == 0 ? "?" : sb.toString().trim();
        }
        catch (Throwable ignored) {}
        return "?";
    }

    /**
     * {@code [CHASE-27]} group: regression watch for the crawler chase and pathing fix.
     *
     * <p>The fix is a global A* detour plus self-driven movement with jumping and stepping up.
     * This group logs one line per sample ({@link #INTERVAL} ticks) for every crawler that has a
     * target, so that a future regression shows up at a glance:
     * <pre>
     *   [CHASE-27] crawler=214 dist=12.4 target=player goals=[CrawlerChaseGoal] path=17 next=3 wanted=true speed=1.00
     * </pre>
     *
     * <p>Reading it: {@code goals} should list {@code CrawlerChaseGoal} while chasing, or
     * {@code CrawlerSwellGoal}/{@code MeleeAttackGoal} up close; {@code path>0} means an A* path
     * exists (the precondition for a long detour); {@code wanted=true} means the move control was
     * commanded, and nothing can move without that; a shrinking {@code dist} is the final proof
     * that the fix works.
     */
    private static final Map<Integer, Integer> s_chaseLines = new HashMap<>();
    private static final int CHASE_MAX_LINES = 20;

    private static void logChase27(Mob mob, LivingEntity target)
    {
        try
        {
            if (target == null)
                return;

            int id = mob.getId();
            int printed = s_chaseLines.getOrDefault(id, 0);
            if (printed >= CHASE_MAX_LINES)
                return;
            s_chaseLines.put(id, printed + 1);

            PathNavigation nav = mob.getNavigation();
            Path p = nav.getPath();

            StringBuilder sb = new StringBuilder();
            sb.append("crawler=").append(id);
            sb.append(" dist=").append(ProbeLog.fmt(Math.sqrt(mob.distanceToSqr(target))));
            sb.append(" target=").append(ProbeLog.safe(() -> target.getType().toString().replace("entity.minecraft.", "")));
            sb.append(" goals=[").append(runningGoals(mob)).append(']');
            sb.append(" path=").append(p == null ? -1 : p.getNodeCount());
            sb.append(" next=").append(p == null ? -1 : p.getNextNodeIndex());
            sb.append(" wanted=").append(ProbeLog.safe(() -> String.valueOf(mob.getMoveControl().hasWanted())));
            sb.append(" speed=").append(ProbeLog.fmt(mob.getSpeed()));
            sb.append(" onGround=").append(mob.onGround());
            sb.append(" y=").append(ProbeLog.fmt(mob.getY()));

            ProbeLog.log("CHASE-27", sb.toString());
        }
        catch (Throwable t)
        {
            ProbeLog.log("CHASE-27", "log error: " + t);
        }
    }

    /** Called on every WALK-S sample: detects a replaced path and logs one line with a stack frame. */    private static void trackPathIdentity(Mob mob, double moved, LivingEntity target)
    {
        try
        {
            int id = mob.getId();
            Path p = mob.getNavigation().getPath();
            String fp = fingerprint(p);
            String prev = s_pathFingerprint.put(id, fp);

            if (prev == null || prev.equals(fp))
                return;

            int changes = s_pathChanges.getOrDefault(id, 0) + 1;
            s_pathChanges.put(id, changes);

            int printed = s_newPathLines.getOrDefault(id, 0);
            if (printed >= NEWPATH_MAX_LINES)
                return;
            s_newPathLines.put(id, printed + 1);

            ProbeLog.log("AI-27-NEWPATH", "crawler=" + id
                    + " change#" + changes
                    + " movedSinceLast=" + ProbeLog.fmt(moved)
                    + " dist=" + (target == null ? "-" : ProbeLog.fmt(Math.sqrt(mob.distanceToSqr(target))))
                    + " old=" + prev
                    + " new=" + fp
                    + " nextIdxNow=" + (p == null ? -1 : p.getNextNodeIndex())
                    + " speed=" + ProbeLog.fmt(mob.getSpeed())
                    + " caller=" + callerOf());
        }
        catch (Throwable t)
        {
            ProbeLog.log("AI-27-NEWPATH", "track error: " + t);
        }
    }

    /** Called after the WALK-S sample: logs a deep diagnostic only when the crawler has a target
     * and stands still. */
    private static void logAi27(Mob mob, LivingEntity target, double moved)
    {
        try
        {
            int id = mob.getId();
            PathNavigation nav = mob.getNavigation();
            Path path = nav.getPath();
            int nextIdx = path == null ? -1 : path.getNextNodeIndex();
            int nodeCount = path == null ? -1 : path.getNodeCount();

            Integer prev = s_prevNextNode.put(id, nextIdx);
            int maxSeen = Math.max(s_maxNextNode.getOrDefault(id, -1), nextIdx);
            s_maxNextNode.put(id, maxSeen);

            boolean stuck = target != null && moved < 0.05d && nav.isInProgress();
            if (!stuck)
                return;

            int printed = s_ai27Count.getOrDefault(id, 0);
            if (printed >= AI27_MAX_LINES)
                return;
            s_ai27Count.put(id, printed + 1);

            StringBuilder sb = new StringBuilder();
            sb.append("crawler=").append(id);
            sb.append(" dist=").append(ProbeLog.fmt(Math.sqrt(mob.distanceToSqr(target))));
            sb.append(" nextIdx=").append(nextIdx).append('/').append(nodeCount);
            sb.append(" prevNextIdx=").append(prev == null ? "-" : String.valueOf(prev));
            sb.append(" maxNextIdxSeen=").append(maxSeen);
            sb.append(" navInProgress=").append(nav.isInProgress());
            sb.append(" navDone=").append(nav.isDone());
            sb.append(" maxWP=").append(ProbeLog.fmt(nav.getMaxDistanceToWaypoint()));
            sb.append(" moveWanted=").append(ProbeLog.safe(() -> String.valueOf(mob.getMoveControl().hasWanted())));
            // Full MoveControl state: wanted coordinates, speed modifier, operation.
            // Wanted coordinates on top of the mob mean it was told to hold position; wanted
            // coordinates far away mean the command was issued but never executed.
            sb.append(" mcWanted=").append(ProbeLog.safe(() ->
                    String.format("%.2f,%.2f,%.2f", mob.getMoveControl().getWantedX(),
                            mob.getMoveControl().getWantedY(), mob.getMoveControl().getWantedZ())));
            sb.append(" mobPos=").append(ProbeLog.safe(() ->
                    String.format("%.2f,%.2f,%.2f", mob.getX(), mob.getY(), mob.getZ())));
            sb.append(" mcSpeedMod=").append(ProbeLog.safe(() -> ProbeLog.fmt(mob.getMoveControl().getSpeedModifier())));
            sb.append(" mcDelta=").append(ProbeLog.vec(mob.getDeltaMovement()));
            sb.append(" zza=").append(ProbeLog.safe(() -> ProbeLog.fmt(mob.zza)));
            sb.append(" xxa=").append(ProbeLog.safe(() -> ProbeLog.fmt(mob.xxa)));
            sb.append(" onGround=").append(mob.onGround());
            sb.append(" inWater=").append(mob.isInWater());
            sb.append(" noAi=").append(mob.isNoAi());
            sb.append(" effAi=").append(mob.isEffectiveAi());
            sb.append(" goals=[").append(runningGoals(mob)).append(']');

            if (path != null && nextIdx >= 0 && nextIdx < nodeCount)
            {
                Node node = path.getNode(nextIdx);
                sb.append(" nextNode=(").append(node.x).append(',').append(node.y).append(',').append(node.z).append(')');
                sb.append(" nodeType=").append(ProbeLog.safe(() -> String.valueOf(node.type)));
                // Real distance to that waypoint, for a direct comparison against maxWP.
                double dx = mob.getX() - (node.x + 0.5d);
                double dy = mob.getY() - (double) node.y;
                double dz = mob.getZ() - (node.z + 0.5d);
                double dNode = Math.sqrt(dx * dx + dy * dy + dz * dz);
                sb.append(" dNode=").append(ProbeLog.fmt(dNode));
                sb.append(" dNode<=maxWP=").append(dNode <= nav.getMaxDistanceToWaypoint());
            }

            ProbeLog.log("AI-27", sb.toString());
        }
        catch (Throwable t)
        {
            ProbeLog.log("AI-27", "log error: " + t);
        }
    }

    /**
     * Reads an int field by reflection.
     *
     * <p>Vanilla field names are obfuscated in production, but this mod's own fields are not, so
     * {@code m_roarCooldown} can be read directly (unlike {@code Mob#goalSelector}, which needs a mixin).
     */
    private static int intField(Object target, String name, int fallback)
    {
        try
        {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        }
        catch (Throwable t)
        {
            return fallback;
        }
    }

    /**
     * Reads the static {@code s_lastRoarHeard} map of sanitypd's {@code ScreamingCrawler}
     * (player UUID -> game tick of the last roar that player heard) and logs a line whenever it
     * changes.
     *
     * <p>This is the direct evidence that the per-player throttle works: two roars for the same
     * player must be at least 240 ticks apart. A server-side self-check can only prove that the
     * constant is longer than the sound, not that the throttle is really applied per player.
     */
    private static void checkRoarThrottle()
    {
        try
        {
            Class<?> crawlerClass = Class.forName("piloser.sanitypd.entity.ScreamingCrawler");
            java.lang.reflect.Field field = crawlerClass.getDeclaredField("s_lastRoarHeard");
            field.setAccessible(true);

            if (!(field.get(null) instanceof Map<?, ?> map))
                return;

            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                String player = String.valueOf(entry.getKey());
                long tick = ((Number)entry.getValue()).longValue();
                Long previous = s_lastThrottle.put(player, tick);

                if (previous != null && previous == tick)
                    continue;

                ProbeLog.log("ROAR-THROTTLE", "player=" + player + " heard a roar at gameTime=" + tick
                        + (previous == null ? " (first)" : " (gap=" + (tick - previous) + " tick, expected >= 240)"));
            }
        }
        catch (Throwable ignored)
        {
            // sanitypd not installed, or the field was renamed: skip silently
        }
    }

    /** Checked by registry name so the probe does not depend on sanitypd's classes. */
    static boolean isCrawler(Entity entity)
    {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "sanitypd".equals(key.getNamespace()) && "screaming_crawler".equals(key.getPath());
    }
}
