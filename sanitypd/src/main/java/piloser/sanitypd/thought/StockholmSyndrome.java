package piloser.sanitypd.thought;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.damage.SanityDamageTypes;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.SanityProcessor;

/**
 * Stockholm Syndrome: being hurt restores {@link ThoughtEffects#STOCKHOLM_SANITY} sanity, at most once every
 * {@link ThoughtEffects#STOCKHOLM_COOLDOWN_TICKS} ticks (0.5 s).
 *
 * <h2>The mania drain is excluded on purpose</h2>
 * Mania deals one point of true damage per second for as long as it lasts, and this mod's true-damage types
 * bypass the vanilla invulnerability frames. Counting that damage would therefore turn the thought into a way
 * out of mania: the drain would pay for the sanity that ends it. Only the {@code sanitypd:mania} type is
 * excluded - a player in mania who is hurt by anything else is still paid, which is what the specification's
 * "this effect is not affected by mania damage" asks for.
 *
 * <h2>Why the window lives in a map and not in the capability</h2>
 * The same reasoning as {@link StressAnalgesia}: "this happened less than ten ticks ago" is worthless one
 * tick later, and putting it in the sanity capability would push it into the save and the sync packet, so a
 * player could log out and back in with the clock frozen.
 *
 * <h2>The window is spent by the hurt, not by the payout</h2>
 * The trigger is the <b>hurt</b>, exactly as with Stress-Induced Analgesia: a player already at full sanity
 * still spends the window, so chip damage cannot be banked and then cashed in with one large hit.
 */
public final class StockholmSyndrome
{
    /** Per player: the game time of the last payout. */
    private static final Map<UUID, Long> LAST_PAYOUT = new ConcurrentHashMap<>();

    private StockholmSyndrome() {}

    /**
     * The hurt hook. Called for every hurt player; only players with the thought equipped, and hurt by
     * something other than the mania drain, do anything.
     *
     * @param player the player that was hurt
     * @param amount the incoming damage, before any of it is applied
     * @param source the damage source, read to keep the mania drain out
     * @return true when the thought paid out (the self-check asserts the cooldown through this)
     */
    public static boolean onHurt(ServerPlayer player, float amount, DamageSource source)
    {
        if (player == null || amount <= 0f)
            return false;

        if (source != null && SanityDamageTypes.isManiaDamage(source))
            return false;

        if (!ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_STOCKHOLM_SYNDROME.get()))
            return false;

        long now = player.level().getGameTime();
        Long last = LAST_PAYOUT.get(player.getUUID());

        if (last != null && now - last < ThoughtEffects.STOCKHOLM_COOLDOWN_TICKS)
            return false;

        ISanity cap = player.getCapability(SanityProvider.CAP).orElse(null);

        if (cap == null)
            return false;

        LAST_PAYOUT.put(player.getUUID(), now);
        SanityProcessor.addSanity(cap, ThoughtEffects.STOCKHOLM_SANITY, player);

        return true;
    }

    /** Drops a player's clock; called on logout so the map cannot grow without bound. */
    public static void forget(Player player)
    {
        if (player != null)
            LAST_PAYOUT.remove(player.getUUID());
    }
}
