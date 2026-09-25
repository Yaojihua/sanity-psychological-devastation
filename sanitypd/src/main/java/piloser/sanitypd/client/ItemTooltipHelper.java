package piloser.sanitypd.client;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.item.IShadowWeapon;
import piloser.sanitypd.item.ShadowRefinement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Tooltip styling helper for weapons and utility items.
 *
 * <p>Two jobs:
 * <ol>
 *   <li>{@link #showTooltipOnShift} adds the "hold SHIFT for more" line;</li>
 *   <li>{@link #movePsychicLineAfterAttributes} moves the psychic damage line below the vanilla
 *       attribute lines and reuses their color.</li>
 * </ol>
 *
 * <h2>Why the psychic damage line is added from an event</h2>
 * Vanilla builds tooltip lines in this order: the name, then the item's own
 * {@code appendHoverText(...)}, then a blank line, then the modifier header and attribute
 * modifiers. A line added inside {@code appendHoverText} therefore always precedes the attribute
 * lines, so it must be appended after the tooltip is fully built, from {@link ItemTooltipEvent}.
 *
 * <h2>Color</h2>
 * Vanilla renders a positive additive modifier (for example {@code +6 Attack Damage}) as
 * {@link ChatFormatting#BLUE} and a negative one (for example {@code -2.4 Attack Speed}) as
 * {@link ChatFormatting#RED}. The shadow weapons show {@code +4} attack damage, so the psychic
 * damage line uses {@link ChatFormatting#BLUE} to match. Green is not used: vanilla reserves
 * GREEN/DARK_GREEN for percentage modifiers and for the offhand branch.
 *
 * <p>This class has no side effects: it only reads items and edits the tooltip list on the client.
 */
@Mod.EventBusSubscriber(modid = SanityMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public abstract class ItemTooltipHelper
{
    /** Line shown when SHIFT is not held. */
    public static final String KEY_PRESS_SHIFT = "item." + SanityMod.MODID + ".tooltip.press_shift";

    /** Vanilla keys used to locate the attribute lines (hand header plus attack damage/speed). */
    private static final String[] ATTRIBUTE_ANCHORS = {
            "item.modifiers.mainhand",
            "item.modifiers.offhand",
            "attribute.name.generic.attack_damage",
            "attribute.name.generic.attack_speed"
    };

    /** Vanilla formats attribute amounts with DecimalFormat("#.##"): 8 renders as "8", 7.5 as "7.5". */
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#.##");

    /** Maximum number of lines probed while SHIFT is held ({@code <id>.tooltip} to {@code .tooltip5}). */
    private static final int MAX_TOOLTIP_LINES = 5;

    /**
     * Adds the "hold SHIFT for more" line, or the expanded description when SHIFT is held.
     *
     * <p>The expanded form walks {@code <id>.tooltip}, {@code <id>.tooltip2}, {@code <id>.tooltip3}
     * and so on, and stops at the first missing key, so each item can define as many lines as it
     * needs without a branch here.
     *
     * @param components tooltip line list
     * @param itemId     item id inside the language key (for example {@code shadow_sword})
     */
    public static void showTooltipOnShift(List<Component> components, String itemId)
    {
        if (!Screen.hasShiftDown())
        {
            components.add(Component.translatable(KEY_PRESS_SHIFT));
            return;
        }

        for (int i = 1; i <= MAX_TOOLTIP_LINES; i++)
        {
            // The first line uses <id>.tooltip, later ones <id>.tooltip2 / .tooltip3 ...
            String key = "item." + SanityMod.MODID + "." + itemId + (i == 1 ? ".tooltip" : ".tooltip" + i);

            if (!Component.translatable(key).getString().equals(key))
                components.add(Component.translatable(key));
        }
    }

    /**
     * Inserts the psychic damage line directly below the last attribute line, using the attribute
     * color. Does nothing when no attribute line is found.
     */
    @SubscribeEvent
    public static void movePsychicLineAfterAttributes(ItemTooltipEvent event)
    {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;

        Item item = stack.getItem();
        if (!(item instanceof IShadowWeapon weapon)) return;

        String labelId = weapon.shadowTooltipId();
        if (labelId == null) return;

        // Language key of the psychic damage line (lang: "+%s Psychic Damage")
        String lineKey = "item." + SanityMod.MODID + "." + labelId + ".psychic";
        // The amount includes the refinement bonus (base 8 plus refinement level) and comes from a single source.
        float damage = ShadowRefinement.psychicDamage(stack, weapon);
        String text = Component.translatable(lineKey, formatAmount(damage)).getString();

        List<Component> lines = event.getToolTip();

        // Do not insert twice; the event can fire more than once.
        for (Component c : lines)
            if (text.equals(c.getString())) return;

        int insertAt = -1;
        for (int i = 0; i < lines.size(); i++)
        {
            String s = lines.get(i).getString();
            for (String anchorKey : ATTRIBUTE_ANCHORS)
            {
                if (s.contains(Component.translatable(anchorKey).getString()))
                {
                    insertAt = i;
                    break;
                }
            }
        }
        if (insertAt < 0) return;

        lines.add(insertAt + 1, Component.translatable(lineKey, formatAmount(damage))
                .withStyle(ChatFormatting.BLUE));

        // Refinement line: only for refined weapons, so unrefined ones do not grow an extra line.
        int refine = ShadowRefinement.level(stack);
        if (refine > 0)
        {
            String refineText = Component.translatable("item." + SanityMod.MODID + ".refine",
                    refine, ShadowRefinement.MAX_LEVEL).getString();

            for (Component c : lines)
                if (refineText.equals(c.getString())) return;

            lines.add(insertAt + 2, Component.translatable("item." + SanityMod.MODID + ".refine",
                    refine, ShadowRefinement.MAX_LEVEL).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String formatAmount(float amount)
    {
        return AMOUNT_FORMAT.format(amount);
    }
}
