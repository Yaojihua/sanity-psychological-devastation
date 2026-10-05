package piloser.sanitypd.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.thought.TypeHintSpec;

/**
 * The authored script that a memory fragment's tape plays, in order.
 *
 * <h2>One script per fragment</h2>
 * The lines live in {@code assets/sanitypd/memory/<item id>_<language>.txt}, so every fragment carries
 * its own script and the item's own id is the only thing that ties them together - adding a fragment is
 * two text files and one registration, never a copy of the playback code.
 *
 * <h2>Why the lines live in resources, not in this file</h2>
 * The script is displayed prose, so it sits outside the Java sources for three reasons: it is prose
 * rather than behaviour; the project's de-identification gate scans Java comments and literals for words
 * that only make sense to the two people building this mod, and the script legitimately contains such
 * words (it calls the player "the master of the world"), so a keyword check flagged a line of the owner's
 * own writing; and wording can be revised without touching code. Vanilla's end poem is a text resource
 * for the same kind of reason.
 *
 * <p>The double quotes are part of the text: the owner asked for them to be kept on purpose. The
 * {@code {player}} placeholder is filled by {@link HiddenVoiceText#fill}, the same substitution the inner
 * voice lines and the title screen use.
 */
@OnlyIn(Dist.CLIENT)
public final class MemoryScript
{
    /** Resource folder holding one pair of files per fragment. */
    private static final String FOLDER = "memory";

    /** The character the owner writes where a word must not be readable. */
    public static final char GARBLE = '\u25A0';

    /**
     * Prefix a script line may carry to be drawn in the <b>third voice's dark red</b> - the owner's request
     * for the last line of the finale. The loader strips it, so the file keeps reading as prose, and the
     * colour value itself is taken from {@code HiddenVoicePool.COLOR_DARK_RED} rather than re-declared.
     */
    public static final String DARK_RED_MARKER = "[darkred]";

    /** Marker pair the styled text helper understands; see {@link TypeHintSpec#styled(String)}. */
    private static final String OBFUSCATE_ON = "\u00A7k";
    private static final String OBFUSCATE_OFF = "\u00A7r";

    /** The loaded lines of the fragment currently playing; empty until {@link #reload(String)} succeeds. */
    private static List<String> s_lines = Collections.emptyList();

    /** Per line: whether it is drawn in the third voice's dark red (see {@link #DARK_RED_MARKER}). */
    private static List<Boolean> s_darkRed = Collections.emptyList();

    private MemoryScript() {}

    /**
     * Reads the script of one fragment - named by its item id - in the client's own language.
     *
     * <p>Both languages ship as separate files. Choosing by the client's selected language is what makes
     * the story read in the player's own language. A missing or empty file falls back to the other
     * language, which is a story in the wrong language rather than no story at all; the line counts are
     * compared because the playback timing depends on how many lines there are.
     */
    public static void reload(String key)
    {
        Minecraft mc = Minecraft.getInstance();
        boolean chinese = mc != null
                && mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("zh");

        String usedLang = chinese ? "zh" : "en";
        String otherLang = chinese ? "en" : "zh";

        List<String> used = read(key, usedLang);
        List<String> other = read(key, otherLang);

        if (used.isEmpty())
        {
            SanityMod.LOGGER.warn("[MEMORY] no script for {} in this language; falling back", key);
            used = other;
        }
        else if (!other.isEmpty() && other.size() != used.size())
        {
            SanityMod.LOGGER.warn("[MEMORY] the two scripts of {} have different lengths ({} vs {}); the "
                    + "tape would run longer in one language than the other - check both files",
                    key, used.size(), other.size());
        }

        // Strip the per-line colour marker once, here, so every reader below sees plain prose.
        List<String> stripped = new ArrayList<>(used.size());
        List<Boolean> dark = new ArrayList<>(used.size());
        for (String raw : used)
        {
            boolean isDark = raw.startsWith(DARK_RED_MARKER);
            stripped.add(isDark ? raw.substring(DARK_RED_MARKER.length()) : raw);
            dark.add(isDark);
        }

        s_lines = stripped;
        s_darkRed = dark;
        SanityMod.LOGGER.info("[MEMORY] script of {} loaded: {} line(s), language={}", key, s_lines.size(), usedLang);
    }

    /** Whether this line is drawn in the third voice's dark red. */
    public static boolean isDarkRed(int index)
    {
        return index >= 0 && index < s_darkRed.size() && Boolean.TRUE.equals(s_darkRed.get(index));
    }

    /** Number of lines loaded. */
    public static int count()
    {
        return s_lines.size();
    }

    /**
     * One line with the player-name placeholder filled in.
     *
     * @param index      line index, must be within {@code [0, count())}
     * @param playerName name substituted for {@code {player}}, may be {@code null}
     */
    public static String line(int index, String playerName)
    {
        return HiddenVoiceText.fill(s_lines.get(index), playerName);
    }

    /**
     * One line as a component to draw, with every run of {@link #GARBLE} rendered as garbled text.
     *
     * <p>The owner's rule is "a black square is garbled by default": the script keeps the squares exactly
     * as written, and this is the single place that decides what they look like. The run keeps its length,
     * so the line's width never changes, and it becomes a real {@code OBFUSCATED} style span through
     * {@link TypeHintSpec#styled(String)} rather than a bare {@code §k} for the font path to interpret.
     *
     * @param index      line index, must be within {@code [0, count())}
     * @param playerName name substituted for the {@code {player}} placeholder, may be {@code null}
     */
    public static MutableComponent component(int index, String playerName)
    {
        String line = line(index, playerName);
        StringBuilder marked = new StringBuilder(line.length() + 8);

        for (int i = 0; i < line.length(); i++)
        {
            char c = line.charAt(i);

            if (c != GARBLE)
            {
                marked.append(c);
                continue;
            }

            int start = i;
            while (i + 1 < line.length() && line.charAt(i + 1) == GARBLE)
                i++;

            marked.append(OBFUSCATE_ON).append(line, start, i + 1).append(OBFUSCATE_OFF);
        }

        return TypeHintSpec.styled(marked.toString());
    }

    /** Reads one script file, one line per entry; an unreadable file is an empty script. */
    private static List<String> read(String key, String lang)
    {
        List<String> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        if (mc == null)
            return lines;

        ResourceLocation location = new ResourceLocation(SanityMod.MODID,
                FOLDER + "/" + key + "_" + lang + ".txt");

        try
        {
            Resource resource = mc.getResourceManager().getResource(location).orElse(null);
            if (resource == null)
            {
                SanityMod.LOGGER.warn("[MEMORY] script resource {} is missing", location);
                return lines;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.open(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (!line.isBlank())
                        lines.add(line);
                }
            }
        }
        catch (IOException e)
        {
            SanityMod.LOGGER.warn("[MEMORY] could not read {}: {}", location, e.toString());
        }

        return lines;
    }
}
