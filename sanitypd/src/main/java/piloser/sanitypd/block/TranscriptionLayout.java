package piloser.sanitypd.block;

import net.minecraft.resources.ResourceLocation;

import piloser.sanitypd.SanityMod;

/**
 * The transcription table's window geometry, in one place.
 *
 * <p>Three consumers draw or describe the same screen: the menu (where the slots are), the screen (where the
 * recesses, the eye band, the arrow and the cost line go) and - once the optional JEI page is present - JEI's
 * own recipe page, which has to look like the machine it documents. The requirement is that the JEI
 * page and the real interface use the same artwork and the same arrangement, and the only way to make that
 * structural rather than a promise is to let all three read these numbers.
 *
 * <p>Coordinates are the window's own, with the top-left corner at (0, 0); a consumer adds its own origin
 * ({@code leftPos}/{@code topPos} in a screen, nothing at all in JEI, which draws from the page's origin).
 *
 * <p>Slot origins are the 16x16 item area; the 18x18 recess is drawn one pixel up and to the left of that
 * origin, which is the convention every vanilla container follows.
 */
public final class TranscriptionLayout
{
    /** Window size. */
    public static final int WIDTH = 176;
    public static final int HEIGHT = 180;

    /** Pitch between neighbouring slots, vanilla's own. */
    public static final int PITCH = 18;

    /** The three material slots, in a row. */
    public static final int MATERIAL_X = 30;
    public static final int MATERIAL_Y = 17;

    /** The inner core slot: one row lower than the materials, sitting above the arrow's bar. */
    public static final int CORE_X = 97;
    public static final int CORE_Y = 35;

    /** The thought slot and the output, on the lower row. */
    public static final int THOUGHT_X = 48;
    public static final int OUTPUT_X = 134;
    public static final int LOWER_Y = 53;

    /**
     * The player inventory block, its label, and the four slot rows.
     *
     * <p>The lower block paints its slot wells from {@code generic_54.png} (the sheet is pasted from
     * {@link #INVENTORY_TOP}) instead of stamping them per slot the way the work area does, so the paste
     * position and the four row coordinates are <b>one combined alignment</b>: the well interior must land at
     * {@code slot - 3} for the 16x16 item to sit centred in its 18x18 frame.
     *
 * <p>⚠️ <b>Measured against the shipped screen</b> (measured on a screenshot, GUI scale 4, window origin
     * {@code (620, 192)}): the row wells' interiors were landing at {@code slot - 2}, so every item in the
     * lower block sat one pixel below and right of centre inside its frame, while the work area - whose wells
 * are stamped from the slot origin itself - was correct. That is the asymmetry play-testing reported
     * ("the lower inventory's items are not centred in their slots; the work area is fine").
     *
     * <p>⚠️ <b>Do not "even out" the gaps.</b> Vanilla's own player rows are 96/114/132 then 154 - three
     * {@code PITCH}s and then a longer gap - because the sheet's last well sits lower than the pitch would put
     * it. Moving only the hotbar (a tempting one-line "fix") breaks the row/well agreement and was wrong:
     * the three main rows and the hotbar shift together or not at all.
     */
    public static final int INVENTORY_TOP = 83;
    public static final int INVENTORY_LABEL_Y = 86;
    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 97;
    public static final int HOTBAR_Y = 155;

    /** The title's baseline, and the label colour vanilla's container screens use. */
    public static final int TITLE_Y = 5;

    /** The arrow: a filled bar, then vanilla's own arrow head stamped at its end. */
    public static final int ARROW_BAR_X = 84;
    public static final int ARROW_BAR_Y = 61;
    public static final int ARROW_BAR_W = 36;
    public static final int ARROW_BAR_H = 3;
    public static final int ARROW_HEAD_U = 104;
    public static final int ARROW_HEAD_V = 35;
    public static final int ARROW_HEAD_X = 120;
    public static final int ARROW_HEAD_Y = 55;
    public static final int ARROW_HEAD_W = 8;
    public static final int ARROW_HEAD_H = 15;

    /** The cost line, centred under the arrow. */
    public static final int COST_X = 106;
    public static final int COST_Y = 71;

    /**
     * Where the no-result cross's strokes sit, relative to the anvil sprite it was transcribed from:
     * {@code anvil.png} at (176, 0, 28, 21), strokes inside columns 6..20 and rows 3..17.
     */
    public static final int CROSS_X = 93;
    public static final int CROSS_Y = 52;
    public static final int CROSS_INSET_X = 6;
    public static final int CROSS_INSET_Y = 3;

    /** The work area: the recessed panel between the title band and the player inventory. */
    public static final int WORK_TOP = MATERIAL_Y;
    public static final int WORK_BORDER_HEIGHT = INVENTORY_TOP - WORK_TOP;

    /** Background of the work area, taken from the sheet the frame is stitched from. */
    public static final int CONTENT_COLOUR = 0xFFC6C6C6;

    /**
 * The eye band: the own artwork, {@code textures/gui/thought_transcription_table_eye.png}, 54x12,
 * at the approved position.
     *
     * <p>Both consumers blit this one file at its natural size - the machine's screen and JEI's page for it - so
     * these numbers are the band's box, not a pattern's dimensions, and changing them never rescales the
 * artwork by accident. The path lives here for the same reason the box does: the design requires the JEI
     * page and the real window to be the same picture, and two copies of a path drift apart.
     *
     * <p>⚠️ <b>Consumers must use the nine-argument {@code GuiGraphics#blit} and pass {@code EYE_W}/{@code EYE_H}
     * as the texture size.</b> The seven-argument overload hard-codes the sheet as 256x256, so a 54x12 picture
     * drawn through it reads a single texel and comes out as one flat colour - the flat bar this band kept
     * turning into, which is what drove an earlier pass to hand-copy the art as a pixel pattern instead. There
     * is no size restriction on the file itself; only the overload matters.
     */
    public static final ResourceLocation EYE_TEXTURE =
            new ResourceLocation(SanityMod.MODID, "textures/gui/thought_transcription_table_eye.png");

    /** The art's box inside {@link #EYE_TEXTURE}, and the sheet's size for the nine-argument blit. */
    public static final int EYE_X = 30;
    public static final int EYE_Y = 38;
    public static final int EYE_W = 54;
    public static final int EYE_H = 12;

    private TranscriptionLayout() {}
}
