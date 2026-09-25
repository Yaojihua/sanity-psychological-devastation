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
}