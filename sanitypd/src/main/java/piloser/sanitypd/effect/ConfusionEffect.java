package piloser.sanitypd.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Confusion (renamed from the former "negative" effect).
 *
 * <ul>
 *   <li>Attack damage -35% ({@link #ATTACK_MULTIPLIER})</li>
 *   <li>Movement speed -35% ({@link #SPEED_MULTIPLIER})</li>
 *   <li>25% chance for an attack to deal no damage at all ({@link #FAIL_CHANCE}, rolled in the
 *       combat logic)</li>
 * </ul>
 *
 * <p>Entry conditions and progression are in {@code SanityCombat.maintainStates}: sanity below 25%
 * of the maximum applies confusion once the player has stayed under the threshold for 30 seconds,
 * and 30 further seconds without recovering to 50% upgrade it to mania. Inner entities are immune.
 *
 * <p>Extends {@link MilkProofEffect}: it <b>cannot be cured by milk</b>, since it is part of the
 * sanity system.
 */
public class ConfusionEffect extends MilkProofEffect
{
    /** Attack -35% (MULTIPLY_TOTAL, applied to the final value). */
    public static final double ATTACK_MULTIPLIER = -0.35;
    /** Movement speed -35%. */
    public static final double SPEED_MULTIPLIER = -0.35;
    /** Chance for an attack to fail. */
    public static final float FAIL_CHANCE = 0.25f;

    /** In MC 1.20.1 an attribute modifier id must be a valid UUID string (parsed with UUID.fromString). */
    private static final String ATTACK_MODIFIER_ID = "2c1a7a54-0001-4a5e-9a11-5c3f2b8d0001";
    private static final String SPEED_MODIFIER_ID = "2c1a7a54-0002-4a5e-9a11-5c3f2b8d0002";

    public ConfusionEffect()
    {
        super(MobEffectCategory.HARMFUL, 0x6C7A96);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_ID, ATTACK_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, SPEED_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
