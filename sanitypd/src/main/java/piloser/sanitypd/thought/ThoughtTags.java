package piloser.sanitypd.thought;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tags the thought chain reads.
 *
 * <p>Category membership is a tag rather than a list in code, so a modpack or a
 * datapack can widen a row without touching this mod: the slot rule and the tooltip's category label both
 * read these keys, which means they cannot disagree.
 *
 * <p>The keys are created once here rather than per call: {@code ItemTags.create} allocates a new key each
 * time, and tags are compared by identity, so building one twice would silently produce two different
 * "same" tags.
 */
public final class ThoughtTags
{
    private ThoughtTags() {}

    /** {@code sanitypd:thought/worldview}: every item allowed in row one. */
    public static final TagKey<Item> WORLDVIEW = ItemTags.create(ThoughtCategory.WORLDVIEW.tag());

    /** {@code sanitypd:thought/methodology}: every item allowed in row two. */
    public static final TagKey<Item> METHODOLOGY = ItemTags.create(ThoughtCategory.METHODOLOGY.tag());

    /** The tag belonging to a category. */
    public static TagKey<Item> of(ThoughtCategory category)
    {
        return category == ThoughtCategory.WORLDVIEW ? WORLDVIEW : METHODOLOGY;
    }
}
