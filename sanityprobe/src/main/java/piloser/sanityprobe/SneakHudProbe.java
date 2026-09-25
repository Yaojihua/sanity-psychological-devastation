package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Client-side probe for the sanity value drawn above the brain meter while sneaking.
 *
 * <h2>Why this needs a client probe</h2>
 * The label is pure client-side HUD rendering, so a dedicated server never reaches that code
 * and the behaviour can only be observed in a real client. The probe turns three things into
 * readable log lines:
 * <ol>
 *   <li><b>Visibility</b>: whether the label really appears and disappears with SHIFT
 *       (only transitions are logged, never one line per tick);</li>
 *   <li><b>Value correctness</b>: sanitypd's own text builder
 *       {@code piloser.sanitypd.client.GuiHandler.sanityValueText(ISanity)} is invoked
 *       reflectively and compared against the probe's independent
 *       {@code round(current)/ceil(max)} reading, yielding MATCH or MISMATCH;</li>
 *   <li><b>Translation key</b>: the key behind that text is parsed out together with its
 *       argument count, and the client language data is checked for the key actually
 *       existing -- a missing key would show the raw key string on screen.</li>
 * </ol>
 *
 * <p>All known sanity-value translation keys are checked in one pass, so a future mismatch
 * between the key used in code and the key shipped in the language file is visible at once.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SneakHudProbe
{
    /** Sanity-value translation keys: the first is the one in use, the others catch stale or wrong keys. */
    private static final String[] SANITY_KEYS = {
            "gui.sanitypd.sanity",
            "hud.sanitypd.sanity",
            "jade.sanitypd.sanity"
    };

    private static int s_tick;
    private static boolean s_lastSneaking;
    private static String s_lastText = "";
    private static String s_hud = "(no sneak yet)";
    private static Object s_lookup;          // cached result of the registry entry's get() (null on failure)

    private SneakHudProbe() {}

    static
    {
        // Since v2.7.0 the line goes through the shared HUD layout; drawing it at a hardcoded
        // y here used to collide with the first registered overlay line.
        ProbeHud.registerLine(() -> "\u000088DDFF" + "[SNEAK] " + s_hud);
    }

    /**
     * Resolves the sanity capability that sanitypd registers, by reflection.
     * On failure the exception type and message are logged: a failed lookup must never be
     * reported as a clean result, or an untested path would look like a passing one.
     */
    private static Object sanityCapability(Minecraft mc)
    {
        try
        {
            if (s_lookup == null)
            {
                Class<?> provider = Class.forName("piloser.sanitypd.capability.SanityProvider");
                Object cap = provider.getField("CAP").get(null);
                Method get = cap.getClass().getMethod("get");
                s_lookup = get.invoke(cap);
                ProbeLog.log("SNEAK-HUD", "capability lookup resolved: " + s_lookup);
            }

            Method m;
            try
            {
                m = s_lookup.getClass().getMethod("getCapability", net.minecraft.world.entity.Entity.class);
            }
            catch (NoSuchMethodException e)
            {
                m = s_lookup.getClass().getMethod("getCapability", Object.class);
            }

            Object result = m.invoke(s_lookup, mc.player);

            if (result instanceof java.util.Optional<?> opt)
                return opt.orElse(null);

            ProbeLog.log("SNEAK-HUD", "capability returned unexpected type: " + result);
            return null;
        }
        catch (Throwable t)
        {
            ProbeLog.log("SNEAK-HUD", "capability reflection FAILED: "
                    + t.getClass().getSimpleName() + " " + t.getMessage());
            return null;
        }
    }

    /**
     * Fallback: reads the fields of the client-side capability object directly. These field
     * names are not obfuscated. Returns {@code [current, max]}, or null when unreadable.
     */
    private static float[] rawSanityFromFields(Object cap)
    {
        try
        {
            java.lang.reflect.Field fSanity = cap.getClass().getDeclaredField("m_sanityVal");
            java.lang.reflect.Field fMax = cap.getClass().getDeclaredField("m_maxSanity");
            fSanity.setAccessible(true);
            fMax.setAccessible(true);
            return new float[] { fSanity.getFloat(cap), fMax.getFloat(cap) };
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static Object call(Object target, String method, Class<?>[] types, Object... args)
    {
        try
        {
            return target.getClass().getMethod(method, types).invoke(target, args);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** sanitypd's real text builder, if present, used to verify that the displayed number matches the actual value. */
    private static Component modText(Object cap)
    {
        try
        {
            Class<?> capInterface = Class.forName("piloser.sanitypd.capability.ISanity");
            Class<?> gui = Class.forName("piloser.sanitypd.client.GuiHandler");
            Method m = gui.getMethod("sanityValueText", capInterface);
            Object text = m.invoke(null, cap);
            return text instanceof Component c ? c : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** Whether the key exists in the client language data (existing means the raw key is never shown). */
    private static boolean keyExists(String key)
    {
        try
        {
            // Language.getLanguageData() returns a Map<String,String> in 1.20.1, not a Function.
            java.util.Map<String, String> data = net.minecraft.locale.Language.getInstance().getLanguageData();
            return data != null && data.get(key) != null;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 2 != 0)
            return;

        try
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null)
            {
                s_lastSneaking = false;
                return;
            }

            boolean sneaking = mc.player.isShiftKeyDown();

            if (sneaking != s_lastSneaking)
            {
                s_lastSneaking = sneaking;
                s_hud = sneaking ? "SHIFT down -> label should appear" : "SHIFT up -> label should hide";

                if (sneaking)
                {
                    // ---------- full cross-check once, when the label appears ----------
                    Object cap = sanityCapability(mc);

                    // Prefer the interface methods; fall back to reading the client fields directly.
                    Object rawCur = cap == null ? null : call(cap, "getSanity", new Class<?>[0]);
                    Object rawMax = cap == null ? null : call(cap, "getMaxSanity", new Class<?>[0]);

                    if (!(rawCur instanceof Number) || !(rawMax instanceof Number))
                    {
                        float[] fields = cap == null ? null : rawSanityFromFields(cap);
                        if (fields != null)
                        {
                            rawCur = Float.valueOf(fields[0]);
                            rawMax = Float.valueOf(fields[1]);
                        }
                    }

                    boolean haveNumbers = rawCur instanceof Number && rawMax instanceof Number;
                    String expected = haveNumbers
                            ? Math.round(((Number) rawCur).floatValue()) + "/"
                                    + Math.max(1, (int) Math.ceil(((Number) rawMax).floatValue()))
                            : "<capability unavailable>";

                    Component text = cap == null ? null : modText(cap);
                    String shown = text == null ? "<mod text method unavailable>" : text.getString();
                    String key = "<none>";
                    int argCount = -1;

                    if (text != null && text.getContents() instanceof TranslatableContents tc)
                    {
                        key = tc.getKey();
                        argCount = tc.getArgs() == null ? 0 : tc.getArgs().length;
                    }

                    // Verdict: the current/max pair must appear inside the text.
                    boolean match = haveNumbers && shown.contains(expected);

                    ProbeLog.log("SNEAK-HUD", "SHOWN sneak label: text=\"" + shown + "\" key=" + key
                            + " args=" + argCount
                            + " expected(round/ceil)=" + expected
                            + " -> " + (match ? "MATCH" : "MISMATCH"));

                    if (text != null && !keyExists(key))
                    {
                        ProbeLog.log("SNEAK-HUD", "!! translation key MISSING in client lang -> raw key would be displayed: " + key);
                    }

                    // ---------- report the existence of the related keys too, to catch a wrong key name ----------
                    for (String k : SANITY_KEYS)
                    {
                        ProbeLog.log("SNEAK-HUD", "key " + k + " existsInLang=" + keyExists(k));
                    }
                }
                else
                {
                    ProbeLog.log("SNEAK-HUD", "SHIFT released: label must be gone (nothing is drawn by sanitypd when not sneaking)");
                }
            }
            else if (sneaking)
            {
                // ---------- while sneaking: log a line only when the shown text changes, to avoid spam ----------
                Object cap = sanityCapability(mc);
                Component text = cap == null ? null : modText(cap);
                String shown = text == null ? "" : text.getString();

                if (!shown.isEmpty() && !shown.equals(s_lastText))
                {
                    s_lastText = shown;
                    ProbeLog.log("SNEAK-HUD", "value changed while sneaking -> \"" + shown + "\"");
                    s_hud = shown;
                }
            }
        }
        catch (Throwable ignored)
        {
            // The probe must never affect gameplay.
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        // This class no longer draws anything itself: the line is registered in the static
        // block above and laid out by {@link HudLayout}. The subscription is kept empty on
        // purpose so it stays obvious who owns the drawing -- do not add rendering back here.
    }
}
