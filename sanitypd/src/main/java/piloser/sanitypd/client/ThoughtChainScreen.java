package piloser.sanitypd.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import piloser.sanitypd.thought.Mindset;
import piloser.sanitypd.thought.MindsetAttributes;
import piloser.sanitypd.thought.Mindsets;
import piloser.sanitypd.thought.ThoughtChainMenu;
import piloser.sanitypd.thought.ThoughtChainProvider;

/**
 * The thought chain screen.
 *
 * <h2>Scheme B: the background is composed, not shipped</h2>
 * Nothing here is baked into an image this mod owns. Every frame, border and slot hole is blitted out of
 * vanilla's double-chest texture, which keeps Mojang's art out of this jar entirely. The box is 176x222 -
 * exactly a vanilla double chest - so any window that fits a double chest fits this one.
 *
 * <p>The composition follows the measured source rectangles of
 * {@code minecraft:textures/gui/container/generic_54.png}: 176 wide, 222 tall, a black/white/light-grey
 * frame, 18x18 slot holes, and the player inventory block at y=125..221 copied verbatim. The two rows this
 * screen keeps are cut from that texture's own hole strip, so the bevels and shading are vanilla's own
 * pixels rather than an approximation.
 *
 * <h2>The third row is drawn, not slotted</h2>
 * Mindsets are neither items nor menu slots ({@link ThoughtChainMenu}), so the row paints itself: one hole
 * per active mindset with its icon, the whole row centred - which is what the owner specified - and a
 * single empty hole with a hover hint while nothing is active.
 *
 * <h2>GuiGraphics, not PoseStack</h2>
 * This project targets 1.20, where screen rendering already takes {@link GuiGraphics}: the PoseStack
 * signatures belong to 1.19.2 and earlier. Slot coordinates therefore also come from {@code GuiGraphics}
 * ({@code blit}/{@code fill}) rather than from helper methods on the screen.
 */
@OnlyIn(Dist.CLIENT)
public class ThoughtChainScreen extends AbstractContainerScreen<ThoughtChainMenu>
{
    /** Vanilla texture the frame is cut from; never shipped, only read at runtime. */
    private static final ResourceLocation CHEST = new ResourceLocation("minecraft",
            "textures/gui/container/generic_54.png");

    /** Fill colour of the box interior in that texture (opaque light grey). */
    private static final int FILL = 0xFFC6C6C6;

    /** Source rectangle of one 18x18 slot hole in that texture. */
    private static final int HOLE_U = 7;
    private static final int HOLE_V = 17;

    /** Source rectangle of the strip holding a whole row of nine holes. */
    private static final int ROW_STRIP_U = 7;
    private static final int ROW_STRIP_V = 17;
    private static final int ROW_STRIP_W = 162;
    private static final int ROW_STRIP_H = 18;

    /** Screen y of each row's hole strip: one pixel above the slot it belongs to. */
    private static final int[] ROW_HOLE_Y = { 29, 59 };

    /** Screen y of the third row's hole and of the icons drawn inside it. */
    private static final int MINDSET_HOLE_Y = 89;
    private static final int MINDSET_ICON_Y = 90;

    /** Screen y of the three subtitles. */
    private static final int[] SUBTITLE_Y = { 20, 50, 80 };

    /** Screen y of the player inventory label (vanilla's own rule: imageHeight - 94). */
    private static final int INVENTORY_LABEL_Y = 128;

    /** Text colour of every label here; vanilla's container label colour. */
    private static final int LABEL_COLOR = 0x404040;

    private static final String[] SUBTITLE_KEYS = {
            "gui.sanitypd.thought_chain.worldview",
            "gui.sanitypd.thought_chain.methodology",
            "gui.sanitypd.thought_chain.trend",
    };
    private static final String INVENTORY_KEY = "gui.sanitypd.thought_chain.inventory";
    private static final String EMPTY_HINT_KEY = "gui.sanitypd.thought_chain.empty_hint";
    private static final String MINDSET_CONDITION_KEY = "thought.sanitypd.tooltip.mindset_condition";
    private static final String MINDSET_EFFECTS_KEY = "mindset.sanitypd.tooltip.effects";

    /** Colour of a value the player gains; the same green the thought tooltips use. */
    private static final int BUFF_COLOR = 0x55FF55;

    /** Colour of a value that costs the player something; the same crimson the thought tooltips use. */
    private static final int DEBUFF_COLOR = 0xFF2E63;

    public ThoughtChainScreen(ThoughtChainMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
    }

    /** Left x of the third row's holes for a given count: the row stays centred as it grows. */
    public static int mindsetRowX(int count)
    {
        return (176 - count * 18) / 2;
    }

    /**
     * The mindsets currently active on the <b>client's</b> copy of the chain.
     *
     * <p>Read from the counts, not from the items: the rule is written in terms of counts, the tooltip uses
     * counts, and counts are what the server pushes, so all three agree by construction.
     *
     * <p>Order is the server's activation order, which is what the owner specified: the mindset that turned
     * on first holds the first slot, and the row closes its gaps when one turns off.
     */
    private List<Mindset> activeMindsets()
    {
        List<Mindset> active = new ArrayList<>();
        Player player = Minecraft.getInstance().player;

        if (player == null)
            return active;

        player.getCapability(ThoughtChainProvider.CAP).ifPresent(chain ->
        {
            // The order comes from the server, so the row shows the sequence the mindsets actually turned on
            // in - not the order this class happens to iterate. Ids the code no longer knows are dropped.
            for (String id : chain.activeMindsetIds())
                Mindsets.byId(id).ifPresent(active::add);
        });

        return active;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY)
    {
        // Frame: top band, interior fill, the two side columns, then the player inventory block verbatim.
        guiGraphics.blit(CHEST, leftPos, topPos, 0, 0, 176, 17);
        guiGraphics.fill(leftPos + 3, topPos + 17, leftPos + 173, topPos + 125, FILL);
        guiGraphics.blit(CHEST, leftPos, topPos + 17, 0, 17, 3, 108);
        guiGraphics.blit(CHEST, leftPos + 173, topPos + 17, 173, 17, 3, 108);
        guiGraphics.blit(CHEST, leftPos, topPos + 125, 0, 125, 176, 97);

        // The two rows this screen keeps, cut from the texture's own hole strip.
        for (int y : ROW_HOLE_Y)
            guiGraphics.blit(CHEST, leftPos + ROW_STRIP_U, topPos + y, ROW_STRIP_U, ROW_STRIP_V, ROW_STRIP_W, ROW_STRIP_H);

        renderMindsetRow(guiGraphics);
    }

    /** Paints the third row: one hole per active mindset, or a single empty hole. */
    private void renderMindsetRow(GuiGraphics guiGraphics)
    {
        List<Mindset> active = activeMindsets();
        int holes = Math.max(1, active.size());
        int left = mindsetRowX(holes);

        for (int i = 0; i < holes; i++)
            guiGraphics.blit(CHEST, leftPos + left + i * 18, topPos + MINDSET_HOLE_Y, HOLE_U, HOLE_V, 18, 18);

        for (int i = 0; i < active.size(); i++)
            // The 9-argument form carries the texture's own size. The 7-argument form assumes a 256x256
            // sheet, so a 16x16 mindset icon would be sampled as a one-pixel sliver of its corner - drawn,
            // but invisible on screen. The chest texture used above really is 256x256, which is why only the
            // icons were affected.
            guiGraphics.blit(active.get(i).icon(), leftPos + left + i * 18 + 1, topPos + MINDSET_ICON_Y, 0, 0, 16, 16, 16, 16);
    }

    /**
     * The mindset tooltip: name, the condition that switches it on, then what it grants.
     *
     * <p>Every number is passed as an argument rather than written into the sentence. That is not only about
     * colouring: a language entry holding a bare "%" next to a placeholder makes Minecraft abandon the
     * substitution and print the raw sentence, which is exactly what the thought tooltips did before.
     *
     * <p><b>Colour follows the same split as the thought tooltips</b>: the mindset's
     * type name carries its own type colour in bold, every value the player <b>gains</b> is the buff green,
     * and the conditions ("above 50%", "at least 5 thoughts") stay uncoloured on purpose - colouring a
     * threshold green would read as "50% is a bonus". The owner asked for this on 2026-10-03: the tooltip
     * was one flat grey block.
     */
    private void renderMindsetTooltip(GuiGraphics guiGraphics, Mindset mindset, int mouseX, int mouseY)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(mindset.translationKey())
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(mindset.type().color())).withBold(true)));
        lines.add(Component.translatable(MINDSET_CONDITION_KEY, typeName(mindset), mindset.threshold())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(MINDSET_EFFECTS_KEY).withStyle(ChatFormatting.GRAY));

        for (Component effect : mindsetEffects(mindset))
            lines.add(effect.copy().withStyle(ChatFormatting.GRAY));

        // One list entry per line, which is what makes the tooltip multi-row: the List overload gives every
        // entry its own row, while renderTooltip(Font, Component, int, int) wraps its text in a single
        // FormattedCharSequence (List.of(text.getVisualOrderText())) and never splits on a newline.
        // Joining the lines with "\n" therefore drew all of them on top of each other - the owner saw
        // "the composure mindset's detail text is a mess" on 2026-10-02.
        guiGraphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    /**
     * The counting type as the player reads it, in the type's own colour and bold - the same treatment the
     * thought tooltip's tag line gives it, so "镇静" reads the same in both places.
     */
    private static Component typeName(Mindset mindset)
    {
        return Component.translatable(mindset.type().translationKey())
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(mindset.type().color())).withBold(true));
    }

    /**
     * The four effects this mindset grants, with the same numbers the code applies.
     *
     * <p>Written here rather than in a data table because a mindset has exactly one set of effects and they are
     * part of its definition: the code that applies them lives in {@code SanityProcessor} and {@code
     * MindsetAttributes}, and these constants are the same numbers those two use.
     *
     * <p>Only the effect's own value goes through {@link #buff}: the condition numbers in front of it stay
     * plain, so the eye lands on what the player actually gets.
     */
    private static List<Component> mindsetEffects(Mindset mindset)
    {
        List<Component> lines = new ArrayList<>();
        String stem = "mindset.sanitypd." + mindset.id() + ".effect";

        if (Mindsets.MADNESS.id().equals(mindset.id()))
        {
            // Madness is the first mindset that also takes something away. Its drain line states no number
            // at all: the rate is the configured darkness rate, equal by definition, so printing a figure
            // here would be a second copy of a value the player can change in the config.
            lines.add(Component.translatable(stem + "1", percent(MindsetAttributes.MADNESS_ABOVE)));
            lines.add(Component.translatable(stem + "2", percent(MindsetAttributes.MADNESS_LOW),
                    buff(percent(MindsetAttributes.MADNESS_RESIST)), buff(percent(MindsetAttributes.MADNESS_ATTACK)),
                    buff(percent(MindsetAttributes.MADNESS_ARMOR))));
            lines.add(Component.translatable(stem + "3", buff((int) (MindsetAttributes.MADNESS_DAMAGE_FLOOR / 2f))));
            return lines;
        }

        lines.add(Component.translatable(stem + "1", buff(20)));
        lines.add(Component.translatable(stem + "2", percent(0.5f), buff(percent(0.15f))));
        lines.add(Component.translatable(stem + "3", percent(0.8f), buff(percent(0.15f)), buff(percent(0.20f))));
        lines.add(Component.translatable(stem + "4", buff(1), buff(percent(0.75f))));

        return lines;
    }

    /**
     * A value the player gains, in the same green the thought tooltips use for positive numbers.
     *
     * <p>The value is an argument of its sentence rather than a separate component appended to it, so the
     * sentence keeps its own word order in every language - only the colour is ours.
     */
    private static Component buff(Object value)
    {
        return Component.literal(String.valueOf(value))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(BUFF_COLOR)));
    }

    /** A fraction as a readable percentage, so no sentence has to hard-code one. */
    private static String percent(float value)
    {
        return Math.round(value * 100f) + "%";
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        // Centred title: "{account}'s Thought Chain", with the name filled in by the open-screen packet.
        guiGraphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, LABEL_COLOR, false);

        for (int row = 0; row < SUBTITLE_KEYS.length; row++)
            guiGraphics.drawString(font, Component.translatable(SUBTITLE_KEYS[row]), 8, SUBTITLE_Y[row], LABEL_COLOR, false);

        guiGraphics.drawString(font, Component.translatable(INVENTORY_KEY), 8, INVENTORY_LABEL_Y, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        List<Mindset> active = activeMindsets();
        int left = leftPos + mindsetRowX(Math.max(1, active.size()));
        int top = topPos + MINDSET_HOLE_Y;

        // Below the first hole on the row, so only the row itself reacts - not the whole window width.
        if (mouseY >= top && mouseY < top + 18)
        {
            int slot = (mouseX - left) / 18;

            if (mouseX >= left && slot >= 0 && slot < Math.max(1, active.size()))
            {
                if (active.isEmpty())
                {
                    // The empty row says what it is waiting for; there is no mindset to describe yet.
                    guiGraphics.renderTooltip(font, Component.translatable(EMPTY_HINT_KEY), mouseX, mouseY);
                }
                else
                {
                    // The second tooltip: what a mindset is, what switches it on, and what it grants. It is
                    // reachable by hovering the icon in the row, because a mindset is not an item and has no
                    // tooltip of its own in an inventory.
                    renderMindsetTooltip(guiGraphics, active.get(slot), mouseX, mouseY);
                }
            }
        }
    }
}
