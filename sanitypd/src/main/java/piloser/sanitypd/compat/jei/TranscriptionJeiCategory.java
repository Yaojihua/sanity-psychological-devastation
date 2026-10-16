package piloser.sanitypd.compat.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import piloser.sanitypd.block.TranscriptionLayout;
import piloser.sanitypd.item.ItemRegistry;

/**
 * The transcription table's JEI page.
 *
 * <p>It is drawn from the <b>same</b> numbers and the <b>same</b> vanilla artwork as the machine itself:
 * every coordinate comes from {@link TranscriptionLayout}, which the menu and the screen also read, and the
 * frame is stitched out of vanilla's own container sheet exactly the way the screen stitches it. The
 * requirement - "the JEI page and the transcription table's crafting area must look exactly the same" - is
 * therefore structural: there is one set of numbers, and three consumers.
 *
 * <p>So is the eye band. The requirement is that this page and the machine's crafting area look
 * <b>exactly</b> the same, and that includes the scrollwork-and-eye strip above the thought slot - without it
 * the page is a different picture. The band is the machine's own {@code TranscriptionLayout.EYE_TEXTURE},
 * blitted here at the same box, so there is still only one picture and only one set of numbers.
 */
public final class TranscriptionJeiCategory implements IRecipeCategory<TranscriptionJeiRecipe>
{
    /** Vanilla sheet the frame is cut from - read at runtime, never shipped. */
    private static final ResourceLocation SHEET =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    /** Vanilla sheet the arrow head comes from, the same one the screen uses. */
    private static final ResourceLocation CRAFTING_TABLE =
            new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");

    /** One 18x18 slot recess inside {@link #SHEET}, and the bar colour that sheet's own arrow uses. */
    private static final int HOLE_U = 7;
    private static final int HOLE_V = 17;
    private static final int ARROW_BAR_COLOUR = 0xFF8B8B8B;

    /** The work area's size on the page: the window's own work band, without the player inventory. */
    private static final int PAGE_WIDTH = TranscriptionLayout.WIDTH;
    private static final int PAGE_HEIGHT = TranscriptionLayout.INVENTORY_TOP;

    private final RecipeType<TranscriptionJeiRecipe> m_type;
    private final Component m_title;
    private final IDrawable m_background;
    private final IDrawable m_icon;
    private final IDrawableStatic m_hole;

    public TranscriptionJeiCategory(IGuiHelper guiHelper)
    {
        m_type = TranscriptionJeiRecipes.type();
        m_title = Component.translatable("container.sanitypd.thought_transcription_table");
        m_icon = guiHelper.createDrawableItemStack(new ItemStack(ItemRegistry.THOUGHT_TRANSCRIPTION_TABLE.get()));
        m_hole = guiHelper.createDrawable(SHEET, HOLE_U, HOLE_V, 18, 18);
        m_background = new Frame(guiHelper);
    }

    @Override
    public RecipeType<TranscriptionJeiRecipe> getRecipeType()
    {
        return m_type;
    }

    @Override
    public Component getTitle()
    {
        return m_title;
    }

    @Override
    public IDrawable getBackground()
    {
        return m_background;
    }

    @Override
    public IDrawable getIcon()
    {
        return m_icon;
    }

    /**
     * Places the six slots at the machine's own coordinates.
     *
     * <p>An empty stack in the recipe means "this slot has to stay empty" - that is how the upgrade rule says
     * itself - so those slots are simply not added. What is left is the shape the player has to build.
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TranscriptionJeiRecipe recipe, IFocusGroup focuses)
    {
        addIngredients(builder, RecipeIngredientRole.INPUT, TranscriptionLayout.MATERIAL_X,
                TranscriptionLayout.MATERIAL_Y, recipe.materialFirst());
        addIngredients(builder, RecipeIngredientRole.INPUT,
                TranscriptionLayout.MATERIAL_X + TranscriptionLayout.PITCH, TranscriptionLayout.MATERIAL_Y,
                recipe.materialSecond());
        addIngredients(builder, RecipeIngredientRole.INPUT,
                TranscriptionLayout.MATERIAL_X + 2 * TranscriptionLayout.PITCH, TranscriptionLayout.MATERIAL_Y,
                recipe.materialThird());
        addIngredients(builder, RecipeIngredientRole.INPUT, TranscriptionLayout.CORE_X, TranscriptionLayout.CORE_Y,
                recipe.core());
        addIngredients(builder, RecipeIngredientRole.INPUT, TranscriptionLayout.THOUGHT_X,
                TranscriptionLayout.LOWER_Y, recipe.thought());
        addIngredients(builder, RecipeIngredientRole.OUTPUT, TranscriptionLayout.OUTPUT_X,
                TranscriptionLayout.LOWER_Y, one(recipe.output()));
    }

    /**
     * A slot's contents, all of them.
     *
     * <p>The whole list goes in rather than its first element: the rule matches on axes, so a dozen different
     * thoughts are equally legal in the same slot, and JEI cycles a slot that holds several ingredients. A
     * page that showed one of them would be narrower than the rule.
     */
    private static void addIngredients(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y,
            List<ItemStack> stacks)
    {
        if (stacks.isEmpty())
            return;

        IRecipeSlotBuilder slot = builder.addSlot(role, x, y);
        slot.addIngredients(VanillaTypes.ITEM_STACK, stacks);
    }

    /** One stack as a one-element list; the recipe record owns the helper. */
    private static List<ItemStack> one(ItemStack stack)
    {
        return TranscriptionJeiRecipe.one(stack);
    }

    /** The cost line, in the same place and the colour the machine uses. */
    @Override
    public void draw(TranscriptionJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics guiGraphics, double mouseX,
            double mouseY)
    {
        Component cost = Component.translatable("container.sanitypd.thought_transcription_table.cost", recipe.cost());
        int width = net.minecraft.client.Minecraft.getInstance().font.width(cost);

        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, cost,
                TranscriptionLayout.COST_X - width / 2, TranscriptionLayout.COST_Y, 0xFF55FF55, false);
    }

    /**
     * The frame, stitched at runtime - the same rectangles the screen stitches, in the same order.
     *
     * <p>Kept as a drawable rather than a single image so the two really do share their source: if the screen's
     * stitching changes, this file is the only other place to change, and both read the same layout constants.
     */
    private static final class Frame implements IDrawable
    {
        private final List<IDrawableStatic> m_pieces = new ArrayList<>();
        private final List<int[]> m_at = new ArrayList<>();

        Frame(IGuiHelper guiHelper)
        {
            // title band, then the two side columns of the work area
            add(guiHelper.createDrawable(SHEET, 0, 0, PAGE_WIDTH, TranscriptionLayout.WORK_TOP), 0, 0);
            add(guiHelper.createDrawable(SHEET, 0, TranscriptionLayout.WORK_TOP, 3,
                    TranscriptionLayout.WORK_BORDER_HEIGHT), 0, TranscriptionLayout.WORK_TOP);
            add(guiHelper.createDrawable(SHEET, 173, TranscriptionLayout.WORK_TOP, 3,
                    TranscriptionLayout.WORK_BORDER_HEIGHT), 173, TranscriptionLayout.WORK_TOP);

            // one recess per slot the machine has, at the machine's own slot positions
            for (int[] slot : slotOrigins())
                add(guiHelper.createDrawable(SHEET, HOLE_U, HOLE_V, 18, 18), slot[0] - 1, slot[1] - 1);
        }

        private void add(IDrawableStatic piece, int x, int y)
        {
            m_pieces.add(piece);
            m_at.add(new int[] { x, y });
        }

        /** The six slot origins, read from the one place that defines them. */
        private static List<int[]> slotOrigins()
        {
            List<int[]> out = new ArrayList<>();

            for (int i = 0; i < 3; i++)
                out.add(new int[] { TranscriptionLayout.MATERIAL_X + i * TranscriptionLayout.PITCH,
                        TranscriptionLayout.MATERIAL_Y });

            out.add(new int[] { TranscriptionLayout.CORE_X, TranscriptionLayout.CORE_Y });
            out.add(new int[] { TranscriptionLayout.THOUGHT_X, TranscriptionLayout.LOWER_Y });
            out.add(new int[] { TranscriptionLayout.OUTPUT_X, TranscriptionLayout.LOWER_Y });

            return out;
        }

        @Override
        public int getWidth()
        {
            return PAGE_WIDTH;
        }

        @Override
        public int getHeight()
        {
            return PAGE_HEIGHT;
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int recipeX, int recipeY)
        {
            // the work area's own fill, then the frame pieces and the recesses
            guiGraphics.fill(recipeX + 3, recipeY + TranscriptionLayout.WORK_TOP, recipeX + 173,
                    recipeY + TranscriptionLayout.INVENTORY_TOP, TranscriptionLayout.CONTENT_COLOUR);

            for (int i = 0; i < m_pieces.size(); i++)
                m_pieces.get(i).draw(guiGraphics, recipeX + m_at.get(i)[0], recipeY + m_at.get(i)[1]);

            // the arrow: a plain bar, then vanilla's own arrow head - both exactly where the screen puts them
            guiGraphics.fill(recipeX + TranscriptionLayout.ARROW_BAR_X, recipeY + TranscriptionLayout.ARROW_BAR_Y,
                    recipeX + TranscriptionLayout.ARROW_BAR_X + TranscriptionLayout.ARROW_BAR_W,
                    recipeY + TranscriptionLayout.ARROW_BAR_Y + TranscriptionLayout.ARROW_BAR_H, ARROW_BAR_COLOUR);
            guiGraphics.blit(CRAFTING_TABLE, recipeX + TranscriptionLayout.ARROW_HEAD_X,
                    recipeY + TranscriptionLayout.ARROW_HEAD_Y, TranscriptionLayout.ARROW_HEAD_U,
                    TranscriptionLayout.ARROW_HEAD_V, TranscriptionLayout.ARROW_HEAD_W,
                    TranscriptionLayout.ARROW_HEAD_H);

            // the scrollwork-and-eye band, from the machine's own texture at the machine's own box.
            //
            // Blitted straight rather than wrapped in an IDrawable on purpose: JEI's drawable helper resolves
            // sprites through the block atlas, and a plain gui texture is not in it, so the band would come out
            // blank. The screen calls the same nine-argument blit, for the same reason it does: the
            // seven-argument overload assumes a 256x256 sheet and reads a single texel out of this 54x12 one.
            guiGraphics.blit(TranscriptionLayout.EYE_TEXTURE, recipeX + TranscriptionLayout.EYE_X,
                    recipeY + TranscriptionLayout.EYE_Y, 0, 0, TranscriptionLayout.EYE_W,
                    TranscriptionLayout.EYE_H, TranscriptionLayout.EYE_W, TranscriptionLayout.EYE_H);
        }
    }
}
