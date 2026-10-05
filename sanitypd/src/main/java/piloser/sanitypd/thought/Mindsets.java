package piloser.sanitypd.thought;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import piloser.sanitypd.SanityMod;

/**
 * Every mindset that exists, in the order the third row lays them out.
 *
 * <p>Two exist today: "Composure" (five composure thoughts) and "Madness" (five madness thoughts). The
 * remaining four types are placeholders whose mindsets are not written yet, and "Hallucination" is
 * specified to have none at all - so {@link #forType(ThoughtType)} is allowed to come back empty and
 * callers must handle that instead of assuming every type has a mindset.
 *
 * <p>The row holds at most seven slots (the owner's ceiling), which is why the row is bounded and
 * centred rather than laid out like an inventory.
 */
public final class Mindsets
{
    /** The composure mindset: turns on at 5 composure thoughts. */
    public static final Mindset COMPOSURE = new Mindset("composure", ThoughtType.COMPOSURE, 5,
            new ResourceLocation(SanityMod.MODID, "textures/gui/mindset_composure.png"));

    /**
     * The madness mindset: turns on at 5 madness thoughts.
     *
     * <p>Its type carries the same name as the mindset, which is the owner's naming and not a mistake:
     * "Madness" is the counting axis and "Mindset: Madness" is what five thoughts on that axis unlock.
     */
    public static final Mindset MADNESS = new Mindset("madness", ThoughtType.MADNESS, 5,
            new ResourceLocation(SanityMod.MODID, "textures/gui/mindset_madness.png"));

    private static final List<Mindset> ALL = List.of(COMPOSURE, MADNESS);

    /** Every mindset, in display order. Callers must not modify the returned list. */
    public static List<Mindset> all()
    {
        return ALL;
    }

    /**
     * The mindset driven by a type, if that type has one.
     *
     * @param type the counting type
     * @return the mindset, or empty for a type that unlocks none (today: hallucination)
     */
    public static Optional<Mindset> forType(ThoughtType type)
    {
        for (Mindset mindset : ALL)
        {
            if (mindset.type() == type)
                return Optional.of(mindset);
        }
        return Optional.empty();
    }

    /**
     * Looks a mindset up by its id.
     *
     * <p>Needed because the active order is stored and sent as ids, not as enum constants: an order list has
     * to survive a restart, and an id is the only part of a mindset that is stable across versions. An id the
     * code no longer knows (a mindset removed between versions) is simply dropped by the caller.
     */
    public static Optional<Mindset> byId(String id)
    {
        for (Mindset mindset : ALL)
        {
            if (mindset.id().equals(id))
                return Optional.of(mindset);
        }
        return Optional.empty();
    }

    private Mindsets() {}
}
