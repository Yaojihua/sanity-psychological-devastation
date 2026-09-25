package piloser.sanitypd.enchantment;

import piloser.sanitypd.SanityMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Enchantment registration.
 *
 * <p>All enchantments here are ordinary, non-treasure enchantments: they can be obtained from an
 * enchanting table, an anvil, villager trades and loot, i.e. {@code isDiscoverable},
 * {@code isTradeable} and {@code isTreasureOnly} all keep their default values.
 *
 * <p>Applies to <b>swords, axes, bows and crossbows</b> (a custom category, since vanilla WEAPON
 * only covers swords). A ranged hit uses the bow or crossbow in the shooter's main hand, so the
 * enchantment still applies.
 */
public final class SanityEnchantments
{
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, SanityMod.MODID);

    /** Custom enchantment category: sword / axe / bow / crossbow. */
    public static final EnchantmentCategory WEAPON_CATEGORY =
            EnchantmentCategory.create("sanitypd_weapon",
                    item -> item instanceof SwordItem
                            || item instanceof AxeItem
                            || item instanceof BowItem
                            || item instanceof CrossbowItem);

    /** Psychic deprivation: each level adds 30% of attack damage as psychic damage (max level 4 naturally, up to 255 with an anvil). */
    public static final RegistryObject<Enchantment> PSYCHIC_DEPRIVATION = ENCHANTMENTS.register(
            "psychic_deprivation",
            () -> new PsychicDeprivationEnchantment(Enchantment.Rarity.COMMON, WEAPON_CATEGORY,
                    new EquipmentSlot[]{ EquipmentSlot.MAINHAND }));

    /** Psychic drain: on hit the target loses 2% sanity per second for 10 seconds (single level). */
    public static final RegistryObject<Enchantment> PSYCHIC_DRAIN = ENCHANTMENTS.register(
            "psychic_drain",
            () -> new PsychicDrainEnchantment(Enchantment.Rarity.COMMON, WEAPON_CATEGORY,
                    new EquipmentSlot[]{ EquipmentSlot.MAINHAND }));

    /** Psychic protection: armor enchantment, 1.5 points of psychic-only protection per level, all four armor slots, survival max level 4, no exclusions. */
    public static final RegistryObject<Enchantment> PSYCHIC_PROTECTION = ENCHANTMENTS.register(
            "psychic_protection",
            () -> new PsychicProtectionEnchantment(Enchantment.Rarity.COMMON, EnchantmentCategory.ARMOR,
                    new EquipmentSlot[]{ EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                            EquipmentSlot.LEGS, EquipmentSlot.FEET }));

    private SanityEnchantments() {}
}
