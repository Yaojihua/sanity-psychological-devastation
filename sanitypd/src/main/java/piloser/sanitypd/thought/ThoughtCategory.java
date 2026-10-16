package piloser.sanitypd.thought;

import net.minecraft.resources.ResourceLocation;
import piloser.sanitypd.SanityMod;

/**
 * Which row of the thought chain a thought may sit in.
 *
 * <p>Rows 1 and 2 of the screen take exactly one category each and reject everything else, so the
 * category is both a display concept ("世界观" / "方法论" as a row label) and a slot rule. The third
 * row is not a category: it holds mindsets, which are not items at all.
 *
 * <h2>A category that owns no row</h2>
 * Hallucinations are the exception: they are neither worldviews nor methodologies, and
 * they may be equipped in <b>either</b> row. So {@link #HALLUCINATION} is a category without a row of its
 * own - {@link #ownsRow()} is false, the screen keeps its two rows, and the slot rule accepts it in both.
 * Its label is still shown on the item, so a hallucination says what it is.
 *
 * <h2>The tag, not a hardcoded list</h2>
 * "Which items belong to this category" is expressed as an {@code ItemTag} rather than a list in code,
 * so a modpack or a datapack can add thoughts without touching this mod. The slot rule and the
 * tooltip's category label then read the same source of truth. Adding a category or an item to a
 * category never requires touching the screen code.
 */
public enum ThoughtCategory
{
    /** Row 1: worldviews. */
    WORLDVIEW("worldview", "thought/worldview"),

    /** Row 2: methodologies. */
    METHODOLOGY("methodology", "thought/methodology"),

 /** No row of its own: both rows accept it, which is what it was required of hallucinations. */
    HALLUCINATION("hallucination", "thought/hallucination");

    /** The categories that own a row, in row order. The menu lays out exactly these. */
    private static final java.util.List<ThoughtCategory> ROW_CATEGORIES = java.util.List.of(WORLDVIEW, METHODOLOGY);

    private final String m_id;
    private final ResourceLocation m_tag;

    ThoughtCategory(String id, String tagPath)
    {
        m_id = id;
        m_tag = new ResourceLocation(SanityMod.MODID, tagPath);
    }

    /** Id stem: also the language key suffix, e.g. {@code worldview}. */
    public String id()
    {
        return m_id;
    }

    /**
     * Whether this category has a row of its own.
     *
     * <p>False only for {@link #HALLUCINATION}. A row-less category is accepted by every row instead, so the
     * two facts - "it has no row" and "both rows take it" - stay in one place (here) rather than being
     * re-derived by the menu, the slot rule and the tooltip separately.
     */
    public boolean ownsRow()
    {
        return this != HALLUCINATION;
    }

    /** The categories that own a row, in row order. */
    public static java.util.List<ThoughtCategory> rowCategories()
    {
        return ROW_CATEGORIES;
    }

    /**
     * The item tag that lists every thought of this category, e.g.
     * {@code sanitypd:thought/worldview}.
     *
     * <p>Callers turn this into an {@code ItemTagKey} with {@code ItemTags.create(...)}; keeping the
     * enum free of that dependency leaves it usable from code that never touches item tags.
     */
    public ResourceLocation tag()
    {
        return m_tag;
    }

    /** Language key of the category's display name, e.g. {@code thought.sanitypd.category.worldview}. */
    public String translationKey()
    {
        return "thought.sanitypd.category." + m_id;
    }
}
