package piloser.sanitypd.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import piloser.sanitypd.thought.ThoughtType;

/**
 * A thought container's typed variant: same icon as the plain container, plus the enchantment glint, and named
 * in its thought type's own colour.
 *
 * <p>Three decisions, all of them the:
 *
 * <ul>
 *   <li><b>Icon</b>: "use the thought container's texture plus the enchantment effect" - so no new art exists
 *       and {@link #isFoil} supplies the glint. {@code isFoil} is what {@code ItemStack#hasFoil} consults, so
 *       the glint appears without enchanting the stack and without touching the stack's own name colour.</li>
 *   <li><b>Name colour</b>: "each thought type has its own colour, use that". The colour is read from
 *       {@link ThoughtType#color()} rather than typed here, so the palette has exactly one copy in the project
 *       and a re-coloured axis cannot leave these items behind. Applying the style in {@link #getName} keeps the
 *       name translatable: the language key still decides the words, the type only decides the colour.</li>
 *   <li><b>Rarity stays {@code COMMON}</b>: a rarity would repaint the name with one of vanilla's four colours
 *       and fight the type colour above.</li>
 * </ul>
 */
public class ThoughtContainerItem extends Item
{
    private final ThoughtType m_type;

    public ThoughtContainerItem(ThoughtType type, Properties properties)
    {
        super(properties);
        m_type = type;
    }

    /** The thought axis this container belongs to. */
    public ThoughtType type()
    {
        return m_type;
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return true;
    }

    @Override
    public Component getName(ItemStack stack)
    {
        return Component.translatable(this.getDescriptionId(stack))
                .withStyle(style -> style.withColor(m_type.color()));
    }
}
