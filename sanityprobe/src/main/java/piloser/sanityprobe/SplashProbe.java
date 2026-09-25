package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Measures the mod's title-screen splash lines at runtime, client side only.
 *
 * <p>{@code SplashManager} is annotated {@code @OnlyIn(CLIENT)} and the title screen itself is
 * client-only, so the measurement runs after a client tick and reports to the log and the F4
 * overlay:
 * <pre>
 * [SPLASH-26] poolSize=457 oursInPool=12/12 expected=12 current="Plant a tree!" placeholderLeft=false => OK
 * [SPLASH-26-SAMPLE] draws=400 oursHit=9 withSectionSign=2 placeholderLeft=0 examples= | ...
 * </pre>
 *
 * <p>Pass criteria:
 * <ul>
 *   <li>{@code oursInPool == expected}: the mod's splashes really are in the vanilla pool (0 means
 *       they never got in);</li>
 *   <li>{@code current} is the line the title screen would actually display;</li>
 *   <li>{@code placeholderLeft=true} means a player-name placeholder was not substituted; it must
 *       always be false;</li>
 *   <li>a {@code poolSize} far below ~400 means the vanilla splashes.txt was not loaded (broken
 *       resource pack);</li>
 *   <li>the {@code draws} sample calls {@code getSplash()} repeatedly, so it proves the mod's lines
 *       can actually be picked ({@code oursHit>0}), that {@code §} formatting codes are present
 *       ({@code withSectionSign>0}) and that no placeholder ever leaks ({@code placeholderLeft==0}).</li>
 * </ul>
 *
 * <p>Side effects: read-only. It reflects on the {@code SplashManager.splashes} field and calls
 * {@code getSplash()}; no state is modified and every exception is swallowed. The conclusion is
 * refreshed on each world change or resource reload (F3+T), because the title screen redraws a
 * splash then.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SplashProbe
{
    /** Number of lines expected in the pool; must match the size of the mod's splash text list. */
    private static final int EXPECTED = 12;

    /**
     * Marker text used to recognize the mod's own lines inside the pool.
     *
     * <p>Warning: these markers must be updated whenever the mod changes its splash texts or their
     * count. Stale markers make the reported numbers wrong.
     */
    private static final String[] OUR_MARKERS = {
            "Do not disturb Cacomorth",
            "master of the world",
            "Cacomorth... macaron",
            "Face your inner self",
            "Can you hear my voice",
            "Weird Little Thing",
            "B E H I N D",
            "Let me go~dsh~",
            "We still need you",
            "Death is not the end",
            "morth in a coconut",
            "vessel called"
    };

    /** Number of sampled draws; each draw picks a random line from the pool. */
    private static final int DRAWS = 400;

    private static int s_tick;
    private static boolean s_done;
    private static String s_hud = null;

    static
    {
        ProbeHud.registerLine(() -> s_hud);
    }

    private SplashProbe() {}

    /** Called by {@link Round26Probe} to force class initialization so the static block registers the overlay line. */
    public static void init()
    {
        // Intentionally empty.
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || s_done)
            return;

        // Wait until both the title screen and the SplashManager are ready.
        if (++s_tick < 60)
            return;

        s_done = true;
        measure();
    }

    /**
     * Allows another measurement when returning to the title screen from a world or after a resource
     * reload. {@code TitleScreen#init} only calls {@code getSplash()} when no splash is cached, so
     * re-entering the title screen draws a new line, which is a good moment to observe another of the
     * mod's texts.
     */
    public static void reset()
    {
        s_done = false;
        s_tick = 0;
    }

    /** Called by {@link Round26Probe} every time the title screen is entered. */
    public static void onTitleScreen()
    {
        if (s_done)
            reset();
    }

    private static void measure()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            SplashManager manager = mc.getSplashManager();

            List<String> pool = readPool(manager);

            if (pool == null)
            {
                ProbeLog.log("SPLASH-26", "probe failed: cannot read the SplashManager.splashes field");
                return;
            }

            int ours = 0;
            for (String marker : OUR_MARKERS)
                for (String s : pool)
                    if (s.contains(marker))
                    {
                        ours++;
                        break;
                    }

            String text = rawText(manager.getSplash());
            boolean placeholderLeft = text != null && text.contains("{player}");

            boolean ok = ours == EXPECTED && !placeholderLeft;

            ProbeLog.log("SPLASH-26", "poolSize=" + pool.size()
                    + " oursInPool=" + ours + "/" + EXPECTED + " expected=" + EXPECTED
                    + " current=\"" + (text == null ? "(unreadable)" : text) + "\""
                    + " placeholderLeft=" + placeholderLeft
                    + " => " + (ok ? "OK" : "CHECK"));

            // Sampling: call getSplash() repeatedly (each call returns a random line) to verify that
            // the mod's own lines are actually reachable.
            int hit = 0;
            int obf = 0;
            int placeholderSeen = 0;
            StringBuilder samples = new StringBuilder();

            for (int i = 0; i < DRAWS; i++)
            {
                String s = rawText(manager.getSplash());

                if (s == null)
                    continue;

                if (s.indexOf('\u00A7') >= 0)
                    obf++;

                if (s.contains("{player}"))
                    placeholderSeen++;

                if (isOurs(s))
                {
                    hit++;
                    if (hit <= 4)
                        samples.append(" | ").append(s);
                }
            }

            ProbeLog.log("SPLASH-26-SAMPLE", "draws=" + DRAWS + " oursHit=" + hit
                    + " withSectionSign=" + obf
                    + " placeholderLeft=" + placeholderSeen
                    + " examples=" + samples);

            s_hud = "SPLASH-26: pool=" + pool.size() + " ours=" + ours + "/" + EXPECTED
                    + " draws=" + DRAWS + " hits=" + hit
                    + " sectionSign=" + obf + " placeholderLeaked=" + placeholderSeen
                    + " current='" + (text == null ? "?" : text) + "'"
                    + (ok ? " [" + (hit > 0 && placeholderSeen == 0 ? "OK" : "CHECK") + "]" : " [FAIL]");
        }
        catch (Throwable t)
        {
            ProbeLog.log("SPLASH-26", "probe failed: " + t);
        }
    }

    private static boolean isOurs(String s)
    {
        for (String marker : OUR_MARKERS)
            if (s.contains(marker))
                return true;

        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readPool(SplashManager manager)
    {
        try
        {
            Field field = SplashManager.class.getDeclaredField("splashes");
            field.setAccessible(true);
            return (List<String>) field.get(manager);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** Reflective read of {@code SplashRenderer.splash}, which has no getter. Returns null if unreadable. */
    private static String rawText(SplashRenderer renderer)
    {
        if (renderer == null)
            return null;

        try
        {
            Field f = SplashRenderer.class.getDeclaredField("splash");
            f.setAccessible(true);
            Object v = f.get(renderer);
            return v instanceof String s ? s : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}
