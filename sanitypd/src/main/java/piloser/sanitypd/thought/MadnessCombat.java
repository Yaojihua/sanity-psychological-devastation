package piloser.sanitypd.thought;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.event.OverflowDamageQueue;
import piloser.sanitypd.item.ItemRegistry;

/**
 * The two time windows the madness thoughts need, and the one hit they add to a target.
 *
 * <h2>Why the hurt has to be remembered here</h2>
 * "Within five seconds of being hurt" is a question the game time can answer only if something recorded
 * when the hurt happened. Vanilla keeps a timestamp on the entity, but reading it means depending on a
 * member whose name is not part of this project's own vocabulary; recording the game time when the event
 * fires is one line, is exactly the quantity the two thoughts ask about, and cannot drift when the game
 * updates that field for its own reasons.
 *
 * <p>Nothing here is saved: a hurt five seconds ago is meaningless across a relog, and a map that is
 * dropped on logout cannot leak a permanently open window.
 */
public final class MadnessCombat
{
    /** Catharsis: how long the drain lasts, in ticks (six seconds). */
    public static final int CATHARSIS_DRAIN_TICKS = 120;

    /** Per player: the game time of his last hurt, and whoever caused it. */
    private record Hurt(long gameTime, UUID attacker) {}

    private static final Map<UUID, Hurt> LAST_HURT = new ConcurrentHashMap<>();

    private MadnessCombat() {}

    /**
     * Server side: records that this player was just hurt by a living attacker.
     *
     * <p>Called for damage that has an attacker. Environmental damage (falling, fire) deliberately does not
     * open either window: both thoughts speak about being struck by something that meant it.
     */
    public static void onPlayerHurt(ServerPlayer player, Entity attacker)
    {
        if (player == null || attacker == null)
            return;

        LAST_HURT.put(player.getUUID(), new Hurt(player.level().getGameTime(), attacker.getUUID()));
    }

    /** Drops a player's record; called when he logs out. */
    public static void forget(Player player)
    {
        if (player != null)
            LAST_HURT.remove(player.getUUID());
    }

    /**
     * Test hook: records a hurt that happened {@code ageTicks} ticks ago.
     *
     * <p>The two windows are 120 and 100 ticks long, so the interesting configuration - "Aggressor still
     * active while Fight or Flight has already flipped back to its attack-up state" - only exists between
     * tick 101 and tick 120 after a hurt. No public call can produce that state: {@link #onPlayerHurt}
     * always stamps <i>now</i>, and a self-check cannot wait 110 ticks to get there.
     *
     * <p>This exists for the damage-ceiling check and must never be called from gameplay code. It is
     * deliberately separate from {@link #onPlayerHurt} so the production path stays a single, honest
     * "record that this just happened".
     *
     * @param player   the player
     * @param attacker the entity that will have caused the hurt (Aggressor only pays out against it)
     * @param ageTicks how long ago the hurt should read as (clamped at 0)
     */
    public static void debugRecordHurtAgo(Player player, Entity attacker, long ageTicks)
    {
        if (player == null || attacker == null)
            return;

        long when = player.level().getGameTime() - Math.max(0, ageTicks);

        LAST_HURT.put(player.getUUID(), new Hurt(when, attacker.getUUID()));
    }

    /**
     * Attack bonus from "Identification with the Aggressor": granted only while hitting back the entity
     * that struck the player, inside the window the owner specified.
     */
    public static float aggressorBonus(Player player, Entity target)
    {
        if (player == null || target == null)
            return 0f;

        Hurt hurt = LAST_HURT.get(player.getUUID());

        if (hurt == null || !hurt.attacker().equals(target.getUUID()))
            return 0f;

        if (player.level().getGameTime() - hurt.gameTime() > MindsetAttributes.AGGRESSOR_WINDOW_TICKS)
            return 0f;

        return ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_IDENTIFICATION_WITH_THE_AGGRESSOR.get());
    }

    /**
     * The attack and movement bonuses of "Fight or Flight", as {@code [attack, movement]}.
     *
     * <p>State 0 while the player has not been hurt for five seconds, state 1 while he has. The two are
     * mutually exclusive by construction: one comparison decides which row of the table is read, so they
     * can never both be applied and cancel each other out into "no effect".
     *
     * @return the two multipliers, or {@code null} when the thought is not in the chain or sanity is high
     */
    public static float[] fightOrFlight(Player player, boolean belowThreshold)
    {
        if (player == null || !belowThreshold)
            return null;

        if (!ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_FIGHT_OR_FLIGHT.get()))
            return null;

        Hurt hurt = LAST_HURT.get(player.getUUID());
        boolean recentlyHurt = hurt != null
                && player.level().getGameTime() - hurt.gameTime() <= MindsetAttributes.FIGHT_OR_FLIGHT_WINDOW_TICKS;

        return ThoughtEffects.FIGHT_OR_FLIGHT[recentlyHurt ? 1 : 0];
    }

    /**
     * Applies the Catharsis drain to a target that was just hit, if the attacker has the thought.
     *
     * <p>The drain itself is queued, not applied here: see {@code OverflowDamageQueue#enqueueDrain}.
     */
    public static void catharsisDrain(ServerPlayer attacker, LivingEntity target)
    {
        if (attacker == null || target == null || !target.isAlive())
            return;

        if (!ThoughtEffects.isEquipped(attacker, ItemRegistry.THOUGHT_CATHARSIS.get()))
            return;

        attacker.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            if (s.getSanity() < s.getMaxSanity() * ThoughtEffects.CATHARSIS_SANITY)
            {
                OverflowDamageQueue.enqueueDrain(target, CATHARSIS_DRAIN_TICKS, attacker);
                SanityMod.LOGGER.debug("[MADNESS-37] catharsis drain queued on {}", target.getName().getString());
            }
        });
    }
}
