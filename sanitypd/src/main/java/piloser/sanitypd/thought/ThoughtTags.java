package piloser.sanitypd.thought;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

    /**
     * {@code sanitypd:thought/hallucination}: the row-less category.
     *
     * <p>No row is laid out from this tag - {@link #fitsRow} adds it to <b>both</b> rows instead, which is
 * what it was required of hallucinations ("可以放在这两行槽位里").
     */
    public static final TagKey<Item> HALLUCINATION = ItemTags.create(ThoughtCategory.HALLUCINATION.tag());

    /** The tag belonging to a category. */
    public static TagKey<Item> of(ThoughtCategory category)
    {
        if (category == ThoughtCategory.WORLDVIEW)
            return WORLDVIEW;

        if (category == ThoughtCategory.METHODOLOGY)
            return METHODOLOGY;

        return HALLUCINATION;
    }

    /**
     * Whether a stack may sit in a row of this category.
     *
     * <p>The row's own tag plus the row-less hallucination tag: a hallucination fits either row, every other
     * thought fits exactly one. The rule lives here alone, so the slot and every shift-click path cannot
     * disagree about it.
     */
    public static boolean fitsRow(ThoughtCategory row, ItemStack stack)
    {
        return stack.is(of(row)) || stack.is(HALLUCINATION);
    }
}
