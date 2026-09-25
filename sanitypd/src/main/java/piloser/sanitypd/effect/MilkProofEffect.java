package piloser.sanitypd.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Base class for status effects that milk cannot cure: confusion, mania and mania immunity are part
 * of the sanity system, and a bucket of milk would defeat the whole mechanic.
 *
 * <p>Forge routes milk through {@code LivingEntity#curePotionEffects(ItemStack)}, which calls
 * {@code MobEffectInstance#isCurativeItem(milk)}; that reads the default
 * {@code IForgeMobEffect#getCurativeItems()}, which returns {@code [Items.MILK_BUCKET]}. Returning an
 * <b>empty list</b> here makes milk - and anything else using the same curative-item check - never
 * match.
 *
 * <p>Do <b>not</b> cancel {@code MobEffectEvent.Remove} instead: that event carries no source, so
 * cancelling it would also block {@code /effect clear} and clearing on death. Overriding
 * {@code getCurativeItems()} closes the curative-item path only.
 *
 * <p>Psychic drain deliberately does not extend this class: it is a hostile effect the player
 * should be able to cure with milk.
 */
public abstract class MilkProofEffect extends MobEffect
{
    protected MilkProofEffect(MobEffectCategory category, int color)
    {
        super(category, color);
    }

    /**
     * Overridden to an <b>empty list</b>, so no curative item (milk, honey bottle, ...) matches.
     *
     * @return always empty
     */
    @Override
    public List<ItemStack> getCurativeItems()
    {
        return Collections.emptyList();
    }
}
