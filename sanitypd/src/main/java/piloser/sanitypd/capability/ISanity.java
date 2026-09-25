package piloser.sanitypd.capability;

import piloser.sanitypd.ICompoundTagSerializable;
import piloser.sanitypd.util.MathHelper;

/**
 * Sanity value, expressed in points.
 *
 * <p>Units:
 * <ul>
 *   <li>{@link #getSanity()} returns a point count: 0 = fully insane, {@link #getMaxSanity()}
 *       (100 by default) = fully sane.</li>
 *   <li>Config values stay in "percent of the cap": positive adds sanity (good for the player),
 *       negative drains it (harmful).</li>
 *   <li>{@link #getMadness()} is a derived 0..1 value kept for the existing thresholds. It is the
 *       same float the mod used internally before the point system, so {@code Blackout.THRESHOLD},
 *       {@code .5f} and {@code .6f} still compare correctly and the visuals are unchanged.</li>
 *   <li>{@link piloser.sanitypd.SanityProcessor#addSanity} also takes points.</li>
 * </ul>
 */
public interface ISanity extends ICompoundTagSerializable
{
    /** Upper bound of the sanity point pool (100 by default); change this constant to retune it. */
    float MAX_SANITY = 100f;

    /** Current sanity points, in [0, getMaxSanity()]. */
    float getSanity();

    void setSanity(float value);

    /** Upper bound of the sanity point pool. */
    default float getMaxSanity()
    {
        return MAX_SANITY;
    }

    // ---------------------------------------------------------------- psychic resistance

    /**
     * Psychic resistance: fraction of the sanity drain part of psychic damage that is negated,
     * in {@code 0.0 ~ 1.0}.
     *
     * <ul>
     *   <li>{@code 0.0} = no reduction (default)</li>
     *   <li>{@code 0.5} = only half of the sanity is drained</li>
     *   <li>{@code 1.0} = the sanity part is not drained at all</li>
     * </ul>
     *
     * <p>It only covers the sanity drain. Any overflow converted into real damage is still dealt in
     * full, because real damage is by definition unmitigable (see {@code SanityDamageTypes}). So at
     * 100% resistance a psychic hit does nothing unless sanity is already empty, in which case the
     * overflow damage is not reduced at all.
     *
     * <p>Inner entities have no sanity capability; their separate psychic resistance is handled in
     * {@code SanityCombat#INNER_PSYCHIC_RESISTANCE}.
     */
    default float getPsychicResistance()
    {
        return 0f;
    }

    /** Sets the psychic resistance ({@code 0.0 ~ 1.0}); implementations clamp out-of-range values. */
    default void setPsychicResistance(float value)
    {
    }

    // ---------------------------------------------------------------- confusion / mania timers
    // Implemented by Sanity, persisted to the save file; unit is ticks.

    /** How long sanity has been below the 25% threshold (30 s triggers "confusion"). */
    int getLowSanityTicks();

    void setLowSanityTicks(int value);

    /** How long "confusion" has lasted (30 s escalates to "mania"). */
    int getConfusionTicks();

    void setConfusionTicks(int value);

    /** How long "mania" has lasted (after 40 s it deals 1 real damage per second). */
    int getManiaTicks();

    void setManiaTicks(int value);

    /**
     * Legacy 0..1 madness value: 0 = sane, 1 = insane.
     *
     * <p>Only meant for comparisons against the existing thresholds ({@code Blackout.THRESHOLD},
     * {@code .5f}, {@code .6f}, ...). Use {@link #getSanity()} for the actual sanity value.
     */
    default float getMadness()
    {
        float max = getMaxSanity();
        return max <= 0f ? 0f : MathHelper.clampNorm(1f - getSanity() / max);
    }
}
