package piloser.sanitypd.effect;

import piloser.sanitypd.SanityProcessor;
import piloser.sanitypd.capability.SanityProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Slow sanity recovery granted by a macaron: {@value #PER_SECOND} sanity point per second for
 * {@value #DURATION_TICKS} ticks (25 seconds), 25 points in total for a player.
 *
 * <p>The recovery is applied once per second rather than per tick, so the HUD brain arrow has an
 * unmistakable step to animate and the value that reaches the sanity bar is a whole number at every
 * step. The arrow itself is driven in {@code GuiHandler} from the presence of this effect, so the
 * player sees the small rising arrow for the whole 25 seconds (see the note there).
 *
 * <p>The icon texture is {@code assets/sanitypd/textures/mob_effect/sanity_regen.png} (18x18, the player's
 * own art). Whether it is drawn is the macaron's decision, in the {@code MobEffectInstance} it applies;
 * this class only supplies the effect. Inner entities have no sanity, so this has no effect on them.
 *
 * <p>Like the other mod effects it extends {@link MilkProofEffect} and cannot be cured by milk.
 */
public class SanityRegenEffect extends MilkProofEffect
{
    /** Sanity restored per second, in points. */
    public static final float PER_SECOND = 1.0f;
    /** Duration granted by one macaron, in ticks: 25 seconds. */
    public static final int DURATION_TICKS = 25 * 20;

    public SanityRegenEffect()
    {
        super(MobEffectCategory.BENEFICIAL, 0xE38AAE);
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

        if (entity instanceof net.minecraft.server.level.ServerPlayer player)
        {
            player.getCapability(SanityProvider.CAP)
                    .ifPresent(cap -> SanityProcessor.addSanity(cap, PER_SECOND, player));
        }
    }
}
