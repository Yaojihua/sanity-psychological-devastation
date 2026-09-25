package piloser.sanitypd.event;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.SanityTags;
import piloser.sanitypd.damage.SanityDamageTypes;
import piloser.sanitypd.entity.ScreamingCrawler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Screaming crawler explosion handling: <b>replace the vanilla physical explosion damage with psychic damage</b>.
 *
 * <h2>Why</h2>
 * The explosion radius and block-breaking rules match a creeper (a charged creeper when charged), but
 * entities inside the radius must take <b>no physical damage</b>; instead each of them takes a fixed
 * amount of psychic damage (easy 45 / normal 60 / hard 75).
 *
 * <h2>How</h2>
 * Vanilla {@code Explosion.explode()} has a Forge hook just before the damage loop:
 * <pre>
 *   List&lt;Entity&gt; list = level.getEntities(...);
 *   ForgeEventFactory.onExplosionDetonate(level, this, list, radius);   // Detonate event
 *   for (Entity e : list) { e.hurt(damageSource, damage); }             // vanilla damage
 * </pre>
 * So:
 * <ol>
 *   <li>{@link #onDetonate} <b>clears</b> {@code getAffectedEntities()}, so no entity takes vanilla damage;</li>
 *   <li>the entities that would have been hit are recorded;</li>
 *   <li>at the end of the tick ({@link #onServerTick}) they take <b>psychic damage</b>, scaled by the
 *       same vanilla line-of-sight factor {@link Explosion#getSeenPercent}, so entities behind blocks
 *       lose less sanity - matching the geometry of a creeper explosion.</li>
 * </ol>
 * Block destruction stays entirely vanilla and is not modified (charged radius 6 = charged creeper).
 *
 * <h2>The damage type here <b>must</b> be psychic</h2>
 * This is not an implementation detail but the definition of the mechanic:
 * <ol>
 *   <li>the "drain sanity + convert overflow to true damage" branch in
 *       {@code SanityCombat.onLivingHurt} is gated on {@code isPsychic(source)};</li>
 *   <li>so only {@link SanityDamageTypes#dealPsychic} drains sanity;</li>
 *   <li>the <b>overflow</b> past zero sanity becomes true damage (only that part costs health);</li>
 *   <li>the death message comes from that overflow segment: {@code SanityCombat} swaps it to
 *       {@code crawler_explosion} when the attacker is a crawler.</li>
 * </ol>
 *
 * <p>So do <b>not</b> replace the call below with {@code dealCrawlerExplosion}: once the type is no
 * longer psychic the hit stops draining sanity and degrades into ordinary physical damage, breaking
 * the whole mechanic.
 *
 * <h2>Inner entities are <b>immune</b> to each other's explosions</h2>
 * The resolution loop <b>skips</b> inner entities based on the {@code sanitypd:inner_entities} tag
 * ({@link SanityTags#isInnerEntity}): otherwise a crawler could kill other inner entities.
 * A skipped inner entity therefore takes neither psychic damage nor overflow true damage.
 */
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrawlerExplosionHandler
{
    /** Crawlers registered during this tick -> the psychic damage they should apply. */
    private static final Map<ScreamingCrawler, Float> PENDING = new IdentityHashMap<>();
    /** Explosions registered during this tick -> the entities they would have hit (collected in the Detonate phase). */
    private static final Map<Explosion, List<Entity>> VICTIMS = new IdentityHashMap<>();
    /**
     * Explosion -> victim -> line-of-sight factor, sampled in the Detonate phase <b>before</b> the block
     * list is cleared.
     *
     * <p>{@code Explosion.getSeenPercent} raytraces against {@code Explosion#getToBlow}, so it must be
     * sampled while that list is still populated: measuring it later (when the blast is told to spare the
     * terrain) would see an empty list and produce a meaningless factor.
     */
    private static final Map<Explosion, Map<Entity, Float>> SEEN = new IdentityHashMap<>();
    /** Crawlers whose blast must not destroy blocks this tick (target at or above 50% sanity). */
    private static final Map<ScreamingCrawler, Boolean> BLOCK_SAFE = new IdentityHashMap<>();

    private CrawlerExplosionHandler() {}

    /** Called by {@link ScreamingCrawler#explodeCrawler()} <b>before</b> the explosion is triggered. */
    public static void registerPending(ScreamingCrawler crawler, float psychicDamage)
    {
        PENDING.put(crawler, psychicDamage);
    }

    /**
     * Marks this crawler's blast as "leave the terrain alone" for the current tick.
     *
     * <p>Triggered when the target's sanity is at or above {@link ScreamingCrawler#NO_BLOCK_DAMAGE_SANITY}
     * (50%). The blast still happens, still deals its psychic damage and still plays its sound; only the
     * block destruction is removed (see {@link #onDetonate}).
     */
    public static void registerBlockSafe(ScreamingCrawler crawler)
    {
        BLOCK_SAFE.put(crawler, Boolean.TRUE);
    }

    /**
     * At detonation: clears the list of entities vanilla would damage and records them instead.
     *
     * <p>An explosion that is not ours is left completely alone (vanilla and other mods' creepers keep working).
     */
    @SubscribeEvent
    public static void onDetonate(final ExplosionEvent.Detonate event)
    {
        Explosion explosion = event.getExplosion();
        Entity exploder = explosion.getExploder();
        if (!(exploder instanceof ScreamingCrawler))
            return;

        // Sample the entities that would have been hit (including the positions needed for the line-of-sight check)
        List<Entity> victims = new ArrayList<>(event.getAffectedEntities());
        VICTIMS.put(explosion, victims);

        // Line-of-sight factors must be sampled while the block list is still intact: getSeenPercent
        // raytraces against Explosion#getToBlow, which is emptied below when the blast spares the terrain.
        Map<Entity, Float> seen = new IdentityHashMap<>();
        Vec3 blastCenter = explosion.getPosition();
        for (Entity victim : victims)
            seen.put(victim, Explosion.getSeenPercent(blastCenter, victim));
        SEEN.put(explosion, seen);


        // The key step: clearing this makes the vanilla damage loop do nothing at all
        event.getAffectedEntities().clear();

        // Sanity gate: at or above 50% sanity the blast must not break any block. Everything else about the
        // explosion is unchanged - the entity list above was already sampled, so the psychic damage is
        // applied exactly as it would have been.
        if (BLOCK_SAFE.containsKey(exploder))
        {
            int blocks = event.getAffectedBlocks().size();
            event.getAffectedBlocks().clear();
            SanityMod.LOGGER.info("[CRAWLER-SANITY] {} block(s) spared by the blast of crawler {}",
                    blocks, ((ScreamingCrawler)exploder).getId());
        }
    }

    /**
     * Applies the psychic damage for this tick, all at once at the end.
     *
     * <p>Why the end of the tick: damaging directly inside Detonate can trigger further explosions
     * (for example when another crawler is killed) while the registry is being iterated. Doing it at
     * the end of the tick is the most robust option and differs from vanilla damage timing by well
     * under one tick, which players cannot perceive.
     */
    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (PENDING.isEmpty())
            return;

        for (Map.Entry<ScreamingCrawler, Float> entry : PENDING.entrySet())
        {
            ScreamingCrawler crawler = entry.getKey();
            float psychic = entry.getValue();

            // Find the explosion belonging to this crawler (usually only one per tick)
            Explosion explosion = null;
            List<Entity> victims = null;
            for (Map.Entry<Explosion, List<Entity>> ve : VICTIMS.entrySet())
            {
                if (ve.getKey().getExploder() == crawler)
                {
                    explosion = ve.getKey();
                    victims = ve.getValue();
                    break;
                }
            }
            if (explosion == null || victims == null)
            {
                SanityMod.LOGGER.warn("[CRAWLER-EXPL] no explosion record for crawler {}, skipping", crawler.getId());
                continue;
            }

            Vec3 center = explosion.getPosition();
            Map<Entity, Float> seenFactors = SEEN.getOrDefault(explosion, java.util.Collections.emptyMap());
            int hit = 0;

            for (Entity victim : victims)
            {
                if (!(victim instanceof LivingEntity living) || !living.isAlive())
                    continue;
                // A crawler does not blow itself up (creepers do not either)
                if (living == crawler)
                    continue;

                // Inner entities are immune to each other's explosions, judged by the
                // sanitypd:inner_entities tag, so a crawler cannot kill other inner entities.
                // "Inner entities do not hurt each other" takes priority over the extra damage a
                // low-sanity target would otherwise receive: a skipped inner entity takes neither
                // psychic damage nor overflow true damage.
                if (SanityTags.isInnerEntity(living))
                    continue;

                // Line-of-sight factor sampled in the Detonate phase, while the block list was still
                // populated (see SEEN). getSeenPercent must NOT be called here: the blast may have been told
                // to spare the terrain, which empties Explosion#getToBlow and makes the ray cast blind.
                Float sampled = seenFactors.get(living);
                float seen = sampled == null ? 1.0f : sampled;
                float amount = psychic * seen;
                if (amount <= 0.0f)
                    continue;
                // This must keep using dealPsychic (type = psychic), for the same reason: the
                // sanity-drain + overflow branch in `SanityCombat.onLivingHurt` is gated on
                // `isPsychic(source)`. Switching to the crawler_explosion type would degrade this hit
                // into ordinary physical damage and drain no sanity at all.
                // => the death message is handled by the overflow segment: when SanityCombat sees a
                //    crawler as the attacker it swaps that segment to crawler_explosion, which drives
                //    the fixed screaming crawler death message.
                //
                // The extra psychic damage for low-sanity targets is applied automatically inside
                // this dealPsychic call by SanityCombat.applyInnerAttackerBonus (attacker is an inner
                // entity => x1.1 / x1.25 when the target's sanity is <= 10% / <= 1%).
                SanityDamageTypes.dealPsychic(living, amount, crawler);
                hit++;
                SanityMod.LOGGER.info("[CRAWLER-EXPL] {} dealt psychic damage to {}: {} (base {} x line-of-sight {})",
                        crawler.getId(), living.getType(), amount, psychic, seen);
            }
            SanityMod.LOGGER.info("[CRAWLER-EXPL] crawler {} explosion finished: affected {} entit(ies); vanilla physical damage was replaced",
                    crawler.getId(), hit);
        }

        PENDING.clear();
        VICTIMS.clear();
        SEEN.clear();
        BLOCK_SAFE.clear();
    }


}
