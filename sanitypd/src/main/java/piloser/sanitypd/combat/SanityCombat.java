package piloser.sanitypd.combat;

import piloser.sanitypd.SanityTags;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.damage.SanityDamageTypes;
import piloser.sanitypd.effect.ConfusionEffect;
import piloser.sanitypd.effect.EffectRegistry;
import piloser.sanitypd.effect.PsychicDrainEffect;
import piloser.sanitypd.enchantment.PsychicDeprivationEnchantment;
import piloser.sanitypd.enchantment.PsychicProtectionEnchantment;
import piloser.sanitypd.enchantment.SanityEnchantments;
import piloser.sanitypd.event.OverflowDamageQueue;
import piloser.sanitypd.item.IShadowWeapon;
import piloser.sanitypd.item.ShadowRefinement;
import piloser.sanitypd.util.MathHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Interaction rules between sanity and combat (all of them run on the server).
 *
 * <ol>
 *   <li><b>Psychic damage</b>: deals no health damage; it drains an equal amount of sanity instead, and the
 *       overflow past zero sanity is converted into an equal amount of true damage. Inner entities
 *       (tag {@code sanitypd:inner_entities}) have no sanity, so psychic damage on them is unconditionally
 *       converted into {@link #INNER_PSYCHIC_MULTIPLIER} times as much true damage.</li>
 *   <li><b>Attacking a low-sanity target</b>: while the target's sanity is below {@link #FEED_THRESHOLD}
 *       of its maximum, the attacker regains sanity equal to the damage actually dealt (players included).</li>
 *   <li><b>Confusion / mania</b>: very low sanity first causes "confusion", which escalates to "mania" if it
 *       is not recovered within 30 seconds (see {@link #maintainStates}); recovering sanity removes either
 *       state. Inner entities are immune and players are unaffected (players have their own HUD and
 *       post-processing presentation).</li>
 * </ol>
 */
public final class SanityCombat
{
    /** Psychic damage taken by an inner entity -> 2.5x as much true damage. */
    public static final float INNER_PSYCHIC_MULTIPLIER = 2.5f;

    // ---------------------------------------------------------------- inner entity "damage bonus"
    /**
     * Extra psychic damage ratio when an inner entity attacks a target with very low sanity.
     *
     * <p>Spec: an inner entity attacking an entity at or below 10% sanity deals an extra 10% psychic
     * damage, and 25% at or below zero sanity. The baseline is the existing damage, so the final result
     * is a multiplier of 1.1 / 1.25.
     */
    public static final float INNER_LOW_SANITY_BONUS = .10f;
    public static final float INNER_ZERO_SANITY_BONUS = .25f;
    /** Threshold (as a fraction of the target's own sanity maximum) that triggers the low-sanity bonus. */
    public static final float INNER_LOW_SANITY_RATIO = .10f;
    /**
     * Threshold (as a fraction of the target's own sanity maximum) that triggers the zero-sanity bonus.
     *
     * <p>The spec says "at or below 0", but sanity is continuous and practically never exactly 0, so
     * <b>1% of the maximum</b> is used as the test for "effectively zero": it covers a true zero as well as
     * a target beaten down to a fraction of a point.
     */
    public static final float INNER_ZERO_SANITY_RATIO = .01f;
    /** The attacker regains sanity when the target's sanity is below this ratio. */
    public static final float FEED_THRESHOLD = 0.5f;

    // ---------------------------------------------------------------- psychic protection
    /**
     * Maximum share of psychic damage the psychic protection enchantment can remove.
     *
     * <p>Note: at 1 point per level, a full set of Protection IV gives only 16 points, that is
     * {@code 16/25 = 64%}, which never reaches this 80% cap. The cap is kept as a ceiling for future
     * higher levels or additional sources.
     *
     * <p>The vanilla formula ({@code points/25}) is used rather than a direct percentage so that the point
     * semantics, the formula and the four-armour stacking all follow vanilla Protection; the only changes
     * are the points per level and clamping the result to this mod's own {@code MAX_ENCHANTMENT_REDUCTION}.
     */
    public static final float MAX_ENCHANTMENT_REDUCTION = 0.80f;

    /** The four armour slots, in fixed order so the total never depends on any Map iteration order. */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ---------------------------------------------------------------- confusion / mania state machine
    /** Sanity below this ratio of the maximum -> enter "confusion". */
    public static final float CONFUSION_ENTER = 0.25f;
    /** Sanity back at this ratio of the maximum -> clear "confusion / mania". */
    public static final float RECOVER = 0.50f;
    /** Every mob must stay below the threshold this long before entering "confusion": 30 seconds. */
    public static final int CONFUSION_DELAY_TICKS = 600;
    /** "Confusion" lasting this long without recovering to 50% -> escalate to "mania": 30 seconds. */
    public static final int CONFUSION_TO_MANIA_TICKS = 600;
    /** Grace period of "mania", during which no damage is dealt: 40 seconds. */
    public static final int MANIA_GRACE_TICKS = 800;
    /** True damage taken per second after the grace period. */
    public static final float MANIA_TRUE_DAMAGE_PER_SECOND = 1.0f;
    /** State maintenance interval: once every 20 ticks (1 second) to spare the CPU. */
    private static final int MAINTAIN_INTERVAL = 20;
    /** Display duration of the two states (refreshed continuously, so a little longer than the timers). */
    private static final int CONFUSION_EFFECT_TICKS = 700;
    private static final int MANIA_EFFECT_TICKS = 1200;

    private SanityCombat() {}

    // ------------------------------------------------------------------ psychic damage
    /**
     * Handles an incoming damage event:
     * it first rolls the "confusion" attack-failure chance, then converts psychic damage into
     * "drain sanity + convert the overflow to true damage".
     */
    public static void onLivingHurt(LivingHurtEvent event)
    {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide())
            return;

        float amount = event.getAmount();
        if (amount <= 0.0f)
            return;

        DamageSource source = event.getSource();

        // Confusion: the attacker may fail to deal any damage at all
        if (rollConfusionAttackFailure(source))
        {
            event.setAmount(0.0f);
            event.setCanceled(true);
            return;
        }

        // Enchantments: psychic deprivation (extra psychic damage) / psychic drain (continuous sanity loss).
        // Only normal entity attacks trigger them; sanity damage and true damage do not re-trigger enchantments.
        if (!isSanityDamage(source))
            applyWeaponEnchantments(target, source, amount);

        if (!SanityDamageTypes.isPsychic(source))
            return;

        // Inner entity damage bonus: an inner entity attacking an entity with very low (or zero) sanity
        // deals heavier psychic damage. It must be applied here, before "drain sanity / convert the
        // overflow" below, so that both stages use the boosted amount.
        amount = applyInnerAttackerBonus(target, source, amount);

        // Psychic damage itself never deals health damage directly
        event.setAmount(0.0f);
        event.setCanceled(true);

        // Psychic protection enchantment: reduces damage like vanilla Protection but only for psychic damage.
        // It is honoured here instead of by the vanilla pipeline because
        // EnchantmentHelper#getDamageProtection sums the points of every enchantment on the whole set, so
        // vanilla Protection IV would also absorb psychic damage, whereas psychic damage must only be
        // reduced by this enchantment and by shields.
        // sanitypd:psychic is therefore listed in bypasses_enchantments (the vanilla pipeline contributes
        // nothing) while this enchantment still reports vanilla-style points, cashed in here with the
        // vanilla formula (points / 25) and then clamped to the cap.
        amount = applyPsychicProtection(target, amount);

        if (SanityTags.isInnerEntity(target))
        {
            // Inner entities have no sanity, so the damage is unconditionally converted into 2.5x true damage.
            // It uses the "psychic overflow" damage type (same numeric rules as true_damage: the same six
            // vanilla bypass tags and scaling=never), but the death message is no longer the true-damage one.
            //
            // The amount is further multiplied by the inner entity's own independent psychic resistance
            // (stalker 10 / crawler 20 / lurker 15 points): the points are "how much is removed", the same
            // scale as on the player side, so the factor is 1 - points/100.
            // 20 points means only 80% is converted; 100 points means immunity to psychic damage
            // (the conversion yields 0 true damage) while the "2.5x conversion" rule itself still holds.
            //
            // Because a synchronous nested application makes the outer psychic hurt() call die() again,
            // which broadcasts two death messages and overwrites the death screen with a generic one, the
            // damage is queued and applied at the end of the tick (see OverflowDamageQueue).
            float innerResistance = innerPsychicResistanceOf(target);
            OverflowDamageQueue.enqueuePsychicOverflow(target, amount * innerResistance * INNER_PSYCHIC_MULTIPLIER, source.getEntity());
            return;
        }

        ISanity cap = target.getCapability(SanityProvider.CAP).orElse(null);
        if (cap == null)
            return;

        // Psychic resistance only reduces the "drain sanity" stage; the true damage produced from the
        // overflow is unaffected, because true damage is defined as ignoring every kind of reduction.
        // At resistance 1.0 the amount here becomes 0, so no sanity is drained and there is no overflow.
        // The reduction is additionally clamped to MAX_ENCHANTMENT_REDUCTION (80%): even if an entity's
        // resistance is set to 100 points through /sanity resist, the effective reduction stays at 80%,
        // leaving 20% of the psychic damage to drain sanity.
        // Inner entities never reach this branch (they are handled by the 2.5x conversion above), so their
        // 100 points still mean full immunity to psychic damage (see innerPsychicResistanceOf).
        float resistFactor = 1f - MathHelper.clamp(cap.getPsychicResistance(), 0f, 1f);
        resistFactor = Math.max(resistFactor, 1f - MAX_ENCHANTMENT_REDUCTION);
        amount *= resistFactor;

        float current = cap.getSanity();
        float deducted = Math.min(current, amount);
        if (deducted > 0.0f)
            cap.setSanity(current - deducted);

        // The part beyond the available sanity (including the case where sanity was already 0) becomes an
        // equal amount of true damage.
        // It uses the dedicated "psychic overflow" type (same numeric rules, but the death message is no
        // longer the true-damage one); if the attacker is a screaming crawler (its self-detonation) the
        // "screaming crawler explosion" type is used instead so the death message reads as the explosion.
        float overflow = amount - deducted;
        if (overflow > 0.0f)
            OverflowDamageQueue.enqueue(target, overflow, source.getEntity());
    }

    /**
     * The inner entity "damage bonus".
     *
     * <p>An inner entity attacking an entity at or below 10% sanity deals an extra 10% psychic damage,
     * and 25% at or below 0. The baseline is the existing damage, so the final multipliers are 1.1 and
     * 1.25. The bonus applies on <b>both</b> attack paths (see below).
     *
     * <table>
     *   <tr><th>Target sanity</th><th>Extra psychic damage</th></tr>
     *   <tr><td>&gt; 10% of the maximum</td><td>none</td></tr>
     *   <tr><td>&le; 10% of the maximum</td><td>+{@link #INNER_LOW_SANITY_BONUS} (10% of the damage)</td></tr>
     *   <tr><td>&le; 1% of the maximum (including zero sanity)</td><td>+{@link #INNER_ZERO_SANITY_BONUS} (25% of the damage)</td></tr>
     * </table>
     *
     * <p>Both thresholds are measured as a percentage of the <b>target's own sanity maximum</b>
     * (through {@link ISanity#getMaxSanity()}, so "other mobs use max health as their maximum" keeps
     * working); the comparison is "at or below".
     *
     * <h3>The two attack paths are treated differently (important)</h3>
     * <ul>
     *   <li><b>Path 1: psychic damage dealt by an inner entity</b> (which is how the crawler
     *       self-detonation arrives) -> the bonus is multiplied straight in ({@code amount * (1 + bonus)});</li>
     *   <li><b>Path 2: an ordinary melee attack (physical damage) by an inner entity</b>
     *       -> the physical part is <b>kept as is</b> and a separate psychic damage instance (the
     *       difference) is added, which this class's own listener then handles (drain sanity, then
     *       convert the overflow to true damage). That is the "extra additional psychic damage".</li>
     * </ul>
     *
     * <p>No bonus is applied when the target has no sanity capability (including <b>inner entities
     * themselves</b>, so they do not get the bonus against each other) or for a nested event
     * (the {@link #PSYCHIC_DEPTH} guard, which prevents a bonus from triggering another bonus).
     *
     * @return the adjusted damage (the melee path returns the original amount, because the bonus is a
     *         separate instance of damage)
     */
    public static float applyInnerAttackerBonus(LivingEntity target, DamageSource source, float amount)
    {
        if (amount <= 0f || target == null || source == null)
            return amount;

        Entity attacker = source.getEntity();
        if (!SanityTags.isInnerEntity(attacker))
            return amount;

        // No bonus in a nested event (the outer one already applied it); see the PSYCHIC_DEPTH documentation
        if (isNestedSanityEvent())
            return amount;

        float bonus = innerPsychicBonusRatio(target);
        if (bonus <= 0f)
            return amount;

        float boosted = amount * (1f + bonus);

        // This hit is psychic damage applied by an inner entity, so the bonus is simply multiplied in.
        // (The crawler self-detonation takes exactly this path: CrawlerExplosionHandler -> dealPsychic -> here.)
        if (SanityDamageTypes.isPsychic(source))
            return boosted;

        // Otherwise this is an ordinary melee attack (physical damage) by an inner entity, and the intent is
        // an extra instance of psychic damage: the physical part is kept as is and a second psychic damage
        // instance equal to the difference is added. SanityCombat's own listener handles that instance
        // (drain sanity, then convert the overflow to true damage), so it is resolved independently.
        float extra = boosted - amount;
        enterNestedEvent();
        try
        {
            SanityDamageTypes.dealPsychic(target, extra, attacker);
            // Leave an assertable trace that the bonus really happened (see getInnerMeleeBonusCount)
            INNER_MELEE_BONUS_COUNT.incrementAndGet();
        }
        finally
        {
            exitNestedEvent();
        }

        return amount;
    }

    /**
     * The psychic damage ratio an inner entity should add against the target:
     * {@code 0} / {@link #INNER_LOW_SANITY_BONUS} / {@link #INNER_ZERO_SANITY_BONUS}.
     *
     * <p>It is a separate method so the "melee bonus" and the "self-detonation bonus" share one set of
     * thresholds, leaving only one place that can compute them wrongly.
     */
    public static float innerPsychicBonusRatio(LivingEntity target)
    {
        float ratio = sanityRatioOf(target);

        if (ratio < 0f)
            return 0f;  // an entity without sanity (including inner entities themselves) gets no bonus

        if (ratio <= INNER_ZERO_SANITY_RATIO)
            return INNER_ZERO_SANITY_BONUS;

        if (ratio <= INNER_LOW_SANITY_RATIO)
            return INNER_LOW_SANITY_BONUS;

        return 0f;
    }

    /**
     * The target's sanity as a fraction of its own maximum; {@code -1} = the entity has no sanity capability.
     *
     * <p>It is shared by the damage bonus and the probe so that the same computation is not written twice
     * (where a mistake in one copy would be easy to miss).
     */
    public static float sanityRatioOf(LivingEntity entity)
    {
        if (entity == null)
            return -1f;

        ISanity cap = entity.getCapability(SanityProvider.CAP).orElse(null);
        if (cap == null)
            return -1f;

        float max = cap.getMaxSanity();
        if (max <= 0f)
            return -1f;

        return cap.getSanity() / max;
    }

    /** Whether the attacker of this damage instance is an inner entity (used by the melee bonus path). */
    public static boolean isInnerAttacker(DamageSource source)
    {
        return source != null && SanityTags.isInnerEntity(source.getEntity());
    }

    /**
     * Re-entry depth guard.
     *
     * <p>The inner melee bonus starts a second psychic hurt from inside the outer hurt event handler, and
     * that psychic hurt dispatches another {@code LivingHurtEvent}. In the nested event
     * {@link #applyInnerAttackerBonus} would again see "the attacker is an inner entity", so without a
     * guard this could recurse without bound. The guard makes that structural rather than merely observed.
     *
     * <p>A thread-local counter: the outer physical event sees 1, so only it applies the bonus; the nested
     * psychic event sees 2 and does not apply it again.
     */
    private static final ThreadLocal<Integer> PSYCHIC_DEPTH = ThreadLocal.withInitial(() -> 0);

    /** Whether this psychic damage event was nested-dispatched by our own bonus logic. */
    private static boolean isNestedSanityEvent()
    {
        return PSYCHIC_DEPTH.get() > 0;
    }

    private static void enterNestedEvent()
    {
        PSYCHIC_DEPTH.set(PSYCHIC_DEPTH.get() + 1);
    }

    private static void exitNestedEvent()
    {
        int depth = PSYCHIC_DEPTH.get() - 1;
        PSYCHIC_DEPTH.set(Math.max(0, depth));
    }

    /**
     * How many times the "inner melee extra psychic damage" has triggered in this process (monotonic).
     *
     * <p>It exists for one reason: <b>to let the diagnostic probe prove that this really happened</b>.
     *
     * <p>The melee hit itself is <b>physical</b> damage, so the probe cannot catch any psychic damage event
     * directly, and the bonus instance happens <b>inside</b> the handling of the outer event. By reading
     * this counter once within the same {@code LivingHurtEvent}, a change in the value proves the bonus was
     * actually dealt. This is the only externally assertable check for the silent failure (the bonus not
     * applying) that would otherwise look like a bug.
     */
    public static int getInnerMeleeBonusCount()
    {
        return INNER_MELEE_BONUS_COUNT.get();
    }

    /** @see #getInnerMeleeBonusCount() */
    private static final java.util.concurrent.atomic.AtomicInteger INNER_MELEE_BONUS_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * The inner entity's "independent psychic resistance" factor ({@code 0.0 ~ 1.0}), that is
     * <b>the share of the damage that still gets through</b>.
     *
     * <pre>
     *   factor = 1 - resistance points / 100
     * </pre>
     *
     * <p>The points (stalker 10 / crawler 20 / lurker 15) mean <b>how much is removed</b>, exactly the same
     * scale as psychic resistance on the player side: <b>more points resist more, 100 points is full
     * immunity</b>.
     * <ul>
     *   <li>crawler 20 points -> factor 0.80 -> 60 psychic damage becomes 60 * 0.80 * 2.5 = 120 true damage;</li>
     *   <li>stalker 10 points -> factor 0.90 -> 60 * 0.90 * 2.5 = 135;</li>
     *   <li>lurker 15 points -> factor 0.85 -> 60 * 0.85 * 2.5 = 127.5;</li>
     *   <li>100 points -> factor 0 -> full immunity to psychic damage (the converted amount is 0).</li>
     * </ul>
     *
     * <p>Anything that is not an inner entity returns {@code 1.0}: it does not use these rules but the
     * percentage reduction of the capability instead.
     */
    public static float innerPsychicResistanceOf(LivingEntity entity)
    {
        if (entity instanceof piloser.sanitypd.entity.InnerEntity inner)
            return MathHelper.clamp(1f - inner.getPsychicResistance() / 100f, 0f, 1f);

        return 1f;
    }

    /**
     * Damage reduction of the <b>psychic protection</b> enchantment, following the vanilla Protection maths.
     *
     * <p>Three steps, all following vanilla:
     * <ol>
     *   <li>take the <b>total level</b> of psychic protection on the <b>four armour slots</b>
     *       ({@code EnchantmentHelper#getEnchantmentLevel(enchantment, entity)} iterates the equipment and
     *       sums it internally);</li>
     *   <li>points = {@code total level * }{@value piloser.sanitypd.enchantment.PsychicProtectionEnchantment#POINTS_PER_LEVEL}
     *       (1 point per level, matching vanilla Protection);</li>
     *   <li>reduce the damage with the vanilla formula {@code amount * (1 - points/25)}, which corresponds
     *       to {@code CombatRules#getDamageAfterMagicAbsorb} without its own 20 point cap, since the cap is
     *       applied by this mod instead.</li>
     * </ol>
     *
     * <p>Why the level is summed here instead of using {@code EnchantmentHelper#getDamageProtection}:
     * {@code sanitypd:psychic} is listed in the {@code bypasses_enchantments} tag (so that no other
     * enchantment reduces psychic damage any more), which means that vanilla entry point is no longer called
     * for psychic damage. This enchantment still reports vanilla-style points, they are just cashed in here.
     *
     * <p><b>Do not switch to {@code EnchantmentHelper#getEnchantmentLevel(enchantment, entity)}</b>:
     * disassembly confirms that it iterates {@code Enchantment#getSlotItems(entity)} internally and
     * <b>returns the first non-zero level</b> rather than the sum, so a full set of four level IV pieces
     * would read as 4 and lose three quarters of the reduction. This method therefore walks the four armour
     * slots explicitly and sums them, which is unambiguous.
     *
     * @return the damage after reduction (returned unchanged when the enchantment is absent)
     */
    public static float applyPsychicProtection(LivingEntity target, float amount)
    {
        if (target == null || amount <= 0f)
            return amount;

        Enchantment enchantment = SanityEnchantments.PSYCHIC_PROTECTION.get();
        int levels = totalArmorEnchantmentLevel(target, enchantment);

        if (levels <= 0)
            return amount;

        float points = levels * PsychicProtectionEnchantment.POINTS_PER_LEVEL;
        // Vanilla formula (points / 25) plus this mod's cap; 1 - reduction = the share actually taken
        float factor = 1f - Math.min(points / 25f, MAX_ENCHANTMENT_REDUCTION);

        return amount * factor;
    }

    /** Total level of this enchantment across the four armour slots (summed, not the first non-zero level). */
    private static int totalArmorEnchantmentLevel(LivingEntity target, Enchantment enchantment)
    {
        int total = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS)
            total += EnchantmentHelper.getItemEnchantmentLevel(enchantment, target.getItemBySlot(slot));

        return total;
    }

    /**
     * Applies the overflow part of psychic damage as true damage and picks the <b>death-message damage
     * type</b> for the attacker.
     *
     * <p>It is a separate method so the probe can test it directly (see {@link #overflowTypeFor}).
     *
     * <p><b>The normal flow no longer calls it directly</b>: damage is queued through
     * {@link OverflowDamageQueue} and applied at the end of the tick (a synchronous nested application
     * would call {@code die()} twice; see that class for the details). It is kept for the special
     * "apply immediately" case (self-checks and debugging) and so that the {@code overflowTypeFor} rules
     * have a single landing point.
     */
    public static void applyOverflowDamage(LivingEntity target, float overflow, Entity attacker)
    {
        if (target == null || overflow <= 0.0f)
            return;

        if (overflowTypeFor(attacker) == SanityDamageTypes.CRAWLER_EXPLOSION)
            SanityDamageTypes.dealCrawlerExplosion(target, overflow, attacker);
        else
            SanityDamageTypes.dealPsychicOverflow(target, overflow, attacker);
    }

    /**
     * Which damage type the "overflow part of psychic damage" should use.
     *
     * <p><b>This small method is what decides the death message</b>, which is why it is a separate method
     * that the probe can assert on directly:
     * <ul>
     *   <li>attacker is a <b>screaming crawler</b> -> {@link SanityDamageTypes#CRAWLER_EXPLOSION},
     *       giving the explosion death message;</li>
     *   <li>anything else (psychic damage from a shadow weapon, for example) ->
     *       {@link SanityDamageTypes#PSYCHIC_OVERFLOW}, giving the psychic-collapse death message.</li>
     * </ul>
     *
     * <p>Note that this only changes the type of the <b>overflow part</b> (by then sanity has already been
     * drained and this part is true damage anyway), so it does <b>not</b> affect the rule that "a crawler
     * explosion deals psychic damage only". Do not confuse it with swapping the explosion entry point to
     * crawler_explosion, which would bypass the sanity resolution entirely.
     */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> overflowTypeFor(Entity attacker)
    {
        return attacker instanceof piloser.sanitypd.entity.ScreamingCrawler
                ? SanityDamageTypes.CRAWLER_EXPLOSION
                : SanityDamageTypes.PSYCHIC_OVERFLOW;
    }

    /** This mod's own damage types (psychic / true / psychic overflow / mania / crawler explosion) all count as
     *  "sanity damage": they do not trigger weapon enchantments and do not incur the "hurt an animal" penalty twice. */
    public static boolean isSanityDamage(DamageSource source)
    {
        return SanityDamageTypes.isPsychic(source)
                || SanityDamageTypes.isTrueDamage(source)
                || SanityDamageTypes.isPsychicOverflow(source)
                || SanityDamageTypes.isCrawlerExplosion(source)
                || SanityDamageTypes.isManiaDamage(source);
    }

    /** An attacker with "confusion" has a 25% chance to deal no damage at all. */
    private static boolean rollConfusionAttackFailure(DamageSource source)
    {
        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker))
            return false;
        if (!attacker.hasEffect(EffectRegistry.CONFUSION.get()))
            return false;
        return attacker.getRandom().nextFloat() < ConfusionEffect.FAIL_CHANCE;
    }

    // ------------------------------------------------------------------ attacking a low-sanity target
    /**
     * Attacking a <b>hostile mob</b> whose sanity is below 50% makes the attacker regain sanity equal to the
     * damage actually dealt.
     *
     * <p><b>Why passive animals must be excluded:</b> an animal's sanity maximum is its max health, and a
     * vanilla cow has only 10 health, so a single 8 point psychic hit from a shadow sword drops it to 2
     * (below 50% of 5). The "feeding" would immediately return +8 to the attacker while the "hurt an animal"
     * penalty is only -4, a net gain of +4, which turns the mechanic upside down.
     *
     * <p>The feeding mechanic is meant to be "draining sanity from a strong enemy while fighting for your
     * life", so it only applies to {@link Monster}; passive animals always go through the "hurt an animal ->
     * lose sanity" penalty and can never yield a net gain.
     */
    public static void onLivingDamage(LivingDamageEvent event)
    {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide())
            return;

        float dealt = event.getAmount();
        if (dealt <= 0.0f)
            return;

        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker) || attacker == target)
            return;
        if (SanityTags.isInnerEntity(attacker) || SanityTags.isInnerEntity(target))
            return;
        // Passive animals (cow / sheep / pig / chicken ...) give no feeding, otherwise it would outweigh the
        // "hurt an animal" penalty and become a net sanity gain.
        if (!(target instanceof Monster))
            return;

        ISanity targetCap = target.getCapability(SanityProvider.CAP).orElse(null);
        ISanity attackerCap = attacker.getCapability(SanityProvider.CAP).orElse(null);
        if (targetCap == null || attackerCap == null)
            return;
        if (targetCap.getSanity() >= targetCap.getMaxSanity() * FEED_THRESHOLD)
            return;

        attackerCap.setSanity(attackerCap.getSanity() + dealt);
    }

    // ------------------------------------------------------------------ low-frequency state maintenance
    /**
     * Weapon enchantment resolution:
     * <ul>
     *   <li><b>psychic deprivation</b>: adds psychic damage of "this attack's damage * 30% * level"
     *       (Sharpness and critical hits scale it along with the attack).</li>
     *   <li><b>psychic drain</b>: puts a hidden effect on the target, which then loses 2% of its sanity per
     *       second for 10 seconds (refreshed by repeated hits).</li>
     * </ul>
     */
    private static void applyWeaponEnchantments(LivingEntity target, DamageSource source, float amount)
    {
        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker) || attacker == target)
            return;

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty())
            return;

        // Shadow weapons (sword 8 / axe 10) always add a fixed amount of psychic damage on hit. The interface
        // check means new weapons need no change here. The shadow refinement level adds 1 more per level,
        // so a base 8 sword at refinement +9 deals 17. The value is computed only in ShadowRefinement (the
        // single source of truth), which also feeds the tooltip, so the two can never disagree.
        if (weapon.getItem() instanceof IShadowWeapon shadow)
            SanityDamageTypes.dealPsychic(target, ShadowRefinement.psychicDamage(weapon, shadow), attacker);

        int deprivation = EnchantmentHelper.getItemEnchantmentLevel(SanityEnchantments.PSYCHIC_DEPRIVATION.get(), weapon);
        if (deprivation > 0)
        {
            float bonus = amount * PsychicDeprivationEnchantment.PSYCHIC_PER_LEVEL * deprivation;
            SanityDamageTypes.dealPsychic(target, bonus, attacker);
        }

        if (EnchantmentHelper.getItemEnchantmentLevel(SanityEnchantments.PSYCHIC_DRAIN.get(), weapon) > 0)
        {
            // Uses a visible icon (texture textures/mob_effect/psychic_drain.png, a 16x16 dark red droplet)
            // with particles disabled, so a hit player can see that sanity is being drained.
            target.addEffect(new MobEffectInstance(EffectRegistry.PSYCHIC_DRAIN.get(), PsychicDrainEffect.DURATION_TICKS, 0, false, true), attacker);
        }
    }

    /**
     * The confusion / mania state machine (each entity is checked once every {@link #MAINTAIN_INTERVAL} ticks).
     *
     * <ol>
     *   <li>Sanity back above 50% of the maximum -> both states are cleared immediately and timers reset.</li>
     *   <li>Sanity below 25% of the maximum: every entity (players included) must stay below the threshold
     *       for {@link #CONFUSION_DELAY_TICKS} (30 seconds) before entering "confusion".</li>
     *   <li>"Confusion" lasting {@link #CONFUSION_TO_MANIA_TICKS} (30 seconds) without recovering to 50%
     *       -> escalates to "mania".</li>
     *   <li>After {@link #MANIA_GRACE_TICKS} (40 seconds) of "mania" the entity takes
     *       {@link #MANIA_TRUE_DAMAGE_PER_SECOND} points of true damage per second (death message: dying of a
     *       breakdown) until its sanity returns above 50% or it dies.</li>
     * </ol>
     *
     * <p>Inner entities have no sanity value and are immune to both states.
     */
    public static void maintainStates(LivingEntity entity)
    {
        if (entity.level().isClientSide())
            return;
        if ((entity.tickCount % MAINTAIN_INTERVAL) != 0)
            return;

        MobEffect confusion = EffectRegistry.CONFUSION.get();
        MobEffect mania = EffectRegistry.MANIA.get();

        if (SanityTags.isInnerEntity(entity))
        {
            if (entity.hasEffect(confusion))
                entity.removeEffect(confusion);
            if (entity.hasEffect(mania))
                entity.removeEffect(mania);
            return;
        }

        ISanity cap = entity.getCapability(SanityProvider.CAP).orElse(null);
        if (cap == null)
            return;

        float max = cap.getMaxSanity();
        float sanity = cap.getSanity();

        // 1) Recovered above 50% -> clear everything
        if (sanity >= max * RECOVER)
        {
            cap.setLowSanityTicks(0);
            cap.setConfusionTicks(0);
            cap.setManiaTicks(0);
            if (entity.hasEffect(confusion))
                entity.removeEffect(confusion);
            if (entity.hasEffect(mania))
                entity.removeEffect(mania);
            return;
        }

        // 2) Already in mania: keep the effect up and deal damage every second after the grace period
        if (cap.getManiaTicks() > 0)
        {
            cap.setManiaTicks(cap.getManiaTicks() + MAINTAIN_INTERVAL);
            ensureEffect(entity, mania, MANIA_EFFECT_TICKS);
            // Mania immunity (the reward for killing an inner entity) blocks only the damage; the state and
            // the warning keep advancing as usual
            if (cap.getManiaTicks() > MANIA_GRACE_TICKS && !entity.hasEffect(EffectRegistry.MANIA_IMMUNITY.get()))
                SanityDamageTypes.dealManiaDamage(entity, MANIA_TRUE_DAMAGE_PER_SECOND, null);
            return;
        }

        // 3) Already confused: escalates to mania after 30 seconds without recovery
        if (cap.getConfusionTicks() > 0)
        {
            cap.setConfusionTicks(cap.getConfusionTicks() + MAINTAIN_INTERVAL);
            if (cap.getConfusionTicks() >= CONFUSION_TO_MANIA_TICKS)
            {
                cap.setConfusionTicks(0);
                cap.setManiaTicks(1);
                if (entity.hasEffect(confusion))
                    entity.removeEffect(confusion);
                ensureEffect(entity, mania, MANIA_EFFECT_TICKS);
            }
            else
            {
                ensureEffect(entity, confusion, CONFUSION_EFFECT_TICKS);
            }
            return;
        }

        // 4) Not confused yet: check whether sanity is already low enough
        if (sanity < max * CONFUSION_ENTER)
        {
            cap.setLowSanityTicks(cap.getLowSanityTicks() + MAINTAIN_INTERVAL);
            // Same threshold for everyone: players and other mobs alike must stay below 25% for 30 seconds
            // before entering "confusion"
            boolean ready = cap.getLowSanityTicks() >= CONFUSION_DELAY_TICKS;
            if (ready)
            {
                cap.setLowSanityTicks(0);
                cap.setConfusionTicks(1);
                ensureEffect(entity, confusion, CONFUSION_EFFECT_TICKS);
            }
        }
        else
        {
            cap.setLowSanityTicks(0);
        }
    }

    /** Top the effect up when it is missing or has less than half its duration left, to avoid adding it every second. */
    private static void ensureEffect(LivingEntity entity, MobEffect effect, int durationTicks)
    {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null || current.getDuration() < durationTicks / 2)
            entity.addEffect(new MobEffectInstance(effect, durationTicks, 0, false, true), null);
    }
}
