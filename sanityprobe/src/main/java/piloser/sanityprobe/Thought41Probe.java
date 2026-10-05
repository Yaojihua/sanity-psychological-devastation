package piloser.sanityprobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * The three thoughts this probe covers - stress-induced analgesia, irritability and conversion disorder -
 * read straight off the running mod (a new mechanic ships with probe cover).
 *
 * <h2>[THOUGHT41-29]</h2>
 * Reports the three registry entries and the numbers the mechanics actually read, then gives one verdict:
 * the ladders/constants must be the ones the owner specified (10/20/30%, 110/117/125%, 1 health, 6 ticks,
 * 60% sanity). Reading them from the loaded mod - rather than from the source - is what makes this a probe
 * and not a second copy of the same grep.
 *
 * <p>Everything here is read-only: it touches no entity and deals no damage. The one thing that cannot be
 * checked this way is the irritability attack modifier, which only exists while the mania effect is on the
 * player; that is the mod's own self-check's job ([SELFCHECK-151] measured mania 0.5 -> 0.7).
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Thought41Probe
{
    private static final String[] ITEMS = {
        "THOUGHT_STRESS_INDUCED_ANALGESIA",
        "THOUGHT_IRRITABILITY",
        "THOUGHT_CONVERSION_DISORDER",
    };

    private Thought41Probe() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        try
        {
            StringBuilder ids = new StringBuilder();
            boolean present = true;

            for (String field : ITEMS)
            {
                Item item = item(field);

                if (item == null)
                {
                    present = false;
                    ids.append(' ').append(field).append("=ABSENT");
                }
                else
                {
                    ids.append(' ').append(BuiltInRegistries.ITEM.getKey(item));
                }
            }

            float[] irritability = ladder("IRRITABILITY");
            float[] conversion = ladder("CONVERSION_DISORDER");
            float heal = constant("ANALGESIA_HEAL");
            float sanity = constant("ANALGESIA_SANITY");
            int cooldown = intConstant("ANALGESIA_COOLDOWN_TICKS");

            boolean ok = present
                    && eq(irritability, .10f, .20f, .30f)
                    && eq(conversion, 1.10f, 1.17f, 1.25f)
                    && heal == 1.0f
                    && sanity == .60f
                    && cooldown == 6;

            ProbeLog.log("THOUGHT41-29", String.format(Locale.ROOT,
                    "items:%s | irritability=%s | conversion=%s | analgesiaHeal=%s sanityAt=%s cooldownTicks=%d "
                            + "| VERDICT=%s",
                    ids, show(irritability), show(conversion), heal, sanity, cooldown,
                    ok ? "PASS(all three match the spec)" : "FAIL(a value or an item does not match the spec)"));
        }
        catch (Throwable t)
        {
            ProbeLog.log("THOUGHT41-29", "failed: " + t);
        }
    }

    private static Item item(String field) throws Exception
    {
        Field f = Class.forName("piloser.sanitypd.item.ItemRegistry").getField(field);
        Object holder = f.get(null);

        if (holder instanceof RegistryObject<?> ro && ro.isPresent())
            return (Item) ro.get();

        return null;
    }

    private static float[] ladder(String field) throws Exception
    {
        Field f = Class.forName("piloser.sanitypd.thought.ThoughtEffects").getField(field);
        return (float[]) f.get(null);
    }

    private static float constant(String field) throws Exception
    {
        return Class.forName("piloser.sanitypd.thought.ThoughtEffects").getField(field).getFloat(null);
    }

    private static int intConstant(String field) throws Exception
    {
        return Class.forName("piloser.sanitypd.thought.ThoughtEffects").getField(field).getInt(null);
    }

    private static boolean eq(float[] actual, float a, float b, float c)
    {
        return actual != null && actual.length == 3
                && Math.abs(actual[0] - a) < 1e-6f && Math.abs(actual[1] - b) < 1e-6f && Math.abs(actual[2] - c) < 1e-6f;
    }

    private static String show(float[] values)
    {
        if (values == null)
            return "null";

        StringBuilder out = new StringBuilder("[");

        for (int i = 0; i < values.length; i++)
            out.append(i == 0 ? "" : "/").append(Math.round(values[i] * 100f)).append('%');

        return out.append(']').toString();
    }
}
