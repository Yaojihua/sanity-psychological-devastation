package piloser.sanitypd.entity;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.SanityTags;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.entity.goal.CrawlerChaseGoal;
import piloser.sanitypd.entity.goal.CrawlerSwellGoal;
import piloser.sanitypd.entity.goal.TargetInsanePlayerGoal;
import piloser.sanitypd.sound.SoundRegistry;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Screaming Crawler - the third inner entity.
 *
 * <p><b>Size</b>: quadruped, 48 MC units (3.00 m) tall - the vanilla creeper model scaled by 1.846,
 * so its proportions match the creeper exactly; hitbox {@code 0.92 x 3.0}. Near-black skin
 * ({@code #0E0E17}, alpha 100), square creeper head, large white hook eyes ({@code #FFF5D5}).
 *
 * <p><b>Self-destruct</b>: swells when a visible player is within 3 blocks, then explodes after
 * {@value #MAX_SWELL} ticks for psychic damage instead of physical damage (easy 45 / normal 60 /
 * hard 75), with vanilla block-breaking rules and a charged-creeper radius.
 * {@link #explodeCrawler()} discards the entity right after, so no death logic runs and nothing
 * drops.
 *
 * <p><b>Animation</b>: idle / walk / swell; assets under {@code geo/}, {@code animations/} and
 * {@code textures/entity/}.
 */
public class ScreamingCrawler extends InnerEntity implements GeoEntity
{
    /** Fuse length in ticks: 30 = 1.5 s, matching the vanilla creeper's maxSwell. */
    public static final int MAX_SWELL = 30;
    /** Swell direction, same meaning as the creeper's DATA_SWELL_DIR: positive = swelling, negative = deflating. */
    private static final EntityDataAccessor<Integer> DATA_SWELL_DIR =
            SynchedEntityData.defineId(ScreamingCrawler.class, EntityDataSerializers.INT);
    /** Whether the crawler was struck by lightning; a powered blast doubles the radius, as for the creeper. */
    private static final EntityDataAccessor<Boolean> DATA_POWERED =
            SynchedEntityData.defineId(ScreamingCrawler.class, EntityDataSerializers.BOOLEAN);

    /**
     * Step height in blocks: 1.1, so the crawler can walk up a full block instead of stalling on it.
     *
     * <p>Vanilla {@code maxUpStep} is 0.6 (half slab only) and {@code StepHeightEntityAttribute}
     * would raise it, but {@link #maxUpStep()} is overridden directly here: no dependency on Forge
     * attribute registration and no risk of another mod overwriting the attribute map. Values above
     * 1.1 are handled by {@code CrawlerChaseGoal}, which adds upward velocity to jump.
     */
    @Override
    public float maxUpStep()
    {
        return 1.1f;
    }

    /** Base psychic damage (normal difficulty). */
    public static final float PSYCHIC_NORMAL = 60f;

    // ------------------------------------------------------------------ chase thresholds

    /**
     * Madness threshold at which the crawler starts chasing a player: 0.80 (sanity at 20%).
     *
     * <p>{@code getMadness() = 1 - sanity/max} in [0,1], where 0 = lucid and 1 = mad, so 20% sanity
     * means madness 0.80. Do not lower this to {@code -1f}: a two-argument
     * {@code TargetInsanePlayerGoal(this, false)} sets the threshold to {@code -1f}, which makes
     * {@code getMadness() >= -1} always true and locks onto every non-creative player.
     */
    public static final float CHASE_MADNESS_THRESHOLD = 0.80f;

    /**
     * Madness threshold for dropping the chase (hysteresis, prevents flicker): 0.85 (sanity 15%).
     *
     * <p>With a single threshold, sanity hovering near 20% would re-acquire and lose the target
     * every tick. It must be higher than {@link #CHASE_MADNESS_THRESHOLD} because
     * {@code getMostInsanePlayer} picks the maddest player above the given threshold.
     */
    public static final float CHASE_MADNESS_EXIT_THRESHOLD = 0.85f;

    // ------------------------------------------------------------------ sanity-gated behaviour

    /**
     * Sanity at or above which the crawler's blast <b>leaves blocks alone</b>: 50%.
     *
     * <p>Expressed as a sanity percentage, because that is how the rule is defined ("a player above 50%
     * sanity does not get their terrain wrecked"). Madness is the complement, so this is madness
     * {@code <= 0.50}. The blast itself still happens and still deals its psychic damage - only the
     * block destruction is switched off.
     */
    public static final float NO_BLOCK_DAMAGE_SANITY = 50f;

    /**
     * Sanity at or above which the crawler <b>must not track the player at all</b>: 75%.
     *
     * <p>This is stronger than the chase thresholds above: it is not "stop chasing" but "drop the target
     * and refuse a new one", so a crawler cannot keep walking after a player who has recovered. The
     * matching branch lives in {@link InnerEntity}, next to the "ignore inner" refusal, because every
     * target assignment funnels through {@code setTarget}.
     */
    public static final float TRACKING_CANCEL_SANITY = 75f;

    /**
     * Sanity (0..100) of a player target, or {@code -1} when the entity is not a player or has no sanity
     * capability.
     *
     * <p>The crawler has no sanity of its own, so this reads the <b>target's</b> capability. It is used for
     * the two sanity gates above.
     */
    public static float targetSanity(@javax.annotation.Nullable net.minecraft.world.entity.Entity target)
    {
        if (!(target instanceof net.minecraft.world.entity.player.Player player))
            return -1f;

        ISanity cap = player.getCapability(SanityProvider.CAP).orElse(null);
        return cap == null ? -1f : cap.getSanity();
    }

    /** Whether the blast at this moment must not destroy blocks, because the target is above 50% sanity. */
    public static boolean blocksProtectedFrom(net.minecraft.world.entity.Entity target)
    {
        float sanity = targetSanity(target);
        return sanity >= 0f && sanity >= NO_BLOCK_DAMAGE_SANITY;
    }

    /** Easy difficulty: 60 x 0.75. */
    public static final float PSYCHIC_EASY = 45f;
    /** Hard difficulty: 60 x 1.5. */
    public static final float PSYCHIC_HARD = 75f;
    /** Normal explosion radius, same as the vanilla creeper. */
    public static final float RADIUS_NORMAL = 3f;
    /** Powered explosion radius, same as the charged creeper. */
    public static final float RADIUS_POWERED = 6f;

    // ---------------------------------------------------------------- sounds

    /** Roar volume: the sample itself is quiet (about -26 dB RMS), so it is raised a little. */
    public static final float ROAR_VOLUME = 1.4f;
    /** Roar cooldown in ticks (5 s) so a repeatedly lost and re-acquired target does not roar non-stop. */
    public static final int ROAR_COOLDOWN_TICKS = 100;
    /**
     * Per-player roar throttle in ticks (12 s).
     *
     * <p>{@link #ROAR_COOLDOWN_TICKS} alone is not enough because it is a per-crawler cooldown:
     * several crawlers acquiring the same player at once would overlap their 10.94 s roars and
     * drown the player in sound. With this throttle each player hears at most one roar per 240
     * ticks (12 s) no matter how many crawlers roar. 12 s is longer than the 10.94 s sample, so the
     * previous roar essentially finishes first, and players far away still hear their own roar.
     */
    public static final int ROAR_PER_PLAYER_COOLDOWN_TICKS = 240;
    /** Roar delivery radius in blocks. Volume 1.4 gives an audible radius near 16 x 1.4 = 22 blocks, so 48 is ample. */
    public static final double ROAR_HEAR_RADIUS = 48.0d;
    /**
     * Explosion volume, which must match the vanilla blast's 4.0.
     *
     * <p>Vanilla plays it at {@code volume 4.0 + Attenuation.NONE} (full volume everywhere), so a
     * positional sound at 2.0 sits 6 dB below it and gets masked. Matching 4.0 makes this sound
     * just as loud even if the vanilla one is not suppressed; the audible radius is 16 x 4 = 64.
     */
    public static final float EXPLODE_VOLUME = 4.0f;
    /**
     * Sound channel for the explosion: blocks, not hostile.
     *
     * <p>Vanilla plays its explosion through {@code SoundSource.BLOCKS}, so sharing the channel
     * keeps both explosions on the same volume slider. Otherwise a player who turns hostile volume
     * down would see the blast but hear nothing.
     */
    public static final net.minecraft.sounds.SoundSource EXPLODE_SOUND_SOURCE = net.minecraft.sounds.SoundSource.BLOCKS;
    /**
     * Ambient sound interval in ticks (30 s).
     *
     * <p>{@code Mob#getAmbientSoundInterval()} defaults to 120 ticks (6 s) while the sample is
     * 6.8 s long, so the default overlaps the sound with itself. Vanilla still multiplies the
     * interval by a random 0.8..1.2 factor.
     */
    public static final int AMBIENT_INTERVAL_TICKS = 600;

    private final AnimatableInstanceCache m_animCache = GeckoLibUtil.createInstanceCache(this);
    private int m_oldSwell;
    private int m_swell;
    /** Roar cooldown timer in ticks. */
    private int m_roarCooldown;
    /** Whether there was an attack target last tick; captures the null -> non-null transition. */
    private boolean m_hadTarget;
    /** Player UUID -> game tick when that player last heard a roar. Static so all crawlers share it. */
    private static final Map<UUID, Long> s_lastRoarHeard = new HashMap<>();

    protected ScreamingCrawler(EntityType<? extends Monster> entityType, Level level)
    {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_SWELL_DIR, -1);
        this.entityData.define(DATA_POWERED, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putShort("powered", (short)(this.entityData.get(DATA_POWERED) ? 1 : 0));
        tag.putShort("ExplosionRadius", (short)(isPowered() ? RADIUS_POWERED : RADIUS_NORMAL));
        tag.putShort("Fuse", (short)MAX_SWELL);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_POWERED, tag.getShort("powered") > 0);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Fuse priority must beat melee: like the creeper, it detonates up close instead of punching.
        this.goalSelector.addGoal(1, new CrawlerSwellGoal(this));
        // Direct chase: beyond 3 blocks it drives MoveControl itself, bypassing the path that left the
        // crawler holding a target but never moving (see CrawlerChaseGoal). Priority 2 beats
        // MeleeAttackGoal(3), so it charges at range and yields to melee/fuse up close.
        this.goalSelector.addGoal(2, new CrawlerChaseGoal(this, 1.0d));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0d, true));
        // Strolling must be registered BEFORE RandomLookAroundGoal: both claim the MOVE flag and a
        // goal cannot take a flag held by a higher-priority goal, so RandomLookAroundGoal could keep
        // MOVE forever and WaterAvoidingRandomStrollGoal never ran - the crawler just stood still.
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0d));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        // Chase threshold: do not chase at 20% sanity or above (madness < 0.80), otherwise it would
        // detonate players before it is even rendered. Hysteresis comes from the exit threshold.
        this.targetSelector.addGoal(0, new TargetInsanePlayerGoal(this, false,
                CHASE_MADNESS_THRESHOLD, CHASE_MADNESS_EXIT_THRESHOLD));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));

        super.registerGoals();
    }

    /**
     * Replaces the default navigation with {@link CrawlerPathNavigation}.
     *
     * <p>{@code GroundPathNavigation} uses a waypoint-reached radius of {@code bbWidth^2 x 3}, which
     * for a 0.92-wide crawler is 2.27 blocks - larger than the 1-block waypoint spacing. Every
     * waypoint was therefore treated as already reached and the crawler never moved along its path,
     * even though path building and knockback still worked. See {@link CrawlerPathNavigation}.
     */
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(net.minecraft.world.level.Level level)
    {
        return new CrawlerPathNavigation(this, level);
    }

    @Override
    public void tick()
    {
        if (this.level().isClientSide())
        {
            // Client side only handles visuals (swell scaling); the logic lives on the server.
            this.m_oldSwell = this.m_swell;
            int dir = this.entityData.get(DATA_SWELL_DIR);
            if (dir > 0 && this.m_swell == 0)
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(),
                        SoundEvents.CREEPER_PRIMED, this.getSoundSource(), 1.0f, 0.5f, false);
            this.m_swell += dir;
            if (this.m_swell < 0)
                this.m_swell = 0;

            // Do NOT return from here without calling super.tick() first. The client branch still
            // needs the vanilla tick: Entity#baseTick -> calculateEntityAnimation ->
            // walkAnimation.update, otherwise walkAnimation stays 0 and GeckoLib's isMoving
            // (motion >= 0.015 AND a non-zero walkAnimation) never turns true, so the legs do not
            // animate. LivingEntity#aiStep is what applies network position interpolation (lerpTo
            // only writes fields, it never calls setPos), so skipping it freezes the client position
            // at the spawn point. tickCount, onGround and deltaMovement freeze as well.
            super.tick();
            return;
        }

        if (this.isAlive())
        {
            this.m_oldSwell = this.m_swell;
            int dir = this.entityData.get(DATA_SWELL_DIR);
            this.m_swell += dir;
            if (this.m_swell < 0)
                this.m_swell = 0;

            // Roar once at the moment a target is first acquired.
            this.tickRoar();

            // Fully swollen -> explode.
            if (dir > 0 && this.m_swell >= MAX_SWELL)
            {

                this.m_swell = 0;
                this.entityData.set(DATA_SWELL_DIR, -1);
                this.explodeCrawler();
                return;
            }
        }

        super.tick();
    }

    /** Triggers a blast that deals psychic damage only; block-breaking rules match vanilla. */
    private void explodeCrawler()
    {
        if (this.level().isClientSide())
            return;

        float radius = isPowered() ? RADIUS_POWERED : RADIUS_NORMAL;
        // Psychic damage: 60 on normal, scaled by difficulty to 45 / 60 / 75
        float psychic = psychicDamageForDifficulty(this.level().getDifficulty());

        // Explosion sound. The vanilla blast also plays and cannot be stopped server-side: its
        // playback happens on the client in ClientPacketListener.handleExplosion ->
        // Explosion.finalizeExplosion, so {@code client/CrawlerExplosionSoundGuard} suppresses it
        // there by marker matching. Volume and channel must stay aligned with vanilla (4.0 / BLOCKS)
        // or the vanilla blast masks this one.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundRegistry.SCREAMING_CRAWLER_EXPLODE.get(), EXPLODE_SOUND_SOURCE, EXPLODE_VOLUME, 1.0f);

        // Order matters: register the psychic damage before level().explode(...), because that call
        // dispatches ExplosionEvent.Detonate and the handler must already know the value in order to
        // replace the vanilla physical damage.
        piloser.sanitypd.event.CrawlerExplosionHandler.registerPending(this, psychic);

        // Sanity gate: a target at or above 50% sanity must not have their terrain destroyed by the blast.
        // Detected here, at detonation, so the rule follows the target's sanity at the moment that matters.
        boolean keepBlocks = blocksProtectedFrom(this.getTarget());
        if (keepBlocks)
        {
            piloser.sanitypd.event.CrawlerExplosionHandler.registerBlockSafe(this);
            SanityMod.LOGGER.info("[CRAWLER-SANITY] blast from {} spares blocks (target sanity {} >= {})",
                    this.getId(), targetSanity(this.getTarget()), NO_BLOCK_DAMAGE_SANITY);
        }

        this.dead = true;
        this.level().explode(this, this.getX(), this.getY() + 0.0625d, this.getZ(),
                radius, Level.ExplosionInteraction.MOB);
        this.discard();
    }

    // ---------------------------------------------------------------- sounds

    /**
     * One-shot roar when a target is first acquired.
     *
     * <p>Vanilla has no "target acquired" event, so this watches {@link #getTarget()} for a
     * null -> non-null transition. Which goal set the target does not matter;
     * {@link TargetInsanePlayerGoal}, {@code HurtByTargetGoal} and {@code Monster}'s own
     * {@code NearestAttackableTargetGoal} all count.
     *
     * <p>{@link #ROAR_COOLDOWN_TICKS} prevents back-to-back roars when the target is lost and
     * re-acquired repeatedly.
     */
    private void tickRoar()
    {
        if (this.m_roarCooldown > 0)
            this.m_roarCooldown--;

        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();

        if (hasTarget && !this.m_hadTarget && this.m_roarCooldown <= 0)
        {
            this.playRoarThrottled();
            this.m_roarCooldown = ROAR_COOLDOWN_TICKS;
        }

        this.m_hadTarget = hasTarget;
    }

    /**
     * Sends the roar to nearby players, throttled per player.
     *
     * <p>{@code this.playSound(...)} would broadcast to every nearby player, and a per-crawler
     * cooldown cannot stop several crawlers from roaring at the same player at once (see
     * {@link #ROAR_PER_PLAYER_COOLDOWN_TICKS}).
     *
     * <p>This sends {@link ClientboundSoundPacket} manually instead, so each player hears at most
     * one roar per {@link #ROAR_PER_PLAYER_COOLDOWN_TICKS} ticks no matter how many crawlers roar.
     */
    private void playRoarThrottled()
    {
        if (!(this.level() instanceof ServerLevel level))
            return;

        SoundEvent roar = SoundRegistry.SCREAMING_CRAWLER_ROAR.get();
        long now = level.getGameTime();

        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(this) > ROAR_HEAR_RADIUS * ROAR_HEAR_RADIUS)
                continue;

            // FakePlayer has no connection; real players always do.
            if (player.connection == null)
                continue;

            Long lastHeard = s_lastRoarHeard.get(player.getUUID());

            if (lastHeard != null && now - lastHeard < ROAR_PER_PLAYER_COOLDOWN_TICKS)
                continue;

            s_lastRoarHeard.put(player.getUUID(), now);
            player.connection.send(new ClientboundSoundPacket(
                    Holder.direct(roar), this.getSoundSource(),
                    this.getX(), this.getY(), this.getZ(),
                    ROAR_VOLUME, this.getVoicePitch(), level.random.nextLong()));
        }
    }

    /** Ambient sound: vanilla {@code Mob} schedules it at random intervals, so no custom timer is needed. */
    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundRegistry.SCREAMING_CRAWLER_AMBIENT.get();
    }

    /**
     * Ambient sound interval.
     *
     * <p>Vanilla {@code Mob} defaults to 120 ticks (6 s) while the sample is 6.8 s long, so the
     * default would overlap the sound with itself. Set to {@link #AMBIENT_INTERVAL_TICKS} (30 s).
     */
    @Override
    public int getAmbientSoundInterval()
    {
        return AMBIENT_INTERVAL_TICKS;
    }

    /** No ambient sound while the fuse is lit; it would clash with the swell pose and the blast. */
    @Override
    public void playAmbientSound()
    {
        if (this.getSwellDir() > 0)
            return;

        super.playAmbientSound();
    }

    /**
     * Psychic damage by difficulty: easy 45 / normal 60 / hard 75. The 1.20.1 {@code Difficulty}
     * enum has no built-in damage multiplier, so the mapping is explicit.
     */
    public static float psychicDamageForDifficulty(net.minecraft.world.Difficulty difficulty)
    {
        if (difficulty == net.minecraft.world.Difficulty.PEACEFUL)
            return PSYCHIC_EASY;
        if (difficulty == net.minecraft.world.Difficulty.EASY)
            return PSYCHIC_EASY;
        if (difficulty == net.minecraft.world.Difficulty.HARD)
            return PSYCHIC_HARD;
        return PSYCHIC_NORMAL;
    }

    // ---------------------------------------------------------------- fuse

    public int getSwellDir()
    {
        return this.entityData.get(DATA_SWELL_DIR);
    }

    public void setSwellDir(int dir)
    {
        this.entityData.set(DATA_SWELL_DIR, dir);
    }

    public boolean isPowered()
    {
        return this.entityData.get(DATA_POWERED);
    }

    public void setPowered(boolean powered)
    {
        this.entityData.set(DATA_POWERED, powered);
    }

    /** Swell progress from 0 to 1 for rendering/scaling, equivalent to the creeper's {@code getSwelling}.
     *
     * <p>{@code m_oldSwell} / {@code m_swell} are updated in both the client and the server tick
     * (the vanilla creeper does the same), so the render pass has valid interpolation endpoints on
     * both sides. */
    public float getSwelling(float partialTick)
    {
        return net.minecraft.util.Mth.lerp(partialTick, (float)this.m_oldSwell, (float)this.m_swell) / (float)(MAX_SWELL - 2);
    }

    /** Struck by lightning -> powered; a powered blast doubles the radius, as for the creeper. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount)
    {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT))
            this.setPowered(true);
        return super.hurt(source, amount);
    }

    // ---------------------------------------------------------------- animation

    /**
     * Playback speed multiplier for the walk animation.
     *
     * <p>The creeper's legs swing once per unit of distance travelled
     * ({@code cos(limbSwing * 0.6662)}), while GeckoLib keyframes advance at a fixed rate per
     * second, so the two cannot match exactly. One full stride at 1.3x comes to roughly 0.65 s,
     * which looks closest to vanilla at this mob's actual movement speed.
     */
    public static final double WALK_ANIM_SPEED = 1.3d;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar)
    {
        controllerRegistrar.add(new AnimationController<GeoAnimatable>(this, "main", 5, state ->
        {
            // 1. Swelling -> swell animation (vanilla only scales; this is a slight wind-up for the new model)
            if (this.getSwellDir() > 0)
                return state.setAndContinue(SWELL_ANIM);

            // 2. Moving -> walk
            if (state.isMoving())
                return state.setAndContinue(DefaultAnimations.WALK);

            // 3. Otherwise -> idle
            //    Vanilla creepers are completely still when stationary, so the idle animation is
            //    deliberately subtle: only a slight head bob, to avoid noisy motion.
            return state.setAndContinue(DefaultAnimations.IDLE);
        }).setAnimationSpeedHandler(animatable ->
        {
            // Slow down while swelling so the wind-up is readable; fixed speed while walking.
            ScreamingCrawler c = (ScreamingCrawler)animatable;
            if (c.getSwellDir() > 0)
                return 0.6d;
            return c.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6d ? WALK_ANIM_SPEED : 1.0d;
        }));
    }

    /** Swell animation: named {@code misc.swell} in the assets, played only during {@link #MAX_SWELL}. */
    private static final software.bernie.geckolib.core.animation.RawAnimation SWELL_ANIM =
            software.bernie.geckolib.core.animation.RawAnimation.begin()
                    .thenLoop("misc.swell");

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return m_animCache;
    }

    /** Attributes: a mid-weight threat - 120 health, 9 attack damage and slightly fast movement. */
    public static AttributeSupplier buildAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0d)
                .add(Attributes.FOLLOW_RANGE, 96.0d)
                .add(Attributes.ATTACK_DAMAGE, 9.0d)
                .add(Attributes.MOVEMENT_SPEED, .27d)
                .add(Attributes.KNOCKBACK_RESISTANCE, .4d)
                .add(Attributes.ATTACK_KNOCKBACK, .6d)
                .build();
    }

    /** Inner-entity tag check (handy for debug printing). */
    public boolean isInner()
    {
        return SanityTags.isInnerEntity(this);
    }

    /**
     * Screaming Crawler psychic resistance: 20 points, i.e. 20% reduction.
     *
     * <p>Example: its own 60-point blast hitting another crawler deals
     * 60 x (1 - 0.20) x 2.5 = 120 real damage.
     */
    @Override
    public float defaultPsychicResistance()
    {
        return 20f;
    }

    /** Debug helper: current sanity (inner entities have no sanity capability, so -1). */
    public float debugSanity()
    {
        ISanity cap = this.getCapability(SanityProvider.CAP).orElse(null);
        return cap == null ? -1f : cap.getSanity();
    }


}
