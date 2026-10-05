package piloser.sanitypd.passive;

import piloser.sanitypd.capability.ISanity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nonnull;

public interface IPassiveSanitySource
{
    float get(@Nonnull ServerPlayer player, @Nonnull ISanity cap, @Nonnull ResourceLocation dim);

    /**
     * Whether this source is expensive.
     *
     * <p>"Expensive" means every call runs an <b>entity scan</b> (with a line-of-sight raycast per
     * matched entity) or a <b>block-cube scan</b>. Such values change slowly, so
     * {@code SanityProcessor} throttles them (see {@code PASSIVE_SCAN_INTERVAL}) instead of
     * recomputing every tick.
     *
     * <p>Sources returning {@code false} only read attributes, effects or position and still run
     * every tick; sources such as {@code BlockStuck} that consume a per-tick flag must stay in the
     * cheap group.
     */
    default boolean isExpensive()
    {
        return false;
    }

    /**
     * Whether this source belongs to the four behaviours "Nature Affinity" boosts.
     *
     * <p>The owner's list is: wearing the garland, staying near a lit campfire (a {@link PassiveBlocks}
     * category), listening to music, and being with a pet. Only those, and only their <b>positive</b>
     * contributions: a mood stabilizer, a macaron or any other pleasant source is deliberately untouched.
     *
     * <p>A marker on the source rather than an {@code instanceof} chain inside the processor, so a future
     * pleasant source is one override here instead of a check somewhere else that can be forgotten.
     */
    default boolean isNatureSoothed()
    {
        return false;
    }
}