package piloser.sanitypd.block;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.thought.EmpoweredThought;
import piloser.sanitypd.thought.ThoughtItem;
import piloser.sanitypd.thought.ThoughtType;

/**
 * The transcription table's first rule, written as a pure function of the six stacks.
 *
 * <p>Specification: three thoughts of one and the same type (duplicates fine, hallucinations and chaos
 * restraint excluded) in the three material slots, one thought container in the thought slot, one inner core in
 * the core slot - and the table transcodes the type's container variant. Every rule of this kind costs two
 * experience levels.
 *
 * <p>Why a rule and not a data recipe: the shape is identical for every type, so four near-identical JSON files
 * would only add four places to get the same thing wrong. A pure function can also be handed to the JEI page,
 * which is what keeps that page and the machine from ever disagreeing - both read this one method.
 *
 * <p><b>Multi-axis thoughts</b>: a thought may feed more than one counter (sleep debt is composure <i>and</i>
 * endurance; depersonalisation is chaos restraint <i>and</i> madness), and the ruling is that each of
 * those axes counts - such a thought can stand in for any of its own types. So the three materials are matched
 * on the axis they <b>share</b>, and when they share several, the first material's own declared axis order
 * decides: {@code ThoughtItem#types()} is written primary axis first, which is also the order the tier ladder
 * reads. Chaos restraint and hallucination stay excluded as types, so depersonalisation can only ever count as
 * madness here.
 */
public final class TranscriptionRules
{
 /** What every transcription costs, in experience levels ("two levels, always"). */
    public static final int XP_COST = 2;

 /** What an upgrade costs, in experience levels ("three levels, always"). */
    public static final int UPGRADE_XP_COST = 3;

 /** Types left out of this rule, with the container variants that do not exist for them. */
    private static final List<ThoughtType> EXCLUDED = List.of(ThoughtType.HALLUCINATION, ThoughtType.CHAOS_RESTRAINT);

    /** The outcome of a matched rule: what comes out, and what it costs in experience levels. */
    public record Result(ItemStack output, int cost) {}

    private TranscriptionRules() {}

    /**
     * The three material slots, the thought slot and the core slot, in that order. Returns null when the inputs
     * do not match - the caller shows the no-result cross in that case.
     */
    public static Result match(ItemStack first, ItemStack second, ItemStack third, ItemStack thought, ItemStack core)
    {
        Result upgrade = matchUpgrade(first, second, third, thought, core);

        return upgrade != null ? upgrade : matchTranscribe(first, second, third, thought, core);
    }

    /**
     * The upgrade half of the dispatcher.
     *
 * <p>Rule: <b>one</b> tiered thought of one of the four transcribable types in the material slots,
     * that type's container variant in the thought slot, an inner core in the core slot - and the same thought
     * comes back out empowered. "Taking it out of the output slot is what completes the upgrade", so nothing
     * happens until the take, exactly as the price is only paid there.
     *
     * <p>Which of the three material slots holds it does not matter, and the other two have to be empty: with
     * the thought in the middle, for instance, the rule still matches. Requiring the first slot - which is what
     * the first version of this did - made the machine look broken to anyone who dropped the thought anywhere
 * else, and play-testing reported exactly that ("it should work in all three").
     *
     * <p>A thought that is already empowered does not match: otherwise it could be fed back in and eaten again.
     */
    private static Result matchUpgrade(ItemStack first, ItemStack second, ItemStack third, ItemStack thought,
            ItemStack core)
    {
        // exactly one material, whichever of the three slots it sits in
        ItemStack only = null;
        int filled = 0;

        for (ItemStack material : new ItemStack[] { first, second, third })
        {
            if (material.isEmpty())
                continue;

            only = material;
            filled++;
        }

        if (filled != 1 || only == null)
            return null;

        if (!(only.getItem() instanceof ThoughtItem item) || !item.tiered())
            return null;

        if (EmpoweredThought.is(only))
            return null;

        if (!core.is(ItemRegistry.INNER_CORE.get()))
            return null;

        for (ThoughtType type : typesOf(only))
        {
            if (!containerMatches(thought, type))
                continue;

            ItemStack output = only.copy();
            output.setCount(1);
            EmpoweredThought.empower(output);

            return new Result(output, UPGRADE_XP_COST);
        }

        return null;
    }

    /** Whether a stack is the container variant that belongs to a type. */
    private static boolean containerMatches(ItemStack stack, ThoughtType type)
    {
        ItemStack variant = variantFor(type);

        return !variant.isEmpty() && stack.is(variant.getItem());
    }

    /** The transcribe half: three thoughts of one type in the material slots and the plain container. */
    private static Result matchTranscribe(ItemStack first, ItemStack second, ItemStack third, ItemStack thought,
            ItemStack core)
    {
        if (!thought.is(ItemRegistry.THOUGHT_CONTAINER.get()))
            return null;

        if (!core.is(ItemRegistry.INNER_CORE.get()))
            return null;

        List<ThoughtType> firstTypes = typesOf(first);
        List<ThoughtType> secondTypes = typesOf(second);
        List<ThoughtType> thirdTypes = typesOf(third);

        if (firstTypes.isEmpty() || secondTypes.isEmpty() || thirdTypes.isEmpty())
            return null;

        // The shared axis, in the first material's own order (see the class javadoc for that tie-break).
        for (ThoughtType type : firstTypes)
        {
            if (!secondTypes.contains(type) || !thirdTypes.contains(type))
                continue;

            ItemStack output = variantFor(type);

            return output == null ? null : new Result(output, XP_COST);
        }

        return null;
    }

    /**
     * Every axis a stack can stand in for, in the item's own declared order, with the types this rule never
     * produces removed. Empty when the stack is not a thought at all.
     */
    public static List<ThoughtType> typesOf(ItemStack stack)
    {
        if (stack.isEmpty() || !(stack.getItem() instanceof ThoughtItem thought))
            return List.of();

        return thought.types().stream().filter(type -> !EXCLUDED.contains(type)).toList();
    }

    /** The container variant of a type, or null for the types this rule does not produce. */
    public static ItemStack variantFor(ThoughtType type)
    {
        if (type == ThoughtType.COMPOSURE)
            return new ItemStack(ItemRegistry.THOUGHT_CONTAINER_COMPOSURE.get());

        if (type == ThoughtType.MADNESS)
            return new ItemStack(ItemRegistry.THOUGHT_CONTAINER_MADNESS.get());

        if (type == ThoughtType.SERVITUDE)
            return new ItemStack(ItemRegistry.THOUGHT_CONTAINER_SERVITUDE.get());

        if (type == ThoughtType.ENDURANCE)
            return new ItemStack(ItemRegistry.THOUGHT_CONTAINER_ENDURANCE.get());

        return null;
    }
}
