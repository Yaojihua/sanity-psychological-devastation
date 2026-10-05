package piloser.sanitypd.thought;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

import piloser.sanitypd.item.ItemRegistry;

/**
 * What a thought <b>item</b> grants, as opposed to what a mindset grants - and the one place its tier ladder
 * is written down.
 *
 * <h2>Two different questions</h2>
 * A mindset is derived state: it switches on when enough thoughts of its type sit in the chain. An item effect
 * is granted by that specific item being in the chain, and is then <b>scaled</b> by how many thoughts of its
 * type the chain holds. Effects ask the second question, and "not equipped" is part of the answer: an item
 * that is absent grants nothing at all, which {@link #tierIndex} reports as -1.
 *
 * <h2>Why the ladder lives here</h2>
 * The mechanics and the tooltip must show the same numbers. Three hooks already needed the same values, and a
 * copy of the ladder at each call site is exactly how a tooltip ends up promising 40% while the code grants
 * 30%. So the ladders are constants, {@link #tierValue} reads them for the mechanics, and {@link #valueAt}
 * hands the same number to the tooltip.
 */
public final class ThoughtEffects
{
    /** Law of the Jungle: fraction of the friendly-creature sanity penalty that is waived. */
    public static final float[] LAW_OF_THE_JUNGLE = { 0.20f, 0.40f, 0.60f };

    /** Nature Affinity: bonus to the sanity restored by the four soothed behaviours. */
    public static final float[] NATURE_AFFINITY = { 0.20f, 0.40f, 0.50f };

    /** Socialization: reduction of the price villagers ask. */
    public static final float[] SOCIALIZATION = { 0.15f, 0.30f, 0.45f };

    /** Lucid Elation: bonus to attack damage. */
    public static final float[] LUCID_ELATION = { 0.25f, 0.35f, 0.45f };

    /** Command Hallucination: bonus to attack damage while a non-mild inner line is on screen. */
    public static final float[] COMMAND_HALLUCINATION = { 0.15f, 0.25f, 0.35f };

    /** Identification with the Aggressor: bonus to the damage dealt to whoever just hurt you. */
    public static final float[] IDENTIFICATION_WITH_THE_AGGRESSOR = { 0.15f, 0.30f, 0.40f };

    /** Psychomotor Agitation: bonus to movement speed at very low sanity or while mania is biting. */
    public static final float[] PSYCHOMOTOR_AGITATION = { 0.10f, 0.20f, 0.30f };

    /**
     * Irritability: attack damage <b>added on top of</b> what the mania effect itself grants.
     *
     * <p>The owner's rule is "the mania effect's attack bonus is raised by an extra ...", and it has to be an
     * addition rather than its own multiplier: both modifiers use {@code MULTIPLY_TOTAL}, which vanilla sums
     * before multiplying the base, so +50% from mania and +20% from here mean +70% - not +80%.
     */
    public static final float[] IRRITABILITY = { 0.10f, 0.20f, 0.30f };

    /**
     * Conversion Disorder: multiplier applied to the true damage a player's psychic overflow turns into.
     *
     * <p>The baseline path converts the overflow 1:1, so these are ratios rather than bonuses: 1.10 means
     * "every point of overflow becomes 1.10 points of true damage".
     */
    public static final float[] CONVERSION_DISORDER = { 1.10f, 1.17f, 1.25f };

    /**
     * Depersonalization: how much of the sanity maximum is lost as a <b>recovery ceiling</b>.
     *
     * <p>Stored as a penalty ({@code -0.20} = "you may only recover to 80% of the maximum") because that is
     * what the tooltip prints and colours: the value keeps the project's sign convention, where a negative
     * number is a debuff and is drawn red. {@link #recoveryCeilingFraction} turns it back into the ceiling the
     * mechanic applies. The owner's numbers are 1 / 3 / 5 chaos-restraint thoughts -&gt; 80% / 70% / 60%.
     *
     * <p>The sanity <b>maximum</b> itself is deliberately untouched: moving it would shift the bar, the HUD and
     * every other mechanic that reads it. This only stops recovery, which is why it is called a ceiling.
     */
    public static final float[] DEPERSONALIZATION = { -0.20f, -0.30f, -0.40f };

    /**
     * Fight or Flight: the two mutually exclusive states, each a pair of (attack, movement) multipliers.
     *
     * <p>Index 0 is the "no damage for five seconds" state - attack up, movement down. Index 1 is the
     * "hurt within the last five seconds" state - attack down, movement up. They are a single table
     * because they are one thought: splitting them across two constants is how one state ends up
     * granting the other state's numbers.
     */
    public static final float[][] FIGHT_OR_FLIGHT = {
            { 0.55f, -0.25f },
            { -0.25f, 0.55f }
    };

    /**
     * Fight or Flight's numbers <b>in the order the sentence prints them</b>: movement down, attack up,
     * attack down, movement up.
     *
     * <p>An untiered thought has no ladder, so {@link #ladderOf} returns zeroes for it and the tooltip has
     * nothing to colour. The sentence still contains four numbers, and they come from
     * {@link #FIGHT_OR_FLIGHT} in a fixed order - so the order is stated once, here, next to the table it
     * reads from, instead of being assumed at the tooltip.
     */
    public static final float[] FIGHT_OR_FLIGHT_ORDER = {
            FIGHT_OR_FLIGHT[0][1], FIGHT_OR_FLIGHT[0][0],
            FIGHT_OR_FLIGHT[1][0], FIGHT_OR_FLIGHT[1][1]
    };

    /**
     * The values a thought with <b>no tier ladder</b> prints, in the order its sentence prints them.
     *
     * <p>Returns an empty array for every other thought. These are not tiers: the same numbers apply at one
     * thought or at five, which is why they cannot live in {@link #ladderOf}.
     */
    public static float[] untieredValuesOf(Item item)
    {
        if (item == ItemRegistry.THOUGHT_FIGHT_OR_FLIGHT.get())
            return FIGHT_OR_FLIGHT_ORDER;

        if (item == ItemRegistry.THOUGHT_SLEEP_DEBT.get())
            return SLEEP_DEBT_ORDER;

        if (item == ItemRegistry.THOUGHT_INSTRUMENTAL_AGGRESSION.get())
            return INSTRUMENTAL_AGGRESSION_ORDER;

        if (item == ItemRegistry.THOUGHT_STRESS_INDUCED_ANALGESIA.get())
            return ANALGESIA_ORDER;

        // Catharsis states its numbers as words ("6 seconds", "40%"), so it has none to colour.
        return EMPTY;
    }

    // ---------------------------------------------------------------- untiered thoughts
    // Neither of these has a 1/3/5 ladder: the numbers are fixed. They still print values, so the tooltip
    // needs them in the order the sentence was written - the ladder table cannot supply what it does not hold.

    /** Sleep Debt: the fraction of a sleep's sanity the thought takes away. */
    public static final float SLEEP_DEBT_PENALTY = 0.15f;

    /** Sleep Debt: what it prints - one value, the penalty. */
    public static final float[] SLEEP_DEBT_ORDER = { -SLEEP_DEBT_PENALTY };

    /** Instrumental Aggression: the attack damage bonus. */
    public static final float INSTRUMENTAL_AGGRESSION_BONUS = 0.15f;

    /** Instrumental Aggression: what it prints - one value, the bonus. */
    public static final float[] INSTRUMENTAL_AGGRESSION_ORDER = { INSTRUMENTAL_AGGRESSION_BONUS };

    // ---------------------------------------------------------------- Stress-Induced Analgesia
    // The first untiered thought whose sentence also states a sanity threshold, so the numbers are split the
    // same way Fight or Flight splits them: the threshold is a condition and stays a plain word in the text,
    // while the payout is a value the tooltip colours.

    /** Stress-Induced Analgesia: the health restored each time it pays out. */
    public static final float ANALGESIA_HEAL = 1.0f;

    /** Stress-Induced Analgesia: the shortest gap between two payouts, in ticks (0.3 s = 6 ticks). */
    public static final int ANALGESIA_COOLDOWN_TICKS = 6;

    /** Stress-Induced Analgesia: the sanity fraction below which being hurt restores health. */
    public static final float ANALGESIA_SANITY = 0.60f;

    /** Stress-Induced Analgesia: what it prints - one value, the health restored. */
    public static final float[] ANALGESIA_ORDER = { ANALGESIA_HEAL };

    /**
     * Whether an untiered thought's printed values are fractions (written as percentages) or plain amounts.
     *
     * <p>Every untiered thought written before this one prints fractions - "-15%", "+15%", the four Fight or Flight
     * states - so the tooltip renders each value as {@code value * 100 + "%"}. Stress-Induced Analgesia is the
     * first one whose number is an absolute amount: <b>one health point</b>. Rendering that through the
     * percentage path would put "100%" in a sentence that promises one health, and the player would have no
     * way to tell which of the two the code actually grants.
     *
     * <p>The answer lives here, next to the values it describes, rather than as a flag the tooltip keeps for
     * itself: a new untiered thought is added in this file, and a tooltip-side list is exactly what would be
     * forgotten when the next one arrives.
     */
    public static boolean untieredValuesArePercent(Item item)
    {
        return item != ItemRegistry.THOUGHT_STRESS_INDUCED_ANALGESIA.get();
    }

    /**
     * Which stored value belongs to which printed line, per untiered thought.
     *
     * <p>Fight or Flight prints four values across <b>two</b> lines (the owner asked for the semicolon-separated
     * sentence to be split: "给战斗或逃跑文本分下行,只有分号分隔不明显"), while the other untiered thoughts print
     * one value on one line. Both are described here so {@link #untieredValuesOf} stays the single stored
     * order and the tooltip never has to guess where a line starts.
     *
     * <p>A line may hold zero values (Catharsis states its numbers as words).
     */
    public static int[] untieredLineValueCountsOf(Item item)
    {
        if (item == ItemRegistry.THOUGHT_FIGHT_OR_FLIGHT.get())
            return new int[] { 2, 2 };                 // condition line (2), then the hurt line (2)

        if (item == ItemRegistry.THOUGHT_SLEEP_DEBT.get()
                || item == ItemRegistry.THOUGHT_INSTRUMENTAL_AGGRESSION.get()
                || item == ItemRegistry.THOUGHT_STRESS_INDUCED_ANALGESIA.get())
            return new int[] { 1 };

        return new int[] { 0 };                        // Catharsis: one line, no numbers
    }

    /** How many values the given printed line of an untiered thought carries. */
    public static int untieredLineValueCount(Item item, int lineIndex)
    {
        int[] counts = untieredLineValueCountsOf(item);

        return lineIndex >= 0 && lineIndex < counts.length ? counts[lineIndex] : 0;
    }

    /** How many printed lines an untiered thought has (one for most, two for Fight or Flight). */
    public static int untieredLineCount(Item item)
    {
        return untieredLineValueCountsOf(item).length;
    }

    /**
     * The language keys of an untiered thought's lines, in order.
     *
     * <p>Most untiered thoughts have a single sentence ({@code .effect}). Fight or Flight has one per line,
     * because a line per sentence is how this project's tooltips express a line break.
     */
    public static String[] untieredLineKeysOf(Item item)
    {
        String id = item.getDescriptionId();

        if (item == ItemRegistry.THOUGHT_FIGHT_OR_FLIGHT.get())
            return new String[] { id + ".effect1", id + ".effect2" };

        return new String[] { id + ".effect" };
    }

    /** Command Hallucination: the sanity fraction below which the inner lines start appearing. */
    public static final float HALLUCINATION_SANITY = 0.50f;

    /** Psychomotor Agitation: the sanity fraction below which it grants its movement speed. */
    public static final float AGITATION_SANITY = 0.30f;

    /** Fight or Flight: the sanity fraction below which it grants either of its two states. */
    public static final float FIGHT_OR_FLIGHT_SANITY = 0.45f;

    /** Catharsis: the sanity fraction below which a hit also drains the target. */
    public static final float CATHARSIS_SANITY = 0.40f;

    /** Returned for an item with no ladder, so callers never have to null-check. */
    private static final float[] NONE = { 0f, 0f, 0f };

    /** Returned for an item with no printable values at all. */
    private static final float[] EMPTY = {};

    /**
     * The tier this item has reached, as an index into a 1 / 3 / 5 ladder.
     *
     * @param owner the player whose chain is read
     * @param item the thought item being asked about
     * @return 0, 1 or 2 for the first, second or third tier, or -1 when the item is not in the chain at all
     *         (which is what makes "not equipped" different from "equipped but still on tier zero")
     */
    public static int tierIndex(Entity owner, Item item)
    {
        if (owner == null || item == null)
            return -1;

        IThoughtChain chain = owner.getCapability(ThoughtChainProvider.CAP).orElse(null);

        if (chain == null || !chain.holds(item))
            return -1;

        int count = 0;
        ThoughtType deciding = decidingType(item);

        if (item instanceof ThoughtItem thought)
        {
            // A thought may feed several counters. By default the highest of them decides, matching the
            // tooltip; a thought that names its deciding axis (see decidingType) counts only that one.
            for (ThoughtType type : thought.types())
            {
                if (deciding != null && type != deciding)
                    continue;

                count = Math.max(count, chain.countOf(type));
            }
        }

        if (count >= TIER_THRESHOLDS[2])
            return 2;
        if (count >= TIER_THRESHOLDS[1])
            return 1;
        return count >= TIER_THRESHOLDS[0] ? 0 : -1;
    }

    /**
     * Whether this item is in the chain at all, tier or not.
     *
     * <p>Untiered thoughts still occupy a tier index of 0 while they are equipped, so {@code tierIndex >= 0}
     * answers "is it in the chain" - but that reads like a tier question. This method exists so the switches
     * that only care about presence (the duplicity effects) say what they mean.
     */
    public static boolean isEquipped(Entity owner, Item item)
    {
        return tierIndex(owner, item) >= 0;
    }

    /** The ladder of a thought item, or three zeroes for an item that has none. */
    public static float[] ladderOf(Item item)
    {
        if (item == ItemRegistry.THOUGHT_LAW_OF_THE_JUNGLE.get())
            return LAW_OF_THE_JUNGLE;
        if (item == ItemRegistry.THOUGHT_NATURE_AFFINITY.get())
            return NATURE_AFFINITY;
        if (item == ItemRegistry.THOUGHT_SOCIALIZATION.get())
            return SOCIALIZATION;
        if (item == ItemRegistry.THOUGHT_LUCID_ELATION.get())
            return LUCID_ELATION;
        if (item == ItemRegistry.THOUGHT_COMMAND_HALLUCINATION.get())
            return COMMAND_HALLUCINATION;
        if (item == ItemRegistry.THOUGHT_IDENTIFICATION_WITH_THE_AGGRESSOR.get())
            return IDENTIFICATION_WITH_THE_AGGRESSOR;
        if (item == ItemRegistry.THOUGHT_PSYCHOMOTOR_AGITATION.get())
            return PSYCHOMOTOR_AGITATION;
        if (item == ItemRegistry.THOUGHT_IRRITABILITY.get())
            return IRRITABILITY;
        if (item == ItemRegistry.THOUGHT_CONVERSION_DISORDER.get())
            return CONVERSION_DISORDER;
        if (item == ItemRegistry.THOUGHT_DEPERSONALIZATION.get())
            return DEPERSONALIZATION;
        return NONE;
    }

    /**
     * Conversion Disorder: what a player's psychic overflow is multiplied by on its way to true damage.
     *
     * <p>Returns <b>1.0</b> - the untouched baseline - rather than 0 for "grants nothing", because this
     * number multiplies the damage. A zero here would silently delete a player's whole overflow, which is a
     * far worse failure than a missing bonus, so the neutral value is stated here instead of at each call.
     *
     * <p>The owner's rule (2026-10-04) is that it also covers inner entities, which have no sanity to
     * overflow: for them the whole psychic amount is converted by {@code INNER_PSYCHIC_MULTIPLIER}, and this
     * same ratio multiplies that result. Both conversion sites therefore read this one method.
     */
    public static float overflowConversion(Entity attacker)
    {
        int index = tierIndex(attacker, ItemRegistry.THOUGHT_CONVERSION_DISORDER.get());

        return index < 0 ? 1.0f : CONVERSION_DISORDER[index];
    }

    /**
     * The value this item currently grants, read from its own ladder.
     *
     * <p>Callers pass only the item, never the numbers: the ladder is looked up here, so a hook cannot be
     * written against a stale copy of it.
     */
    public static float tierValue(Entity owner, Item item)
    {
        int index = tierIndex(owner, item);

        return index < 0 ? 0f : ladderOf(item)[index];
    }

    /** The value this item grants at a given tier index, for a tooltip that prints what the code grants. */
    public static float valueAt(Item item, int tierIndex)
    {
        float[] ladder = ladderOf(item);

        return tierIndex < 0 || tierIndex >= ladder.length ? 0f : ladder[tierIndex];
    }

    /**
     * How many thoughts of the deciding axis each tier needs, ascending: 1, 3 and 5.
     *
     * <p>Lives here rather than in the tooltip for the same reason the ladders do: the tooltip used to carry
     * its own copy of this table <b>and</b> its own "count to tier" mapping, which is how it ended up
     * highlighting a tier the effect was not on (owner: "高亮有bug"). One table, read by {@link #tierIndex}
     * and by {@link #tierThreshold}.
     */
    private static final int[] TIER_THRESHOLDS = { 1, 3, 5 };

    /** Number of tiers on every ladder. */
    public static int tierCount()
    {
        return TIER_THRESHOLDS.length;
    }

    /** The count a tier index needs; the tooltip prints this number, the mechanic compares against it. */
    public static int tierThreshold(int index)
    {
        return index >= 0 && index < TIER_THRESHOLDS.length ? TIER_THRESHOLDS[index] : 0;
    }

    /**
     * The one axis whose count decides this item's tier, or {@code null} when the highest axis does.
     *
     * <p>Depersonalization is the first <b>tiered</b> thought sitting on two axes, and the owner's rule is
     * "count chaos-restraint thoughts". Without this override the default (highest axis wins) would let five
     * madness thoughts push its recovery ceiling to the third tier - a silent behaviour nobody asked for.
     */
    private static ThoughtType decidingType(Item item)
    {
        return item == ItemRegistry.THOUGHT_DEPERSONALIZATION.get() ? ThoughtType.CHAOS_RESTRAINT : null;
    }

    /**
     * The axes whose counts decide this thought's tier, in the order the tooltip prints them.
     *
     * <p>The tooltip's header sentence names the axes it scales with, so it has to name exactly the ones
     * {@link #tierIndex} counts. Printing all of an item's axes would promise that five madness thoughts
     * deepen Depersonalization's restriction - which is the opposite of what the mechanic does (owner's rule:
     * it scales with chaos-restraint thoughts only). This is the single place that answers "which axes does
     * the ladder read", used by the mechanics through {@link #tierIndex} and by the tooltip through here.
     */
    public static java.util.List<ThoughtType> tierAxesOf(ThoughtItem thought)
    {
        ThoughtType deciding = decidingType(thought);

        return deciding == null ? thought.types() : java.util.List.of(deciding);
    }

    /**
     * Whether this item's ladder is a <b>penalty</b> (its first step is negative).
     *
     * <p>Depersonalization is the first purely negative thought in the mod, and the tooltip's shared header
     * sentence says "additional bonus from ..." - which reads as the opposite of what a penalty does. The
     * tooltip asks this to pick its wording; the numbers themselves are unchanged.
     */
    public static boolean isPenalty(Item item)
    {
        float[] ladder = ladderOf(item);

        return ladder.length > 0 && ladder[0] < 0f;
    }

    /**
     * The fraction of the sanity maximum this entity may currently recover to; {@code 1.0} when unrestricted.
     *
     * <p>Read on both sides on purpose: the server clamps recovery with it
     * ({@code SanityProcessor#addSanity} and the passive tick) and the client colours the HUD value with it,
     * so the number on screen and the maths behind it cannot disagree.
     */
    public static float recoveryCeilingFraction(Entity owner)
    {
        int index = tierIndex(owner, ItemRegistry.THOUGHT_DEPERSONALIZATION.get());

        return index < 0 ? 1.0f : 1.0f + DEPERSONALIZATION[index];
    }

    /**
     * The sanity fraction a thought needs, or 0 when its sentence states no such condition.
     *
     * <p>Read by the mechanics (which compare against it) and by the tooltip (which prints it), so the number
     * in the sentence and the number in the code cannot drift apart. Nature Affinity has no sanity condition
     * and returns 0; the tooltip also uses that to know how many placeholders its sentence takes.
     */
    public static float sanityThreshold(Item item)
    {
        if (item == ItemRegistry.THOUGHT_LAW_OF_THE_JUNGLE.get()
                || item == ItemRegistry.THOUGHT_SOCIALIZATION.get())
            return 0.5f;

        if (item == ItemRegistry.THOUGHT_LUCID_ELATION.get())
            return 0.6f;

        if (item == ItemRegistry.THOUGHT_CATHARSIS.get())
            return CATHARSIS_SANITY;

        if (item == ItemRegistry.THOUGHT_FIGHT_OR_FLIGHT.get())
            return FIGHT_OR_FLIGHT_SANITY;

        if (item == ItemRegistry.THOUGHT_PSYCHOMOTOR_AGITATION.get())
            return AGITATION_SANITY;

        // Stress-Induced Analgesia: "while your sanity is below 60%". The thought is untiered, but the
        // threshold is still read from here by the mechanic, so the sentence and the code share one number.
        if (item == ItemRegistry.THOUGHT_STRESS_INDUCED_ANALGESIA.get())
            return ANALGESIA_SANITY;

        // Command Hallucination states no sanity number of its own: its condition is the inner line being
        // on screen, and that line has already been chosen by the sanity rules. Returning a number here
        // would put a threshold into a sentence that has none.
        return 0f;
    }

    private ThoughtEffects() {}
}
