package piloser.sanitypd.client;

import java.util.ArrayList;
import java.util.List;


import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import piloser.sanitypd.thought.IThoughtChain;
import piloser.sanitypd.thought.ThoughtCategory;
import piloser.sanitypd.thought.ThoughtChainProvider;
import piloser.sanitypd.thought.ThoughtEffects;
import piloser.sanitypd.thought.ThoughtItem;
import piloser.sanitypd.thought.ThoughtType;

/**
 * The thought tooltip: what a thought grants, and which tier it is on.
 *
 * <h2>Why an event instead of {@code Item#appendHoverText}</h2>
 * A tooltip is a client-side thing, but item classes are loaded on a dedicated server too, and Forge's
 * runtime dist cleaner refuses to load a class that names client types. Putting this in {@code ThoughtItem}
 * would therefore break the server start - the same trap {@code ThoughtChainPacket} fell into. So the item
 * class stays data-only and the tooltip lives here, in an {@code @OnlyIn(CLIENT)} handler that a dedicated
 * server never loads.
 *
 * <h2>Two shapes</h2>
 * A <b>tiered</b> thought shows where it stands right now, how to open the detail page, and - while shift is
 * held - what it grants and the 1/3/5 ladder, with the tier in force in bold. An <b>untiered</b> thought has
 * exactly one sentence forever: no ladder, no shift hint, nothing hidden behind a key press.
 *
 * <h2>"Not equipped" is about this item, not about its type</h2>
 * The tier comes from how many thoughts of the item's <b>type</b> sit in the chain, but whether that tier is
 * shown at all depends on whether <b>this</b> thought is in the chain. Two thoughts can share a type, so
 * counting alone would make an unequipped sibling claim an effect it is not granting. That is why the chain
 * syncs the set of equipped items as well as the counts.
 */
@OnlyIn(Dist.CLIENT)
public final class ThoughtTooltips
{
    private static final String CURRENT_KEY = "thought.sanitypd.tooltip.current";

    /**
     * The placeholder inside {@link #CURRENT_KEY}, used to cut the label's sentence in two.
     *
     * <p>The label is rendered with an empty argument and the substituted text removed, so the line reads
     * "Current effect: " followed by the sentence's own component - one translation instead of two nested
     * ones (see {@link #effectLine}).
     */
    private static final String CURRENT_PLACEHOLDER = "%1$s";

    /**
     * The label an <b>untiered</b> thought's effect sits behind.
     *
     * <p>An untiered thought shows the same sentence forever, so "current effect" invites the reader to go
     * looking for the other tiers. The owner asked for the wording to match the tiered thoughts' page header
     * instead, that is, the "when slotted into your Thought Chain" wording:
     * "for thoughts with no tier, change the label in front of the description from the in-effect wording to
     * the equipped-in-chain wording, to prevent misunderstanding" (2026-10-03).
     *
     * <p>It is a key of its own rather than a reuse of {@code tooltip.when_equipped}: the two are separate
     * sentences today and may be reworded separately later, and a shared key would silently couple them.
     */
    private static final String EQUIPPED_KEY = "thought.sanitypd.tooltip.equipped";
    private static final String NOT_EQUIPPED_KEY = "thought.sanitypd.tooltip.not_equipped";
    /**
     * The collapsed hint is the project's own line rather than a thought-specific one: the mod already says
     * "§7按住 §9SHIFT§7 查看更多信息" on every other item with a detail page, and a second wording would make
     * the same gesture read as two different features.
     */
    private static final String HOLD_KEY = "item.sanitypd.tooltip.press_shift";
    private static final String RELEASE_KEY = "thought.sanitypd.tooltip.release_shift";
    private static final String WHEN_KEY = "thought.sanitypd.tooltip.when_equipped";
    private static final String HEADER_KEY = "thought.sanitypd.tooltip.tier_header";

    /**
     * The header sentence used by a <b>penalty</b> thought instead of {@link #HEADER_KEY}.
     *
     * <p>The shared sentence says "additional bonus from ...", which is the opposite of what a purely negative
     * thought does. Picking a second sentence keeps the promise honest without inventing per-item wording.
     */
    private static final String HEADER_PENALTY_KEY = "thought.sanitypd.tooltip.tier_header_penalty";
    private static final String TIER_KEY = "thought.sanitypd.tooltip.tier";
    private static final String CATEGORY_KEY_PREFIX = "thought.sanitypd.category.";

    /** Colour of a value that helps the player. */
    private static final int BUFF_COLOR = 0x55FF55;

    /** Colour of a value that costs the player something. */
    private static final int DEBUFF_COLOR = 0xFF2E63;

    // The tier thresholds are NOT declared here on purpose: they live in ThoughtEffects next to the ladders and
    // the "count to tier" mapping. A local copy is how the tooltip once highlighted a tier the effect was not
    // on - see ThoughtEffects#tierThreshold, and the project check run before a release that keeps the tier
    // data single-sourced.

    /** Suffixes of the per-item language entries, matching {@link #TIER_THRESHOLDS}. */
    private static final String[] TIER_SUFFIXES = { ".tier1", ".tier3", ".tier5" };

    /**
     * Adds the thought lines to a tooltip.
     *
     * @param event Forge's tooltip event; only stacks whose item is a {@link ThoughtItem} are touched
     */
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event)
    {
        if (!(event.getItemStack().getItem() instanceof ThoughtItem thought))
            return;

        List<Component> lines = event.getToolTip();
        IThoughtChain chain = localChain();
        boolean equipped = chain != null && chain.holds(thought);
        // The tier index comes from the same call the mechanic uses, and from nowhere else. The tooltip used to
        // recompute it from "the highest of this thought's type counts" - correct for a single-axis thought and
        // wrong for one whose ladder counts one specific axis. The owner saw exactly that ("高亮有bug"): the bold
        // line marked the madness tier while the effect used the chaos-restraint one.
        int current = equipped ? ThoughtEffects.tierIndex(Minecraft.getInstance().player, thought) : -1;
        boolean shift = Screen.hasShiftDown();

        lines.add(tagLine(thought));

        if (!thought.tiered())
        {
            // Untiered: one sentence, no ladder and no hidden page. The sentence is the current effect by
            // definition, because it never changes.
            //
            // ⚠️ This line and the tiered one below are deliberately built the same way: the sentence is the
            // translatable component and the numbers are arguments. An early version wrote it as
            // CURRENT_KEY(sentence) - the whole sentence nested as an argument of another sentence - and the
            // numbers came out as the literal text "%1$s", because a nested argument is not formatted again.
            // The owner saw exactly that ("some thoughts still show a key instead of the number").
            lines.addAll(effectLines(thought));
            return;
        }

        lines.add(Component.translatable(CURRENT_KEY,
                        equipped ? tierText(thought, current) : Component.translatable(NOT_EQUIPPED_KEY))
                .withStyle(ChatFormatting.GRAY));

        lines.add(Component.translatable(shift ? RELEASE_KEY : HOLD_KEY).withStyle(ChatFormatting.DARK_GRAY));

        if (!shift)
            return;

        lines.add(Component.translatable(WHEN_KEY, Component.translatable(thought.getDescriptionId() + ".when_equipped"))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(ThoughtEffects.isPenalty(thought) ? HEADER_PENALTY_KEY : HEADER_KEY,
                typeNames(thought)).withStyle(ChatFormatting.GRAY));

        for (int i = 0; i < ThoughtEffects.tierCount(); i++)
        {
            MutableComponent line = Component.translatable(TIER_KEY, ThoughtEffects.tierThreshold(i), tierSentence(thought, i));

            // The tier in force is the one that matters, so it is the one that stands out. Its number is
            // coloured either way, because the colour says what the effect does, not where the player stands.
            lines.add(i == current ? line.withStyle(ChatFormatting.BOLD) : line.withStyle(ChatFormatting.GRAY));
        }
    }

    /** The type-and-category line: every type this thought feeds, then the row it belongs to. */
    private static Component tagLine(ThoughtItem thought)    {
        MutableComponent line = Component.empty();

        for (ThoughtType type : thought.types())
        {
            if (!line.getString().isEmpty())
                line.append(Component.literal(" "));

            line.append(Component.translatable(type.translationKey())
                    .withStyle(Style.EMPTY.withColor(type.color()).withBold(true)));
        }

        line.append(Component.literal(" "));
        line.append(Component.translatable(categoryKey(thought.category()))
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

        return line;
    }

    /**
     * The type names the header sentence scales with, joined by {@code /}.
     *
     * <p>Deliberately <b>not</b> "every type this thought feeds": the tag line above already lists those. This
     * one names the axes the ladder actually counts, so a thought on two axes cannot promise that the wrong
     * axis deepens its tiers (see {@code ThoughtEffects#tierAxesOf}).
     */
    private static Component typeNames(ThoughtItem thought)
    {
        MutableComponent names = Component.empty();

        for (ThoughtType type : ThoughtEffects.tierAxesOf(thought))
        {
            if (!names.getString().isEmpty())
                names.append(Component.literal("/"));

            names.append(Component.translatable(type.translationKey()));
        }

        return names;
    }

    /**
     * The effect text of the tier the player is currently on, or the base text when still on tier zero.
     *
     * @param index the tier index the <b>mechanic</b> reports ({@code ThoughtEffects#tierIndex}), never a raw
     *              type count - passing a count here is exactly how the tooltip ended up disagreeing with the
     *              effect it describes
     */
    private static Component tierText(ThoughtItem thought, int index)
    {
        return index < 0
                ? Component.translatable(thought.getDescriptionId() + ".when_equipped")
                : tierSentence(thought, index);
    }

    /**
     * One tier's sentence, with its number supplied by the mechanic.
     *
     * <p>The language entry holds the sentence and <b>either one or two</b> placeholders, depending on
     * whether that sentence states a condition: with a condition it carries {@code %1$s} for the condition
     * and {@code %2$s} for the value, without one it carries only {@code %1$s} for the value (Nature
     * Affinity and Command Hallucination are the two that state no condition number). The number comes from
     * {@link ThoughtEffects#valueAt}, which is the same ladder the effect itself reads. Writing "40%" into the
     * language file would be a second copy of the number, and the copy in the file is the one nobody updates.
     *
     * <p>The colour follows the sign: a positive value helps and is green, a negative one costs and is red.
     * The condition numbers in the sentence (the "50%" of "above 50%") stay uncoloured, which is why only the
     * effect value is a placeholder, and why a sentence with a condition passes the condition as plain text
     * rather than as a coloured component.
     */
    private static Component tierSentence(ThoughtItem thought, int index)
    {
        String key = thought.getDescriptionId() + TIER_SUFFIXES[index];
        float threshold = ThoughtEffects.sanityThreshold(thought);

        // Sentences with a condition carry two placeholders (condition first, value last); Nature Affinity
        // states no number condition and carries only the value. Passing the count the language entry actually
        // has is what keeps the two in step - and no sentence may contain a bare "%" any more, because a
        // percent sign next to a placeholder makes Minecraft abandon the substitution and print the raw text.
        return threshold > 0f
                ? Component.translatable(key, thresholdComponent(threshold), valueComponent(thought, index))
                : Component.translatable(key, valueComponent(thought, index));
    }

    /**
     * The current-effect lines of a thought that has <b>no tier ladder</b>.
     *
     * <p>Its sentences never change, so there is no "which tier am I on" question - but they still print
     * numbers (Fight or Flight prints four), and those have to be coloured by their sign like every other
     * value in this mod. The numbers come from {@link ThoughtEffects#untieredValuesOf} in the order the
     * sentences were written, so the colour follows the mechanism and not a copy of it in this class.
     *
     * <h2>One translation per line, then concatenation</h2>
     * Each line is built as <b>label + sentence</b>, and each sentence is one translatable with its own
     * arguments - never a sentence nested inside another. Nesting is not composition in Minecraft's text
     * system: an argument of a translatable is rendered, not formatted again, so a nested sentence keeps its
     * literal {@code %1$s} on screen (measured on a dedicated server).
     *
     * <h2>Why several lines</h2>
     * Fight or Flight states two mutually exclusive states separated by a semicolon, which the owner reported
     * as hard to read ("给战斗或逃跑文本分下行,只有分号分隔不明显"). Splitting it needs <b>separate entries or
     * separate list elements</b>: the single-component tooltip overload does not split on a newline.
     */
    private static List<Component> effectLines(ThoughtItem thought)
    {
        float[] values = ThoughtEffects.untieredValuesOf(thought);
        String[] keys = ThoughtEffects.untieredLineKeysOf(thought);
        List<Component> lines = new ArrayList<>();
        int at = 0;

        for (int i = 0; i < keys.length; i++)
        {
            int count = ThoughtEffects.untieredLineValueCount(thought, i);
            Object[] arguments = new Object[count];

            for (int j = 0; j < count; j++)
                arguments[j] = valueComponent(values[at++], ThoughtEffects.untieredValuesArePercent(thought));

            MutableComponent line = i == 0
                    ? label().append(Component.translatable(keys[i], arguments))
                    : Component.translatable(keys[i], arguments);

            lines.add(line.withStyle(ChatFormatting.GRAY));
        }

        return lines;
    }

    /** One value as the player reads it: a percentage, coloured by its sign. */
    private static Component valueComponent(float value)
    {
        return valueComponent(value, true);
    }

    /**
     * One value as the player reads it, coloured by its sign.
     *
     * @param percent true for a fraction (written as a percentage), false for a plain amount such as
     *                "1" health. The unit is decided by {@link ThoughtEffects#untieredValuesArePercent},
     *                so the sentence and the number cannot disagree about what "1" means.
     */
    private static Component valueComponent(float value, boolean percent)
    {
        String text = percent
                ? Math.round(Math.abs(value) * 100f) + "%"
                : (value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.abs(value)));

        return Component.literal(text)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(value < 0f ? DEBUFF_COLOR : BUFF_COLOR)));
    }

    /** The "When slotted into your Thought Chain: " label, without the placeholder its entry carries. */
    private static MutableComponent label()
    {
        MutableComponent text = Component.translatable(EQUIPPED_KEY, Component.literal(""));

        // Drop the substituted argument, so the label is exactly the language file's text before its
        // placeholder. The placeholder itself never survives translation.
        String flat = text.getString();
        String tail = flat.substring(Math.max(flat.lastIndexOf(CURRENT_PLACEHOLDER), 0));

        return tail.isEmpty() ? text : Component.literal(flat.substring(0, flat.length() - tail.length()));
    }

    /**
     * One coloured component per value, in the order they were passed.
     *
     * <p>A value that is not a percentage (the "6 seconds" of Catharsis) is deliberately not coloured by
     * this path: only the sentences whose numbers this mod can name are listed in
     * {@link ThoughtEffects#untieredValuesOf}, and everything else prints its own words.
     */
    private static Object[] arguments(float[] values)
    {
        Object[] out = new Object[values.length];

        for (int i = 0; i < values.length; i++)
        {
            float value = values[i];
            out[i] = Component.literal(Math.round(Math.abs(value) * 100f) + "%")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(value < 0f ? DEBUFF_COLOR : BUFF_COLOR)));
        }

        return out;
    }

    /** The condition number as the player reads it, deliberately left uncoloured: it is a threshold, not a value. */
    private static Component thresholdComponent(float threshold)
    {
        return Component.literal(Math.round(threshold * 100f) + "%");
    }

    /** A ladder value as the player will read it: a percentage, coloured by its sign. */
    private static Component valueComponent(ThoughtItem thought, int index)
    {
        float value = ThoughtEffects.valueAt(thought, index);
        String percent = Math.round(Math.abs(value) * 100f) + "%";

        return Component.literal(percent)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(value < 0f ? DEBUFF_COLOR : BUFF_COLOR)));
    }

    /**
     * The local player's chain.
     *
     * <p>The client's copy carries the synced counts and the equipped-item set; the contents themselves are
     * only known while the screen is open, which is exactly why the tooltip needs those two instead.
     */
    private static IThoughtChain localChain()
    {
        Player player = Minecraft.getInstance().player;

        return player == null ? null : player.getCapability(ThoughtChainProvider.CAP).orElse(null);
    }

    /** Category name key, built from the enum name so the key shape stays visible in one place. */
    private static String categoryKey(ThoughtCategory category)
    {
        return CATEGORY_KEY_PREFIX + category.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Public because the event bus instantiates the handler; nothing else is expected to.
     */
    public ThoughtTooltips() {}
}
