package piloser.sanitypd.compat.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * One row of the transcription table's own JEI page.
 *
 * <p>The page shows two kinds of rule, and both are the <b>same</b> rules the machine executes: the class
 * {@code piloser.sanitypd.block.TranscriptionRules} decides them, and this record is only the shape JEI needs
 * in order to draw them. Nothing here re-decides anything - if a rule changes, the page changes with it,
 * because there is nowhere else for the answer to live.
 *
 * <p>Each material field holds <b>every</b> thought that slot accepts, not an example of one. That is what
 * makes JEI cycle the slot's contents and show the whole set: the rule matches on a thought's <i>axes</i>, so
 * a dozen different thoughts are all legal in the same place, and a page that showed one of them would be
 * telling the player something narrower than the truth.
 *
 * <p>What the five stacks mean is fixed by the machine's own slot map:
 *
 * <pre>
 *   materialFirst/Second/Third   slots 0 1 2, in that order
 *   thought                      slot 4
 *   core                         slot 3
 *   output                       slot 5
 * </pre>
 *
 * <p>An empty list is drawn as "this slot must be empty", which is how the upgrade rule states itself: one
 * tiered thought in the material row, nothing beside it.
 */
public record TranscriptionJeiRecipe(List<ItemStack> materialFirst, List<ItemStack> materialSecond,
        List<ItemStack> materialThird, List<ItemStack> thought, List<ItemStack> core, ItemStack output, int cost)
{
    /** The transcribe rule: three thoughts of one type, the plain container, a core. */
    public static TranscriptionJeiRecipe transcribe(List<ItemStack> thoughts, List<ItemStack> plainContainer,
            List<ItemStack> core, ItemStack output, int cost)
    {
        return new TranscriptionJeiRecipe(thoughts, thoughts, thoughts, plainContainer, core, output, cost);
    }

    /** The upgrade rule: a single tiered thought, its own type's container variant, a core. */
    public static TranscriptionJeiRecipe upgrade(List<ItemStack> tiered, List<ItemStack> variantContainer,
            List<ItemStack> core, ItemStack output, int cost)
    {
        return new TranscriptionJeiRecipe(tiered, List.of(), List.of(), variantContainer, core, output, cost);
    }

    /** One stack as a one-element list, for the fields that really are a single item. */
    public static List<ItemStack> one(ItemStack stack)
    {
        return stack == null || stack.isEmpty() ? List.of() : List.of(stack);
    }
}
