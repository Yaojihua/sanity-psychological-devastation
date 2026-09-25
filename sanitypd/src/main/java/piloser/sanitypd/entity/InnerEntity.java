package piloser.sanitypd.entity;

import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.config.ConfigProxy;
import piloser.sanitypd.effect.EffectRegistry;
import piloser.sanitypd.sound.SoundRegistry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class InnerEntity extends Monster
{
    private final AtomicBoolean m_skipAttackInteraction = new AtomicBoolean(false);

    /**
     * Independent psychic resistance of an inner entity (unit: points, 100 = fully immune).
     *
     * <p>Defaults: decayed stalker 10 / screaming crawler 20 / horror stalker 15. Inner entities have
     * no sanity capability ({@link piloser.sanitypd.capability.SanityProvider} is not attached to
     * them), so the resistance lives on the entity itself: {@link #defaultPsychicResistance()} gives
     * the per-type default and {@code /sanity resist set} can change it at runtime.
     *
     * <p>How it takes part in the math: the core rule for inner entities is "psychic damage converts
     * unconditionally into 2.5x true damage", and this resistance cuts a part off on top of that,
     * using the same scale as the player side (higher points resist more, 100 = immune):
     * <pre>
     *   factor = 1 - resistance / 100
     *   crawler resistance 20 => 60 psychic damage -> 60 * 0.80 * 2.5 = 120 true damage
     * </pre>
     * At 100 points the factor is 0, so psychic damage is fully negated while the
     * "psychic damage becomes true damage" rule itself still applies.
     *
     * <p>This is <b>not</b> the player-side "percentage reduction" system: inner entities have no
     * sanity capability, so this resistance is stored on the entity and adjusted via
     * {@code /sanity resist}.
     */
    private float m_psychicResistance;

    protected InnerEntity(EntityType<? extends Monster> entityType, Level level)
    {
        super(entityType, level);
        m_psychicResistance = defaultPsychicResistance();
    }

    /**
     * Default psychic resistance points of this inner entity type (overridden by subclasses).
     *
     * <p>Per-type values: decayed stalker {@code 10} / screaming crawler {@code 20} /
     * horror stalker {@code 15}. The base class returns {@code 0}, so an add-on's inner entity
     * without an override simply has no extra resistance.
     */
    public float defaultPsychicResistance()
    {
        return 0f;
    }

    /** Current psychic resistance points ({@code 0 ~ 100}). */
    public float getPsychicResistance()
    {
        return m_psychicResistance;
    }

    /** Sets the psychic resistance points (out-of-range values are clamped to {@code 0 ~ 100}). */
    public void setPsychicResistance(float value)
    {
        m_psychicResistance = value < 0f ? 0f : Math.min(value, 100f);
    }

    @Override
    public boolean skipAttackInteraction(Entity entity)
    {
        if (entity instanceof Player player && !ConfigProxy.getSaneSeeInnerEntities(player.level().dimension().location()) &&
                !(player.isCreative() || player.isSpectator()) && getTarget() != player)
        {
            player.getCapability(SanityProvider.CAP).ifPresent(s ->
            {
                m_skipAttackInteraction.set(s.getMadness() < .6f);
            });

            return m_skipAttackInteraction.get();
        }

        return super.skipAttackInteraction(entity);
    }

    /**
     * Whether this target is a player carrying the "ignore inner" buff.
     *
     * <p>Used in <b>two</b> places only, to block active attacks; nothing else touches it:
     * <ol>
     *   <li>{@link #setTarget} (the <b>real source</b>: every path that sets a target funnels here)</li>
     *   <li>{@link #tick} (drops a stale target acquired before the buff was drunk)</li>
     * </ol>
     *
     * <h3>The buff adds <b>only</b> the "do not attack actively" rule</h3>
     * It adds no damage reduction of any kind. An earlier layer that made buffed players take no
     * damage from inner entities (a {@code doHurtTarget} override) was a hidden damage reduction
     * and has been removed:
     * <ul>
     *   <li>if the holder hits it, it <b>retaliates normally and deals normal damage</b>;</li>
     *   <li>it does <b>not</b> set the holder as its target on its own (unless the holder strikes first).</li>
     * </ul>
     */
    public static boolean isInnerImmune(@Nullable Entity entity)
    {
        return entity instanceof Player player
                && player.hasEffect(EffectRegistry.INNER_IMMUNITY.get());
    }

    /**
     * Refuses a player carrying the "ignore inner" buff <b>at the source</b>, so no goal can acquire
     * one as a target.
     *
     * <p><b>Why clearing the target in {@link #tick} is not enough:</b> {@code Monster.registerGoals()}
     * brings in vanilla {@code NearestAttackableTargetGoal<Player>}, which re-sets the target every
     * tick (it ends up calling {@code Mob.setTarget}), while the clearing ran after
     * {@code super.tick()} - so within a single tick the target was already set, {@code MeleeAttackGoal}
     * attacked normally inside {@code super.tick()}, and only then was it cleared. The attacks never
     * actually stopped.
     *
     * <p>Every path that sets a target (the two vanilla target goals, this mod's
     * {@code TargetInsanePlayerGoal}, ...) funnels into {@code setTarget}, so intercepting here is the
     * real fix.
     *
     * <h3>Retaliation is allowed while immune</h3>
     * A player carrying the buff <b>is fought back</b> when they hit an inner entity. One exception is
     * therefore let through: <b>the entity that just hit us</b> ({@link #getLastHurtByMob()}).
     * Vanilla {@code HurtByTargetGoal} (registered on all inner entities) uses exactly that to acquire
     * its attacker, which is the native retaliation path - no extra AI needed.
     *
     * <p>"Do not attack actively" is still fully in force: a player who merely walks by, comes close
     * or has low sanity is still refused.
     */
    @Override
    public void setTarget(LivingEntity target)
    {
        if (isInnerImmune(target) && target != getLastHurtByMob())
            return;

        // Sanity gate: a recovered player is not a target at all. This is stronger than "stop chasing" -
        // the crawler refuses the target instead of merely walking slower, so it cannot keep following
        // someone who is no longer insane.
        if (isTrackingCancelled(target) && target != getLastHurtByMob())
            return;

        super.setTarget(target);
    }

    // ------------------------------------------------------------------ about doHurtTarget (do not add it back)
    //
    // A doHurtTarget(Entity) override used to return false for buffed players.
    // It has been removed: that override was a hidden damage reduction. It turned "retaliation" into
    // a free swing that dealt no damage, while the "ignore inner" mechanic is defined by exactly one
    // rule - inner entities do not attack the holder actively.
    // => to keep retaliation painful, no immunity check may be added on any damage output path.
    //    (`getTarget()` is the only interception point; `doHurtTarget` keeps vanilla behaviour.)


    /**
     * Drops a target that carries the "ignore inner" buff.
     *
     * <p><b>Why it is cleared both before and after {@code super.tick()}:</b>
     * <ul>
     *   <li><b>before</b>: a player may have been targeted first and drunk the buff afterwards, and
     *       that stale target has to go immediately or this tick's AI still attacks with it;</li>
     *   <li><b>after</b>: a safety net ({@link #setTarget} already blocks new targets, this just
     *       confirms once more).</li>
     * </ul>
     *
     * <p>An exception is made for the retaliation target: if the immune player is exactly
     * {@link #getLastHurtByMob()} (the one that just hit us), it is kept. Otherwise
     * {@code HurtByTargetGoal} would have its freshly acquired target wiped in the same tick and
     * retaliation could never happen.
     */
    @Override
    public void tick()
    {
        if (!this.level().isClientSide())
            dropInnerImmuneTarget();

        super.tick();

        if (!this.level().isClientSide())
            dropInnerImmuneTarget();
    }

    private void dropInnerImmuneTarget()
    {
        LivingEntity current = this.getTarget();

        if (isInnerImmune(current) && current != getLastHurtByMob())
            super.setTarget(null);

        // Same clearing rule for the sanity gate: a target whose sanity climbed back above
        // TRACKING_CANCEL_SANITY is dropped immediately, before this tick's AI can use it.
        else if (isTrackingCancelled(current) && current != getLastHurtByMob())
            super.setTarget(null);
    }

    /**
     * Whether tracking this entity must be cancelled because its sanity climbed back up.
     *
     * <p>Rule: <b>above {@code ScreamingCrawler#TRACKING_CANCEL_SANITY} (75%) sanity the crawler must not
     * track the player at all.</b> Unlike the chase thresholds (which only decide whether to
     * <i>start</i> chasing), this is a hard cancel: the target is refused at {@link #setTarget} and
     * dropped every tick, so no chase, melee or fuse can continue against a recovered player.
     *
     * <p>Non-player entities (and players without the sanity capability) are never cancelled: this rule is
     * about sanity only.
     */
    public static boolean isTrackingCancelled(@Nullable Entity entity)
    {
        float sanity = ScreamingCrawler.targetSanity(entity);
        return sanity >= 0f && sanity >= ScreamingCrawler.TRACKING_CANCEL_SANITY;
    }

    @Override
    public boolean shouldDropExperience()
    {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(@Nonnull DamageSource damageSource)
    {
        return SoundRegistry.INNER_ENTITY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundRegistry.INNER_ENTITY_DEATH.get();
    }
}