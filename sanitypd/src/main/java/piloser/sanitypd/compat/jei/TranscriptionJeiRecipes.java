package piloser.sanitypd.compat.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import piloser.sanitypd.block.TranscriptionRules;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.thought.ThoughtItem;
import piloser.sanitypd.thought.ThoughtType;

/**
 * The rows the transcription table's JEI page shows, built from the machine's own rule object.
 *
 * <p>Every row here is produced by asking {@link TranscriptionRules} what it answers - {@code typesOf} for a
 * thought's axes and {@code variantFor} for a type's container - rather than by repeating the rule a
 * second time in this package. That is deliberate: a page that re-states a rule is a page that will one day
 * state it differently, and the requirement is that the page and the machine look and behave the
 * same.
 *
 * <p>The rows are exhaustive in the same sense the machine is: one transcribe row per transcribable type
 * (with a thought that really carries that axis), and one upgrade row per transcribable type that has a
 * tiered thought. A thought with no transcribable axis (a hallucination) contributes nothing, exactly as it
 * matches nothing at the table.
 */
public final class TranscriptionJeiRecipes
{
    /** The one page this mod adds; the id is also the plugin's uid. */
    public static final ResourceLocation UID = new ResourceLocation("sanitypd", "thought_transcription");

    /**
     * The page's recipe type, declared once: the category, the registered rows and the catalyst all have to
     * name the same object, and creating it in three places is how three pages that never meet happen.
     */
    public static final mezz.jei.api.recipe.RecipeType<TranscriptionJeiRecipe> TYPE =
            mezz.jei.api.recipe.RecipeType.create(UID.getNamespace(), UID.getPath(), TranscriptionJeiRecipe.class);

    private TranscriptionJeiRecipes() {}

    /** The page's recipe type; see {@link #TYPE}. */
    public static mezz.jei.api.recipe.RecipeType<TranscriptionJeiRecipe> type()
    {
        return TYPE;
    }

    /** Every row, in the order the page lists them: the four transcriptions, then the four upgrades. */
    public static List<TranscriptionJeiRecipe> all()
    {
        List<TranscriptionJeiRecipe> rows = new ArrayList<>();

        for (ThoughtType type : ThoughtType.values())
        {
            // variantFor answers null - not an empty stack - for an axis this rule never produces, so the
            // contract has to be read off the method rather than assumed. (Assuming it cost a crashed JEI
            // plugin once: the page registered nothing and JEI showed no page at all, with only one line in
            // the log to say why.)
            ItemStack variant = TranscriptionRules.variantFor(type);

            if (variant == null)
                continue;

            // Every thought this axis accepts, so JEI cycles the slot and the player sees the whole set.
            List<ItemStack> examples = thoughtsOf(type);

            if (examples.isEmpty())
                continue;

            rows.add(TranscriptionJeiRecipe.transcribe(examples,
                    one(new ItemStack(ItemRegistry.THOUGHT_CONTAINER.get())),
                    one(new ItemStack(ItemRegistry.INNER_CORE.get())),
                    variant.copy(), TranscriptionRules.XP_COST));

            List<ItemStack> tiered = tieredThoughtsOf(type);

            if (tiered.isEmpty())
                continue;

            ItemStack upgraded = tiered.get(0).copy();
            upgraded.getOrCreateTag().putBoolean(piloser.sanitypd.thought.EmpoweredThought.TAG, true);

            rows.add(TranscriptionJeiRecipe.upgrade(tiered, one(variant.copy()),
                    one(new ItemStack(ItemRegistry.INNER_CORE.get())), upgraded,
                    TranscriptionRules.UPGRADE_XP_COST));
        }

        return rows;
    }

    /** One stack as a one-element list; the record's own helper, re-exported for readability here. */
    private static List<ItemStack> one(ItemStack stack)
    {
        return TranscriptionJeiRecipe.one(stack);
    }

    /** Every registered thought carrying this axis, or an empty list when the mod has none. */
    private static List<ItemStack> thoughtsOf(ThoughtType type)
    {
        List<ItemStack> out = new ArrayList<>();

        for (Item item : thoughtItems())
            if (item instanceof ThoughtItem thought && TranscriptionRules.typesOf(new ItemStack(item)).contains(type))
                out.add(new ItemStack(item));

        return out;
    }

    /** Every registered thought this axis can deepen, or an empty list when it has no tiered one. */
    private static List<ItemStack> tieredThoughtsOf(ThoughtType type)
    {
        List<ItemStack> out = new ArrayList<>();

        for (Item item : thoughtItems())
            if (item instanceof ThoughtItem thought && thought.tiered()
                    && TranscriptionRules.typesOf(new ItemStack(item)).contains(type))
                out.add(new ItemStack(item));

        return out;
    }

    /**
     * Every thought this mod registered.
     *
     * <p>Read from the registry rather than from a list written here, so a thought added later shows up on the
     * page without anyone remembering to add it - the same reason the coverage gate reads the registry.
     */
    private static List<Item> thoughtItems()
    {
        List<Item> out = new ArrayList<>();

        for (Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS)
            if (item instanceof ThoughtItem model && model.types().size() > 0)
                out.add(item);

        return out;
    }
}
