package piloser.sanitypd.event;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.SanityTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only "block spawning" switches. There are <b>two independent switches</b> that can be combined:
 *
 * <ul>
 *   <li>{@code /sanity nospawn on|off|status} - blocks <b>non-inner</b> mobs (slimes included);</li>
 *   <li>{@code /sanity innerspawn on|off|status} - the opposite, blocks <b>inner</b> mobs.
 *       With both on, nothing spawns at all.</li>
 * </ul>
 *
 * <h2>What each switch does</h2>
 * <ol>
 *   <li><b>Clears the world as soon as it is enabled</b>: every matching entity in <b>all dimensions</b>
 *       is {@code discard()}ed (removed, no drops, no death logic - the cleanest state for testing);</li>
 *   <li><b>Rejects new spawns while active</b>: {@link EntityJoinLevelEvent} is cancelled outright,
 *       which covers natural spawning, spawners, spawn eggs and {@code /summon};</li>
 *   <li><b>Sweeps again every 5 seconds</b> to catch anything that existed before the switch was
 *       turned on or slipped past the join event.</li>
 * </ol>
 *
 * <h2>The filter must use the entity category, not {@code instanceof Monster}</h2>
 * Slimes are {@code Slime extends Mob implements Enemy} and are <b>not</b> {@code Monster}s
 * (zombies, skeletons and spiders are), but they do register under
 * {@link MobCategory#MONSTER}, so <b>only filtering by category catches them too</b>.
 * The same applies to phantoms, magma cubes, drowned, piglins and so on, with no need to list them.
 *
 * <p>Inner entities are identified through the {@code sanitypd:inner_entities} tag, so entities an
 * add-on adds to that tag are treated consistently by both switches.
 */
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TestSpawnGuard
{
    /** Sweep interval in ticks = 5 seconds. */
    private static final int SWEEP_INTERVAL = 100;

    /** Switch state (one copy per server instance, reset on restart - a test helper needs no persistence). */
    private static boolean s_blockNonInner;
    private static boolean s_blockInner;
    private static int s_tick;

    private TestSpawnGuard() {}

    // ------------------------------------------------------------------ nospawn (non-inner mobs)

    public static boolean isEnabled()
    {
        return s_blockNonInner;
    }

    // ------------------------------------------------------------------ innerspawn (inner mobs)

    public static boolean isInnerBlockEnabled()
    {
        return s_blockInner;
    }

    // ------------------------------------------------------------------ switches

    /**
     * Turns "block non-inner mob spawning" on or off ({@code /sanity nospawn}).
     *
     * @return how many entities this call cleared when enabled, 0 when disabled
     */
    public static int setEnabled(MinecraftServer server, boolean enabled)
    {
        s_blockNonInner = enabled;

        if (!enabled)
            return 0;

        int removed = clearAll(server);
        SanityMod.LOGGER.info("[NOSPAWN] spawn guard enabled: immediately removed {} non-inner monster(s)", removed);
        return removed;
    }

    /**
     * Turns "block inner mob spawning" on or off ({@code /sanity innerspawn}).
     *
     * @return how many inner entities this call cleared when enabled, 0 when disabled
     */
    public static int setInnerBlockEnabled(MinecraftServer server, boolean enabled)
    {
        s_blockInner = enabled;

        if (!enabled)
            return 0;

        int removed = clearInnerAll(server);
        SanityMod.LOGGER.info("[NOSPAWN] inner-entity guard enabled: immediately removed {} inner entit(ies)", removed);
        return removed;
    }

    // ------------------------------------------------------------------ filter predicates

    /**
     * Whether {@code nospawn} should block this entity: it is in the
     * <b>{@link MobCategory#MONSTER} category</b> and <b>not tagged as an inner entity</b>.
     *
     * <p>Do not change this to {@code entity instanceof Monster}: that <b>misses slimes</b> (see the class comment).
     *
     * <p><b>Bosses are exempt</b> - see {@link #isProtectedBoss}. The Ender Dragon, the Wither, the Warden
     * and the Elder Guardian all register under {@code MONSTER}, so without that exemption switching this
     * guard on deleted the dragon out of an existing world with no death, no drops and no way to get it
     * back: the end portal could never be opened again and the save was effectively destroyed. The same
     * risk applies to any other one-per-world entity, which is why the exemption exists.
     */
    public static boolean isBlockedMonster(Entity entity)
    {
        if (entity == null)
            return false;

        if (isProtectedBoss(entity))
            return false;

        if (entity.getType().getCategory() != MobCategory.MONSTER)
            return false;

        return !SanityTags.isInnerEntity(entity);
    }

    /**
     * Entities this test guard must <b>never</b> remove, whatever their spawn category.
     *
     * <h2>Why this list has to exist</h2>
     * The guard is a debugging aid, and it clears existing entities as well as blocking new ones. Bosses
     * share the {@code MONSTER} category with ordinary mobs, so a category-only filter treats the Ender
     * Dragon as "just another monster" and discards it. That is not a cosmetic loss: the dragon is
     * one-per-world and cannot respawn, so the player's save can no longer be completed. A test switch must
     * not be able to do that.
     *
     * <p>The list is intentionally short and explicit rather than a config option: protection from
     * save-destroying behaviour should not depend on a setting being correct.
     */
    private static boolean isProtectedBoss(Entity entity)
    {
        return entity.getType() == net.minecraft.world.entity.EntityType.ENDER_DRAGON
                || entity.getType() == net.minecraft.world.entity.EntityType.WITHER
                || entity.getType() == net.minecraft.world.entity.EntityType.WARDEN
                || entity.getType() == net.minecraft.world.entity.EntityType.ELDER_GUARDIAN;
    }

    /**
     * Whether {@code innerspawn} should block this entity: it <b>is tagged as an inner entity</b>.
     *
     * <p>Exactly complementary to {@link #isBlockedMonster}, so with both switches on not a single
     * {@link MobCategory#MONSTER} entity is left behind.
     */
    public static boolean isBlockedInner(Entity entity)
    {
        return SanityTags.isInnerEntity(entity);
    }

    /** Blocked when either switch blocks it. */
    private static boolean isBlockedByAny(Entity entity)
    {
        return (s_blockNonInner && isBlockedMonster(entity))
                || (s_blockInner && isBlockedInner(entity));
    }

    // ------------------------------------------------------------------ events

    /** A new entity joins the level -&gt; reject it outright. */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event)
    {
        if ((!s_blockNonInner && !s_blockInner) || event.getLevel().isClientSide())
            return;

        if (isBlockedByAny(event.getEntity()))
            event.setCanceled(true);
    }

    /** Periodic re-sweep. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if ((!s_blockNonInner && !s_blockInner) || event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % SWEEP_INTERVAL != 0)
            return;

        int removed = 0;
        if (s_blockNonInner)
            removed += clearAll(event.getServer());
        if (s_blockInner)
            removed += clearInnerAll(event.getServer());

        if (removed > 0)
            SanityMod.LOGGER.info("[NOSPAWN] rescan removed {} additional blocked mob(s)", removed);
    }

    // ------------------------------------------------------------------ world clearing

    /** Removes every non-inner mob in all dimensions. */
    public static int clearAll(MinecraftServer server)
    {
        return clearMatching(server, TestSpawnGuard::isBlockedMonster);
    }

    /** Removes every inner entity in all dimensions. */
    public static int clearInnerAll(MinecraftServer server)
    {
        return clearMatching(server, TestSpawnGuard::isBlockedInner);
    }

    private static int clearMatching(MinecraftServer server, java.util.function.Predicate<Entity> filter)
    {
        int removed = 0;

        for (ServerLevel level : server.getAllLevels())
            removed += clearIn(level, filter);

        return removed;
    }

    private static int clearIn(ServerLevel level, java.util.function.Predicate<Entity> filter)
    {
        // Collect first, then remove: discarding while iterating the entity table would modify
        // the collection being traversed
        List<Entity> victims = new ArrayList<>();

        for (Entity entity : level.getAllEntities())
        {
            if (filter.test(entity))
                victims.add(entity);
        }

        for (Entity victim : victims)
            victim.discard();   // discard: removes with no drops and no death logic

        return victims.size();
    }
}
