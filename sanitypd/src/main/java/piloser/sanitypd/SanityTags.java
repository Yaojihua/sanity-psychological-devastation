package piloser.sanitypd;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Entity tags used by this mod.
 *
 * <p>{@link #INNER_ENTITIES}: these entities have <b>no sanity value</b> and ignore every sanity
 * mechanic. They are immune to the confusion effect, but any psychic damage they take is
 * unconditionally converted into 2.5x real damage.
 *
 * <p>The tag contents live in {@code data/sanitypd/tags/entity_types/inner_entities.json}, so
 * add-ons and data packs can add their own entities without touching code.
 */
public final class SanityTags
{
    /** Inner entities (trackers, stalkers and similar). */
    public static final TagKey<EntityType<?>> INNER_ENTITIES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(SanityMod.MODID, "inner_entities"));

    private SanityTags() {}

    /** Whether the entity is an inner entity (no sanity, confusion-immune, psychic damage becomes 2.5x real damage). */
    public static boolean isInnerEntity(Entity entity)
    {
        return entity != null && entity.getType().is(INNER_ENTITIES);
    }
}
