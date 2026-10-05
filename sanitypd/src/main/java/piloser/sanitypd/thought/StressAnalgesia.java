package piloser.sanitypd.thought;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.player.Player;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.item.ItemRegistry;

/**
 * Stress-Induced Analgesia: while sanity is below {@link ThoughtEffects#ANALGESIA_SANITY}, being hurt
 * restores {@link ThoughtEffects#ANALGESIA_HEAL} health, at most once every
 * {@link ThoughtEffects#ANALGESIA_COOLDOWN_TICKS} ticks (0.3 s).
 *
 * <h2>Why the rate limit is not decoration</h2>
 * This mod's {@code psychic}, {@code true_damage}, {@code true_damage_attributed}, {@code mania} and
 * {@code crawler_explosion} types all <b>bypass the vanilla invulnerability frames</b> (that is what the
 * {@code bypasses_cooldown} tag on them is for), so a source that hurts every tick - a crawler standing on
 * the player, the mania drain - would otherwise turn one health per hurt into twenty health per second.
 * The window is what makes the thought a rebate rather than immortality.
 *
 * <h2>Why the window lives in a map and not in the capability</h2>
 * Same reasoning as {@link HintState}: it says "this happened less than six ticks ago", which is worthless
 * one tick later. Putting it in the sanity capability would push it into the save and the sync packet, and a
 * player could log out and back in with the clock frozen. A map that is cleared on logout is the whole
 * requirement.
 *
 * <h2>The cooldown is spent even when the player is at full health</h2>
 * The trigger is the <b>hurt</b>, not the healing: the owner's wording is "at most once every 0.3 seconds".
 * Bank the timer instead and a player could take chip damage until the window is free and then eat one big
 * hit with the heal waiting for it, which is a different mechanic from the one that was specified.
 */
public final class StressAnalgesia
{
    /** Per player: the game time of the last payout. */
    private static final Map<UUID, Long> LAST_PAYOUT = new ConcurrentHashMap<>();

    private StressAnalgesia() {}

    /**
     * The hurt hook. Called for every living entity that is hurt; only players with the thought equipped and
     * their sanity below the threshold do anything.
     *
     * @param player the player that was hurt
     * @param amount the incoming damage, before any of it is applied
     * @return true when the thought paid out (the self-check asserts the cooldown through this)
     */
    public static boolean onHurt(Player player, float amount)
    {
        if (player == null || amount <= 0f)
            return false;

        if (!ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_STRESS_INDUCED_ANALGESIA.get()))
            return false;

        ISanity cap = player.getCapability(SanityProvider.CAP).orElse(null);

        if (cap == null || cap.getSanity() >= cap.getMaxSanity() * ThoughtEffects.ANALGESIA_SANITY)
            return false;

        long now = player.level().getGameTime();
        Long last = LAST_PAYOUT.get(player.getUUID());

        if (last != null && now - last < ThoughtEffects.ANALGESIA_COOLDOWN_TICKS)
            return false;

        LAST_PAYOUT.put(player.getUUID(), now);
        player.heal(ThoughtEffects.ANALGESIA_HEAL);

        return true;
    }

    /** Drops a player's clock; called on logout so the map cannot grow without bound. */
    public static void forget(Player player)
    {
        if (player != null)
            LAST_PAYOUT.remove(player.getUUID());
    }
}
