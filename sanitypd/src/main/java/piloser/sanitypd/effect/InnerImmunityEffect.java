package piloser.sanitypd.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * Inner immunity: a beneficial effect with an icon and no particles, applied by the Gamma Mood
 * Stabilizer. Its icon ({@code assets/sanitypd/textures/mob_effect/inner_immunity.png}) reuses that
 * item's texture (16x16, unchanged); a dedicated icon can be swapped in later.
 *
 * <p>While active ({@link #DURATION_TICKS} = 60 seconds):
 * <ul>
 *   <li><b>Inner entities do not attack you</b> - checked in {@code entity/InnerEntity#tick} (drops
 *       targets carrying the effect, covering all vanilla/AI targeting) and in
 *       {@code entity/goal/TargetInsanePlayerGoal} (skips such players when picking a target);</li>
 *   <li><b>A screaming crawler does not self-destruct</b> - checked in
 *       {@code entity/goal/CrawlerSwellGoal} (a player with the effect counts as an invalid target,
 *       so the fuse rewinds).</li>
 * </ul>
 *
 * <p>Extends {@link MilkProofEffect}: it cannot be cured by milk, and its 2 minute cooldown would
 * make that a wasted use.
 */
public class InnerImmunityEffect extends MilkProofEffect
{
    /** Duration in ticks: 60 seconds (the duration granted by the Gamma Mood Stabilizer). */
    public static final int DURATION_TICKS = 60 * 20;

    public InnerImmunityEffect()
    {
        // BENEFICIAL; category color #940096 matches the Gamma item texture's main color
        super(MobEffectCategory.BENEFICIAL, 0x940096);
    }
}
