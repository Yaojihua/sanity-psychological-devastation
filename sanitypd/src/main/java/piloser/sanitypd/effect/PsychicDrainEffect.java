package piloser.sanitypd.effect;

import piloser.sanitypd.capability.SanityProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Psychic drain: a status effect with an icon and no particles, applied by the Psychic Drain
 * enchantment.
 *
 * <p>Icon texture: {@code assets/sanitypd/textures/mob_effect/psychic_drain.png}
 * (16x16, crimson droplet).
 *
 * <p>Each second it removes {@link #DRAIN_PER_SECOND} of the target's maximum sanity
 * (2 points per second for a player), for 10 seconds (the duration set when applied).
 *
 * <p>Inner entities have no sanity value, so this naturally has no effect on them.
 *
 * <p><b>Note</b>: unlike the other custom effects it deliberately does <b>not</b> extend
 * {@code MilkProofEffect}, because it is a hostile effect applied by enchanted weapons and the
 * player <b>should</b> be able to cure it with milk, which keeps the vanilla behaviour.
 */
public class PsychicDrainEffect extends MobEffect
{
    /** Fraction of maximum sanity drained per second. */
    public static final float DRAIN_PER_SECOND = 0.02f;
    /** Duration applied by the enchantment, in ticks: 10 seconds. */
    public static final int DURATION_TICKS = 200;

    public PsychicDrainEffect()
    {
        super(MobEffectCategory.HARMFUL, 0x8A5BC4);
    }

    /** Runs once every 20 ticks (1 second). */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier)
    {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier)
    {
        if (entity.level().isClientSide())
            return;

        entity.getCapability(SanityProvider.CAP).ifPresent(s ->
                s.setSanity(s.getSanity() - s.getMaxSanity() * DRAIN_PER_SECOND));
    }
}
