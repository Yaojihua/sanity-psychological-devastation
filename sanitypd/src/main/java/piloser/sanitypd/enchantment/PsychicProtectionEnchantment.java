package piloser.sanitypd.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import piloser.sanitypd.damage.SanityDamageTypes;

/**
 * Psychic Protection, modeled on the vanilla {@code ProtectionEnchantment}.
 *
 * <p>Points per level {@value #POINTS_PER_LEVEL}, using the vanilla reduction formula
 * {@code points / 25}, capped at <b>80%</b> entity-side in
 * {@link piloser.sanitypd.combat.SanityCombat} (a full set of level IV gives 16 points, i.e. 64%, so
 * the cap is not reached in practice). Natural max level <b>4</b>, while the anvil can still merge up
 * to 255 as in {@code PsychicDeprivationEnchantment}. It applies to all four armor pieces, is
 * obtained like a normal enchantment (enchanting table, anvil, trading, loot) and is
 * <b>not exclusive</b>: it coexists with vanilla Protection, Fire Protection and the rest, which
 * exclude each other.
 *
 * <h2>Why it reports points but the reduction is applied elsewhere</h2>
 * Psychic damage is meant to be reduced only by Psychic Protection and by shields. Vanilla
 * {@code EnchantmentHelper#getDamageProtection} sums the protection points of every enchantment on
 * the entity, so as long as psychic damage went through the vanilla enchantment reduction path,
 * plain Protection IV would also shield against it. This mod therefore adds
 * {@code sanitypd:psychic} to the {@code bypasses_enchantments} tag (the same approach already used
 * for {@code bypasses_armor}), which zeroes the vanilla enchantment reduction for psychic damage.
 * This enchantment still reports its points with vanilla semantics; the actual reduction is applied
 * by {@link piloser.sanitypd.combat.SanityCombat} on the sanity drain using the vanilla formula.
 *
 * <p>It does not extend the vanilla {@code ProtectionEnchantment} because that class's {@code Type}
 * enum only covers ALL, FIRE, FALL, EXPLOSION and PROJECTILE and cannot recognize custom damage
 * types.
 */
public class PsychicProtectionEnchantment extends Enchantment
{
    /**
     * Protection points granted per level.
     *
     * <p>The value matches vanilla Protection exactly (1 point per level, reduction
     * {@code points / 25}); the differences are that it only applies to psychic damage and that it
     * covers all four armor slots without being exclusive.
     */
    public static final float POINTS_PER_LEVEL = 1f;

    public PsychicProtectionEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots)
    {
        super(rarity, category, slots);
    }

    /**
     * Reports this enchantment's protection points for <b>psychic damage</b> and 0 for everything
     * else: it is specialized, not a general damage reduction.
     *
     * <p>Structurally the same as {@code ProtectionEnchantment#getDamageProtection}: it rules out
     * {@code BYPASSES_INVULNERABILITY} (that damage already ignores all protection) and then
     * returns the points. The extra requirement here is that the damage is psychic, which is the
     * difference between this enchantment and vanilla Protection.
     */
    @Override
    public int getDamageProtection(int level, DamageSource source)
    {
        if (source == null || !SanityDamageTypes.isPsychic(source))
            return 0;

        return Math.round(level * POINTS_PER_LEVEL);
    }

    /** Natural max level 4, matching vanilla Protection. */
    @Override
    public int getMaxLevel()
    {
        return 4;
    }

    /**
     * Enchanting table cost: the same shape as vanilla Protection ({@code Type.ALL}):
     * {@code getMinCost = 1 + (level - 1) * 11} and {@code getMaxCost = getMinCost + 11}.
     */
    @Override
    public int getMinCost(int level)
    {
        return 1 + (level - 1) * 11;
    }

    @Override
    public int getMaxCost(int level)
    {
        return getMinCost(level) + 11;
    }

    /**
     * {@code checkCompatibility} is deliberately not overridden, because this enchantment is
     * intended to be non-exclusive.
     *
     * <p>For comparison, vanilla's four protection enchantments exclude each other through the
     * {@code exclusive_set/armor} tag. This enchantment does not carry that tag and does not
     * override this method, so it can coexist with Protection, Fire Protection, Blast Protection and
     * Projectile Protection.
     */
}
