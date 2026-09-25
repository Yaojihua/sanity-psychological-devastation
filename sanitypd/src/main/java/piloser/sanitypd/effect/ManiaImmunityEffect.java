package piloser.sanitypd.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * Mania immunity: a status effect with an icon and no particles.
 *
 * <p>Icon texture: {@code assets/sanitypd/textures/mob_effect/mania_immunity.png}
 * (16x16, green shield with a heart).
 *
 * <p>Applied to the killer by the loot logic ({@code loot.InnerLoot}) after an inner entity dies,
 * for {@link #DURATION_TICKS} (10 seconds). It blocks the 1 point per second of real damage dealt
 * by mania; that check lives in {@code SanityCombat.maintainStates}.
 *
 * <p>Only the <b>damage</b> is blocked. The mania state itself (timer, screen-edge warning,
 * whispers) keeps running, so the damage resumes immediately once the immunity ends if sanity is
 * still below 50%.
 *
 * <p>Extends {@link MilkProofEffect}: it <b>cannot be cured by milk</b>, so the reward for killing
 * an inner entity is not washed away by a bucket of milk.
 */
public class ManiaImmunityEffect extends MilkProofEffect
{
    /** Duration in ticks: 10 seconds. */
    public static final int DURATION_TICKS = 200;

    public ManiaImmunityEffect()
    {
        // BENEFICIAL; category color #2E6B4F matches the icon's main color
        super(MobEffectCategory.BENEFICIAL, 0x2E6B4F);
    }
}
