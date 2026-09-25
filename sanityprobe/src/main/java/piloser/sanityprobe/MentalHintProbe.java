package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * Client-side probe for the three tiers of inner monologue hints.
 *
 * <h2>Why this must be measured on the client</h2>
 * Tiering, picking and rendering the hints all happen on the client
 * ({@code piloser.sanitypd.client.MentalHintManager} plus {@code GuiHandler}), so a dedicated
 * server never reaches that code and the behaviour can only be observed in a real client.
 *
 * <h2>What it reports</h2>
 * <ol>
 *   <li><b>[HINT-TIER]</b>: one line whenever the madness level crosses a tier boundary. The
 *       thresholds are mild at madness 0.50, severe at 0.75 and deep at 0.90, i.e. sanity at or
 *       below 50%, 25% and 10%. The current pool size for that tier is included.</li>
 *   <li><b>[HINT-24]</b>: a one-off check once the player is in game. It reads the actual tier
 *       thresholds, the default pool size per tier (12 / 9 / 4) and verifies that the custom
 *       pool API really exists, so the {@code /sanity hint} commands have something to call.</li>
 * </ol>
 *
 * <p>WARNING: read-only. A failed reflective lookup logs one line and is never allowed to
 * affect gameplay.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MentalHintProbe
{
    /** Tier thresholds, mirroring the constants of sanitypd's MentalHintManager. */
    private static final String[] TIER_FIELDS = { "T0_MADNESS", "T1_MADNESS", "T2_MADNESS" };
    private static final String[] TIER_NAMES = { "mild", "severe", "deep" };

    private static int s_tick;
    private static int s_lastTier = Integer.MIN_VALUE;
    private static boolean s_once;

    private MentalHintProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 5 != 0)
            return;

        try
        {
            tick();
        }
        catch (Throwable t)
        {
            ProbeLog.log("HINT-TIER", "probe error: " + t);
        }
    }

    private static void tick()
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        if (!s_once)
        {
            s_once = true;
            reportApi();
        }

        float madness = madnessOf(mc);

        if (madness < 0f)
            return;

        int tier = tierFor(madness);

        if (tier != s_lastTier)
        {
            s_lastTier = tier;
            ProbeLog.log("HINT-TIER", String.format(java.util.Locale.ROOT,
                    "crossed tier => %s | madness=%.3f (sanity %s%%) | poolSize=%s",
                    tier < 0 ? "none(sanity >50%)" : TIER_NAMES[tier],
                    madness, ProbeLog.fmt((1f - madness) * 100f), poolSize(tier)));
        }
    }

    /** One-off check of the threshold constants, the default pool sizes and the custom pool API. */
    private static void reportApi()
    {
        Class<?> manager;

        try
        {
            manager = Class.forName("piloser.sanitypd.client.MentalHintManager");
        }
        catch (Throwable t)
        {
            ProbeLog.log("HINT-24", "MentalHintManager NOT FOUND => FAIL (" + t + ")");
            return;
        }

        for (int i = 0; i < TIER_FIELDS.length; i++)
        {
            try
            {
                float v = manager.getField(TIER_FIELDS[i]).getFloat(null);
                ProbeLog.log("HINT-24", String.format(java.util.Locale.ROOT,
                        "%s threshold %s = %.2f => %s", TIER_NAMES[i], TIER_FIELDS[i], v,
                        Math.abs(v - EXPECTED[i]) < 1e-4f ? "PASS" : "FAIL(expected " + EXPECTED[i] + ")"));
            }
            catch (Throwable t)
            {
                ProbeLog.log("HINT-24", TIER_NAMES[i] + " threshold read failed: " + t);
            }
        }

        for (int tier = 0; tier < 3; tier++)
        {
            try
            {
                Method m = manager.getMethod("defaultCount", int.class);
                int count = (int) m.invoke(null, tier);
                ProbeLog.log("HINT-24", String.format(java.util.Locale.ROOT,
                        "tier %d default pool size = %d => %s", tier, count,
                        count == EXPECTED_POOL[tier] ? "PASS" : "FAIL(expected " + EXPECTED_POOL[tier] + ")"));
            }
            catch (Throwable t)
            {
                ProbeLog.log("HINT-24", "tier " + tier + " defaultCount failed: " + t);
            }
        }

        // Whether the custom pool and the immediate-display API are all present.
        String[][] api = {
                { "addHint", "int,java.lang.String" },
                { "removeHint", "int,int" },
                { "clearHints", "int" },
                { "customCount", "int" },
                { "effectiveHints", "int" },
                { "showNow", "int" },
                { "onClientSetup" },
        };

        for (String[] entry : api)
        {
            try
            {
                if (entry.length == 2)
                {
                    Class<?>[] types = new Class<?>[entry[1].split(",").length];
                    String[] names = entry[1].split(",");
                    for (int i = 0; i < names.length; i++)
                        types[i] = names[i].equals("int") ? int.class : Class.forName(names[i]);
                    manager.getMethod(entry[0], types);
                }
                else
                {
                    manager.getMethod(entry[0]);
                }

                ProbeLog.log("HINT-24", "api " + entry[0] + " => PASS");
            }
            catch (Throwable t)
            {
                ProbeLog.log("HINT-24", "api " + entry[0] + " => FAIL (" + t + ")");
            }
        }
    }

    private static final float[] EXPECTED = { .50f, .75f, .90f };
    private static final int[] EXPECTED_POOL = { 12, 9, 4 };

    /** Reads madness from the capability with the same formula the mod uses (reflectively, no dependency on sanitypd). */
    private static float madnessOf(Minecraft mc)
    {
        try
        {
            Class<?> providerClass = Class.forName("piloser.sanitypd.capability.SanityProvider");
            Object capHolder = providerClass.getField("CAP").get(null);
            Object cap = capHolder.getClass().getMethod("get").invoke(capHolder);
            Object optional = cap.getClass()
                    .getMethod("getCapability", net.minecraft.world.entity.Entity.class)
                    .invoke(cap, mc.player);

            if (!(optional instanceof java.util.Optional<?> opt) || opt.isEmpty())
                return -1f;

            Object sanity = opt.get();
            float s = (float) sanity.getClass().getMethod("getSanity").invoke(sanity);
            float m = (float) sanity.getClass().getMethod("getMaxSanity").invoke(sanity);
            return m <= 0f ? -1f : 1f - s / m;
        }
        catch (Throwable t)
        {
            return -1f;
        }
    }

    private static int tierFor(float madness)
    {
        int tier = -1;

        for (int i = 0; i < EXPECTED.length; i++)
        {
            if (madness >= EXPECTED[i])
                tier = i;
        }

        return tier;
    }

    private static String poolSize(int tier)
    {
        if (tier < 0)
            return "n/a";

        try
        {
            Class<?> manager = Class.forName("piloser.sanitypd.client.MentalHintManager");
            int defaults = (int) manager.getMethod("defaultCount", int.class).invoke(null, tier);
            int custom = (int) manager.getMethod("customCount", int.class).invoke(null, tier);
            return defaults + " defaults + " + custom + " custom";
        }
        catch (Throwable t)
        {
            return "<err>";
        }
    }
}
