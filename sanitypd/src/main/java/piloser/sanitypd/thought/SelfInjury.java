package piloser.sanitypd.thought;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.SanityProcessor;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.damage.SanityDamageTypes;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.net.PacketHandler;
import piloser.sanitypd.net.SelfInjuryPacket;

/**
 * The non-suicidal self-injury thought's clock: the one place the act is decided and performed.
 *
 * <h2>The two clocks</h2>
 * The rule has two stages, so the state has two counters:
 * <ol>
 *   <li><b>Armed</b> - sanity stays below {@link ThoughtEffects#SELF_INJURY_SANITY} for
 *       {@link ThoughtEffects#SELF_INJURY_SUSTAIN_TICKS} (three minutes). Any moment spent at or above that
 *       fraction disarms the thought completely: the counters go back to zero and the client is told to drop
 *       an announcement it may already have received. "Stays below" has to mean the whole three minutes or the
 *       mechanic would fire for a player who dipped and recovered.</li>
 *   <li><b>Rolling</b> - then every {@link ThoughtEffects#SELF_INJURY_INTERVAL_TICKS} (one minute) rolls
 *       {@link ThoughtEffects#selfInjuryChance}. A period ends in the act only if its own roll succeeded.</li>
 * </ol>
 *
 * <h2>Why the roll happens a warning early</h2>
 * The outcome of a period is decided {@link ThoughtEffects#SELF_INJURY_WARN_TICKS} before it lands, not at the
 * last tick, because the presentation needs five seconds of lead time: the inner lines have to fade
 * out, the view has to come out of focus and the sound has to duck <b>before</b> the wound. The decision is
 * therefore taken at {@code INTERVAL - WARN} and only its consequence waits for the end of the period, which
 * also means the client can be told the truth immediately.
 *
 * <h2>Nothing is saved</h2>
 * Like {@code MadnessCombat}'s hurt window, the state is a plain map keyed by UUID: three minutes of "has been
 * low" is meaningless across a relog, and a map dropped on logout cannot leak an announcement into the next
 * session.
 */
public final class SelfInjury
{
    /**
     * Per player: ticks spent below the sanity fraction, where the current period stands, and whether this
     * period has already been announced to the client.
     */
    private record State(int lowTicks, int cycleTicks, boolean announced) {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** Rolled once per period per player, so it does not need to be reproducible. */
    private static final Random RAND = new Random();

    private SelfInjury() {}

    /** Drops a player's clock; called on logout, next to {@code MadnessCombat#forget}. */
    public static void forget(Player player)
    {
        if (player != null)
            STATES.remove(player.getUUID());
    }

    /**
     * One tick of the clock. Called for every player from {@link SanityProcessor#tickPlayer} <b>before</b> its
     * own early exits, so a player who switches to creative mid-warning cannot leave an announcement frozen
     * and unfired: this method clears the state instead, and tells the client.
     */
    public static void tick(ServerPlayer player)
    {
        if (player == null)
            return;

        ISanity sanity = player.getCapability(SanityProvider.CAP).orElse(null);

        if (sanity == null
                || player.isCreative()
                || player.isSpectator()
                || !ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_NON_SUICIDAL_SELF_INJURY.get()))
        {
            if (STATES.remove(player.getUUID()) != null)
                PacketHandler.sendSelfInjury(player, SelfInjuryPacket.CANCEL);

            return;
        }

        State state = STATES.get(player.getUUID());
        boolean below = sanity.getSanity() < sanity.getMaxSanity() * ThoughtEffects.SELF_INJURY_SANITY;

        if (!below)
        {
            // Sanity came back up: disarm. An announcement already sent has to be withdrawn, or the client
            // would keep fading the inner lines out and ducking the sound for a wound that is not coming.
            if (state != null && state.announced())
                PacketHandler.sendSelfInjury(player, SelfInjuryPacket.CANCEL);

            if (state != null)
                STATES.remove(player.getUUID());

            return;
        }

        int lowTicks = (state == null ? 0 : state.lowTicks()) + 1;

        if (lowTicks < ThoughtEffects.SELF_INJURY_SUSTAIN_TICKS)
        {
            STATES.put(player.getUUID(), new State(lowTicks, 0, false));
            return;
        }

        int cycle = (state == null ? 0 : state.cycleTicks()) + 1;
        boolean announced = state != null && state.announced();

        if (!announced && cycle == ThoughtEffects.SELF_INJURY_INTERVAL_TICKS - ThoughtEffects.SELF_INJURY_WARN_TICKS)
        {
            float chance = ThoughtEffects.selfInjuryChance(player);

            if (chance > 0f && RAND.nextFloat() < chance)
            {
                announced = true;
                PacketHandler.sendSelfInjury(player, SelfInjuryPacket.WARN);
                SanityMod.LOGGER.info("[SELF-INJURY] announced: player={} chance={}% tier={} (in {} ticks)",
                        player.getGameProfile().getName(), Math.round(chance * 100f),
                        ThoughtEffects.tierIndex(player, ItemRegistry.THOUGHT_NON_SUICIDAL_SELF_INJURY.get()),
                        ThoughtEffects.SELF_INJURY_WARN_TICKS);
            }
        }

        if (cycle >= ThoughtEffects.SELF_INJURY_INTERVAL_TICKS)
        {
            cycle = 0;

            if (announced)
            {
                announced = false;
                act(player, sanity);
            }
        }

        STATES.put(player.getUUID(), new State(lowTicks, cycle, announced));
    }

    /**
     * Performs one act <b>right now</b>, ignoring the clock: the {@code /sanity selfinjury <targets>} command.
     *
     * <p>Why it exists: the rolled act needs three minutes below 25% sanity, so everything the act sets off -
     * the wound, the sanity it gives back, the slowness, the client's whole presentation and the six seconds the
     * self-injury hint pool speaks in - is otherwise impossible to look at on demand.
     *
     * <p>It routes through {@link #act(ServerPlayer, ISanity)}, the same method the clock calls, so what the
     * player sees is the mechanic rather than a second implementation of it. Two deliberate differences from a
     * rolled act: an announcement still in flight is withdrawn first (the wound is arriving now, not in five
     * seconds), and the clock is dropped afterwards, so the next rolled act is a full sustain plus interval away
     * exactly as it would be after any other act.
     *
     * @return whether the act happened ({@code false} when the player has no sanity data at all)
     */
    public static boolean forceAct(ServerPlayer player)
    {
        if (player == null)
            return false;

        ISanity sanity = player.getCapability(SanityProvider.CAP).orElse(null);

        if (sanity == null)
            return false;

        State state = STATES.get(player.getUUID());

        if (state != null && state.announced())
            PacketHandler.sendSelfInjury(player, SelfInjuryPacket.CANCEL);

        STATES.remove(player.getUUID());
        act(player, sanity);

        SanityMod.LOGGER.info("[SELF-INJURY] act forced by command: player={}", player.getGameProfile().getName());
        return true;
    }

    /**
     * Performs the act: the wound, the sanity it gives back, the slowness, and the packet that starts the
     * client's presentation.
     *
     * <p>The sanity goes through {@link SanityProcessor#addSanity} like every other sanity change in this mod -
     * including the delta stabilizer's <b>reduction</b> - so the configured multipliers, the garland and
     * Depersonalization's recovery ceiling all apply, and the HUD arrow and the "you lost sanity" feedback stay
     * honest. A payout that bypassed the channel would be a special case nobody could explain later.
     *
     * <p>The wound is applied through {@code SanityDamageTypes#dealSelfInjury}, which is where "never lethal,
     * never mitigated, never absorbed" is enforced; at one health point it takes nothing at all, and the rest of
     * the act still happens - the wound was inflicted, and the sanity and slowness are not payment for health
     * lost.
     */
    private static void act(ServerPlayer player, ISanity sanity)
    {
        float lost = SanityDamageTypes.dealSelfInjury(player, ThoughtEffects.SELF_INJURY_DAMAGE);

        boolean below = sanity.getSanity() < sanity.getMaxSanity() * ThoughtEffects.SELF_INJURY_SANITY;
        float sanityDelta = below
                ? ThoughtEffects.SELF_INJURY_SANITY_RESTORE
                : -ThoughtEffects.SELF_INJURY_SANITY_PENALTY;

        SanityProcessor.addSanity(sanity, sanityDelta, player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                ThoughtEffects.SELF_INJURY_SLOWNESS_TICKS,
                ThoughtEffects.SELF_INJURY_SLOWNESS_AMPLIFIER, false, true));

        PacketHandler.sendSelfInjury(player, SelfInjuryPacket.ACT);

        SanityMod.LOGGER.info("[SELF-INJURY] act: player={} healthLost={} sanityDelta={} ({}) sanity={}/{}",
                player.getGameProfile().getName(), lost, sanityDelta, below ? "restore" : "penalty",
                sanity.getSanity(), sanity.getMaxSanity());
    }
}
