package piloser.sanitypd.thought;

import java.util.UUID;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Attribute modifiers the composure mindset grants, kept in step with the conditions that grant them.
 *
 * <h2>Why modifiers and not a stat of our own</h2>
 * Attack damage, movement speed and armour already flow through vanilla attributes: every other mod, every
 * tooltip and the client's own movement prediction read them there. Writing the bonus anywhere else would make
 * the value invisible to all of that, and the client would disagree with the server about how fast the player
 * moves.
 *
 * <h2>Transient, and re-derived every tick</h2>
 * Modifiers are added as <b>transient</b> (never written to the player's NBT) and removed the moment the
 * condition stops holding, so a player who dies mid-buff cannot carry a permanent +20% speed in his save. The
 * refresh is idempotent: it only attaches what is missing and detaches what is no longer wanted, which is what
 * makes calling it every tick safe.
 *
 * <h2>Fixed ids</h2>
 * The ids are constants, not {@code UUID.randomUUID()}: a random id would look "already applied" on every tick
 * and stack a fresh modifier each time, which is the classic way this ends up at +1000% speed.
 */
public final class MindsetAttributes
{
    /** Composure: +15% attack damage. */
    private static final UUID ATTACK_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e01");
    /** Composure: +20% movement speed. */
    private static final UUID SPEED_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e02");
    /** Composure while the garland is worn: +1 armour. */
    private static final UUID ARMOR_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e03");
    /** Lucid Elation: +25 / 35 / 45% attack damage while sanity is above 60%. */
    private static final UUID LUCID_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e04");
    /** Madness: +20% attack damage below 40% sanity. */
    private static final UUID MADNESS_ATTACK_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e05");
    /** Madness: +20% armour below 40% sanity. */
    private static final UUID MADNESS_ARMOR_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e06");
    /** Command Hallucination: +15 / 25 / 35% attack damage while an inner line is on screen. */
    private static final UUID HALLUCINATION_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e07");
    /** Identification with the Aggressor: +15 / 30 / 40% attack damage inside the six second window. */
    private static final UUID AGGRESSOR_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e08");
    /** Fight or Flight: both states write this one attack modifier. */
    private static final UUID FIGHT_ATTACK_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e09");
    /** Fight or Flight: both states write this one movement modifier. */
    private static final UUID FIGHT_SPEED_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e0a");
    /** Psychomotor Agitation: +10 / 20 / 30% movement speed. */
    private static final UUID AGITATION_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e0b");
    /** Instrumental Aggression: +15% attack damage while the thought is in the chain. */
    private static final UUID INSTRUMENTAL_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e0c");
    /** Irritability: +10 / 20 / 30% attack damage on top of the mania effect's own bonus. */
    private static final UUID IRRITABILITY_ID = UUID.fromString("6f1c9f2e-2b1a-4a55-9c3e-1a2b3c4d5e0d");

    /**
     * The name each modifier carries, in one place.
     *
     * <p>Every modifier used to be named {@code "sanitypd:composure"}, which made a developer's log useless:
     * attack, movement, armour and all six thought bonuses printed as the same word, so nothing could be told
     * apart (the probe showed {@code {composure=0.550x,composure=0.300x}} and the reader had to guess).
     *
     * <p>The name is the modifier's identity in every diagnostic - the attribute tooltip, a debug screen and
     * the probe - so it now says both where the bonus comes from and which attribute it moves. Nothing
     * gameplay-facing reads it: {@link #set} matches modifiers by {@code UUID}, and the client only needs the
     * name to be stable within a session.
     */
    private static String name(String source, String effect)
    {
        return "sanitypd:" + source + "/" + effect;
    }

    private static final String COMPOSURE_ATTACK = name("composure", "attack");
    private static final String COMPOSURE_SPEED = name("composure", "speed");
    private static final String COMPOSURE_ARMOR = name("composure", "armor");
    private static final String LUCID_ATTACK = name("lucid-elation", "attack");
    private static final String MADNESS_ATTACK_NAME = name("madness", "attack");
    private static final String MADNESS_ARMOR_NAME = name("madness", "armor");
    private static final String HALLUCINATION_ATTACK = name("hallucination", "attack");
    private static final String AGGRESSOR_ATTACK = name("aggressor", "attack");
    private static final String FIGHT_ATTACK = name("fight-or-flight", "attack");
    private static final String FIGHT_SPEED = name("fight-or-flight", "speed");
    private static final String AGITATION_SPEED = name("agitation", "speed");
    private static final String INSTRUMENTAL_ATTACK = name("instrumental", "attack");
    private static final String IRRITABILITY_ATTACK = name("irritability", "attack");

    // ---------------------------------------------------------------- madness mindset
    // The numbers of the madness mindset and of the thoughts that share it. They are read both by the
    // mechanics and by the tooltip, so the screen cannot promise a figure the code does not apply.

    /** Madness: the sanity fraction below which it starts draining and pays out its bonuses. */
    public static final float MADNESS_ABOVE = 0.50f;

    /** Madness: the lower sanity fraction that gates the resistance, attack and armour bonuses. */
    public static final float MADNESS_LOW = 0.40f;

    /** Madness: the sanity points the conditional bonuses are worth. */
    public static final float MADNESS_RESIST = 0.20f;
    public static final float MADNESS_ATTACK = 0.20f;
    public static final float MADNESS_ARMOR = 0.20f;

    /** Madness: half a heart equals one health point, and the floor is four hearts. */
    public static final float MADNESS_DAMAGE_FLOOR = 8.0f;

    /** Identification with the Aggressor: how long the window stays open after being hurt, in ticks. */
    public static final int AGGRESSOR_WINDOW_TICKS = 120;

    /** Fight or Flight: how long after being hurt the second state is in force, in ticks. */
    public static final int FIGHT_OR_FLIGHT_WINDOW_TICKS = 100;

    /**
     * Brings the player's attributes in line with the composure mindset.
     *
     * @param player the player to adjust
     * @param composure whether the composure mindset is active at all
     * @param aboveEighty whether sanity is above 80% of the cap (the attack and speed conditions)
     * @param garlandWorn whether the garland is on the player's head (the armour condition)
     * @param lucidElation attack bonus from the Lucid Elation thought (0 when it grants nothing right now)
     */
    public static void refresh(Player player, boolean composure, boolean aboveEighty, boolean garlandWorn,
                               float lucidElation)
    {
        boolean high = composure && aboveEighty;
        set(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, COMPOSURE_ATTACK, 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, high);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, COMPOSURE_SPEED, 0.20, AttributeModifier.Operation.MULTIPLY_TOTAL, high);
        // Flat +1 armour, not a percentage: the owner's wording is "+1 armour".
        set(player, Attributes.ARMOR, ARMOR_ID, COMPOSURE_ARMOR, 1.0, AttributeModifier.Operation.ADDITION,
                composure && garlandWorn);
        // A second attack modifier, from a different source: vanilla sums every MULTIPLY_TOTAL on one attribute
        // into a single multiplier, so +15% here and +45% there mean +60%, not +66.75%.
        set(player, Attributes.ATTACK_DAMAGE, LUCID_ID, LUCID_ATTACK, lucidElation, AttributeModifier.Operation.MULTIPLY_TOTAL,
                lucidElation > 0f);
    }

    /**
     * The attributes the madness mindset and its thoughts grant.
     *
     * <p>Kept separate from {@link #refresh} so the composure path is not touched by this batch: a single
     * method taking nine flags is how one mindset's condition ends up gating the other's modifier.
     *
     * @param player          the player to adjust
     * @param madness         whether the madness mindset is active at all
     * @param madnessLow      whether sanity is below {@link #MADNESS_LOW} (the bonus condition)
     * @param hallucination   attack bonus from Command Hallucination, 0 when it grants nothing right now
     * @param aggressor       attack bonus from Identification with the Aggressor, 0 outside its window
     * @param fightAttack     attack bonus from Fight or Flight, signed: negative in the "hurt" state
     * @param fightSpeed      movement bonus from Fight or Flight, signed: negative in the "untouched" state
     * @param agitation       movement bonus from Psychomotor Agitation, 0 when its condition does not hold
     * @param instrumental    attack bonus from Instrumental Aggression, 0 when the thought is not in the chain
     * @param irritability    attack bonus from Irritability, 0 unless the mania effect is on the player
     */
    public static void refreshMadness(Player player, boolean madness, boolean madnessLow, float hallucination,
                                      float aggressor, float fightAttack, float fightSpeed, float agitation,
                                      float instrumental, float irritability)
    {
        boolean bonus = madness && madnessLow;
        set(player, Attributes.ATTACK_DAMAGE, MADNESS_ATTACK_ID, MADNESS_ATTACK_NAME, MADNESS_ATTACK,
                AttributeModifier.Operation.MULTIPLY_TOTAL, bonus);
        set(player, Attributes.ARMOR, MADNESS_ARMOR_ID, MADNESS_ARMOR_NAME, MADNESS_ARMOR, AttributeModifier.Operation.MULTIPLY_TOTAL,
                bonus);

        // The three thought-sourced bonuses ride on their own item being in the chain, which is why they are
        // passed in already resolved: a value of 0 is the "not granting anything" case and removes the
        // modifier, so leaving the chain mid-window cannot leave a bonus behind.
        set(player, Attributes.ATTACK_DAMAGE, HALLUCINATION_ID, HALLUCINATION_ATTACK, hallucination,
                AttributeModifier.Operation.MULTIPLY_TOTAL, hallucination != 0f);
        set(player, Attributes.ATTACK_DAMAGE, AGGRESSOR_ID, AGGRESSOR_ATTACK, aggressor,
                AttributeModifier.Operation.MULTIPLY_TOTAL, aggressor != 0f);
        set(player, Attributes.ATTACK_DAMAGE, FIGHT_ATTACK_ID, FIGHT_ATTACK, fightAttack,
                AttributeModifier.Operation.MULTIPLY_TOTAL, fightAttack != 0f);
        set(player, Attributes.MOVEMENT_SPEED, FIGHT_SPEED_ID, FIGHT_SPEED, fightSpeed,
                AttributeModifier.Operation.MULTIPLY_TOTAL, fightSpeed != 0f);
        set(player, Attributes.MOVEMENT_SPEED, AGITATION_ID, AGITATION_SPEED, agitation,
                AttributeModifier.Operation.MULTIPLY_TOTAL, agitation != 0f);
        // Instrumental Aggression is madness-typed but not madness-mindset-gated: it pays out whenever it
        // sits in the chain, which is why it is passed in already resolved like the other item-sourced ones.
        set(player, Attributes.ATTACK_DAMAGE, INSTRUMENTAL_ID, INSTRUMENTAL_ATTACK, instrumental,
                AttributeModifier.Operation.MULTIPLY_TOTAL, instrumental != 0f);

        // Irritability amplifies the mania effect's own +50%, so the caller resolves it to 0 whenever mania
        // is not on the player. Both multipliers are MULTIPLY_TOTAL and vanilla sums that operation before
        // multiplying the base - which is exactly the owner's "add it to what mania already gives" (50% + 20%
        // = 70%, not 50% x 1.2).
        set(player, Attributes.ATTACK_DAMAGE, IRRITABILITY_ID, IRRITABILITY_ATTACK, irritability,
                AttributeModifier.Operation.MULTIPLY_TOTAL, irritability != 0f);
    }

    private static void set(Player player, Attribute attribute, UUID id, String name, double amount,
                            AttributeModifier.Operation operation, boolean wanted)
    {
        AttributeInstance instance = player.getAttribute(attribute);

        if (instance == null)
            return;

        AttributeModifier existing = instance.getModifier(id);

        if (wanted)
        {
            if (existing == null)
            {
                instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
            }
            else if (existing.getAmount() != amount)
            {
                // The tier moved. Replace rather than add: keeping the old modifier would leave the previous
                // tier's number in force forever, and adding a second one would stack both.
                instance.removeModifier(existing);
                instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
            }
        }
        else if (existing != null)
        {
            instance.removeModifier(existing);
        }
    }

    private MindsetAttributes() {}
}
