package piloser.sanitypd.damage;

import piloser.sanitypd.SanityMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Damage types added by this mod.
 *
 * <ul>
 *   <li><b>psychic</b>: deals no damage on its own. A hit drains an equal amount of sanity instead;
 *       once the target's sanity is exhausted, the excess becomes an equal amount of true damage.
 *       On inner entities it always converts to 2.5x true damage.</li>
 *   <li><b>true_damage</b>: ignores armor, enchantment protection, resistance and shields (the datapack
 *       adds it to the vanilla {@code bypasses_*} damage type tags). ⚠️ It does <b>not</b> ignore absorption:
 *       1.20.1 has no {@code bypasses_absorption} tag and no such constant in {@code DamageTypeTags}, so a
 *       golden apple's hearts soak a true hit like any other. {@link #dealSelfInjury} is the one caller that
 *       lifts the absorption for the length of its own call, because its spec says so.</li>
 *   <li><b>mania</b>: true damage used for the per-second health drain in the mania state; same
 *       rules as true damage, only the death message differs.</li>
 *   <li><b>psychic_overflow</b>: true damage for the overflow part of psychic damage.</li>
 *   <li><b>crawler_explosion</b>: used only by the screaming crawler's self-destruct.</li>
 * </ul>
 *
 * <p>Add-on mods that want to apply psychic damage should call {@link #dealPsychic}; the conversion
 * rules then apply automatically. Amounts are measured in sanity points (a full player has 100).
 */
public final class SanityDamageTypes
{
    /*
     * 1.20 damage types are a pure datapack registry:
     *   (1) they are not in BuiltInRegistries (there is no DAMAGE_TYPE among those constants);
     *   (2) they are not a Forge registry either.
     * So Forge never fires RegisterEvent for them: with DeferredRegister the RegistryObject is
     * never populated and whoever calls .get() crashes at runtime.
     * The correct approach is what this class does: a ResourceKey plus
     * data/sanitypd/damage_type/*.json for the values.
     */

    /** Registry key for psychic damage (also used in {@code DamageSource#is} checks and the {@code death.attack.psychic} message). */
    public static final ResourceKey<DamageType> PSYCHIC = key("psychic");
    /**
     * Registry key for <b>sourceless</b> true damage (ignores armor/enchantments/resistance/shields; see the
     * class note about absorption).
     *
     * <p>This type means "true damage with no source" only. Both of its lang entries carry the same
     * sourceless wording, which also covers the "no entity but a kill credit" case.
     *
     * <p>Do not merge it back with {@link #TRUE_DAMAGE_ATTRIBUTED}: one damage type cannot produce
     * both a sourceless message and a "slain by &lt;attacker&gt;" message, because vanilla uses the base
     * lang entry for a hit that has an entity <i>and</i> for one that has no source at all.
     */
    public static final ResourceKey<DamageType> TRUE_DAMAGE = key("true_damage");
    /**
     * Type for true damage <b>with an attacker</b>.
     *
     * <p>Split from {@link #TRUE_DAMAGE} because vanilla's
     * {@code DamageSource#getLocalizedDeathMessage} routes both "has an entity" and "completely
     * sourceless" hits to the base lang entry: a single type cannot show a sourceless line for
     * {@code /sanity truedamage} while still showing "&lt;name&gt; was slain by &lt;attacker&gt;" for mobs.
     *
     * <p>Both types have identical numbers and join the same vanilla bypass tags; only the death
     * message differs. Routing lives in {@link #dealTrueDamage}. The crawler self-destruct uses
     * {@link #CRAWLER_EXPLOSION} and is unaffected.
     */
    public static final ResourceKey<DamageType> TRUE_DAMAGE_ATTRIBUTED = key("true_damage_attributed");
    /** Damage type for the per-second health drain in the mania state: true damage with its own death message. */
    public static final ResourceKey<DamageType> MANIA = key("mania");
    /**
     * True damage type used for the <b>overflow part</b> of psychic damage.
     *
     * <p>A separate type is needed because routing the overflow through {@code true_damage} made
     * "killed by psychic damage at low effective health" show the sourceless true damage message,
     * which reads wrong in game. With this type the overflow keeps a psychic-flavoured message.
     *
     * <p>Numeric rules are identical to {@code true_damage} (same vanilla bypass tags, ignoring
     * armor/resistance/enchantments/shields/invulnerability frames); only the death message differs.
     */
    public static final ResourceKey<DamageType> PSYCHIC_OVERFLOW = key("psychic_overflow");

    /**
     * Psychic damage type used only by the <b>screaming crawler explosion</b>.
     *
     * <p>Whenever the killing blow comes from a screaming crawler, this type is used so the death
     * message is consistent regardless of whether the sanity drain or the overflow stage killed the target.
     */
    public static final ResourceKey<DamageType> CRAWLER_EXPLOSION = key("crawler_explosion");

    /**
 * Psychic damage type used only by a <b>shadow TNT</b> blast with no known owner.
     *
     * <p>Message id: {@code death.attack.shadow_tnt}. Like {@link #CRAWLER_EXPLOSION} it is used for the
     * <b>overflow segment</b> of a psychic hit, so sanity has already been drained and only the death
     * message differs.
     *
     * <p>It deliberately carries <b>no causing entity</b>: vanilla's
     * {@code DamageSource#getLocalizedDeathMessage} routes any source that has one to the {@code .player}
     * entry and renders that entity's display name for {@code %2$s}, and the three messages this mod needs
     * ("blown up by Shadow TNT" / "by the one <i>you</i> set off" / "by the one <i>someone else</i> set
     * off") cannot be distinguished that way - the self-ignited case would print the victim's own name
     * where the sentence wants "yourself". So each case has its own type and its own base entry.
     */
    public static final ResourceKey<DamageType> SHADOW_TNT = key("shadow_tnt");

    /** Psychic damage type used by a shadow TNT blast the <b>victim themselves</b> set off. */
    public static final ResourceKey<DamageType> SHADOW_TNT_SELF = key("shadow_tnt_self");

    /**
     * Psychic damage type used by a shadow TNT blast <b>someone else</b> set off.
     */
    public static final ResourceKey<DamageType> SHADOW_TNT_BY_OWNER = key("shadow_tnt_by_owner");

    /**
     * The self-injury damage of the non-suicidal self-injury thought: the victim is its own source.
     *
     * <p>It is a type of its own rather than a reuse of {@link #MANIA} or {@link #TRUE_DAMAGE} because the
     * sentence that has to be printed is neither of theirs ("&lt;name&gt; killed themselves" belongs to this
     * act alone), and because the act's two numeric rules are its own (see {@link #dealSelfInjury}).
     *
     * <p>It joins the same six vanilla bypass tags as the other true-damage-style types, so armour,
     * enchantment protection, resistance, shields and the 20-tick invulnerability window cannot reduce or
     * swallow it (the tag gate asserts the cooldown one).
     */
    public static final ResourceKey<DamageType> SELF_INJURY = key("self_injury");

    /** Whether this damage type is one of the three shadow TNT death-message types. */
    public static boolean isShadowTnt(DamageSource source)
    {
        return source != null && (source.is(SHADOW_TNT) || source.is(SHADOW_TNT_SELF) || source.is(SHADOW_TNT_BY_OWNER));
    }

    private SanityDamageTypes() {}

    private static ResourceKey<DamageType> key(String path)
    {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(SanityMod.MODID, path));
    }

    /** Builds a psychic damage source with the given entity as attacker. */
    public static DamageSource psychic(Level level, Entity attacker)
    {
        return source(level, PSYCHIC, attacker, attacker);
    }

    /** Builds a psychic damage source, distinguishing the direct source from the causing entity (e.g. projectiles). */
    public static DamageSource psychic(Level level, Entity direct, Entity causing)
    {
        return source(level, PSYCHIC, direct, causing);
    }

    /**
     * Builds a true damage source <b>with an attacker</b> (ordinary "slain by &lt;attacker&gt;" death message).
     *
     * <p>A {@code null} attacker does <b>not</b> produce this type: it falls back to
     * {@link #sourcelessTrueDamage}; see {@link #TRUE_DAMAGE_ATTRIBUTED}.
     */
    public static DamageSource trueDamage(Level level, Entity attacker)
    {
        if (attacker == null)
            return sourcelessTrueDamage(level);
        return source(level, TRUE_DAMAGE_ATTRIBUTED, attacker, attacker);
    }

    /** Builds a true damage source with a source, distinguishing direct source from causing entity (e.g. projectiles). */
    public static DamageSource trueDamage(Level level, Entity direct, Entity causing)
    {
        if (direct == null && causing == null)
            return sourcelessTrueDamage(level);
        return source(level, TRUE_DAMAGE_ATTRIBUTED, direct, causing);
    }

    /**
     * Builds a truly <b>sourceless</b> true damage source (no {@code entity} at all).
     *
     * <p>Produced by the {@code /sanity truedamage} command, which is not an attacker and therefore
     * passes no entity.
     */
    public static DamageSource sourcelessTrueDamage(Level level)
    {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(TRUE_DAMAGE);
        return new DamageSource(holder);
    }

    private static DamageSource source(Level level, ResourceKey<DamageType> key, Entity direct, Entity causing)
    {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
        if (causing == null)
            return new DamageSource(holder, direct);
        return new DamageSource(holder, direct, causing);
    }

    /** Builds the mania health-drain damage source (true damage). */
    public static DamageSource maniaDamage(Level level, Entity attacker)
    {
        return source(level, MANIA, attacker, attacker);
    }

    /**
     * Applies mania damage to the target (true damage with its own {@code death.attack.mania} message).
     *
     * @param target   the target
     * @param amount   damage amount
     * @param attacker attacker, may be {@code null}
     * @return the amount actually dealt
     */
    public static float dealManiaDamage(LivingEntity target, float amount, Entity attacker)
    {
        if (target == null || amount <= 0.0f)
            return 0.0f;
        target.hurt(maniaDamage(target.level(), attacker), amount);
        return amount;
    }

    /** Whether this damage source is the mod's mania damage. */
    public static boolean isManiaDamage(DamageSource source)
    {
        return source != null && source.is(MANIA);
    }

    /** Whether this damage source is psychic damage. */
    public static boolean isPsychic(DamageSource source)
    {
        return source != null && source.is(PSYCHIC);
    }

    /** Whether this damage source is the mod's true damage (<b>both</b> types count: sourceless and attributed). */
    public static boolean isTrueDamage(DamageSource source)
    {
        return source != null && (source.is(TRUE_DAMAGE) || source.is(TRUE_DAMAGE_ATTRIBUTED));
    }

    /** Whether this damage source is "sourceless true damage" (the only one that shows a sourceless death message). */
    public static boolean isSourcelessTrueDamage(DamageSource source)
    {
        return source != null && source.is(TRUE_DAMAGE);
    }

    /** Whether this damage source is the overflow part of psychic damage. */
    public static boolean isPsychicOverflow(DamageSource source)
    {
        return source != null && source.is(PSYCHIC_OVERFLOW);
    }

    /** Whether this damage source is the screaming crawler explosion. */
    public static boolean isCrawlerExplosion(DamageSource source)
    {
        return source != null && source.is(CRAWLER_EXPLOSION);
    }

    /** Whether this damage source is the self-injury inflicted by the self-injury thought. */
    public static boolean isSelfInjury(DamageSource source)
    {
        return source != null && source.is(SELF_INJURY);
    }

    /**
     * Builds the self-injury damage source: the victim is <b>its own</b> source, both directly and as the
     * causing entity.
     *
 * <p>That is what the design requires ("the damage's source is yourself"), and it is also what the death
     * message is written against: the base and the {@code .player} entries of {@code death.attack.self_injury}
     * print the same sentence, so whichever branch vanilla picks reads correctly.
     */
    public static DamageSource selfInjury(Level level, Entity self)
    {
        return source(level, SELF_INJURY, self, self);
    }

    /**
     * Applies the self-injury damage: <b>never lethal, never mitigated, never absorbed</b>.
     *
 * <p>Three rules, all of them the specification for this act, and all of them enforced here so no call site
     * can forget one:
     * <ul>
     *   <li><b>Never lethal</b> - the amount is clamped so at least one health point remains. A wound that
     *       kills would turn a mechanic the player is supposed to survive into a death sentence at low health,
     *       and the clamp is what makes "该伤害不会致死" true.</li>
     *   <li><b>Never mitigated</b> - the type is in the six vanilla bypass tags, so armour, enchantments,
     *       resistance and shields change nothing.</li>
     *   <li><b>Never absorbed</b> - absorption hearts are <b>not</b> tag-controllable in 1.20.1: there is no
     *       {@code bypasses_absorption} tag in the vanilla data (checked against the shipped jar), so a golden
     *       apple would silently eat the whole wound. The absorption amount is therefore lifted for the
     *       duration of the {@code hurt} call and put back afterwards, which keeps every event and every
     *       listener intact while making the health loss exactly the amount asked for.</li>
     * </ul>
     *
     * @return the health actually lost (0 when the clamp left nothing to take, e.g. at one health point)
     */
    public static float dealSelfInjury(LivingEntity target, float amount)
    {
        if (target == null || amount <= 0.0f)
            return 0.0f;

        float loss = Math.min(amount, target.getHealth() - 1.0f);

        if (loss <= 0.0f)
            return 0.0f;

        float absorption = target.getAbsorptionAmount();

        if (absorption > 0.0f)
            target.setAbsorptionAmount(0.0f);

        // No knockback: a wound a player inflicts on themselves must not shove them. hurt() marks the entity
        // so its velocity is taken from the server (hurtMarked) and can add a push of its own, so both are
        // captured first and put back afterwards. Measured live: the player was being pushed away from their own
        // knife, which also made the act's own five seconds hard to watch.
        double keptX = target.getDeltaMovement().x;
        double keptY = target.getDeltaMovement().y;
        double keptZ = target.getDeltaMovement().z;
        boolean keptMarked = target.hurtMarked;

        try
        {
            target.hurt(selfInjury(target.level(), target), loss);
        }
        finally
        {
            target.setDeltaMovement(keptX, keptY, keptZ);
            target.hurtMarked = keptMarked;

            if (absorption > 0.0f && target.isAlive())
                target.setAbsorptionAmount(absorption);
        }

        return loss;
    }

    /**
     * Rebuilds a damage source with the same attacker but a different damage type.
     *
     * <p>Used to rewrite death messages: the original psychic source is replaced by the screaming
     * crawler explosion type while the direct/causing entities stay the same, so kill credit,
     * invulnerability-frame bypassing and similar behaviour are unchanged and only the message differs.
     */
    public static DamageSource withType(DamageSource original, ResourceKey<DamageType> type)
    {
        if (original == null)
            return null;
        return source(original.getEntity() == null ? null : original.getEntity().level(),
                type, original.getDirectEntity(), original.getEntity());
    }

    /**
     * <b>Dangerous method - do not use it as an explosion entry point.</b>
     *
     * <p>It only swaps the damage type and performs no sanity accounting. The sanity-drain plus
     * overflow branch in {@code SanityCombat.onLivingHurt} is gated on {@code isPsychic(source)},
     * and this type is <b>not</b> psychic, so calling it directly means the hit drains no sanity at
     * all and degrades into ordinary physical damage.
     *
     * <p>The only correct use is inside {@code SanityCombat}, when resolving the overflow part of
     * psychic damage: if the attacker is a screaming crawler, that small overflow segment is swapped
     * to this type. Sanity has already been drained by then and the segment is true damage anyway,
     * so only the death message changes.
     *
     * <p>Therefore the crawler self-destruct entry point <b>must</b> keep using {@link #dealPsychic}
     * (see {@code CrawlerExplosionHandler}).
     */
    public static float dealCrawlerExplosion(LivingEntity target, float amount, Entity attacker)
    {
        if (target == null || amount <= 0.0f)
            return 0.0f;
        target.hurt(source(target.level(), CRAWLER_EXPLOSION, attacker, attacker), amount);
        return amount;
    }

    /** Builds only the "psychic overflow" damage source (deals no damage; for self-checks and debugging). */
    public static DamageSource psychicOverflowSource(Level level, Entity attacker)
    {
        return source(level, PSYCHIC_OVERFLOW, attacker, attacker);
    }

    /**
     * Applies the overflow segment with an <b>explicit</b> death-message type, without any attacker.
     *
     * <p>Only the shadow TNT blast uses this. That hit must not carry a causing entity (see
     * {@link #SHADOW_TNT}), so going through {@link #dealPsychicOverflow} - which attaches the attacker -
     * would send vanilla down the {@code .player} death-message branch and print the wrong sentence. The
     * numbers are those of the psychic overflow: true damage that ignores armour, resistance, enchantments,
     * shields and the invulnerability window, because this segment is applied after sanity has already been
     * drained.
     */
    public static float dealOverflowWithType(LivingEntity target, float amount, ResourceKey<DamageType> messageType)
    {
        return dealOverflowWithType(target, amount, null, messageType);
    }

    /**
     * The same, with the attacker carried through.
     *
     * <p>⚠️ <b>The attacker must not be dropped.</b> An earlier version passed {@code null} for both entities
 * so that vanilla would render the base death-message entry - and on the client that hit silently
     * did nothing at all: the explosion drained sanity, the overflow was queued with the right message type,
     * and the player's health simply did not move. The screaming crawler's overflow (same queue, same tick,
     * but a real attacker in the source) hurt normally in the very same session, which is what pinned it down.
     *
     * <p>The message type still decides the wording: it is our own type rather than the generic psychic
     * overflow, and {@code SanityCombat#overflowTypeFor} is bypassed entirely for this path.
     */
    public static float dealOverflowWithType(LivingEntity target, float amount, Entity attacker, ResourceKey<DamageType> messageType)
    {
        if (target == null || amount <= 0.0f || messageType == null)
            return 0.0f;

        net.minecraft.world.damagesource.DamageSource src = source(target.level(), messageType, attacker, attacker);

        // Which exit of LivingEntity#hurt refuses this hit? Three of them return false, and their fixes are
        // completely different, so each is evaluated separately instead of guessing:
        //   * the Forge attack hook (a LivingAttackEvent listener cancelled it),
        //   * isInvulnerableTo (the entity is immune to this source),
        //   * isDeadOrDying.
        boolean hookAllows = net.minecraftforge.common.ForgeHooks.onLivingAttack(target, src, amount);
        boolean invulnerableTo = target.isInvulnerableTo(src);
        boolean dead = target.isDeadOrDying();
        int timeBefore = target.invulnerableTime;

        boolean landed = target.hurt(src, amount);

        piloser.sanitypd.SanityMod.LOGGER.info(
                "[OVF-TYPE] msgId={} amount={} landed={} | hookAllows={} invulnerableTo={} deadOrDying={} "
                        + "invulnerableTime={} healthBefore={} healthAfter={} alive={} bypassCooldownTag={} bypassInvulnTag={}",
                src.type().msgId(), amount, landed, hookAllows, invulnerableTo, dead,
                timeBefore, target.getHealth() + (landed ? amount : 0.0f), target.getHealth(), target.isAlive(),
                src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN),
                src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY));

        return amount;
    }

    /** Builds only the "screaming crawler explosion" damage source (deals no damage; for self-checks and debugging). */
    public static DamageSource dealCrawlerExplosionSource(LivingEntity target, Entity attacker)
    {
        return source(target.level(), CRAWLER_EXPLOSION, attacker, attacker);
    }

    /**
     * Applies the true damage caused by the "psychic overflow" to the target (same numbers as true
     * damage, only the death message is more fitting).
     *
     * @param target   the target
     * @param amount   damage amount
     * @param attacker attacker (may be {@code null}; the death message then omits its name)
     */
    public static float dealPsychicOverflow(LivingEntity target, float amount, Entity attacker)
    {
        if (target == null || amount <= 0.0f)
            return 0.0f;
        target.hurt(source(target.level(), PSYCHIC_OVERFLOW, attacker, attacker), amount);
        return amount;
    }

    /**
     * Applies psychic damage to the target (the recommended entry point): runs the full
     * "drain sanity, then convert the overflow to true damage" rules automatically.
     *
     * @param target   the target (player or mob)
     * @param amount   psychic damage amount (= sanity points to drain)
     * @param attacker attacker, may be {@code null}
     * @return the amount actually processed as psychic damage
     */
    public static float dealPsychic(LivingEntity target, float amount, Entity attacker)
    {
        if (target == null || amount <= 0.0f)
            return 0.0f;
        target.hurt(psychic(target.level(), attacker), amount);
        return amount;
    }

    /**
     * Psychic damage whose <b>death message</b> is forced to a specific type.
     *
     * <p>Used by the shadow TNT blast, which has to pick one of three sentences from the owner of the primed
     * entity (none / the victim / somebody else) - a choice the attacker entity alone cannot express, since
     * the damage source has no causing entity by design.
     *
     * <p>The override is pushed on a thread-local stack for the duration of the {@code hurt} call, which is
     * the only window in which {@code SanityCombat} can inspect it: the sanity drain and the queueing of the
     * overflow segment both happen inside that call. The stack is popped in a {@code finally}, and the pop is
     * guarded by the push result, so an exception thrown while applying damage cannot leave the override
     * behind for the next hit.
     */
    public static float dealPsychic(LivingEntity target, float amount, Entity attacker, ResourceKey<DamageType> messageType)
    {
        if (target == null || amount <= 0.0f)
            return 0.0f;

        if (messageType == null)
            return dealPsychic(target, amount, attacker);

        boolean pushed = piloser.sanitypd.combat.SanityCombat.pushOverflowMessageType(messageType);

        try
        {
            target.hurt(psychic(target.level(), attacker), amount);
        }
        finally
        {
            if (pushed)
                piloser.sanitypd.combat.SanityCombat.popOverflowMessageType();
        }

        return amount;
    }

    /**
     * Applies true damage to the target (ignores all reductions).
     *
     * <p><b>Death message routing:</b>
     * <ul>
     *   <li>{@code attacker == null} or {@code attacker == target} (self damage)
     *       =&gt; <b>sourceless true damage</b> ({@link #TRUE_DAMAGE});</li>
     *   <li>anything else (really hit by another entity) =&gt; attributed true damage
     *       ({@link #TRUE_DAMAGE_ATTRIBUTED}), so the message is the ordinary
     *       "slain by &lt;attacker&gt;" one and is <b>not</b> affected by the sourceless wording.</li>
     * </ul>
     *
     * @param target   the target
     * @param amount   damage amount
     * @param attacker attacker; {@code null} means no source (command/environment)
     * @return the amount actually dealt
     */
    public static float dealTrueDamage(LivingEntity target, float amount, Entity attacker)
    {
        if (target == null || amount <= 0.0f)
            return 0.0f;
        // Attacking yourself counts as sourceless (this is the /sanity truedamage @s path)
        Entity realAttacker = (attacker != null && attacker != target) ? attacker : null;
        target.hurt(trueDamage(target.level(), realAttacker), amount);
        return amount;
    }
}
