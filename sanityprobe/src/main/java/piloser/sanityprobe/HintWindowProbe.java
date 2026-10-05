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
 * Client-side probe for the warning-window rules of the inner monologue.
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
    /** Cached client renderer handle and its private quiet-stretch field, read reflectively. */
    private static Object s_gui;
    private static Field s_quietField;
    /** Cached private "the extra inner-voice line is on screen" flag of the same object. */
    private static Field s_hiddenField;
    /** Whether the quiet stretch currently suppresses the regular draw ("hold" / "free"). */
    private static String s_lastHold = "";
    /** Whether an extra inner-voice line is on screen right now, and how long it has been (probe ticks). */
    private static boolean s_hisOnScreen;
    private static int s_hisTicks;

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
        float quiet = quietStretch();

        if (!state.equals(s_lastState))
        {
            s_lastState = state;
            ProbeLog.log("HINTWIN-28", String.format(Locale.ROOT,
                    "window=%s | madness=%.3f (sanity %s%%) | maniaTicks=%d (grace %d) | immunityTicks=%s | quiet=%s | expected=%s",
                    state, madness, ProbeLog.fmt((1f - madness) * 100f), maniaTicks, MANIA_GRACE_TICKS,
                    immunityTicks < 0 ? "none" : String.valueOf(immunityTicks), ProbeLog.fmt(quiet),
                    expectedPool(state, madness)));
        }

        // The warning-window quiet stretch suppresses the regular draw while it is above zero. A value that
        // stays above zero while no warning window is open means the centre is reserved for a line that will
        // never come - which is exactly what "no inner-voice line appears at all" looks like from outside.
        String hold = quiet > 0f ? "hold" : "free";
        if (!hold.equals(s_lastHold))
        {
            s_lastHold = hold;
            ProbeLog.log("HINTWIN-28", String.format(Locale.ROOT,
                    "quiet=%s quietValue=%s | window=%s | maniaTicks=%d | immunityTicks=%s | madness=%.3f",
                    hold.toUpperCase(Locale.ROOT), ProbeLog.fmt(quiet), state, maniaTicks,
                    immunityTicks < 0 ? "none" : String.valueOf(immunityTicks), madness));
        }

        // How long an extra inner-voice line actually stays on screen. Measured, not assumed: the on-screen
        // window of these lines is a tuning value, and "it stays too long" was reported from a live session.
        // One line per appearance, with the length only - never the text.
        boolean his = hiddenVoiceOnScreen();
        if (his && !s_hisOnScreen)
        {
            s_hisOnScreen = true;
            s_hisTicks = 0;
        }
        else if (his)
        {
            s_hisTicks += 10;   // this probe runs every 10 ticks
        }
        else if (s_hisOnScreen)
        {
            s_hisOnScreen = false;
            int ticks = s_hisTicks + 10;
            ProbeLog.log("VOICE-32", String.format(Locale.ROOT,
                    "his-line window closed after ~%d ticks (~%.1fs) => %s",
                    ticks, ticks / 20f, ticks <= 160 ? "OK(short)" : "CHECK(still long)"));
        }

        s_hud = String.format(Locale.ROOT, "%s mad=%.2f man=%d imm=%s q=%s",
                state, madness, maniaTicks, immunityTicks < 0 ? "-" : String.valueOf(immunityTicks),
                quiet > 0f ? ProbeLog.fmt(quiet) : "-");
    }

    /** Whether one of the extra inner-voice lines is on screen right now, via reflection. */
    private static boolean hiddenVoiceOnScreen()
    {
        try
        {
            Object gui = gui();
            if (gui == null)
                return false;

            if (s_hiddenField == null)
            {
                s_hiddenField = gui.getClass().getDeclaredField("m_hintHiddenVoice");
                s_hiddenField.setAccessible(true);
            }

            return s_hiddenField.getBoolean(gui);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /**
     * Reads the warning-window quiet stretch of the centre-line renderer, via reflection.
     *
     * <p>The field is private and client-only, so this is the only way to observe it; the probe never writes
     * to it. A non-zero value that does not drain is the failure this probe exists to catch.
     */
    private static float quietStretch()
    {
        try
        {
            Object gui = gui();
            if (gui == null)
                return 0f;

            if (s_quietField == null)
            {
                s_quietField = gui.getClass().getDeclaredField("m_maniaHintQuiet");
                s_quietField.setAccessible(true);
            }

            return s_quietField.getFloat(gui);
        }
        catch (Throwable t)
        {
            return 0f;
        }
    }

    /**
     * The client-side centre-line renderer, resolved once through {@code SanityMod#getInstance()/getGui()}.
     *
     * <p>Its interesting state (the warning quiet stretch, the extra-voice flag) is private, so reflection is
     * the only way to observe it. The probe never writes to it.
     */
    private static Object gui()
    {
        try
        {
            if (s_gui == null)
            {
                Class<?> mod = Class.forName("piloser.sanitypd.SanityMod");
                Object instance = mod.getMethod("getInstance").invoke(null);
                s_gui = mod.getMethod("getGui").invoke(instance);
            }

            return s_gui;
        }
        catch (Throwable t)
        {
            return null;
        }
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

    /** One-off check that the warning-window constants and the expiry pool really exist. */
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
