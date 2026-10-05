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
    METHODOLOGY("methodology", "thought/methodology");

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
