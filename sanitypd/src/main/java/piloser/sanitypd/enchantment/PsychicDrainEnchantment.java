package piloser.sanitypd.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Psychic Drain.
 *
 * <p>On hit, the target loses <b>2% of its maximum sanity per second for 10 seconds</b>
 * (2 points per second for a player, 20 points in total). Hitting again refreshes the duration.
 *
 * <p>Single level only; it is obtainable normally from an enchanting table, enchanted books, loot
 * and trading. The actual drain is handled by the hidden {@code sanitypd:psychic_drain} status
 * effect (see PsychicDrainEffect).
 */
public class PsychicDrainEnchantment extends Enchantment
{
    public PsychicDrainEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots)
    {
        super(rarity, category, slots);
    }

    @Override
    public int getMinLevel()
    {
        return 1;
    }

    /** Single level only. */
    @Override
    public int getMaxLevel()
    {
        return 1;
    }

    @Override
    public int getMinCost(int level)
    {
        return 1;
    }

    @Override
    public int getMaxCost(int level)
    {
        return 40;
    }
}
