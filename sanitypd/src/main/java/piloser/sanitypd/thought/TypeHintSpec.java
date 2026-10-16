package piloser.sanitypd.thought;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Type hint pools: the per-thought-type inner-voice lines and the rules that place them.
 *
 * <p>Specification: while the thought chain holds at least one thought of a given type, that
 * type's own pool joins the draw of <b>one</b> madness tier - composure joins the mild tier, every other
 * type joins the severe tier. The lines are deliberately <b>not</b> part of the editable pool: they never
 * appear in {@code /sanity hint list}, the remove/clear commands cannot reach them, and a text that
 * repeats one of them is refused <b>silently</b> (the same rule the extra inner-voice pool uses, see
 * {@link piloser.sanitypd.client.HiddenVoiceText}). Each type keeps its own pool; the pools only ever
 * <i>mix into</i> the tier draw, they are never merged into another pool's storage.
 *
 * <p>This half is deliberately <b>free of {@code @OnlyIn(Dist.CLIENT)}</b>: it is a table plus pure text
 * work, so a headless self-check on a dedicated server can assert the counts, the tier mapping, the
 * placeholder filling and the obfuscation parsing. The client half - reading the local player's chain and
 * resolving the language values - lives in {@code piloser.sanitypd.client.TypeHintPools}. The split is the
 * same one {@code HiddenVoiceText} / {@code HiddenVoicePool} already uses.
 */
public final class TypeHintSpec
{
    /** Language key stem: {@code gui.sanitypd.hinttype.<type id>.<index>}. */
    public static final String KEY_PREFIX = "gui.sanitypd.hinttype.";

    /**
     * Index of the mild madness tier and of the severe one.
     *
     * <p>Named here instead of reusing {@code MentalHintManager}'s constants so this class stays loadable
     * without the client-only manager. The values are asserted against that class by the probe.
     */
    public static final int TIER_MILD = 0;
    public static final int TIER_SEVERE = 1;

    /**
     * Index of the chaos-restraint line that leaves the pool once the story is anchored.
     *
 * <p>That line is the one that half-remembers the hidden thing ("I remember now, it is..."). The
     * rule: once all four story fragments have been read through, the player <b>has</b> remembered, so the line
     * has nothing left to say and must stop being dealt. Reading the fragments is exactly what
     * {@code HiddenVoicePool#anchored()} means for this save, so the pool asks that rather than storing a second
     * flag that could disagree with it.
     */
    public static final int CHAOS_RESTRAINT_ANCHORED_DROP_INDEX = 3;

    /**
     * One pool: which type feeds it, which madness tier it shares its draw with, how many lines it has and
     * in which colour those lines are drawn.
     *
     * @param type  the counting axis this pool belongs to
     * @param tier  madness tier whose draw this pool joins ({@link #TIER_MILD} or {@link #TIER_SEVERE})
     * @param count number of built-in lines; mirrors the language keys exactly
     * @param color {@code 0xRRGGBB} draw colour; {@code 0xFFFFFF} = the tier's normal white
     * @param dropsWhenAnchored index of the one line that stops being dealt once the story is anchored
     *              ({@code -1} = the whole pool always stays), see
     *              {@link #CHAOS_RESTRAINT_ANCHORED_DROP_INDEX}
     */
    public record Pool(ThoughtType type, int tier, int count, int color, int dropsWhenAnchored)
    {
        /** A pool whose lines all stay for good. */
        public Pool(ThoughtType type, int tier, int count, int color)
        {
            this(type, tier, count, color, -1);
        }

        /** Language key of one line of this pool. */
        public String key(int index)
        {
            return KEY_PREFIX + type.id() + "." + index;
        }
    }

    /**
 * Every pool, in the order the author listed them.
     *
 * <p>Hallucination has no pool: the specification names five types, and this table is the single place
     * that decides which types have one - adding the sixth later is a row here plus its language keys.
     *
     * <p>Every pool is drawn in the tier's normal white. The chaos-restraint pool used to carry its own
 * dark red ({@code 0x8B2E2E}, the type's tooltip colour); the design requires the normal white in the same
     * round the self-injury pool was added, so the colour column now says {@code 0xFFFFFF} everywhere and only
     * the extra inner-voice pool changes what a line looks like.
     */
    public static final List<Pool> POOLS = List.of(
            new Pool(ThoughtType.COMPOSURE, TIER_MILD, 4, 0xFFFFFF),
            new Pool(ThoughtType.MADNESS, TIER_SEVERE, 9, 0xFFFFFF),
            new Pool(ThoughtType.ENDURANCE, TIER_SEVERE, 5, 0xFFFFFF),
            new Pool(ThoughtType.SERVITUDE, TIER_SEVERE, 3, 0xFFFFFF),
            new Pool(ThoughtType.CHAOS_RESTRAINT, TIER_SEVERE, 5, 0xFFFFFF, CHAOS_RESTRAINT_ANCHORED_DROP_INDEX));

    private TypeHintSpec() {}

    /** The pools that share the given tier's draw; empty for the deep and expiry tiers. */
    public static List<Pool> poolsForTier(int tier)
    {
        List<Pool> out = new ArrayList<>();

        for (Pool p : POOLS)
        {
            if (p.tier() == tier)
                out.add(p);
        }

        return out;
    }

    /** Every language key these pools need, in pool order; the gate and the probe read this list. */
    public static List<String> allKeys()
    {
        List<String> out = new ArrayList<>();

        for (Pool p : POOLS)
        {
            for (int i = 0; i < p.count(); i++)
                out.add(p.key(i));
        }

        return out;
    }

    /** Total number of built-in type lines across all pools. */
    public static int totalLineCount()
    {
        int n = 0;

        for (Pool p : POOLS)
            n += p.count();

        return n;
    }

    /**
     * Fills the player-name placeholder of a language value.
     *
     * <p>Both spellings resolve: {@code {player}} (used by the extra voice, the title screen and the
 * the text for the endurance pool) and the legacy {@code %s} of the three madness tiers. Delegated
     * to {@code HiddenVoiceText} so there is exactly one implementation of the rule.
     */
    public static String fill(String raw, String playerName)
    {
        return piloser.sanitypd.client.HiddenVoiceText.fill(raw, playerName);
    }

    /** A language value with the obfuscation markers taken out, i.e. the text a player would type. */
    public static String plain(String raw)
    {
        if (raw == null)
            return "";

        return raw.replace('\u00A7' + "k", "").replace('\u00A7' + "K", "")
                .replace("\u00A7r", "").replace("\u00A7R", "");
    }

    /**
     * Whether a submitted text repeats one of the pool lines.
     *
     * <p>Forgiving on purpose (whitespace runs and capitalisation are ignored), because these lines are
     * typed by hand: a difference in spacing is still the same sentence to the player. The rule is the one
     * {@code HiddenVoiceText} already applies to the extra inner-voice pool, so the two cannot drift apart.
     *
     * @param typed      text the player submitted, may be {@code null}
     * @param rawValues  raw language values of the type pools, {@code null} entries allowed
     * @param playerName name substituted for the placeholder, may be {@code null}
     */
    public static boolean matchesForbiddenText(String typed, Collection<String> rawValues, String playerName)
    {
        return piloser.sanitypd.client.HiddenVoiceText.matchesAny(typed, rawValues, playerName);
    }

    /**
     * Builds a drawable component from a resolved line, turning {@code §k} / {@code §r} into real styles.
     *
 * <p>Why this exists: the chaos-restraint line 4 names the hidden thing with three garbled
     * glyphs. The obfuscation must be a <b>style</b> - a bare {@code §k} inside an already-built literal is
     * not guaranteed to be interpreted by the font path (the title-screen entries rely on it, but that
     * claim was never verified on a live screen, so nothing here depends on it). The drawn component is
     * therefore assembled with {@code Style#withObfuscated(true)} spans, which vanilla's font renderer
     * scrambles per frame by design.
     *
     * <p>Only {@code §k} and {@code §r} are interpreted; any other {@code §} sequence is left in the text
     * unchanged, so an unexpected code is visible as a defect instead of silently recolouring a line.
     */
    public static MutableComponent styled(String text)
    {
        MutableComponent out = Component.empty();

        if (text == null || text.isEmpty())
            return out;

        StringBuilder pending = new StringBuilder();
        boolean obfuscated = false;
        boolean red = false;

        for (int i = 0; i < text.length(); i++)
        {
            // Inline marker pair, for the one case a whole line is too much: a script line that names the
            // hidden thing in red while the rest of the sentence stays white (the tape's last fragment).
            if (text.startsWith(RED_ON, i))
            {
                out.append(styledRun(pending.toString(), obfuscated, red));
                pending.setLength(0);
                red = true;
                i += RED_ON.length() - 1;
                continue;
            }

            if (text.startsWith(RED_OFF, i))
            {
                out.append(styledRun(pending.toString(), obfuscated, red));
                pending.setLength(0);
                red = false;
                i += RED_OFF.length() - 1;
                continue;
            }

            char c = text.charAt(i);

            if (c == '\u00A7' && i + 1 < text.length())
            {
                char code = Character.toLowerCase(text.charAt(i + 1));

                if (code == 'k' || code == 'r')
                {
                    out.append(styledRun(pending.toString(), obfuscated, red));
                    pending.setLength(0);
                    obfuscated = code == 'k';
                    i++;
                    continue;
                }
            }

            pending.append(c);
        }

        out.append(styledRun(pending.toString(), obfuscated, red));
        return out;
    }

    private static MutableComponent styledRun(String text, boolean obfuscated, boolean red)
    {
        MutableComponent c = Component.literal(text == null ? "" : text);

        if (obfuscated)
            c = c.withStyle(Style.EMPTY.withObfuscated(true));

        return red ? c.withStyle(Style.EMPTY.withColor(COLOR_DARK_RED)) : c;
    }

    /** Marker pair for a dark red run inside a line; see {@link #styled(String)}. */
    public static final String RED_ON = "[red]";

    /** Closes the run opened by {@link #RED_ON}. */
    public static final String RED_OFF = "[/red]";

    /**
     * The third voice's colour, {@code 0x8B0000}, mirrored here instead of referenced: that pool is
     * client-only and this helper runs on both sides. The value is the single red used everywhere else.
     */
    public static final int COLOR_DARK_RED = 0x8B0000;

    /** Type id -> pool, or {@code null} when the type has no pool. */
    public static Pool poolOf(ThoughtType type)
    {
        for (Pool p : POOLS)
        {
            if (p.type() == type)
                return p;
        }

        return null;
    }

    /** Lower-case id list, for the probe and for log lines. */
    public static String idList()
    {
        StringBuilder sb = new StringBuilder();

        for (Pool p : POOLS)
        {
            if (sb.length() > 0)
                sb.append('+');

            sb.append(p.type().id());
        }

        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
