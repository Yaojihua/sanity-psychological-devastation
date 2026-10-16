package piloser.sanitypd.loot;

import piloser.sanitypd.SanityProcessor;
import piloser.sanitypd.SanityTags;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.client.render.layer.Blackout;
import piloser.sanitypd.effect.EffectRegistry;
import piloser.sanitypd.effect.ManiaImmunityEffect;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.thought.IThoughtChain;
import piloser.sanitypd.thought.ThoughtChainProvider;
import piloser.sanitypd.thought.ThoughtEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;

/**
 * Drop rules for inner matter. All of this runs on the server.
 *
 * <h2>1. Darkened animals -> inner shards</h2>
 * The "animals turn black" effect is a <b>client-side illusion</b>: {@code Mixin*Renderer#getTextureLocation}
 * tests the <b>observing player's</b> own madness against {@link Blackout#THRESHOLD} (0.7), so the server
 * does not know the animal looks black. The condition here is therefore the killer's madness being
 * {@code >= 0.7}, exactly matching what the player sees.
 *
 * <p>Only <b>chicken / cow / pig / sheep</b> can appear darkened, since {@code Blackout} defines only
 * those four textures plus a wool layer; shards drop only from these four, so a mob never drops a shard
 * while looking normal. Counts: 1 shard 50%, 2 shards 30%, 3 shards 20%.
 *
 * <h2>1b. The four creature visions (Hallucination batch)</h2>
 * Four thoughts widen that same payout to creatures the player may not see as dark at all: the horse
 * family, villagers/illagers/wandering traders, llamas and goats, and the water creatures. The gate is the
 * <b>same</b> one - the killer's madness at or above {@link Blackout#THRESHOLD} - and the roll is the same
 * {@link #rollShardCount}, so a vision adds creatures to the rule rather than a second rule. The mapping
 * lives in {@link #isHorseKind} and its three siblings, one place each.
 *
 * <h2>2. Inner mobs -> experience + inner clump + sanity + mania immunity</h2>
 * 30 experience, 1 clump 60% / 2 clumps 40%, {@value #KILL_SANITY_REWARD} sanity restored to the killer,
 * and {@link ManiaImmunityEffect} applied. That effect blocks the 1 damage per second from mania for
 * 10 seconds and shows a green shield plus heart icon in the top right.
 *
 * <h2>3. Convergent Evolution raises every count the holder causes</h2>
 * While that thought is held, each inner shard and inner clump payout above is increased by
 * {@link ThoughtEffects#CONVERGENT_BONUS} and rounded up - one multiplier, applied where the count is
 * decided, so no drop path can be forgotten.
 */
public class InnerLoot
{
    // ---- dark animals drop shards ----
    /** Chance of dropping 1 shard. */
    public static final float SHARD_1_CHANCE = .50f;
    /** Chance of dropping 2 shards (cumulative 0.80, leaving 0.20 for 3). */
    public static final float SHARD_2_CHANCE = .30f;

    // ---- inner mobs ----
    /** Experience dropped by an inner mob. */
    public static final int INNER_XP = 30;
    /** Chance of dropping 1 clump (the remaining 0.40 gives 2). */
    public static final float CLUMP_1_CHANCE = .60f;
    /**
     * Chance of a screaming crawler dropping 1 shadow gunpowder (the remaining 0.40 gives 2).
     *
     * <p>Separate from {@link #CLUMP_1_CHANCE} even though the numbers are the same today: they are two
     * independent payouts of one kill, and moving one must never move the other.
     */
    public static final float SHADOW_GUNPOWDER_1_CHANCE = .60f;
    /** Sanity restored for killing an inner mob; goes through the standard channel and is scaled by
     *  positive_multiplier, so the default 1.0 yields exactly 10. */
    public static final float KILL_SANITY_REWARD = 10f;

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide())
            return;

        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof ServerPlayer killer) || killer == entity)
            return;

        boolean convergent = holdsConvergentEvolution(killer);

        // ---- inner mobs ----
        if (SanityTags.isInnerEntity(entity))
        {
            dropItems(event.getDrops(), entity, ItemRegistry.INNER_CLUMP.get(),
                    innerYield(rollClumpCount(entity.getRandom()), convergent));

            // The screaming crawler is the only source of shadow gunpowder, and this payout is an
            // <b>extra</b> roll on top of the clump - it removes nothing from the existing drop.
            //
            // Note that this is a LivingDropsEvent, so it needs a real player kill. A crawler that reaches
            // the end of its own fuse calls discard() instead of dying, so a self-detonation leaves no
 // drops at all - which is how the rule reads: "kill one, 60% for 1, 40% for 2".
            if (entity instanceof piloser.sanitypd.entity.ScreamingCrawler)
                dropItems(event.getDrops(), entity, ItemRegistry.SHADOW_GUNPOWDER.get(),
                        innerYield(rollShadowGunpowderCount(entity.getRandom()), convergent));

            killer.getCapability(SanityProvider.CAP).ifPresent(cap ->
                    SanityProcessor.addSanity(cap, KILL_SANITY_REWARD, killer));

            // Mania damage immunity for 10 seconds, shown as an icon
            // (textures/mob_effect/mania_immunity.png, 16x16); with showIcon=false the player would have
            // no way to know the reward was granted.
            killer.addEffect(new MobEffectInstance(EffectRegistry.MANIA_IMMUNITY.get(),
                    ManiaImmunityEffect.DURATION_TICKS, 0, false, true), killer);
            return;
        }

        // ---- darkened animals, and the creatures one of the four visions covers ----
        if (!paysShards(killer, entity))
            return;

        dropItems(event.getDrops(), entity, ItemRegistry.INNER_SHARD.get(),
                innerYield(rollShardCount(entity.getRandom()), convergent));
    }

    /**
     * Whether this kill pays out inner shards: a darkened animal, or a creature a vision thought of the
     * killer's covers - both only while the killer's madness is at or above {@link Blackout#THRESHOLD},
     * which is what "low sanity" means for this payout everywhere else too.
     */
    public static boolean paysShards(ServerPlayer killer, LivingEntity entity)
    {
        ISanity cap = killer.getCapability(SanityProvider.CAP).orElse(null);

        if (cap == null || cap.getMadness() < Blackout.THRESHOLD)
            return false;

        return isBlackoutAnimal(entity) || visionCovers(killer, entity);
    }

    /** Whether one of the four creature visions the killer holds covers this creature. */
    public static boolean visionCovers(ServerPlayer killer, LivingEntity entity)
    {
        IThoughtChain chain = killer.getCapability(ThoughtChainProvider.CAP).orElse(null);

        if (chain == null)
            return false;

        if (isHorseKind(entity) && chain.holds(ItemRegistry.THOUGHT_HALLUCINATION_HORSE.get()))
            return true;

        if (isHumanKind(entity) && chain.holds(ItemRegistry.THOUGHT_HALLUCINATION_HUMAN.get()))
            return true;

        if (isSheepKind(entity) && chain.holds(ItemRegistry.THOUGHT_HALLUCINATION_SHEEP.get()))
            return true;

        return isAquatic(entity) && chain.holds(ItemRegistry.THOUGHT_HALLUCINATION_FISH.get());
    }

    /**
     * Whether the killer holds Convergent Evolution, which raises every inner-material count it causes.
     *
     * <p>Read once per death, next to the payout it changes, so both the clump path and the shard path use
     * the same answer.
     */
    public static boolean holdsConvergentEvolution(ServerPlayer killer)
    {
        return killer.getCapability(ThoughtChainProvider.CAP)
                .map(chain -> chain.holds(ItemRegistry.THOUGHT_HALLUCINATION_CONVERGENT_EVOLUTION.get()))
                .orElse(false);
    }

    /**
     * One inner-material count after Convergent Evolution: the base count plus
     * {@link ThoughtEffects#CONVERGENT_BONUS}, rounded up.
     *
     * <p>Pure, and taking the base count rather than a random source, so the rounding can be asserted on
     * every boundary (1 -> 2, 2 -> 4, 3 -> 6) without waiting for a lucky roll.
     */
    public static int innerYield(int base, boolean convergent)
    {
        return convergent ? (int) Math.ceil(base * (1f + ThoughtEffects.CONVERGENT_BONUS)) : base;
    }

    // ------------------------------------------------------------------ the four creature visions

    /** Horse: the horse family, llamas excepted - they are the sheep vision's business. */
    public static boolean isHorseKind(LivingEntity entity)
    {
        return entity instanceof AbstractHorse && !(entity instanceof Llama);
    }

    /**
     * Human: villagers, illagers (the raid family - pillagers, vindicators, evokers and illusioners) and
     * wandering traders.
     *
     * <p>The illager base class in the official mappings is {@code AbstractIllager}; a ravager and a witch are
     * raiders but not illagers, so they are not covered by this vision.
     */
    public static boolean isHumanKind(LivingEntity entity)
    {
        return entity instanceof Villager || entity instanceof WanderingTrader || entity instanceof AbstractIllager;
    }

    /** Sheep: llamas (trader llamas ride on Llama) and goats. */
    public static boolean isSheepKind(LivingEntity entity)
    {
        return entity instanceof Llama || entity instanceof Goat;
    }

    /** Fish: the four fish, squid (glow squid included), dolphins, turtles, guardians, elder guardians. */
    public static boolean isAquatic(LivingEntity entity)
    {
        return entity instanceof AbstractFish || entity instanceof Squid || entity instanceof Dolphin
                || entity instanceof Turtle || entity instanceof Guardian || entity instanceof ElderGuardian;
    }

    /**
     * Inner mobs always award 30 experience.
     *
     * <p>{@code LivingExperienceDropEvent} is not used here: it only fires for mobs that already have
     * vanilla experience (a cow, for example) and never for our custom inner mobs, so the award is made
     * manually from {@code LivingDeathEvent}. Inner mobs have no vanilla experience, so this grants 30
     * rather than adding to an existing value.
     */
    @SubscribeEvent
    public void onInnerEntityDeath(LivingDeathEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !SanityTags.isInnerEntity(entity))
            return;
        if (entity.level() instanceof ServerLevel level)
            ExperienceOrb.award(level, entity.position(), INNER_XP);
    }

    // ------------------------------------------------------------------ checks and rolls

    /**
     * Whether this is one of the four animals that can appear darkened.
     * Matches the texture set defined in {@code Blackout} (chicken / cow / pig / sheep plus the wool layer).
     */
    public static boolean isBlackoutAnimal(LivingEntity entity)
    {
        return entity instanceof Chicken || entity instanceof Cow
                || entity instanceof Pig || entity instanceof Sheep;
    }

    /** Shard count: 1 (50%), 2 (30%), 3 (20%). */
    public static int rollShardCount(RandomSource rand)
    {
        float roll = rand.nextFloat();
        if (roll < SHARD_1_CHANCE)
            return 1;
        if (roll < SHARD_1_CHANCE + SHARD_2_CHANCE)
            return 2;
        return 3;
    }

    /** Clump count: 1 (60%) or 2 (40%). */
    public static int rollClumpCount(RandomSource rand)
    {
        return rand.nextFloat() < CLUMP_1_CHANCE ? 1 : 2;
    }

 /** Shadow gunpowder count: 1 (60%) or 2 (40%), by the specification. */
    public static int rollShadowGunpowderCount(RandomSource rand)
    {
        return rand.nextFloat() < SHADOW_GUNPOWDER_1_CHANCE ? 1 : 2;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Adds one item to the drops of this death.
     *
     * <p>{@code LivingDropsEvent#getDrops()} holds {@link ItemEntity}, not ItemStack, so the entity has
     * to be created by hand; the whole count goes into a single stack.
     */
    private static void dropItems(Collection<ItemEntity> drops, LivingEntity entity, Item item, int count)
    {
        if (count <= 0)
            return;

        ItemEntity drop = new ItemEntity(entity.level(),
                entity.getX(), entity.getY() + .5d, entity.getZ(), new ItemStack(item, count));
        drop.setDefaultPickUpDelay();
        drops.add(drop);
    }
}
