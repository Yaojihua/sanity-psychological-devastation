package piloser.sanityprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Visibility switch for the probe's on-screen text overlay, toggled with F4.
 *
 * <p>This is the probe's own overlay drawn in the top-left corner (the {@code [PROBE]} /
 * {@code [PROBE-SND]} / {@code [PROBE-STAB]} lines), never vanilla's sound subtitles, and it never
 * touches the main mod's HUD.
 *
 * <p>Probe groups can register their own line through {@link #registerLine(Supplier)}; all
 * registered lines are laid out and drawn by {@link HudLayout} instead of each group hooking
 * {@code RenderGuiEvent} itself.
 *
 * <p>Since v2.7.0 this is the only HUD entry point: anything that wants to appear in the top-left
 * corner must go through {@code registerLine}. Groups that draw directly with hardcoded
 * y-coordinates inevitably overlap the dynamic lines. A line's color is set with a
 * {@code "\u0000RRGGBB"} prefix (see {@link HudLayout}).
 */
public final class ProbeHud
{
    private static volatile boolean s_visible = true;

    /** One overlay line contributed by a probe group, displayed in registration order. */
    private static final List<Supplier<String>> LINES = new ArrayList<>();

    private ProbeHud() {}

    public static boolean isVisible()
    {
        return s_visible;
    }

    public static void setVisible(boolean visible)
    {
        s_visible = visible;
    }

    public static void toggle()
    {
        s_visible = !s_visible;
    }

    /**
     * Registers one overlay line. The supplier returns {@code null} or an empty string when the line
     * should not be shown this frame.
     *
     * <p>Any exception thrown by a supplier is swallowed: the probe must never affect rendering.
     */
    public static synchronized void registerLine(Supplier<String> line)
    {
        if (line != null && !LINES.contains(line))
            LINES.add(line);
    }

    /** Collects all registered overlay lines; supplier exceptions are swallowed and the line is skipped. */
    public static synchronized List<String> collectLines()
    {
        List<String> out = new ArrayList<>();

        for (Supplier<String> supplier : LINES)
        {
            try
            {
                String value = supplier.get();

                if (value != null && !value.isEmpty())
                    out.add(value);
            }
            catch (Throwable ignored)
            {
                // A broken supplier must never crash rendering
            }
        }

        return out;
    }
}
