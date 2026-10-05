package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Group: Depersonalization and its sanity <b>recovery ceiling</b> (probe v2.18.0).
 *
 * <p>The owner's spec: a purely negative thought that stops you recovering past a fraction of the sanity
 * maximum - 1 / 3 / 5 chaos-restraint thoughts in the chain mean 80% / 70% / 60%. The maximum itself is
 * untouched; only recovery is clamped, and the value above the brain gauge turns grey-red while the player
 * sits at the ceiling.
 *
 * <p>What this group checks, and why it is worth a group of its own: the ceiling is a <b>new kind of rule</b>
 * (a cap on gains rather than a change to the maximum), so the three things that can silently go wrong are
 * "the ladder and the sentence disagree", "the ceiling reads the wrong axis" and "the HUD colour never
 * switches". The first two are re-read from the mod here; the third is reported as a state line, because a
 * colour is something only the screen can settle.
 *
 * <p>Read-only: registers no gameplay content, changes no values, every entry point is guarded.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DepersonalizationProbe
{
    /** Expected ladder, as the penalty the tooltip prints (a negative number reads as a debuff). */
    private static final float[] EXPECTED_LADDER = { -0.20f, -0.30f, -0.40f };

    /** Expected ceilings the owner asked for: 80% / 70% / 60% of the maximum. */
    private static final float[] EXPECTED_CEILING = { 0.80f, 0.70f, 0.60f };

    /** Expected HUD colour while the value sits at the ceiling (grey-red). */
    private static final int EXPECTED_COLOUR = 0xB06060;

    private static int s_tick;
    private static boolean s_once;
    private static String s_lastState = "";
    private static String s_hud = "-";

    private DepersonalizationProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 20 != 0)
            return;

        try
        {
            tick();
        }
        catch (Throwable t)
        {
            ProbeLog.log("DEPERSON-44", "probe error: " + t);
        }
    }

    /** Leaving the world drops the once-only flag, so the next world reports again. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        s_once = false;
        s_lastState = "";
    }

    private static void tick() throws Exception
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        if (!s_once)
        {
            s_once = true;
            reportApi();
        }

        reportState(mc.player);
    }

    /** One-off check of the ladder, the ceilings, the tooltip agreement and the HUD colour constant. */
    private static void reportApi()
    {
        try
        {
            Class<?> effects = Class.forName("piloser.sanitypd.thought.ThoughtEffects");
            Class<?> registry = Class.forName("piloser.sanitypd.item.ItemRegistry");
            Class<?> itemClass = Class.forName("net.minecraft.world.item.Item");

            float[] ladder = (float[]) effects.getField("DEPERSONALIZATION").get(null);
            boolean ladderOk = ladder.length == EXPECTED_LADDER.length;

            StringBuilder detail = new StringBuilder();

            for (int i = 0; i < ladder.length; i++)
            {
                if (i >= EXPECTED_LADDER.length || Math.abs(ladder[i] - EXPECTED_LADDER[i]) > 1e-4f)
                    ladderOk = false;

                if (detail.length() > 0)
                    detail.append('/');

                detail.append(Math.round(ladder[i] * 100f));
            }

            ProbeLog.log("DEPERSON-44", String.format(Locale.ROOT,
                    "ladder %s (expected -20/-30/-40) => %s", detail, ladderOk ? "OK" : "CHECK"));

            // The tooltip must read the same table the mechanic does.
            Object holder = registry.getField("THOUGHT_DEPERSONALIZATION").get(null);
            Object item = holder.getClass().getMethod("get").invoke(holder);
            Method valueAt = effects.getMethod("valueAt", itemClass, int.class);

            boolean tooltipOk = true;
            StringBuilder tooltip = new StringBuilder();

            for (int i = 0; i < EXPECTED_LADDER.length; i++)
            {
                float v = ((Number) valueAt.invoke(null, item, i)).floatValue();

                if (Math.abs(v - EXPECTED_LADDER[i]) > 1e-4f)
                    tooltipOk = false;

                float ceiling = 1f + v;

                if (Math.abs(ceiling - EXPECTED_CEILING[i]) > 1e-4f)
                    tooltipOk = false;

                if (tooltip.length() > 0)
                    tooltip.append('/');

                tooltip.append(String.format(Locale.ROOT, "%.2f", ceiling));
            }

            ProbeLog.log("DEPERSON-44", String.format(Locale.ROOT,
                    "ceilings %s (expected 0.80/0.70/0.60) tooltip==mechanic => %s",
                    tooltip, tooltipOk ? "OK" : "CHECK"));

            // The HUD colour: re-read the constant, and make sure it is neither white nor a bright red (the
            // bright red already means "your sanity is low" in this HUD family).
            int colour = readColour();
            boolean colourOk = colour == EXPECTED_COLOUR && (colour & 0xFFFFFF) != 0xFFFFFF
                    && (colour & 0xFF) < 0xFF;

            ProbeLog.log("DEPERSON-44", String.format(Locale.ROOT,
                    "hudColour %06X (expected %06X) => %s",
                    colour & 0xFFFFFF, EXPECTED_COLOUR, colourOk ? "OK" : "CHECK"));

            ProbeHud.registerLine(() -> "\u00A7c[DEPERSON] " + s_hud);
        }
        catch (Throwable t)
        {
            ProbeLog.log("DEPERSON-44", "API check failed (expected only when sanitypd is absent): " + t);
        }
    }

    /** The live state: is the thought held, which tier, what ceiling, and is the value sitting on it. */
    private static void reportState(Player player)
    {
        try
        {
            Class<?> effects = Class.forName("piloser.sanitypd.thought.ThoughtEffects");
            Class<?> registry = Class.forName("piloser.sanitypd.item.ItemRegistry");
            Class<?> itemClass = Class.forName("net.minecraft.world.item.Item");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");

            Object holder = registry.getField("THOUGHT_DEPERSONALIZATION").get(null);
            Object item = holder.getClass().getMethod("get").invoke(holder);

            int tier = (Integer) effects.getMethod("tierIndex", entityClass, itemClass).invoke(null, player, item);
            float ceilingFraction = (Float) effects.getMethod("recoveryCeilingFraction", entityClass)
                    .invoke(null, player);

            Object cap = readCap(player);
            float value = -1f;
            float max = -1f;

            if (cap != null)
            {
                value = (Float) cap.getClass().getMethod("getSanity").invoke(cap);
                max = (Float) cap.getClass().getMethod("getMaxSanity").invoke(cap);
            }

            float ceiling = max * ceilingFraction;
            boolean atCeiling = cap != null && max > 0f && value >= ceiling - 0.01f && ceilingFraction < 1f;

            String state = String.format(Locale.ROOT, "held=%s tier=%d ceiling=%.2f value=%.2f/%.2f at=%s",
                    tier >= 0, tier, ceilingFraction, value, max, atCeiling);

            if (!state.equals(s_lastState))
            {
                s_lastState = state;
                ProbeLog.log("DEPERSON-44", state);
            }

            s_hud = String.format(Locale.ROOT, "ceil=%s%% at=%s",
                    Math.round(ceilingFraction * 100f), atCeiling);
        }
        catch (Throwable t)
        {
            ProbeLog.log("DEPERSON-44", "state check failed (expected only when sanitypd is absent): " + t);
        }
    }

    /** Reads the HUD colour constant out of GuiHandler (private, hence reflection). */
    private static int readColour() throws Exception
    {
        Field field = Class.forName("piloser.sanitypd.client.GuiHandler").getDeclaredField("SANITY_CEILING_COLOUR");
        field.setAccessible(true);

        return ((Number) field.get(null)).intValue();
    }

    /** The mod's sanity capability, read the way the other groups read it (see ProbeImpactProbe#readCap). */
    private static Object readCap(Player player)
    {
        try
        {
            Object token = Class.forName("piloser.sanitypd.capability.SanityProvider").getField("CAP").get(null);
            Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
            Object holder = player.getClass().getMethod("getCapability", capabilityClass).invoke(player, token);

            return holder == null ? null
                    : holder.getClass().getMethod("orElse", Object.class).invoke(holder, (Object) null);
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}
