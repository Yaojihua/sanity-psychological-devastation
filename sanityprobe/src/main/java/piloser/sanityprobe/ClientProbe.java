package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Groups B + C (client side): walk-animation detection and floating appearance. This is also the
 * single draw entry point for the probe's overlay text.
 *
 * <h2>Why it must be measured on the client</h2>
 * The walk animation is decided by <b>GeckoLib while rendering on the client</b>. Disassembling
 * GeckoLib 4.2's {@code GeoEntityRenderer.actuallyRender} shows what it computes:
 * <pre>
 *   motionMag = (|deltaX| + |deltaZ|) / 2
 *   isMoving  = (motionMag &gt;= getMotionAnimThreshold()) &amp;&amp; (walkAnimation value != 0)
 *   default getMotionAnimThreshold() = 0.015f
 *   isMoving ? play move.walk : play misc.idle
 * </pre>
 * So "no walk animation" and "not walking" look identical on the client. Logging all three values
 * here separates "it really is not moving" from "it moved but failed the check".
 *
 * <h2>Overlay layout (since v2.7.0 everything is laid out by {@link HudLayout})</h2>
 * <pre>
 * line 1   [PROBE] header: version | F4 hint | group keys                            fixed header
 * after    lines registered by the probe groups (walk / float / sound / stabilizer / ...)
 * over cap ...N more lines not shown (press F4 or increase the GUI scale)
 * </pre>
 * Never draw overlay text with a hardcoded {@code graphics.drawString(..., 4, <y>, ...)}: the four
 * hardcoded y-coordinates (24/34/46) overlapped each other as soon as lines were added in v2.0.0 and
 * all of them now go through {@link HudLayout}. Line step and line cap rules live in that class.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientProbe
{
    /** GeckoLib {@code GeoRenderer#getMotionAnimThreshold} default value (from disassembly). */
    public static final float GECKOLIB_MOTION_THRESHOLD = 0.015f;

    private static final double RADIUS = 48.0d;
    private static final int INTERVAL = 10;

    private static int s_tick;
    private static String s_hud = "(probe: waiting for a screaming_crawler nearby)";
    private static String s_hud2 = "";

    private ClientProbe() {}

    /** Registers the B/C lines once (color prefix documented in {@link HudLayout}). */
    private static void ensureHudLines()
    {
        // A "\u0000RRGGBB" prefix sets the color of this line (per-line color is allowed since v2.7.0)
        ProbeHud.registerLine(() -> "\u0000FFFF55" + "[WALK] " + s_hud);
        ProbeHud.registerLine(() -> "\u0000AAAAAA" + "[FLOAT] " + s_hud2);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % INTERVAL != 0)
            return;

        // v2.7.0: groups B/C also go through the shared layout (they used to draw directly at the
        // hardcoded y = TOP+14 / TOP+28)
        ensureHudLines();

        // Whole-client check (v2.0.0): arm every client group once and register their overlay lines
        try
        {
            Round26Probe.ensureInited();
        }
        catch (Throwable t)
        {
            ProbeLog.log("P26", "round-26 arm failed: " + t);
        }

        // Spawn-egg tint / icon / name only hold on the client, so one report is enough
        try
        {
            EggProbe.clientSample(Minecraft.getInstance());
        }
        catch (Throwable t)
        {
            ProbeLog.log("EGG-25-C", "hook failed: " + t);
        }

        // The tick events of the other groups are auto-registered by @EventBusSubscriber; this only
        // forces their static initializers to run (which registers their overlay lines).
        try
        {
            ShieldPriorityProbe.init();
            EggProbe26.init();
            EnchantProbe26.init();
        }
        catch (Throwable t)
        {
            ProbeLog.log("P26", "group init failed: " + t);
        }

        try
        {
            sample();
        }
        catch (Throwable t)
        {
            s_hud = "(probe error: " + t + ")";
        }

        // v2.7.0: every 10 seconds log how many lines were drawn / dropped and how wide the widest
        // line was, so the layout itself can be verified from the log.
        HudLayout.tick();
    }

    private static void sample()
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
        {
            s_hud = "(probe: no level)";
            return;
        }

        LivingEntity crawler = nearestCrawler(mc);

        if (crawler == null)
        {
            s_hud = "(probe: no screaming_crawler within " + (int)RADIUS + " blocks)";
            s_hud2 = "";
            return;
        }

        double dx = crawler.getDeltaMovement().x;
        double dz = crawler.getDeltaMovement().z;
        double motionMag = (Math.abs(dx) + Math.abs(dz)) / 2.0d;

        float waSpeed = crawler.walkAnimation.speed();
        float waSpeedPt = crawler.walkAnimation.speed(1.0f);
        float waPos = crawler.walkAnimation.position();
        float clamped = Math.min(waSpeedPt, 1.0f);

        // Recompute what GeckoLib would decide, using the disassembled formula. Both variants are
        // evaluated because it cannot be pinned down 100% which value gets clamped.
        boolean variantSpeed = motionMag >= GECKOLIB_MOTION_THRESHOLD && clamped != 0.0f;
        boolean variantRaw = motionMag >= GECKOLIB_MOTION_THRESHOLD && waSpeed != 0.0f;
        boolean willWalk = variantSpeed || variantRaw;

        ProbeLog.log("WALK-C", "id=" + crawler.getId()
                + " dist=" + ProbeLog.fmt(Math.sqrt(crawler.distanceToSqr(mc.player)))
                + " motionMag=" + ProbeLog.fmt(motionMag)
                + " delta=" + ProbeLog.vec(crawler.getDeltaMovement())
                + " waSpeed=" + ProbeLog.fmt(waSpeed)
                + " waSpeedPt=" + ProbeLog.fmt(waSpeedPt)
                + " waPos=" + ProbeLog.fmt(waPos)
                + " waIsMoving=" + crawler.walkAnimation.isMoving()
                + " geckoIsMoving=" + willWalk
                + " => animation=" + (willWalk ? "move.walk" : "misc.idle"));

        ProbeLog.log("FLOAT-C", "id=" + crawler.getId()
                + " y=" + ProbeLog.fmt(crawler.getY())
                + " bbMinY=" + ProbeLog.fmt(crawler.getBoundingBox().minY)
                + " bbMaxY=" + ProbeLog.fmt(crawler.getBoundingBox().maxY)
                + " bbW=" + ProbeLog.fmt(crawler.getBoundingBox().maxX - crawler.getBoundingBox().minX)
                + " onGround=" + crawler.onGround()
                + " noGravity=" + crawler.isNoGravity()
                + " deltaY=" + ProbeLog.fmt(crawler.getDeltaMovement().y)
                + " blockBelow=" + ProbeLog.safe(() -> mc.level.getBlockState(
                        net.minecraft.core.BlockPos.containing(crawler.getX(), crawler.getBoundingBox().minY - 0.1d, crawler.getZ()))
                        .getBlock().toString()));

        s_hud = "crawler#" + crawler.getId()
                + " dist=" + ProbeLog.fmt(Math.sqrt(crawler.distanceToSqr(mc.player)))
                + " motion=" + ProbeLog.fmt(motionMag)
                + " waSpeed=" + ProbeLog.fmt(waSpeed)
                + " anim=" + (willWalk ? "WALK" : "IDLE");
        s_hud2 = "  y=" + ProbeLog.fmt(crawler.getY())
                + " bbMinY=" + ProbeLog.fmt(crawler.getBoundingBox().minY)
                + " onGround=" + crawler.onGround()
                + " | F3+B = show hitbox";
    }

    private static LivingEntity nearestCrawler(Minecraft mc)
    {
        AABB area = mc.player.getBoundingBox().inflate(RADIUS);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, area))
        {
            if (!WalkProbe.isCrawler(entity))
                continue;

            double distance = entity.distanceToSqr(mc.player);

            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = entity;
            }
        }

        return best;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();

            // v2.7.0 one-shot diagnostic that reports only the "gate blocked it" case, at most 5
            // lines per process. It is kept because event delivery cannot be observed from outside:
            // a missing HUD may mean the event never arrived, was gated, or was drawn off-screen,
            // and all three look identical on screen. These lines tell them apart from the log.
            if (s_renderDiag < 5 && (mc.player == null || mc.options.hideGui || !ProbeHud.isVisible()))
            {
                s_renderDiag++;
                ProbeLog.log("HUD-27-GATE", "render event reached but skipped:"
                        + " player=" + (mc.player != null)
                        + " hideGui=" + mc.options.hideGui
                        + " hudVisible=" + ProbeHud.isVisible()
                        + " screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()));
            }

            if (mc.player == null || mc.options.hideGui)
                return;

            // F4 hides the whole probe overlay
            if (!ProbeHud.isVisible())
                return;

            // v2.7.0: the only draw entry point. Line step, width cap, line cap and staying on
            // screen are all handled inside HudLayout. Never hardcode a y-coordinate here or
            // anywhere else, or the lines will overlap again (see the HudLayout class comment).
            HudLayout.draw(event.getGuiGraphics(), mc, "ClientProbe");
        }
        catch (Throwable ignored)
        {
            // The probe must never affect rendering
        }
    }

    private static int s_renderDiag;
}
