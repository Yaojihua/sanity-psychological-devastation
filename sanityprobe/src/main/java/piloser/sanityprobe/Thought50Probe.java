package piloser.sanityprobe;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The two thoughts of the stockholm/overdose batch, read off the running mod.
 *
 * <h2>[THOUGHT50]</h2>
 * Reports both registry entries, the counting types they carry, the constants the mechanics read and the
 * plumbing the tooltip reads, then gives one verdict. Reading them from the loaded mod - rather than from
 * the source - is what makes this a probe instead of a second copy of the same grep.
 *
 * <h2>The cooldown shortcut is measured, not just read</h2>
 * Shortening a cooldown is the one part of this batch whose whole point is arithmetic on a live value, so
 * the group puts a thought into the chain of a throwaway fake player of its own and compares the two
 * numbers (with and without the thought). No world entity is touched, nothing is dealt any damage, and no
 * real player is read or written: the fake player exists for the length of this method.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Thought50Probe
{
    private static final String TAG = "THOUGHT50";

    private static final String REGISTRY = "piloser.sanitypd.item.ItemRegistry";
    private static final String EFFECTS = "piloser.sanitypd.thought.ThoughtEffects";
    private static final String OVERDOSE = "piloser.sanitypd.thought.Overdose";
    private static final String ITEM_CLASS = "piloser.sanitypd.item.StabilizerItem$Kind";

    private Thought50Probe() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        try
        {
            Item stockholm = item("THOUGHT_STOCKHOLM_SYNDROME");
            Item overdose = item("THOUGHT_OVERDOSE");

            String ids = String.format(Locale.ROOT, "stockholm=%s overdose=%s",
                    name(stockholm), name(overdose));

            boolean shape = stockholm != null && overdose != null
                    && "sanitypd:thought_stockholm_syndrome".equals(name(stockholm))
                    && "sanitypd:thought_overdose".equals(name(overdose))
                    && "WORLDVIEW".equals(enumName(stockholm, "category"))
                    && "METHODOLOGY".equals(enumName(overdose, "category"))
                    && types(stockholm).equals("[SERVITUDE, ENDURANCE]")
                    && types(overdose).equals("[ENDURANCE]")
                    && Boolean.FALSE.equals(tiered(stockholm))
                    && Boolean.FALSE.equals(tiered(overdose));

            float stockholmSanity = constant("STOCKHOLM_SANITY");
            int stockholmCooldown = intConstant("STOCKHOLM_COOLDOWN_TICKS");
            float overdoseCut = constant("OVERDOSE_COOLDOWN_CUT");
            int overdoseNausea = intConstant("OVERDOSE_NAUSEA_TICKS");

            boolean numbers = stockholmSanity == 5f && stockholmCooldown == 10
                    && Math.abs(overdoseCut - 0.45f) < 1e-6f && overdoseNausea == 200;

            // The tooltip plumbing: one printed line each, one coloured value each, and the unit - the
            // sanity payout is an absolute amount and must not be rendered as a percentage.
            boolean plumbing = untieredLineCount(stockholm) == 1
                    && untieredLineCount(overdose) == 1
                    && untieredLineValueCount(stockholm, 0) == 1
                    && untieredLineValueCount(overdose, 0) == 1
                    && eq(untieredValues(stockholm), 5f)
                    && eq(untieredValues(overdose), 0.45f)
                    && !untieredPercent(stockholm)
                    && untieredPercent(overdose);

            int baseAlpha = baseCooldown("ALPHA");
            int baseBeta = baseCooldown("BETA");
            int[] plain = cooldowns(event.getServer().overworld(), null, item("THOUGHT_OVERDOSE"));
            int[] shortened = cooldowns(event.getServer().overworld(), "THOUGHT_OVERDOSE", item("THOUGHT_OVERDOSE"));

            boolean shortcut = baseAlpha == 300 && baseBeta == 6000
                    && shortened[0] == 165 && shortened[1] == 3300
                    && plain[0] == baseAlpha && plain[1] == baseBeta;

            boolean ok = shape && numbers && plumbing && shortcut;

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "%s | types sv=%s od=%s | sanity=%s cooldownTicks=%d cut=%s nauseaTicks=%d "
                            + "| cooldown alpha %d->%d (plain %d), beta %d->%d (plain %d) "
                            + "| tooltip lines %d/%d values %d/%d percent %s/%s | VERDICT=%s",
                    ids, types(stockholm), types(overdose), stockholmSanity, stockholmCooldown, overdoseCut,
                    overdoseNausea, baseAlpha, shortened[0], plain[0], baseBeta, shortened[1], plain[1],
                    untieredLineCount(stockholm), untieredLineCount(overdose),
                    untieredLineValueCount(stockholm, 0), untieredLineValueCount(overdose, 0),
                    untieredPercent(stockholm), untieredPercent(overdose),
                    ok ? "PASS(both match the spec)" : "FAIL(a value, a type or a cooldown does not match)"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "failed (expected only when sanitypd is absent): " + t);
        }
    }

    // ------------------------------------------------------------------ registry / constants

    private static Item item(String field)
    {
        try
        {
            Object holder = Class.forName(REGISTRY).getField(field).get(null);

            if (holder instanceof RegistryObject<?> ro && ro.isPresent())
                return (Item) ro.get();
        }
        catch (Throwable ignored)
        {
            // Reported by the caller as an absent item.
        }

        return null;
    }

    private static String name(Item item)
    {
        return item == null ? "ABSENT" : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Object call(Item item, String method)
    {
        try
        {
            Method m = item.getClass().getMethod(method);

            return m.invoke(item);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String enumName(Item item, String method)
    {
        Object value = call(item, method);

        return value == null ? "?" : String.valueOf(value);
    }

    private static List<?> typeList(Item item)
    {
        Object value = call(item, "types");

        return value instanceof List<?> list ? list : List.of();
    }

    private static String types(Item item)
    {
        return typeList(item).toString();
    }

    private static Boolean tiered(Item item)
    {
        Object value = call(item, "tiered");

        return value instanceof Boolean b ? b : null;
    }

    private static float constant(String field)
    {
        try
        {
            return Class.forName(EFFECTS).getField(field).getFloat(null);
        }
        catch (Throwable t)
        {
            return Float.NaN;
        }
    }

    private static int intConstant(String field)
    {
        try
        {
            return Class.forName(EFFECTS).getField(field).getInt(null);
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    private static float[] untieredValues(Item item)
    {
        try
        {
            Method m = Class.forName(EFFECTS).getMethod("untieredValuesOf", Class.forName("net.minecraft.world.item.Item"));

            return (float[]) m.invoke(null, item);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static boolean untieredPercent(Item item)
    {
        try
        {
            Method m = Class.forName(EFFECTS).getMethod("untieredValuesArePercent", Class.forName("net.minecraft.world.item.Item"));

            return Boolean.TRUE.equals(m.invoke(null, item));
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    private static int untieredLineCount(Item item) throws Exception
    {
        return (Integer) Class.forName(EFFECTS)
                .getMethod("untieredLineCount", Class.forName("net.minecraft.world.item.Item")).invoke(null, item);
    }

    private static int untieredLineValueCount(Item item, int line) throws Exception
    {
        return (Integer) Class.forName(EFFECTS)
                .getMethod("untieredLineValueCount", Class.forName("net.minecraft.world.item.Item"), int.class)
                .invoke(null, item, line);
    }

    // ------------------------------------------------------------------ the cooldown shortcut, measured

    /** The kind's own cooldown, straight off the enum the item uses. */
    private static int baseCooldown(String kind)
    {
        try
        {
            Class<?> k = Class.forName(ITEM_CLASS);

            for (Object value : k.getEnumConstants())
            {
                if (((Enum<?>) value).name().equals(kind))
                    return (Integer) k.getMethod("cooldownTicks").invoke(value);
            }
        }
        catch (Throwable t)
        {
            // Reported as -1 by the caller's verdict.
        }

        return -1;
    }

    /**
     * Both stabilizer cooldowns for a throwaway fake player, optionally with one thought in its chain.
     *
     * <p>The chain is the mod's own capability; the container it hands out is a vanilla one, so the item is
     * put in through a compiled call rather than a reflective name (vanilla members are renamed in
     * production, the mod's are not).
     */
    private static int[] cooldowns(ServerLevel level, String thoughtField, Item thoughtItem)
    {
        int alpha = baseCooldown("ALPHA");
        int beta = baseCooldown("BETA");

        try
        {
            FakePlayer player = FakePlayerFactory.get(level,
                    new GameProfile(UUID.nameUUIDFromBytes(("thought50-" + thoughtField).getBytes()), "Thought50Probe"));

            if (thoughtField != null && thoughtItem != null)
            {
                Field capField = Class.forName("piloser.sanitypd.thought.ThoughtChainProvider").getField("CAP");
                Object chain = player.getCapability((net.minecraftforge.common.capabilities.Capability) capField.get(null)).orElse(null);

                if (chain == null)
                    return new int[] { alpha, beta };

                Object container = chain.getClass().getMethod("container").invoke(chain);
                ((net.minecraft.world.Container) container).setItem(0, new ItemStack(thoughtItem));
            }

            Class<?> overdose = Class.forName(OVERDOSE);
            Method m = overdose.getMethod("cooldownTicks", Class.forName("net.minecraft.server.level.ServerPlayer"), int.class);

            return new int[] {
                (Integer) m.invoke(null, player, alpha),
                (Integer) m.invoke(null, player, beta)
            };
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "cooldown measurement failed: " + t);
            return new int[] { -1, -1 };
        }
    }

    private static boolean eq(float[] actual, float expected)
    {
        return actual != null && actual.length == 1 && Math.abs(actual[0] - expected) < 1e-6f;
    }
}
