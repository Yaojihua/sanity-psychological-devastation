package piloser.sanitypd.effect;

import piloser.sanitypd.SanityMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Status effect registration. */
public final class EffectRegistry
{
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, SanityMod.MODID);

    /** Confusion: first stage of low sanity (attack/movement -35%, 25% chance to deal no damage). */
    public static final RegistryObject<MobEffect> CONFUSION = EFFECTS.register("confusion", ConfusionEffect::new);

    /** Mania: upgrades from confusion after 30 seconds without recovery (attack +50%, armor +20%, movement/attack speed +35%). */
    public static final RegistryObject<MobEffect> MANIA = EFFECTS.register("mania", ManiaEffect::new);

    /**
     * Psychic drain: shows an icon (crimson droplet), applied by the Psychic Drain enchantment,
     * draining 2% sanity per second for 10 seconds.
     *
     * <p>It is the only effect in this mod that <b>can be cured by milk</b>, since it is a hostile
     * effect the player should be able to remove.
     */
    public static final RegistryObject<MobEffect> PSYCHIC_DRAIN = EFFECTS.register("psychic_drain", PsychicDrainEffect::new);

    /**
     * Mania immunity: shows an icon (green shield with a heart). Killing an inner entity grants the
     * killer 10 seconds of immunity to mania damage (see {@link ManiaImmunityEffect}).
     *
     * <p>{@code confusion}, {@code mania} and {@code mania_immunity} all extend
     * {@link MilkProofEffect} and <b>cannot be cured by milk</b>.
     */
    public static final RegistryObject<MobEffect> MANIA_IMMUNITY = EFFECTS.register("mania_immunity", ManiaImmunityEffect::new);

    /**
     * Inner immunity: shows an icon (the Gamma Mood Stabilizer item texture, reused unchanged).
     *
     * <p>Applied by the Gamma Mood Stabilizer for 1 minute. While active, inner entities do not
     * attack the player and screaming crawlers do not self-destruct (the checks are in
     * {@code InnerEntity#tick}, {@code TargetInsanePlayerGoal} and {@code CrawlerSwellGoal}).
     *
     * <p>Like the other custom effects it extends {@link MilkProofEffect} and <b>cannot be cured by
     * milk</b>.
     */
    public static final RegistryObject<MobEffect> INNER_IMMUNITY = EFFECTS.register("inner_immunity", InnerImmunityEffect::new);

    private EffectRegistry() {}
}
