package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * The self-injury hint pool's own group ({@code [HINT-POOL-76]}).
 *
 * <p>What only a real client can answer: whether the eighth pool exists, how many built-in lines it has, and
 * whether every one of its language keys resolves to text in the language actually loaded - a key that does not
 * resolve is drawn as the raw key or as nothing at all, and the pool is invisible to {@code /sanity hint list}
 * until it is opened by hand.
 *
 * <p>Read-only: reflection plus the client's own language lookup, no gameplay content, no writes.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HintPoolProbe
{
    /** Tag of this group; every line it writes starts with {@code [HINT-POOL-76]}. */
    private static final String TAG = "HINT-POOL-76";

    private static final String MANAGER = "piloser.sanitypd.client.MentalHintManager";

    /** The pool's index and built-in line count, mirroring MentalHintManager and the hint5X keys. */
    private static final int EXPECTED_INDEX = 4;
    private static final int EXPECTED_LINES = 8;

    /** Language key stem of the pool, mirroring MentalHintManager's prefix constant. */
    private static final String KEY_STEM = "gui.sanitypd.hint5";

    private static final int INTERVAL_TICKS = 20;

    private static int s_tick;
    private static boolean s_reported;
    private static Class<?> s_clazz;
    private static boolean s_lookedUp;

    private HintPoolProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % INTERVAL_TICKS != 0)
            return;

        if (s_reported)
            return;

        if (Minecraft.getInstance().player == null)
            return;

        s_reported = true;
        report();
    }

    /** Leaving the world drops the flag, so the next world reports again with its own language. */
    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event)
    {
        s_reported = false;
    }

    private static void report()
    {
        Class<?> clazz = modClass();

        if (clazz == null)
            return;

        try
        {
            int index = (int) clazz.getField("INDEX_SELF_INJURY").get(null);
            int count = (int) clazz.getMethod("defaultCount", int.class).invoke(null, index);
            String name = ((Component) clazz.getMethod("tierName", int.class).invoke(null, index)).getString();

            int resolved = 0;
            int blanks = 0;
            int withPlaceholder = 0;

            for (int i = 0; i < count; i++)
            {
                String key = KEY_STEM + i;
                String text = Component.translatable(key).getString();

                if (text == null || text.isBlank())
                    blanks++;
                else if (text.equals(key))
                    blanks++;
                else
                {
                    resolved++;

                    if (text.contains("{player}"))
                        withPlaceholder++;
                }
            }

            boolean shapeOk = index == EXPECTED_INDEX && count == EXPECTED_LINES
                    && resolved == EXPECTED_LINES && blanks == 0 && !name.isBlank() && !name.startsWith("commands.");

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "pool index=%d lines=%d resolved=%d blank=%d placeholder=%d name=\"%s\" => %s",
                    index, count, resolved, blanks, withPlaceholder, name, shapeOk ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "CHECK(could not read the pool: " + t + ")");
        }

        reportWindow();
    }

    /**
 * The window: the pool answers one tick after the wound and speaks for six seconds.
     *
     * <p>Read from the presentation class rather than repeated as literals here: two copies of a duration drift.
     */
    private static void reportWindow()
    {
        try
        {
            Class<?> presentation = Class.forName("piloser.sanitypd.client.SelfInjuryPresentation");
            int delay = (int) presentation.getField("HINT_DELAY_TICKS").get(null);
            int window = (int) presentation.getField("HINT_WINDOW_TICKS").get(null);
            boolean quiet = (boolean) presentation.getMethod("suppressesOtherPools").invoke(null);

            boolean ok = delay == 1 && window == 120;

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "window delay=%d ticks, length=%d ticks (%d s), tailOpen=%s => %s",
                    delay, window, window / 20, quiet, ok ? "OK" : "CHECK(expected 1/120)"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "CHECK(could not read the window: " + t + ")");
        }
    }

    private static Class<?> modClass()
    {
        if (!s_lookedUp)
        {
            s_lookedUp = true;

            try
            {
                s_clazz = Class.forName(MANAGER);
            }
            catch (Throwable t)
            {
                ProbeLog.log(TAG, "CHECK(mod absent: " + MANAGER + " is not on this classpath)");
            }
        }

        return s_clazz;
    }
}
