package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Single layout engine for the probe HUD (added in v2.7.0).
 *
 * <h2>Why it exists</h2>
 * Four independent renderers used to draw into the same top-left corner with hardcoded
 * y-coordinates, so they overlapped as soon as the number of registered lines changed:
 * <pre>
 * ClientProbe (dynamic lines):  y = 4, 18, 32, 46, 60, ... (step 14, grows with the line count)
 * SoundProbe (hardcoded):       y = 24   overlaps the WALK line at 18
 * StabilizerProbe (hardcoded):  y = 34   overlaps the FLOAT line at 32
 * SneakHudProbe (hardcoded):    y = 46   overlaps the first registered line at 46
 * </pre>
 *
 * <h2>Rules</h2>
 * <ol>
 *   <li><b>Every line is drawn exactly once</b>: all HUD text must go through
 *       {@link ProbeHud#registerLine} and is laid out by {@link #draw}. Never add another
 *       {@code graphics.drawString(..., 4, <hardcoded y>, ...)}.</li>
 *   <li><b>F3-style layout (v2.8.0)</b>: the text is drawn at half scale, so a line only takes
 *       {@link #LINE_STEP} = 7 px and a full column fits roughly twice as many rows as the vanilla
 *       font. There is no fixed line limit any more: the column is filled down to the bottom of the
 *       screen, exactly like the F3 debug screen, so no line is dropped on a normal display.</li>
 *   <li><b>Nothing is drawn off-screen</b>: lines wider than the screen are truncated on a pixel
 *       boundary and end with {@code ~}; a footer (only when the screen really runs out of height)
 *       reports how many lines did not fit.</li>
 * </ol>
 *
 * <p>All entry points swallow exceptions and change no game state; the class only affects the
 * probe's own overlay lines.
 */
public final class HudLayout
{
    /** Line step in pixels at {@link #SCALE}; vanilla font height is 9, so half scale needs 5 plus spacing. */
    public static final int LINE_STEP = 7;

    /** Drawing origin, with a small margin from the top-left corner. */
    public static final int LEFT = 4;
    public static final int TOP = 4;

    /** Text scale of the whole column, matching the F3 debug screen's compact look. */
    public static final float SCALE = 0.5f;

    /** One HUD text line (text plus color). */
    private record HudLine(String text, int color) {}

    private static int s_tick;
    private static int s_lastDrawn = -1;
    private static int s_lastHidden;
    private static int s_lastMaxWidth;
    private static int s_lastCap;

    /**
     * Cache of the fitted result from the previous frame.
     *
     * <p>{@link #fit} measures width in pixels and may cut several substrings, while the render
     * event runs every frame, so without this cache each frame would allocate dozens of strings and
     * waste GC time. The previous result is reused as long as the raw content and the screen width
     * are unchanged; only composed lines that actually changed are recomputed.
     *
     * <p>Accessed from the client thread only (render event), so no synchronization is needed.
     */
    private static final List<String> s_cacheRaw = new ArrayList<>();
    private static final List<String> s_cacheFitted = new ArrayList<>();
    private static int s_cacheWidth = -1;

    private HudLayout() {}

    /**
     * Called from the client tick: every 10 seconds it logs how many lines were actually drawn,
     * how many were dropped and how wide the widest line was.
     *
     * <p>This makes the layout itself verifiable: the log carries {@code lines=} and {@code hidden=}
     * directly, so it is immediately clear whether the layout engine is doing its job.
     */
    public static void tick()
    {
        if (++s_tick % 200 != 0 || s_lastDrawn < 0)
            return;

        ProbeLog.log("HUD-27", "layout lines=" + s_lastDrawn + " (cap " + s_lastCap + " by screen height)"
                + " hidden=" + s_lastHidden + " scale=" + SCALE + " lineStep=" + LINE_STEP
                + " widest=" + s_lastMaxWidth + "px left=" + LEFT);
    }

    /**
     * Draws the whole probe HUD. {@code caller} is only used to tag exception logs; the render
     * path must never let an exception escape.
     *
     * @return number of lines actually drawn this frame (header and footer included), or -1 on error
     */
    public static int draw(GuiGraphics graphics, Minecraft mc, String caller)
    {
        try
        {
            // No shadow (4th argument false): with tightly packed lines a shadow can be mistaken
            // for a double-drawn glyph, and flat color is easier to read
            return draw0(graphics, mc);
        }
        catch (Throwable t)
        {
            ProbeLog.log("HUD-27", "draw failed in " + caller + ": " + t);
            return -1;
        }
    }

    private static int draw0(GuiGraphics graphics, Minecraft mc)
    {
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        List<HudLine> lines = new ArrayList<>();

        // ---- fixed header ----
        lines.add(new HudLine("[PROBE] v" + SanityProbe.VERSION + " | " + hudKeyName() + " hides overlay"
                + " | V shield W egg X enchant R refine Y splash | " + SanityProbe.ROUND_TAG, 0xFFFFCC44));

        // ---- lines contributed by the probe groups (walk / float / sound / stabilizer / sneak ...) ----
        for (String raw : ProbeHud.collectLines())
        {
            if (raw == null || raw.isEmpty())
                continue;

            String line = raw;
            int color = 0xFF55FFAA;

            // Legacy form: a "\u0000RRGGBB" prefix selects the line color
            if (line.length() > 7 && line.charAt(0) == '\u0000')
            {
                try
                {
                    color = 0xFF000000 | Integer.parseInt(line.substring(1, 7), 16);
                    line = line.substring(7);
                }
                catch (NumberFormatException ignored)
                {
                    // Malformed prefix: treat the whole line as plain text
                }
            }

            lines.add(new HudLine(line, color));
        }

        // ---- width cap: overlong lines are truncated by pixels, not by character count, so that
        //      mixed CJK/Latin text is measured correctly.
        //      Cache rule (see the s_cache* fields): reuse the previous frame only when both the
        //      screen width and the raw content are unchanged; on a width change everything must be
        //      refitted from the raw text, otherwise already-truncated text gets truncated twice. ----
        int maxWidth = Math.max(16, screenWidth - LEFT - 2);
        int widest = 0;

        List<String> rawTexts = new ArrayList<>(lines.size());
        List<String> fitted = new ArrayList<>(lines.size());
        boolean sameWidth = screenWidth == s_cacheWidth;

        for (int i = 0; i < lines.size(); i++)
        {
            HudLine line = lines.get(i);
            String raw = line.text();

            widest = Math.max(widest, font.width(raw));

            boolean reuse = sameWidth
                    && i < s_cacheRaw.size()
                    && s_cacheRaw.get(i).equals(raw);

            String shown = reuse ? s_cacheFitted.get(i) : fit(font, raw, maxWidth);

            rawTexts.add(raw);
            fitted.add(shown);
            lines.set(i, new HudLine(shown, line.color()));
        }

        s_cacheRaw.clear();
        s_cacheRaw.addAll(rawTexts);
        s_cacheFitted.clear();
        s_cacheFitted.addAll(fitted);
        s_cacheWidth = screenWidth;

        // ---- line cap: how many rows of LINE_STEP pixels fit between TOP and the bottom of the screen.
        //      There is no fixed maximum any more: like the F3 debug screen (and unlike v2.7.0, which
        //      stopped at 8 lines and left the last groups invisible), the column uses all the height
        //      that is actually available. ----
        int byHeight = Math.max(1, (screenHeight - TOP - LINE_STEP) / LINE_STEP);
        int cap = byHeight;
        int hidden = Math.max(0, lines.size() - cap);
        int drawn = Math.min(lines.size(), cap);

        // The whole column is drawn at SCALE: push once, draw in scaled coordinates, pop.
        // Drawing coordinates below are in unscaled pixels, so one row is LINE_STEP apart.
        graphics.pose().pushPose();
        graphics.pose().scale(SCALE, SCALE, 1f);

        float y = TOP;

        for (int i = 0; i < drawn; i++)
        {
            HudLine line = lines.get(i);
            graphics.drawString(font, line.text(), LEFT, (int) y, line.color(), false);
            y += LINE_STEP;
        }

        if (hidden > 0)
            graphics.drawString(font, "..." + hidden + " more line(s) not shown (" + hudKeyName()
                    + " hides the overlay, a smaller GUI scale shows more)", LEFT, (int) y, 0xFFFF8888, false);

        graphics.pose().popPose();

        s_lastDrawn = drawn;
        s_lastHidden = hidden;
        s_lastCap = cap;
        s_lastMaxWidth = widest;
        return drawn;
    }

    /**
     * The key currently bound to the overlay toggle, so the header always names the real key after a rebind.
     *
     * <p>Never throws: a failure to resolve the binding falls back to the default key name.
     */
    private static String hudKeyName()
    {
        try
        {
            return ProbeKeybinds.TOGGLE_HUD.getTranslatedKeyMessage().getString();
        }
        catch (Throwable t)
        {
            return "F4";
        }
    }

    /** Truncates a line to {@code maxWidth} pixels, marking the cut with {@code ~} to show there is more text. */
    private static String fit(Font font, String text, int maxWidth)
    {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth)
            return text;

        String ellipsis = " ~";
        int budget = maxWidth - font.width(ellipsis);

        if (budget <= 0)
            return "~";

        int end = text.length();

        while (end > 0 && font.width(text.substring(0, end)) > budget)
            end--;

        return text.substring(0, end) + ellipsis;
    }
}
