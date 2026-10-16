package piloser.sanitypd.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.block.ThoughtTranscriptionTableBlockEntity;
import piloser.sanitypd.block.ThoughtTranscriptionTableMenu;
import piloser.sanitypd.block.TranscriptionLayout;

/**
 * The thought transcription table's screen.
 *
 * <p><b>Where every pixel comes from.</b> The frame is stitched at runtime out of vanilla sheets - the same
 * rule the thought chain's screen follows (plan B): no vanilla art is copied into this mod's assets. The one
 * picture file this screen owns is our own scrollwork-and-eye band; everything else is stitched from vanilla
 * sheets or drawn with {@code fill}.
 *
 * <ul>
 *   <li>`generic_54.png` supplies the title band (0,0,176,17), the two side borders (0,17,3,66) and
 *       (173,17,3,66), the player inventory block (0,125,176,97) pasted at y=83, and one slot recess
 *       (7,17,18,18) stamped under each table slot.</li>
 *   <li>`crafting_table.png` supplies the arrow head (104,35,8,15); the bar before it is a plain
 *       {@code fill} in the colour that sheet's own bar uses (#8B8B8B, sampled at (100,42)).</li>
 *   <li>The scrollwork-and-eye band is blitted from `thought_transcription_table_eye.png`, one texture pixel to
 *       one screen pixel, at the box {@link TranscriptionLayout} gives it. It is ours, so there is nothing to
 *       transcribe and nothing to keep out of the assets.</li>
 *   <li>The no-result cross is the anvil's {@code (176,0,28,21)} sprite <b>transcribed</b> as a pixel pattern
 *       below and drawn with {@code fill}. Vanilla blits that sprite whole, and it is fully opaque: its grey
 *       background paints over the arrow and chops it into pieces (verified by compositing the vanilla anvil
 * screen - the author rejected exactly that look). Keeping only its strokes gives the cross he asked for,
 *       and keeps every vanilla pixel out of this mod's assets.</li>
 * </ul>
 *
 * <p>The layout is the one the author approved from the mockup: materials over the
 * eye band over the thought slot on the left, the inner core above a long arrow in the middle, the output on
 * the right. The screen reads slot positions from the menu instead of re-typing them, so the recesses cannot
 * drift away from the slots.
 */
public class ThoughtTranscriptionTableScreen extends AbstractContainerScreen<ThoughtTranscriptionTableMenu>
{
    private static final ResourceLocation SHEET =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final ResourceLocation CRAFTING_TABLE =
            new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");

    private static final int TITLE_Y = 5;
    private static final int WORK_TOP = 17;
    private static final int WORK_BORDER_HEIGHT = ThoughtTranscriptionTableMenu.INVENTORY_TOP - WORK_TOP;
    private static final int CONTENT_COLOUR = 0xFFC6C6C6;
    private static final int TEXT_COLOUR = 0xFF404040;
    private static final int COST_COLOUR = 0xFF55FF55;
    /** The anvil's other half: a price the player cannot pay is written in red. */
    private static final int COST_UNAFFORDABLE_COLOUR = 0xFFFF5555;

    /** The arrow: bar drawn, head stamped, both on the axis the approved mockup uses (y = 62). */
    private static final int ARROW_BAR_X = 84;
    private static final int ARROW_BAR_Y = 61;
    private static final int ARROW_BAR_W = 36;
    private static final int ARROW_BAR_H = 3;
    private static final int ARROW_COLOUR = 0xFF8B8B8B;
    private static final int HEAD_U = 104;
    private static final int HEAD_V = 35;
    private static final int HEAD_X = 120;
    private static final int HEAD_Y = 55;

    /** The cost line, drawn only when the menu has something to say (phase 1: never). */
    private static final int COST_X = 106;
    private static final int COST_Y = 71;

    /** Where the anvil's cross sprite would sit, and where its strokes really are inside that sprite. */
    private static final int CROSS_X = 93;
    private static final int CROSS_Y = 52;
    private static final int CROSS_INSET_X = 6;
    private static final int CROSS_INSET_Y = 3;
    private static final int CROSS_MAIN = 0xFF962300;
    private static final int CROSS_HIGHLIGHT = 0xFFBA370F;

    /**
     * The anvil cross sprite's strokes, transcribed from `anvil.png` (176,0,28,21), columns 6..20 and rows
     * 3..17. '#' is the sprite's main red and '+' its highlight, so the shape is the vanilla one pixel for
     * pixel without shipping the texture.
     */
    private static final String[] CROSS_PATTERN = {
            "##...........##",
            "#+#.........#+#",
            ".#+#.......#+#.",
            "..#+#.....#+#..",
            "...#+#...#+#...",
            "....#+#.#+#....",
            ".....#+#+#.....",
            "......#+#......",
            ".....#+#+#.....",
            "....#+#.#+#....",
            "...#+#...#+#...",
            "..#+#.....#+#..",
            ".#+#.......#+#.",
            "#+#.........#+#",
            "##...........##",
    };

    public ThoughtTranscriptionTableScreen(ThoughtTranscriptionTableMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = ThoughtTranscriptionTableMenu.IMAGE_WIDTH;
        this.imageHeight = ThoughtTranscriptionTableMenu.IMAGE_HEIGHT;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = ThoughtTranscriptionTableMenu.INVENTORY_LABEL_Y;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        // Two lines, both copied from the thought chain's screen because both are mandatory and neither is
        // obvious:
        //
        // 1. renderBackground(guiGraphics) - the shading every other screen in the game puts behind itself.
        //    A container screen overrides the render pipeline, so the base class never issues this call and a
 // window that does not issue it stands out as the only bright one (the report: "every screen
        //    goes grey, this one does not"). The one-argument overload is the one that shades.
        // 2. renderTooltip afterwards - AbstractContainerScreen#render runs renderBg and renderLabels but
        //    never renderTooltip, so without this line nothing has a tooltip at all, items included.
        diagnoseOnce();

        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY)
    {
        int x = this.leftPos;
        int y = this.topPos;

        // the frame: title band, the work area's own borders and fill, then the player inventory block
        guiGraphics.blit(SHEET, x, y, 0, 0, ThoughtTranscriptionTableMenu.IMAGE_WIDTH, WORK_TOP);
        guiGraphics.fill(x + 3, y + WORK_TOP, x + 173, y + ThoughtTranscriptionTableMenu.INVENTORY_TOP, CONTENT_COLOUR);
        guiGraphics.blit(SHEET, x, y + WORK_TOP, 0, WORK_TOP, 3, WORK_BORDER_HEIGHT);
        guiGraphics.blit(SHEET, x + 173, y + WORK_TOP, 173, WORK_TOP, 3, WORK_BORDER_HEIGHT);
        guiGraphics.blit(SHEET, x, y + ThoughtTranscriptionTableMenu.INVENTORY_TOP, 0, 125,
                ThoughtTranscriptionTableMenu.IMAGE_WIDTH, 97);

        // the six recesses, taken from the menu's own slot positions
        for (int i = 0; i < ThoughtTranscriptionTableBlockEntity.SLOT_COUNT; i++)
        {
            Slot slot = this.menu.slots.get(i);
            guiGraphics.blit(SHEET, x + slot.x - 1, y + slot.y - 1, 7, 17, 18, 18);
        }

        drawEye(guiGraphics, x, y);

        guiGraphics.fill(x + ARROW_BAR_X, y + ARROW_BAR_Y, x + ARROW_BAR_X + ARROW_BAR_W,
                y + ARROW_BAR_Y + ARROW_BAR_H, ARROW_COLOUR);
        guiGraphics.blit(CRAFTING_TABLE, x + HEAD_X, y + HEAD_Y, HEAD_U, HEAD_V, 8, 15);

        if (this.menu.showsNoResultCross())
            drawCross(guiGraphics, x, y);
    }

    /**
 * The eye band: the own 54x12 artwork, blitted straight from our sheet.
     *
     * <p>The band used to be painted from a pixel pattern with {@code fill}, because an earlier pass could not
     * get a picture to appear there and the pattern removed the failure mode. That pattern was a hand-drawn
 * approximation of this file, and it showed: the report was a flat grey block with the eyes gone.
     * The artwork itself was never the problem - the file has been in {@code assets/} all along, byte for byte
 * identical to what the author drew - so the band now blits it, one texture pixel to one screen pixel, at
 * the approved position. Nothing is scaled or recoloured: the two eye whites, the lid linework
     * and the scrollwork are the authored pixels.
     *
     * <p>⚠️ <b>This must be the nine-argument {@code blit}, with the sheet's real size passed in.</b> The
     * seven-argument overload hard-codes the sheet as 256x256 (its bytecode passes {@code sipush 256, sipush
     * 256} straight through), so it normalises the UVs as if this were a 256x256 atlas and reads a single texel
     * out of a 54x12 picture - which is exactly the flat bar this band kept turning into. The mod's own HUD
     * sheet already blits the nine-argument way ({@code GuiHandler} passes {@code texw = 256, texh = 128}).
     */
    private void drawEye(GuiGraphics guiGraphics, int x, int y)
    {
        guiGraphics.blit(TranscriptionLayout.EYE_TEXTURE, x + TranscriptionLayout.EYE_X, y + TranscriptionLayout.EYE_Y,
                0, 0, TranscriptionLayout.EYE_W, TranscriptionLayout.EYE_H,
                TranscriptionLayout.EYE_W, TranscriptionLayout.EYE_H);
    }

    private void drawCross(GuiGraphics guiGraphics, int x, int y)
    {
        for (int row = 0; row < CROSS_PATTERN.length; row++)
        {
            String line = CROSS_PATTERN[row];

            for (int column = 0; column < line.length(); column++)
            {
                char pixel = line.charAt(column);

                if (pixel == '.')
                    continue;

                int colour = pixel == '#' ? CROSS_MAIN : CROSS_HIGHLIGHT;
                int px = x + CROSS_X + CROSS_INSET_X + column;
                int py = y + CROSS_Y + CROSS_INSET_Y + row;
                guiGraphics.fill(px, py, px + 1, py + 1, colour);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        Component hint = hoveredSlotHint();

        // The hint joins the tooltip that is already being drawn, rather than being a second tooltip call
        // after it. An empty slot has no tooltip of its own, so calling again is the only way to show one -
        // and that is exactly the shape ThoughtChainScreen uses for its third row, whose icons are also not
        // items. Keeping the hint in this one call means there is never a second box stacked on the first.
        if (hint == null)
        {
            super.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        diag("hint drawn for table slot " + this.hoveredSlot.getContainerSlot());

        List<Component> lines = new ArrayList<>();
        lines.add(hint);

        guiGraphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    /**
     * A one-shot reading of what this screen was actually given, printed only when
     * {@code -Dsanitypd.tableDiag=true} is set.
     *
     * <p>It answers the question every "the table does not work" report needs answered first: is the client's
     * menu holding the real block entity or the dummy container the lookup falls back to? Slot filters, the
     * preview and the hint all read that same container, so all three fail together when it is the dummy -
     * which is exactly the shape of a report like "nothing shows and nothing crafts".
     */
    private void diagnoseOnce()
    {
        if (s_diagnosed || !Boolean.getBoolean("sanitypd.tableDiag"))
            return;

        s_diagnosed = true;

        Container table = this.menu.tableContainer();
        boolean real = table instanceof ThoughtTranscriptionTableBlockEntity;

        diag("container is the block entity = " + real
                + " (class " + table.getClass().getSimpleName() + ", size " + table.getContainerSize() + ")"
                + " | output slot = " + table.getItem(ThoughtTranscriptionTableBlockEntity.OUTPUT_SLOT)
                + " | cost line = " + this.menu.costLine()
                + " | slots = " + this.menu.slots.size());
    }

    private void diag(String message)
    {
        if (Boolean.getBoolean("sanitypd.tableDiag"))
            SanityMod.LOGGER.info("[TABLE-DIAG] {}", message);
    }

    /** One-shot guard for {@link #diagnoseOnce()}. */
    private static boolean s_diagnosed;

    /**
     * The hint for a hovered, empty table slot, drawn in the ordinary tooltip box. Only for table slots (the
     * container identity decides that, not a slot index - a player slot can carry the same index), and only
     * while the slot is empty: an occupied slot already draws its item tooltip, and two boxes stacked on each
     * other would be worse than one.
     */
    private Component hoveredSlotHint()
    {
        if (this.hoveredSlot == null || !this.hoveredSlot.getItem().isEmpty())
            return null;

        if (this.hoveredSlot.container != this.menu.tableContainer())
            return null;

        return ThoughtTranscriptionTableMenu.slotHint(this.hoveredSlot.getContainerSlot());
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        guiGraphics.drawString(this.font, this.title, (this.imageWidth - this.font.width(this.title)) / 2,
                TITLE_Y, TEXT_COLOUR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                TEXT_COLOUR, false);

        Component cost = this.menu.costLine();

        // Same place, two colours: green while the two levels are there, red while they are not (the anvil's
 // rule, which the design requires). The position never moves - only the colour changes.
        // The cost line's outline is the anvil's, and the anvil's is a single shadow pass at (+1, +1) - not a
        // ring around every glyph. Drawing the ring (which the first attempt did) smears the characters: the
 // the wording was "the experience text is a mess".
        if (cost != null)
            outlinedText(guiGraphics, cost, COST_X - this.font.width(cost) / 2, COST_Y,
                    this.menu.costAffordable() ? COST_COLOUR : COST_UNAFFORDABLE_COLOUR);
    }

    /**
     * Text with vanilla's shadow, which is one offset pass in shadow grey and then the text on top.
     *
     * <p>{@code Font#drawShadow} is the exact vanilla call but it takes a {@code PoseStack}, and this class
     * only holds a {@code GuiGraphics}. The same result is reproduced here: the shadow colour is the text
     * colour divided by four, which is the ratio vanilla's font renderer uses.
     */
    private void outlinedText(GuiGraphics guiGraphics, Component text, int x, int y, int colour)
    {
        int shadow = (colour & 0xFF000000)
                | (((colour >> 16) & 0xFF) / 4 << 16)
                | (((colour >> 8) & 0xFF) / 4 << 8)
                | ((colour & 0xFF) / 4);

        guiGraphics.drawString(this.font, text, x + 1, y + 1, shadow, false);
        guiGraphics.drawString(this.font, text, x, y, colour, false);
    }
}
