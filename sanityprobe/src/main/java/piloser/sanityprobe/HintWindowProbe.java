package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Client-side probe for the warning-window rules of the inner monologue (round-28 behaviour).
 *
 * <h2>Why this must be measured on the client</h2>
 * Tier selection, the pre-damage window and the immunity-expiry window all live in
 * {@code piloser.sanitypd.client.GuiHandler} and {@code MentalHintManager}, which a dedicated server
 * never reaches. The rules being watched:
 *
 * <ul>
 *   <li>madness at or above the severe threshold = severe pool only (the deep pool stays reserved);</li>
 *   <li>the deep pool speaks once, in the last 5 seconds before the mania damage starts;</li>
 *   <li>while mania immunity is held, the severe pool is drawn and the expiry pool takes over for the
 *       buff's final 5 seconds.</li>
 * </ul>
 *
 * <h2>What it reports</h2>
 * <ol>
 *   <li><b>[HINTWIN-28]</b>: one line whenever the <i>window state</i> changes, plus a one-off API check
 *       (tier threshold constants, the expiry pool index and its built-in line count).</li>
 *   <li><b>[HINTWIN-28]</b> is also mirrored in the top-left overlay as {@code [HWIN] …} so a single
 *       screenshot carries the evidence.</li>
 * </ol>
 *
 * <p>WARNING: read-only. Every reflective lookup is guarded; a failure logs one line and can never
 * affect gameplay.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HintWindowProbe
{
    /** Grace period before mania starts dealing damage, mirroring SanityCombat#MANIA_GRACE_TICKS. */
    private static final int MANIA_GRACE_TICKS = 800;
    /** Warning window length, mirroring GuiHandler#IMMUNITY_EXPIRY_WARNING_TICKS. */
    private static final int WARNING_WINDOW_TICKS = 100;

    private static int s_tick;
    private static String s_lastState = "";
    private static boolean s_once;
    private static String s_hud = "-";

    private HintWindowProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        // Every 10 ticks: the windows are 100 ticks long, so this cannot miss one
        if (++s_tick % 10 != 0)
            return;

        try
        {
            tick();
        }
        catch (Throwable t)
        {
            ProbeLog.log("HINTWIN-28", "probe error: " + t);
        }
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

        float madness = madnessOf(mc.player);
        if (madness < 0f)
            return;

        int maniaTicks = maniaTicksOf(mc.player);
        MobEffectInstance immunity = immunityOf(mc.player);
        int immunityTicks = immunity == null ? -1 : immunity.getDuration();

        String state = stateOf(madness, maniaTicks, immunityTicks);

        if (!state.equals(s_lastState))
        {
            s_lastState = state;
            ProbeLog.log("HINTWIN-28", String.format(Locale.ROOT,
                    "window=%s | madness=%.3f (sanity %s%%) | maniaTicks=%d (grace %d) | immunityTicks=%s | expected=%s",
                    state, madness, ProbeLog.fmt((1f - madness) * 100f), maniaTicks, MANIA_GRACE_TICKS,
                    immunityTicks < 0 ? "none" : String.valueOf(immunityTicks), expectedPool(state, madness)));
        }

        s_hud = String.format(Locale.ROOT, "%s mad=%.2f man=%d imm=%s",
                state, madness, maniaTicks, immunityTicks < 0 ? "-" : String.valueOf(immunityTicks));
    }

    /** Which display window the current numbers put the player in. */
    private static String stateOf(float madness, int maniaTicks, int immunityTicks)
    {
        if (madness < 0.50f)
            return "closed-sane";

        if (immunityTicks >= 0 && maniaTicks > 0)
            return immunityTicks <= WARNING_WINDOW_TICKS ? "expiry-window" : "immunity-severe";

        if (maniaTicks >= MANIA_GRACE_TICKS - WARNING_WINDOW_TICKS && maniaTicks <= MANIA_GRACE_TICKS)
            return "pre-damage-deep";

        if (maniaTicks > MANIA_GRACE_TICKS)
            return "damage-severe";

        return "severe";
    }

    /** The pool the rules above say should be on screen, as a readable expectation. */
    private static String expectedPool(String state, float madness)
    {
        return switch (state)
        {
            case "closed-sane" -> "none (sanity above 50%)";
            case "expiry-window" -> "expiry pool (one line)";
            case "immunity-severe" -> "severe pool";
            case "pre-damage-deep" -> "deep pool (one line)";
            default -> madness >= 0.75f ? "severe pool" : "mild pool";
        };
    }

    /** Reads ISanity#getMadness() through the capability, or -1 when it is not available. */
    private static float madnessOf(Player player) throws Exception
    {
        Class<?> providerClass = Class.forName("piloser.sanitypd.capability.SanityProvider");
        Field capField = providerClass.getField("CAP");
        Object capToken = capField.get(null);

        Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
        Method getCapability = Player.class.getMethod("getCapability", capabilityClass);

        // Null token means the capability is not registered (for example a different mod set)
        if (capToken == null)
            return -1f;

        Object cap = getCapability.invoke(player, capToken);
        if (cap == null)
            return -1f;

        Object orElse = cap.getClass().getMethod("orElse", Object.class).invoke(cap, (Object) null);
        if (orElse == null)
            return -1f;

        Method getMadness = orElse.getClass().getMethod("getMadness");
        return (Float) getMadness.invoke(orElse);
    }

    /** Reads ISanity#getManiaTicks(), or 0 when it is not available. */
    private static int maniaTicksOf(Player player) throws Exception
    {
        Class<?> providerClass = Class.forName("piloser.sanitypd.capability.SanityProvider");
        Object capToken = providerClass.getField("CAP").get(null);
        if (capToken == null)
            return 0;

        Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
        Object cap = Player.class.getMethod("getCapability", capabilityClass).invoke(player, capToken);
        if (cap == null)
            return 0;

        Object orElse = cap.getClass().getMethod("orElse", Object.class).invoke(cap, (Object) null);
        if (orElse == null)
            return 0;

        return (Integer) orElse.getClass().getMethod("getManiaTicks").invoke(orElse);
    }

    /** The mania-immunity effect instance the player is carrying, or {@code null}. */
    private static MobEffectInstance immunityOf(Player player) throws Exception
    {
        Class<?> registryClass = Class.forName("piloser.sanitypd.effect.EffectRegistry");
        Object holder = registryClass.getField("MANIA_IMMUNITY").get(null);
        if (holder == null)
            return null;

        Object effect = holder.getClass().getMethod("get").invoke(holder);
        if (!(effect instanceof MobEffect mobEffect))
            return null;

        return player.getEffect(mobEffect);
    }

    /** One-off check that the round-28 constants and the expiry pool really exist. */
    private static void reportApi()
    {
        try
        {
            Class<?> manager = Class.forName("piloser.sanitypd.client.MentalHintManager");

            float severeOnly = ((Number) manager.getField("SEVERE_ONLY_MADNESS").get(null)).floatValue();
            float t2 = ((Number) manager.getField("T2_MADNESS").get(null)).floatValue();
            int expiryIndex = ((Number) manager.getField("INDEX_EXPIRY").get(null)).intValue();
            int tierCount = ((Number) manager.getField("TIER_COUNT").get(null)).intValue();

            Method defaultCount = manager.getMethod("defaultCount", int.class);
            int expiryLines = (Integer) defaultCount.invoke(null, expiryIndex);

            ProbeLog.log("HINTWIN-28", String.format(Locale.ROOT,
                    "API severeOnly=%.2f (deep threshold %.2f) tierCount=%d expiryIndex=%d expiryLines=%d => %s",
                    severeOnly, t2, tierCount, expiryIndex, expiryLines,
                    (severeOnly > 0f && expiryIndex == tierCount && expiryLines > 0) ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log("HINTWIN-28", "API check failed (expected only if sanitypd is absent): " + t);
        }

        ProbeHud.registerLine(() -> "\u0000FFAAFF" + "[HWIN] " + s_hud);
    }
}
