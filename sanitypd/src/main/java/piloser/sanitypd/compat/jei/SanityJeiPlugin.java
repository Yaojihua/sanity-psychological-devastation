package piloser.sanitypd.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import piloser.sanitypd.item.ItemRegistry;

/**
 * This mod's JEI plugin: the transcription table's own recipe page.
 *
 * <p><b>Why a plugin at all.</b> The table's two rules are Java, not {@code data/<modid>/recipes/*.json} -
 * they have to be, because both match on a thought's <i>type</i> rather than on an item, and a data recipe
 * cannot express "three thoughts of one and the same axis". JEI reads recipes from that data folder, so
 * without this class it cannot see the table at all: the interface is there, the machine works, and JEI shows
 * nothing. That was the whole of the report.
 *
 * <p><b>Why nothing else here needs to change when a rule changes.</b> The rows come from
 * {@link TranscriptionJeiRecipes}, which asks {@code TranscriptionRules} for its own answers, and the page is
 * laid out from {@code TranscriptionLayout}, which is also where the menu and the screen read their numbers.
 * So the page cannot drift away from the machine in either content or geometry.
 *
 * <p><b>Optional by construction.</b> JEI scans for this annotation itself; nothing in the mod's own code
 * refers to this package, so a player without JEI never loads these classes at all (the same arrangement the
 * Jade integration uses).
 */
@JeiPlugin
public final class SanityJeiPlugin implements IModPlugin
{
    @Override
    public ResourceLocation getPluginUid()
    {
        return TranscriptionJeiRecipes.UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration)
    {
        registration.addRecipeCategories(new TranscriptionJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration)
    {
        registration.addRecipes(TranscriptionJeiRecipes.type(), TranscriptionJeiRecipes.all());
    }

    /**
     * The table is what the rows belong to: right-clicking it in JEI opens this page.
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration)
    {
        registration.addRecipeCatalyst(new ItemStack(ItemRegistry.THOUGHT_TRANSCRIPTION_TABLE.get()),
                TranscriptionJeiRecipes.type());
    }
}
