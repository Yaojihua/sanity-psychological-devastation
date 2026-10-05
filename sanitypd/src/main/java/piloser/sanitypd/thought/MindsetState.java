package piloser.sanitypd.thought;

import net.minecraft.world.entity.Entity;

/**
 * Asks whether a mindset is currently active for an entity, the one way every effect does it.
 *
 * <p>Kept in one place on purpose: a mindset's effect must be decided by exactly the rule the screen and the
 * tooltip show ("the count of its counting type has reached its threshold"), and an effect that re-derived
 * that rule on its own could drift from the icon on the thought chain screen.
 *
 * <p>Safe for any entity: the chain capability is attached to players, so every other entity answers
 * {@code false} instead of failing. That matters because some call sites (the sanity cap, passive sanity
 * sources) run for mobs too.
 */
public final class MindsetState
{
    /**
     * @param owner the entity whose chain is asked; null or a non-player means "not active"
     * @param mindset the mindset to look for
     * @return whether that mindset is active right now
     */
    public static boolean isActive(Entity owner, Mindset mindset)
    {
        if (owner == null || mindset == null)
            return false;

        IThoughtChain chain = owner.getCapability(ThoughtChainProvider.CAP).orElse(null);

        return chain != null && chain.countOf(mindset.type()) >= mindset.threshold();
    }

    private MindsetState() {}
}
