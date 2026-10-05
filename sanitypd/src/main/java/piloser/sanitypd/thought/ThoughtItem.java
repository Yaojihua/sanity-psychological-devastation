package piloser.sanitypd.thought;

import java.util.List;

import net.minecraft.world.item.Item;

/**
 * A thought: the item kind that lives in the first two rows of the thought chain.
 *
 * <p>A thought carries three pieces of data and no behaviour of its own yet:
 *
 * <ul>
 *   <li>{@link #category()} - which row may hold it (worldview / methodology);</li>
 *   <li>{@link #types()} - one or more {@link ThoughtType} counters it feeds. A single thought can feed
 *       several at once, which is why this is a list and not a single value;</li>
 *   <li>{@link #tiered()} - whether it has the 1 / 3 / 5 tier ladder. An untiered thought shows no
 *       "hold shift" hint and no extra detail text at all, and its current effect never changes.</li>
 * </ul>
 *
 * <p><b>Effects are mechanism-level, never {@code MobEffect}.</b> This mod does not register status
 * effects for thoughts: nothing may appear in the HUD's effect row, nothing may be cleared by milk, and
 * nothing may be broadcast to clients that way. Whatever a thought does is done in the code that reads
 * the thought chain.
 *
 * <p>The item name is {@code category:name} - for example "世界观:弱肉强食" / "Worldview: Law of the
 * Jungle" - so the language entry carries the category prefix and the tooltip must not repeat the name.
 */
public class ThoughtItem extends Item
{
    private final ThoughtCategory m_category;
    private final List<ThoughtType> m_types;
    private final boolean m_tiered;

    /**
     * @param properties item properties; thoughts are {@code stacksTo(1)} because a thought is
     *                   equipment rather than a resource
     * @param category   the row this thought may sit in
     * @param types      one or more counters this thought feeds; must not be empty
     * @param tiered     whether the 1 / 3 / 5 tier ladder applies
     */
    public ThoughtItem(Properties properties, ThoughtCategory category, List<ThoughtType> types, boolean tiered)
    {
        super(properties);
        m_category = category;
        m_types = List.copyOf(types);
        m_tiered = tiered;
    }

    /** The row this thought may sit in. */
    public ThoughtCategory category()
    {
        return m_category;
    }

    /** The counters this thought feeds, in the order they are written. */
    public List<ThoughtType> types()
    {
        return m_types;
    }

    /** Whether this thought has the 1 / 3 / 5 tier ladder (and therefore the shift-gated detail page). */
    public boolean tiered()
    {
        return m_tiered;
    }
}
