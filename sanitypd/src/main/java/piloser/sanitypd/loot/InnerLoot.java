package piloser.sanitypd.loot;

import piloser.sanitypd.SanityProcessor;
import piloser.sanitypd.SanityTags;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.client.render.layer.Blackout;
import piloser.sanitypd.effect.EffectRegistry;
import piloser.sanitypd.effect.ManiaImmunityEffect;
import piloser.sanitypd.item.ItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
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
 * <h2>2. Inner mobs -> experience + inner clump + sanity + mania immunity</h2>
 * 30 experience, 1 clump 60% / 2 clumps 40%, {@value #KILL_SANITY_REWARD} sanity restored to the killer,
 * and {@link ManiaImmunityEffect} applied. That effect blocks the 1 damage per second from mania for
 * 10 seconds and shows a green shield plus heart icon in the top right.
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

        // ---- inner mobs ----
        if (SanityTags.isInnerEntity(entity))
        {
            dropItems(event.getDrops(), entity, ItemRegistry.INNER_CLUMP.get(), rollClumpCount(entity.getRandom()));

            killer.getCapability(SanityProvider.CAP).ifPresent(cap ->
                    SanityProcessor.addSanity(cap, KILL_SANITY_REWARD, killer));

            // Mania damage immunity for 10 seconds, shown as an icon
            // (textures/mob_effect/mania_immunity.png, 16x16); with showIcon=false the player would have
            // no way to know the reward was granted.
            killer.addEffect(new MobEffectInstance(EffectRegistry.MANIA_IMMUNITY.get(),
                    ManiaImmunityEffect.DURATION_TICKS, 0, false, true), killer);
            return;
        }

        // ---- darkened animals (black only for a killer with madness >= 0.7) ----
        if (!isBlackoutAnimal(entity))
            return;

        ISanity cap = killer.getCapability(SanityProvider.CAP).orElse(null);
        if (cap == null || cap.getMadness() < Blackout.THRESHOLD)
            return;

        dropItems(event.getDrops(), entity, ItemRegistry.INNER_SHARD.get(), rollShardCount(entity.getRandom()));
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
