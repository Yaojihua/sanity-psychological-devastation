package piloser.sanitypd.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Psychic Deprivation.
 *
 * <p>On dealing damage, additionally applies psychic damage equal to AttackerDamage * 30% * level
 * (level 1 +30%, level 2 +60%, level 3 +90%, ...). That psychic damage is independent of the
 * weapon's own damage, but it grows with Sharpness (more attacker damage) and with critical hits
 * (higher damage for that hit).
 *
 * <p>Natural sources (enchanting table, enchanted books, loot, villager trades) stop at
 * {@link #NATURAL_MAX_LEVEL}: those sources roll a random level in {@code 1~getMaxLevel()}, so
 * returning 4 here caps them hard. The anvil goes up to {@link #MAX_LEVEL}, because anvil merging
 * reads {@code getMaxLevel()} (from {@code AnvilMenu#createResult}, which runs after Forge's
 * {@code AnvilUpdateEvent}) to decide whether a level can be raised, so {@link #getMaxLevel()} is
 * temporarily widened to 255 for the duration of the anvil computation and two level-4 books can be
 * merged all the way up.
 *
 * <p>The window is a {@link ThreadLocal} that is only active on the server thread during the anvil
 * computation and is cleared at the end of the server tick, so it cannot affect loot generation
 * (which usually runs on a worker thread) or other random enchanting.
 */
public class PsychicDeprivationEnchantment extends Enchantment
{
    /** Highest level obtainable from natural sources (enchanting table, books, loot, trades). */
    public static final int NATURAL_MAX_LEVEL = 4;
    /** Highest level an anvil can merge up to. */
    public static final int MAX_LEVEL = 255;
    /** Extra psychic damage per level, as a fraction of this hit's attacker damage. */
    public static final float PSYCHIC_PER_LEVEL = 0.30f;

    /** Anvil computation window; thread local so it cannot leak into other random processes. */
    private static final ThreadLocal<Boolean> ANVIL_WINDOW = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public PsychicDeprivationEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots)
    {
        super(rarity, category, slots);
    }

    /** Opens the anvil window (triggered by AnvilUpdateEvent). */
    public static void openAnvilWindow()
    {
        ANVIL_WINDOW.set(Boolean.TRUE);
    }

    /** Closes the anvil window (at the end of the server tick). */
    public static void closeAnvilWindow()
    {
        ANVIL_WINDOW.set(Boolean.FALSE);
    }

    @Override
    public int getMinLevel()
    {
        return 1;
    }

    @Override
    public int getMaxLevel()
    {
        return ANVIL_WINDOW.get() ? MAX_LEVEL : NATURAL_MAX_LEVEL;
    }

    /** Enchanting table cost window: level 4 needs about 25 enchanting power (30 bookshelves). */
    @Override
    public int getMinCost(int level)
    {
        return 1 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level)
    {
        return getMinCost(level) + 15;
    }
}
