package piloser.sanitypd.thought;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One slot of a thought chain row.
 *
 * <p>Two rules live here, and only here, so nothing else has to know them:
 *
 * <ol>
 *   <li><b>Category.</b> A slot only takes items of its own row, decided by
 *       {@link ThoughtTags#of(ThoughtCategory)}. A datapack that widens the tag therefore widens the row,
 *       with no code change.</li>
 *   <li><b>One of each thought.</b> The same thought may not sit in the chain twice - not in the same row
 *       and not across rows - which is what the owner asked for ("同类唯一"). The check walks the whole
 *       container rather than the row, so a thought that somehow declares the other category still cannot
 *       be duplicated.</li>
 * </ol>
 *
 * <p>Note the deliberate asymmetry with the counters: the <b>rule</b> reads the tag, while
 * {@link IThoughtChain#countOf} reads {@link ThoughtItem#types()}. An item added to the tag by a datapack
 * therefore occupies a slot without feeding a counter. That is a known limit, not an oversight: a counting
 * type also carries a colour, a language key and a mindset threshold, none of which a tag can express.
 */
public class ThoughtSlot extends Slot
{
    private final ThoughtCategory m_category;

    public ThoughtSlot(Container container, int index, int x, int y, ThoughtCategory category)
    {
        super(container, index, x, y);
        m_category = category;
    }

    @Override
    public boolean mayPlace(ItemStack stack)
    {
        if (stack.isEmpty() || !stack.is(ThoughtTags.of(m_category)))
            return false;

        for (int i = 0; i < container.getContainerSize(); i++)
        {
            if (i == getContainerSlot())
                continue;

            if (ItemStack.isSameItem(container.getItem(i), stack))
                return false;
        }

        return true;
    }
}
