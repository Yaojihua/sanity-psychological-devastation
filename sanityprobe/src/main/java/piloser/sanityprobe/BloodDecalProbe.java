package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * The blood decal layer's own group ({@code [BLOOD-78]}).
 *
 * <p>What a machine can judge here, and what it cannot: the constants (life, fade, size, capacity, the drop
 * schedule), whether the three textures really resolve in the language-independent resource manager, and the
 * live counters - pools placed, placements refused for want of a block surface, pools still alive, frames that
 * actually drew one. The look of a pool on screen, and whether a given spot was the right spot, stay a human
 * judgement; that is what the counters are for, so "nothing drew" cannot be mistaken for "nothing ran".
 *
 * <p>Read-only: reflection plus the client's own resource manager, no gameplay content, no writes.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodDecalProbe
{
    /** Tag of this group; every line it writes starts with {@code [BLOOD-78]}. */
    private static final String TAG = "BLOOD-78";

    private static final String DECALS = "piloser.sanitypd.client.BloodDecals";
    private static final String PRESENTATION = "piloser.sanitypd.client.SelfInjuryPresentation";

 /** The numbers: thirty seconds, the last five fading, one pool per second five times. */
    private static final int LIFE_SPEC = 600;
    private static final int FADE_SPEC = 100;
    private static final int DROPS_SPEC = 5;
    private static final int INTERVAL_SPEC = 20;
    private static final float SIZE_SPEC = 0.34f;
    private static final int CAPACITY_SPEC = 128;

    /** How often the counters are sampled and compared; one second, like the other timeline probes. */
    private static final int INTERVAL_TICKS = 20;

    /** Variant textures that must be present; the names are the ones the mod builds its paths from. */
    private static final String[] TEXTURES =
    {
        "textures/decal/blood_stain_1.png",
        "textures/decal/blood_stain_2.png",
        "textures/decal/blood_stain_3.png",
    };

    private static int s_tick;
    private static boolean s_constantsReported;
    private static int s_lastSpawned = -1;
    private static int s_lastActive = -1;
    private static int s_lastRejected = -1;
    private static int s_lastDrawn = -1;
    private static boolean s_verdictPrinted;

    private BloodDecalProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % INTERVAL_TICKS != 0)
            return;

        try
        {
            tick();
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "probe error: " + t);
        }
    }

    /** Leaving the world drops the once-only flags, so the next world reports again. */
    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event)
    {
        s_constantsReported = false;
        s_verdictPrinted = false;
        s_lastSpawned = s_lastActive = s_lastRejected = s_lastDrawn = -1;
    }

    private static void tick() throws Exception
    {
        if (Minecraft.getInstance().player == null)
            return;

        Class<?> decals = Class.forName(DECALS);

        if (!s_constantsReported)
        {
            s_constantsReported = true;
            reportConstants(decals);
            reportTextures();
        }

        int spawned = (int) decals.getMethod("spawnedCount").invoke(null);
        int active = (int) decals.getMethod("activeCount").invoke(null);
        int rejected = (int) decals.getMethod("rejectedCount").invoke(null);
        int drawn = (int) decals.getMethod("drawnFrameCount").invoke(null);

        if (spawned == s_lastSpawned && active == s_lastActive && rejected == s_lastRejected && drawn == s_lastDrawn)
            return;

        s_lastSpawned = spawned;
        s_lastActive = active;
        s_lastRejected = rejected;
        s_lastDrawn = drawn;

        // Counts on every change, so "the layer is idle" and "the layer never ran" can be told apart.
        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "pools spawned=%d active=%d refused=%d drawnFrames=%d lastY=%s",
                spawned, active, rejected, drawn, lastSpawnY(decals)));

        if (!s_verdictPrinted && spawned > 0 && drawn > 0)
        {
            s_verdictPrinted = true;
            ProbeLog.log(TAG, spawned > DROPS_SPEC * 8
                    ? "VERDICT=CHECK(spawned far more pools than the schedule allows; check the act timeline)"
                    : "VERDICT=PASS(pools placed on a surface and drawn)");
        }
    }

    private static String lastSpawnY(Class<?> decals)
    {
        try
        {
            double y = (double) decals.getMethod("lastSpawnY").invoke(null);
            return Double.isNaN(y) ? "n/a" : String.format(Locale.ROOT, "%.3f", y);
        }
        catch (Throwable t)
        {
            return "?";
        }
    }

    private static void reportConstants(Class<?> decals) throws Exception
    {
        int life = (int) decals.getField("LIFE_TICKS").get(null);
        int fade = (int) decals.getField("FADE_TICKS").get(null);
        float size = (float) decals.getField("SIZE").get(null);
        int capacity = (int) decals.getField("MAX_DECALS").get(null);

        Class<?> presentation = Class.forName(PRESENTATION);
        int drops = (int) presentation.getField("BLOOD_DROPS").get(null);
        int interval = (int) presentation.getField("BLOOD_INTERVAL_TICKS").get(null);

        boolean ok = life == LIFE_SPEC && fade == FADE_SPEC && size == SIZE_SPEC && capacity == CAPACITY_SPEC
                && drops == DROPS_SPEC && interval == INTERVAL_SPEC;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "constants life=%d fade=%d size=%.3f capacity=%d drops=%d interval=%d => %s",
                life, fade, size, capacity, drops, interval, ok ? "OK" : "CHECK(expected "
                + LIFE_SPEC + "/" + FADE_SPEC + "/" + SIZE_SPEC + "/" + CAPACITY_SPEC + "/"
                + DROPS_SPEC + "/" + INTERVAL_SPEC + ")"));
    }

    private static void reportTextures()
    {
        var resources = Minecraft.getInstance().getResourceManager();
        int found = 0;

        for (String path : TEXTURES)
        {
            try
            {
                if (resources.getResource(new ResourceLocation("sanitypd", path)).isPresent())
                    found++;
            }
            catch (Throwable ignored)
            {
                // A missing texture counts as missing; the verdict below reports how many were found.
            }
        }

        ProbeLog.log(TAG, "textures resolved=" + found + "/" + TEXTURES.length + " => "
                + (found == TEXTURES.length ? "OK" : "CHECK(the decal textures are missing from this jar)"));
    }
}
