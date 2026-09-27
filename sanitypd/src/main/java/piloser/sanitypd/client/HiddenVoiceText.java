package piloser.sanitypd.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Text half of the extra inner-voice pool: decides whether a text a player wants to add repeats one of
 * the built-in extra lines.
 *
 * <p>Written as a plain, side-agnostic utility on purpose. The pool itself ({@link HiddenVoicePool}) needs
 * a client - it reads the save folder and plays a sound - but this rule is pure string work, so leaving the
 * annotation off keeps the class loadable on a dedicated server, where the rule can be asserted headlessly.
 * Annotating it with {@code @OnlyIn(Dist.CLIENT)} would make Forge's {@code RuntimeDistCleaner} reject it at
 * class load on a dedicated server and turn "is this rule correct" into something only a live client can
 * answer.
 */
public final class HiddenVoiceText
{
    /** Player-name placeholder used by the extra lines' language values. */
    public static final String PLAYER_PLACEHOLDER = "{player}";

    /** Also substituted when a value uses it: the placeholder the three madness tiers already use. */
    private static final String PERCENT_S = "%s";

    private HiddenVoiceText() {}

    /**
     * Whether the submitted text repeats one of the raw language values.
     *
     * <p>Comparison is deliberately forgiving: surrounding and repeated inner whitespace is ignored and case
     * does not matter. These lines are typed by hand, so a difference of spacing or capitalisation is still
     * the same sentence to the player, and letting it through would put one sentence on screen twice.
     *
     * @param typed      text the player submitted, may be {@code null}
     * @param rawValues  raw language values, {@code null} entries allowed
     * @param playerName name substituted for the placeholder, may be {@code null}
     * @return {@code true} when the text must be refused
     */
    public static boolean matchesAny(String typed, Collection<String> rawValues, String playerName)
    {
        String needle = normalize(typed);

        if (needle.isEmpty() || rawValues == null)
            return false;

        for (String raw : rawValues)
        {
            if (raw == null)
                continue;

            for (String variant : variants(raw, playerName))
            {
                if (needle.equals(normalize(variant)))
                    return true;
            }
        }

        return false;
    }

    /**
     * The forms of one raw value that count as the same line: exactly as written, and with the player-name
     * placeholder filled in (the form that actually reaches the screen).
     */
    public static List<String> variants(String raw, String playerName)
    {
        List<String> out = new ArrayList<>(2);

        if (raw == null)
            return out;

        out.add(raw);

        String filled = fill(raw, playerName);
        if (!filled.equals(raw))
            out.add(filled);

        return out;
    }

    /** Fills the player-name placeholder of a raw language value. */
    public static String fill(String raw, String playerName)
    {
        if (raw == null)
            return "";

        String name = playerName == null ? "" : playerName;
        return raw.replace(PLAYER_PLACEHOLDER, name).replace(PERCENT_S, name);
    }

    /** Trim, collapse inner whitespace runs and lower case; applied to both sides of the comparison. */
    public static String normalize(String text)
    {
        if (text == null)
            return "";

        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
