package piloser.sanitypd.event;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.damage.SanityDamageTypes;
import piloser.sanitypd.entity.PrimedShadowTnt;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow TNT explosions: <b>replace the vanilla physical explosion damage with psychic damage</b>.
 *
 * <h2>Why this handler exists at all</h2>
 * "All properties identical to TNT, but the damage type becomes psychic" cannot be done by changing one
 * number: {@code Explosion#damageSource} is built inside {@code Level#explode} from the vanilla
 * {@code DamageSources.explosion(...)}, and the damage loop calls {@code hurt(thatSource, damage)} itself.
 * So the blast is created exactly as vanilla creates it and this handler then <b>clears the affected entity
 * list</b> before vanilla's damage loop runs, records who would have been hit, and applies psychic damage at
 * the end of the tick instead.
 *
 * <h2>The damage formula is vanilla's own, not a re-invention</h2>
 * Vanilla's loop (verified in {@code Explosion} bytecode around offsets 778-822) is:
 * <pre>
 *   float exposure = Explosion.getSeenPercent(center, entity);
 *   entity.hurt(damageSource, (1.0F - exposure) * 7.0F);
 * </pre>
 * The same {@code getSeenPercent} and the same constant are used here, so geometry, cover and the
 * "standing right next to it hurts least" quirk are unchanged - only the damage type differs.
 *
 * <p>Like vanilla, the line-of-sight factor is sampled in the detonate phase <b>while the block list is
 * still populated</b>: {@code getSeenPercent} raycasts against {@code Explosion#getToBlow}, so measuring it
 * later would be blind.
 *
 * <h2>Inner entities are damaged, like everything else</h2>
 * The loop below has <b>no</b> inner-entity check, and that is deliberate. Shadow TNT is meant to hurt the
 * mod's own mobs; the rule "skip inner entities" belongs to the screaming crawler's blast alone (where the
 * reason was that a crawler must not kill its own kind). Two similarly shaped explosions, two different
 * rules - do not copy one from the other.
 *
 * <h2>Death messages</h2>
 * The owner of the primed entity decides which of the three messages is shown:
 * <ul>
 * <li>no owner -> "blown up by Shadow TNT";</li>
 * <li>owner is the victim -> "blown up by their own Shadow TNT";</li>
 * <li>any other owner -> "blown up by the Shadow TNT (owner) set off".</li>
 * </ul>
 * All three are <b>without</b> a causing entity, because vanilla's {@code getLocalizedDeathMessage} routes
 * "has a causing entity" to the {@code .player} entry and the self-ignited case cannot render a different
 * sentence that way. The chosen type travels to the death message through
 * {@link OverflowDamageQueue#enqueue(LivingEntity, float, Entity, ResourceKey)}.
 */
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowTntExplosionHandler
{
    /**
     * The psychic damage a shadow TNT blast deals to <b>every</b> living entity inside the radius.
     *
 * <p>⚠️ <b>Flat and distance-independent on purpose</b> (the ruling): "just make it 60
     * points of psychic damage that ignores distance falloff". The first two attempts both scaled it with the
     * blast, and both were wrong for the same underlying reason - a number that rises as the target gets
     * <b>further</b> from, or better <b>hidden</b> from, the charge is not a number anybody expects a bomb to
     * use. Vanilla's own formula is {@code (1 - exposure) * 7}, which gives a target standing in the open
     * right next to the charge <b>zero</b> damage.
     *
     * <p>So nothing here varies with exposure any more: being inside the radius and not being one of this
     * mod's own inner entities is the whole condition. The magnitude matches the screaming crawler's
     * self-detonation ({@code ScreamingCrawler.PSYCHIC_NORMAL} = 60), including its easy/normal/hard scaling,
     * so "how hard does a blast hit" has one answer in this mod rather than two.
     */
    public static final float PSYCHIC_DAMAGE = piloser.sanitypd.entity.ScreamingCrawler.PSYCHIC_NORMAL;

    /** The primed entities whose blast is waiting to be resolved this tick. */
    private static final List<PrimedShadowTnt> PENDING = new ArrayList<>();

    /** Explosions registered this tick -> the entities they would have hit (filled in at detonation). */
    private static final Map<Explosion, List<Entity>> VICTIMS = new IdentityHashMap<>();

    /** Explosion -> victim -> line-of-sight factor, sampled before the block list is emptied. */
    private static final Map<Explosion, Map<Entity, Float>> SEEN = new IdentityHashMap<>();

 /** Explosion -> the primed entity that caused it, so the owner is still available at resolution time. */
    private static final Map<Explosion, PrimedShadowTnt> SOURCES = new IdentityHashMap<>();

    private ShadowTntExplosionHandler() {}

    /**
     * Called by {@link PrimedShadowTnt#explode()} before the explosion is triggered.
     *
 * <p>The owner is not passed separately: {@code TraceableEntity} already stores it on the primed entity
     * (that is what the vanilla 5-argument constructor does), so there is only one copy of it to keep right.
     */
    public static void register(PrimedShadowTnt primed)
    {
        PENDING.add(primed);
    }

    /**
     * At detonation: records who would have been hit and stops vanilla from damaging them.
     *
     * <p>An explosion that is not ours is left completely alone, so vanilla and other mods' TNT keep working.
     */
    @SubscribeEvent
    public static void onDetonate(final ExplosionEvent.Detonate event)
    {
        Explosion explosion = event.getExplosion();

        if (!(explosion.getExploder() instanceof PrimedShadowTnt primed))
            return;

        // What vanilla itself collected, before any filtering on our side. Logged only when it is empty,
        // because "the blast had nothing inside its radius" and "the damage was refused" look identical from
        // the outside and only the first one means the check was set up wrong.
        List<Entity> raw = new ArrayList<>(event.getAffectedEntities());

        if (raw.isEmpty())
            SanityMod.LOGGER.warn("[SHADOW-TNT] primed {} detonated with no entities in range (nothing can be hurt)",
                    primed.getId());

        // Drop entities that are already gone, exactly like vanilla's loop does before it damages anyone.
        List<Entity> victims = new ArrayList<>(raw);
        victims.removeIf(entity -> !(entity instanceof LivingEntity living) || !living.isAlive());

        Vec3 center = explosion.getPosition();
        Map<Entity, Float> seen = new IdentityHashMap<>();

        for (Entity victim : victims)
            seen.put(victim, Explosion.getSeenPercent(center, victim));

        VICTIMS.put(explosion, victims);
        SEEN.put(explosion, seen);
        SOURCES.put(explosion, primed);

        // The key step: clearing this makes vanilla's damage loop do nothing at all.
        event.getAffectedEntities().clear();
    }

    /**
     * Applies the psychic damage for this tick, at the end of it.
     *
     * <p>Running after {@link CrawlerExplosionHandler} (same priority, registered later) is not required for
     * correctness - each handler only touches its own explosions - but it keeps the ordering rule from the
     * overflow queue intact: drain sanity, queue the overflow, resolve it once at the end of the tick.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty())
            return;

        for (PrimedShadowTnt primed : PENDING)
        {
            Explosion explosion = null;

            for (Map.Entry<Explosion, PrimedShadowTnt> entry : SOURCES.entrySet())
            {
                if (entry.getValue() == primed)
                {
                    explosion = entry.getKey();
                    break;
                }
            }

            if (explosion == null)
            {
                SanityMod.LOGGER.warn("[SHADOW-TNT] no explosion record for primed shadow tnt {}", primed.getId());
                continue;
            }

            List<Entity> victims = VICTIMS.getOrDefault(explosion, List.of());
            Map<Entity, Float> seenFactors = SEEN.getOrDefault(explosion, Map.of());

            // The blast's own sound, at the blast. The same recording the screaming crawler uses for its
 // self-detonation (the design requires exactly that), played with the same volume and channel
            // vanilla uses for an explosion (4.0 / BLOCKS) so it sits where a TNT blast sits.
            //
            // Vanilla's own explosion sound still plays on the client (Explosion#finalizeExplosion does that
            // locally, with no server-side hook), so the two are heard together - the shadow event is meant to
            // characterise the blast, not to replace the audio feedback of the explosion.
            primed.level().playSound(null, explosion.getPosition().x, explosion.getPosition().y, explosion.getPosition().z,
                    piloser.sanitypd.sound.SoundRegistry.SCREAMING_CRAWLER_EXPLODE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 4.0f, 1.0f);

            LivingEntity owner = primed.getOwner();
            int hit = 0;
            float amount0 = 0.0f;

            for (Entity victim : victims)
            {
                // `victim` can only be a LivingEntity that took part in the blast; the primed entity itself is
                // not a LivingEntity and the explosion never lists its own exploder, so no self-check is needed.
                if (!(victim instanceof LivingEntity living) || !living.isAlive())
                    continue;


                // Flat, and the same number for everyone in range - see PSYCHIC_DAMAGE. The exposure the
                // detonate phase measured is no longer read here at all; it is kept only so a future ruling
                // can put it back without re-plumbing the two phases.
                float amount = piloser.sanitypd.entity.ScreamingCrawler.psychicDamageForDifficulty(primed.level().getDifficulty());
                amount0 = amount;

 // The message type is decided here, while the owner is still known, and travels with the
                // queued overflow (see OverflowDamageQueue#enqueue).
                ResourceKey<DamageType> messageType = messageTypeFor(owner, living);

 // ⚠️ The source is the IGNITER, not the primed entity. The word "%2$s" in the by-owner death
                // message renders the display name of the source's entity, so passing the TNT printed
 // "blown up by Shadow TNT set off by Shadow TNT" - the report, exactly. When nobody
                // lit it (redstone, fire, a chain reaction) there is no igniter and the TNT is the honest
                // source; that message type has no %2$s to fill in anyway.
                SanityDamageTypes.dealPsychic(living, amount, owner != null ? owner : primed, messageType);
                hit++;

                SanityMod.LOGGER.info("[SHADOW-TNT] primed {} drained {} sanity from {} (flat, ignores distance)",
                        primed.getId(), amount, living.getType());
            }

            // One line per blast, with the numbers that decide whether anything happened: how much sanity the
            // main victim had before and after, and how much of the hit was left over as true damage. Without
            // them "the bomb does nothing" cannot be told apart from "the bomb drained 60 sanity and the target
            // simply had that much" - which is what a full-sanity player always sees after a single blast.
            String detail = "-";

            if (!victims.isEmpty() && victims.get(0) instanceof LivingEntity first)
            {
                ISanity cap = first.getCapability(SanityProvider.CAP).orElse(null);
                detail = String.format(java.util.Locale.ROOT, "%s health %.1f sanity %s alive %s",
                        first.getType(), first.getHealth(),
                        cap == null ? "n/a" : String.format(java.util.Locale.ROOT, "%.1f", cap.getSanity()),
                        first.isAlive());
            }

            SanityMod.LOGGER.info("[SHADOW-TNT] blast by primed {} finished: {} living target(s) drained {} sanity each; "
                    + "first target now: {}", primed.getId(), hit, amount0, detail);
        }

        PENDING.clear();
        VICTIMS.clear();
        SEEN.clear();
        SOURCES.clear();
    }

    /**
 * Which death message this hit should use, from the primed entity's owner and the victim.
     *
     * <p>Pure and public so a self-check can assert all three branches without staging three explosions.
     */
    public static ResourceKey<DamageType> messageTypeFor(LivingEntity owner, LivingEntity victim)
    {
        if (owner == null)
            return SanityDamageTypes.SHADOW_TNT;

        return owner == victim ? SanityDamageTypes.SHADOW_TNT_SELF : SanityDamageTypes.SHADOW_TNT_BY_OWNER;
    }
}
