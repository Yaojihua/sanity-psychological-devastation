package piloser.sanitypd.thought;

/**
 * The six counting types a thought can carry.
 *
 * <p>A type is a <b>counting axis</b>, deliberately orthogonal to {@link ThoughtCategory}: the
 * category decides which row of the thought chain a thought may sit in, while the type decides which
 * counter it feeds and therefore which mindset it can help unlock.
 *
 * <h2>Why the colours are what they are</h2>
 * The owner fixed these colours by name (light blue / red / purple / dark red / orange / light grey)
 * and they are shown in bold in the tooltip's tag line. They also have to stay readable next to the
 * two <b>numeric</b> colours used inside the tooltip detail page - buff green {@code 0x55FF55} and
 * debuff crimson {@code 0xFF2E63}. That is why "Composure" is a light blue rather than a cyan (cyan
 * sat too close to the green), why "Chaos Restraint" is a very dark red, and why the debuff colour is
 * a cold crimson rather than a plain red: three red-family colours now coexist and are separated by
 * hue and lightness instead of by name alone.
 *
 * <h2>Thresholds do not live here</h2>
 * How many thoughts unlock a mindset is a property of the mindset, not of the type: most types unlock
 * one at 5, "Chaos Restraint" needs 6, and "Hallucination" has no mindset at all. See {@link Mindsets}.
 */
public enum ThoughtType
{
    /** Composure: the calm axis. Unlocks its mindset at 5 thoughts. */
    COMPOSURE("composure", 0x7FC8FF),

    /** Madness. Unlocks its mindset at 5 thoughts. */
    MADNESS("madness", 0xFF5555),

    /** Servitude. Unlocks its mindset at 5 thoughts. */
    SERVITUDE("servitude", 0xB36BFF),

    /** Chaos Restraint: the one type with a higher bar, 6 thoughts. */
    CHAOS_RESTRAINT("chaos_restraint", 0x8B2E2E),

    /** Endurance. Unlocks its mindset at 5 thoughts. */
    ENDURANCE("endurance", 0xFFA03C),

    /** Hallucination: counts like the others, but unlocks no mindset. */
    HALLUCINATION("hallucination", 0xBFBFBF);

    private final String m_id;
    private final int m_color;

    ThoughtType(String id, int color)
    {
        m_id = id;
        m_color = color;
    }

    /** Id stem: also the language key suffix, e.g. {@code composure}. */
    public String id()
    {
        return m_id;
    }

    /** The {@code 0xRRGGBB} colour this type is written in, as an opaque RGB value. */
    public int color()
    {
        return m_color;
    }

    /** Language key of the type's display name, e.g. {@code thought.sanitypd.type.composure}. */
    public String translationKey()
    {
        return "thought.sanitypd.type." + m_id;
    }
}
