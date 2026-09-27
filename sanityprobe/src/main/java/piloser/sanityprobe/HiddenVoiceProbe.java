package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Client-side probe for the extra inner-voice pool and the sound it triggers.
 *
 * <h2>Why this must be measured on the client</h2>
 * The pool lives in {@code piloser.sanitypd.client.HiddenVoicePool}, which needs a client: it reads the
 * unlock marker out of the save folder and plays a sound. A dedicated server never reaches that code, so
 * only a real client can observe it.
 *
 * <h2>What it reports</h2>
 * <ol>
 *   <li><b>[VOICE-32] API</b>: one-off check of the pool constants (line count, colour, sound cooldown,
 *       marker file name) and of how many language values resolve to drawable text.</li>
 *   <li><b>[VOICE-32] rule</b>: the rule that refuses a custom line repeating a pool line, exercised with
 *       synthetic values so that no real line has to be printed into the log.</li>
 *   <li><b>[VOICE-32] state</b>: one line whenever the unlock state or the sound-cooldown window changes.</li>
 *   <li><b>[VOICE-32] width</b>: how wide each resolved line is at the centre line's 2x scale compared with
 *       the current GUI width, re-checked whenever the language changes. This is what catches a line that
 *       would run off the screen, without putting the text in the log.</li>
 *   <li>The live state is mirrored in the top-left overlay as {@code [VOICE] …}, laid out by
 *       {@link HudLayout} like every other probe line.</li>
 * </ol>
 *
 * <p>WARNING: read-only. Every reflective lookup is guarded; a failure logs one line and can never affect
 * gameplay.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HiddenVoiceProbe
{
    /** Language key prefix of the extra pool, mirroring HiddenVoicePool's prefix. */
    private static final String KEY_PREFIX = "gui.sanitypd.hint4";

    /** Expected number of built-in lines, mirroring HiddenVoicePool#LINE_COUNT. */
    private static final int EXPECTED_LINES = 6;

    /** Expected on-screen colour, mirroring HiddenVoicePool#COLOR_DARK_RED. */
    private static final int EXPECTED_COLOR = 0x8B0000;

    /** Expected sound cooldown in ticks, mirroring HiddenVoicePool#SOUND_COOLDOWN_TICKS. */
    private static final int EXPECTED_COOLDOWN = 5 * 60 * 20;

    /** The centre line is drawn at twice the font size (see GuiHandler#drawHintLine). */
    private static final int CENTRE_SCALE = 2;

    /** GUI pixels kept free on each side of the centre line when judging an overflow. */
    private static final int WIDTH_MARGIN = 8;

    private static int s_tick;
    private static boolean s_once;
    private static String s_lastState = "";
    private static String s_hud = "-";
    private static String s_language = "";

    private HiddenVoiceProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        // The pool's own sound cooldown is five minutes, so a slow poll cannot miss a state change
        if (++s_tick % 10 != 0)
            return;

        try
        {
            tick();
        }
        catch (Throwable t)
        {
            ProbeLog.log("VOICE-32", "probe error: " + t);
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
            reportRule();
        }

        reportWidths(mc);

        boolean unlocked = unlocked();
        float cooldown = cooldownTicks();
        String state = (unlocked ? "unlocked" : "locked") + "|" + (cooldown <= 0f ? "ready" : "cooling");

        if (!state.equals(s_lastState))
        {
            s_lastState = state;
            ProbeLog.log("VOICE-32", String.format(Locale.ROOT,
                    "state=%s | soundCooldown=%s | save=%s",
                    unlocked ? "unlocked" : "locked",
                    cooldown <= 0f ? "ready" : ProbeLog.fmt(cooldown / 20f) + "s",
                    saveFile()));
        }

        s_hud = String.format(Locale.ROOT, "%s cd=%s",
                unlocked ? "unlocked" : "locked",
                cooldown <= 0f ? "-" : Math.round(cooldown / 20f) + "s");
    }

    /** One-off check of the pool constants and of the language values behind them. */
    private static void reportApi()
    {
        try
        {
            Class<?> pool = Class.forName("piloser.sanitypd.client.HiddenVoicePool");

            int lines = ((Number) pool.getField("LINE_COUNT").get(null)).intValue();
            int colour = ((Number) pool.getField("COLOR_DARK_RED").get(null)).intValue();
            int cooldown = ((Number) pool.getField("SOUND_COOLDOWN_TICKS").get(null)).intValue();
            String file = String.valueOf(pool.getField("SAVE_FILE_NAME").get(null));
            int resolved = (Integer) pool.getMethod("resolvedLineCount").invoke(null);

            boolean ok = lines == EXPECTED_LINES && colour == EXPECTED_COLOR && cooldown == EXPECTED_COOLDOWN
                    && resolved == EXPECTED_LINES && !file.isBlank();

            ProbeLog.log("VOICE-32", String.format(Locale.ROOT,
                    "API lines=%d colour=%06X cooldown=%dtick resolved=%d/%d marker=%s => %s",
                    lines, colour, cooldown, resolved, EXPECTED_LINES, file, ok ? "OK" : "CHECK"));

            // The extra pool must stay OUT of the visible candidate list: the command list and the pool sizes
            // come from effectiveHints, so its size has to be exactly "built-in + custom" of that tier.
            Class<?> manager = Class.forName("piloser.sanitypd.client.MentalHintManager");
            int builtin = (Integer) manager.getMethod("defaultCount", int.class).invoke(null, 1);
            int custom = (Integer) manager.getMethod("customCount", int.class).invoke(null, 1);
            int effective = ((java.util.List<?>) manager.getMethod("effectiveHints", int.class)
                    .invoke(null, 1)).size();

            ProbeLog.log("VOICE-32", String.format(Locale.ROOT,
                    "severetier builtin=%d custom=%d effective=%d (must equal builtin+custom) => %s",
                    builtin, custom, effective, effective == builtin + custom ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log("VOICE-32", "API check failed (expected only when sanitypd is absent): " + t);
        }

        ProbeHud.registerLine(() -> "\u0000FF6666" + "[VOICE] " + s_hud);
    }

    /**
     * Exercises the text rule: first with synthetic values, then with a real language value, because the rule
     * is what stops one sentence from ending up in a pool twice. Nothing from the pool is printed.
     */
    private static void reportRule()
    {
        try
        {
            Class<?> text = Class.forName("piloser.sanitypd.client.HiddenVoiceText");
            Method matches = text.getMethod("matchesAny", String.class, java.util.Collection.class, String.class);

            boolean tolerant = (Boolean) matches.invoke(null, "  HELLO   world ", List.of("Hello World"), "Dev");
            boolean different = (Boolean) matches.invoke(null, "some other line", List.of("Hello World"), "Dev");
            boolean placeholder = (Boolean) matches.invoke(null, "Hi Dev", List.of("Hi {player}"), "Dev");

            ProbeLog.log("VOICE-32", String.format(Locale.ROOT,
                    "rule tolerant=%s different=%s placeholder=%s => %s",
                    tolerant, !different, placeholder,
                    tolerant && !different && placeholder ? "OK" : "CHECK"));

            // The rule as the command applies it: a pool line must be refused both as written and with the
            // player name filled in, while an unrelated line has to pass.
            Minecraft mc = Minecraft.getInstance();
            String name = mc.player == null ? "" : mc.player.getDisplayName().getString();

            Class<?> pool = Class.forName("piloser.sanitypd.client.HiddenVoicePool");
            Method forbidden = pool.getMethod("matchesForbiddenText", String.class);

            String raw = Component.translatable(KEY_PREFIX + 0).getString();
            String filled = raw.replace("{player}", name);

            boolean refusesRaw = (Boolean) forbidden.invoke(null, raw);
            boolean refusesFilled = !filled.equals(raw) && (Boolean) forbidden.invoke(null, filled);
            boolean acceptsOther = !((Boolean) forbidden.invoke(null, "a line that is not part of the pool"));

            ProbeLog.log("VOICE-32", String.format(Locale.ROOT,
                    "commandrule refusesLine=%s refusesNamedLine=%s acceptsUnrelated=%s => %s",
                    refusesRaw, refusesFilled, acceptsOther,
                    refusesRaw && refusesFilled && acceptsOther ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log("VOICE-32", "rule check failed (expected only when sanitypd is absent): " + t);
        }
    }

    /**
     * Measures every resolved line at the centre line's scale and compares it with the GUI width.
     *
     * <p>Re-runs whenever the language changes, because the line lengths differ per language. Only widths are
     * logged: the text itself stays out of the log.
     */
    private static void reportWidths(Minecraft mc)
    {
        String language = String.valueOf(mc.options.languageCode);

        if (language.equals(s_language))
            return;

        s_language = language;

        String name = mc.player == null ? "" : mc.player.getDisplayName().getString();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int room = guiWidth - WIDTH_MARGIN * 2;

        for (int i = 0; i < EXPECTED_LINES; i++)
        {
            String key = KEY_PREFIX + i;

            try
            {
                String raw = Component.translatable(key).getString();
                String resolved = raw.replace("{player}", name).replace("%s", name);
                int width = mc.font.width(resolved) * CENTRE_SCALE;
                boolean fits = width <= room;

                ProbeLog.log("VOICE-32", String.format(Locale.ROOT,
                        "width lang=%s line=%d %dpx at %dx (room %dpx) => %s",
                        language, i, width, CENTRE_SCALE, room, fits ? "OK" : "OVERFLOW"));
            }
            catch (Throwable t)
            {
                ProbeLog.log("VOICE-32", "width check failed for " + key + ": " + t);
            }
        }
    }

    /** Whether the pool is unlocked for the save being played, via reflection. */
    private static boolean unlocked()
    {
        try
        {
            Class<?> pool = Class.forName("piloser.sanitypd.client.HiddenVoicePool");
            return (Boolean) pool.getMethod("isUnlocked").invoke(null);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /** Ticks left before another cave sound may play, via reflection. */
    private static float cooldownTicks()
    {
        try
        {
            Class<?> pool = Class.forName("piloser.sanitypd.client.HiddenVoicePool");
            return ((Number) pool.getMethod("soundCooldownTicks").invoke(null)).floatValue();
        }
        catch (Throwable t)
        {
            return 0f;
        }
    }

    /** Name of the per-save marker file, via reflection. */
    private static String saveFile()
    {
        try
        {
            Class<?> pool = Class.forName("piloser.sanitypd.client.HiddenVoicePool");
            return String.valueOf(pool.getField("SAVE_FILE_NAME").get(null));
        }
        catch (Throwable t)
        {
            return "<unknown>";
        }
    }
}
