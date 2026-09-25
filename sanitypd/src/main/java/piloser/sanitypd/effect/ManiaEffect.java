package piloser.sanitypd.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Mania: upgrades from confusion when sanity has not recovered to 50% for 30 seconds.
 *
 * <ul>
 *   <li>Attack damage +50% ({@link #ATTACK_MULTIPLIER})</li>
 *   <li>Armor +20% ({@link #ARMOR_MULTIPLIER})</li>
 *   <li>Movement speed +35% ({@link #SPEED_MULTIPLIER})</li>
 *   <li>Attack speed +35% ({@link #ATTACK_SPEED_MULTIPLIER})</li>
 * </ul>
 *
 * <p>It ends as soon as sanity is back above 50% of the maximum. After 40 seconds it deals 1 point
 * of real damage per second until sanity recovers above 50% or the entity dies (the damage logic is
 * in {@code SanityCombat.maintainStates}).
 *
 * <p>Extends {@link MilkProofEffect}: it <b>cannot be cured by milk</b>, since it is part of the
 * sanity system.
 */
public class ManiaEffect extends MilkProofEffect
{
    /** Attack +50%. */
    public static final double ATTACK_MULTIPLIER = 0.50;
    /** Armor +20%. */
    public static final double ARMOR_MULTIPLIER = 0.20;
    /** Movement speed +35%. */
    public static final double SPEED_MULTIPLIER = 0.35;
    /** Attack speed +35%. */
    public static final double ATTACK_SPEED_MULTIPLIER = 0.35;

    /** In MC 1.20.1 a modifier id must be a valid UUID string. */
    private static final String ATTACK_MODIFIER_ID = "2c1a7a54-0003-4a5e-9a11-5c3f2b8d0003";
    private static final String ARMOR_MODIFIER_ID = "2c1a7a54-0004-4a5e-9a11-5c3f2b8d0004";
    private static final String SPEED_MODIFIER_ID = "2c1a7a54-0005-4a5e-9a11-5c3f2b8d0005";
    private static final String ATTACK_SPEED_MODIFIER_ID = "2c1a7a54-0006-4a5e-9a11-5c3f2b8d0006";

    public ManiaEffect()
    {
        super(MobEffectCategory.BENEFICIAL, 0xD94F3D);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_ID, ATTACK_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ARMOR, ARMOR_MODIFIER_ID, ARMOR_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, SPEED_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_ID, ATTACK_SPEED_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
