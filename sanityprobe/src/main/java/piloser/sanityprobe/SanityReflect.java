package piloser.sanityprobe;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;

/**
 * Reads the main mod's registry objects through reflection. The probe deliberately has no
 * compile-time dependency on that mod: it ships as a separate project and must still start when
 * the main mod is not installed.
 *
 * <p>A mod's own class and field names are not obfuscated in production, so looking them up by
 * name is reliable. This differs from Minecraft fields such as {@code Mob#goalSelector}, which
 * must be reached through a mixin.
 */
public final class SanityReflect
{
    private SanityReflect() {}

    private static Object registryObject(String className, String fieldName)
    {
        try
        {
            Class<?> clazz = Class.forName(className);
            Object value = clazz.getField(fieldName).get(null);
            return value == null ? null : value.getClass().getMethod("get").invoke(value);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** Returns the item behind {@code piloser.sanitypd.item.ItemRegistry.<field>}, or null if unavailable. */
    public static Item item(String fieldName)
    {
        Object value = registryObject("piloser.sanitypd.item.ItemRegistry", fieldName);
        return value instanceof Item item ? item : null;
    }

    /** Returns the effect behind {@code piloser.sanitypd.effect.EffectRegistry.<field>}, or null if unavailable. */
    public static MobEffect effect(String fieldName)
    {
        Object value = registryObject("piloser.sanitypd.effect.EffectRegistry", fieldName);
        return value instanceof MobEffect effect ? effect : null;
    }
}
