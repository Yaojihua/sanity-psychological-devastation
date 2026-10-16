package piloser.sanitypd.thought;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import piloser.sanitypd.item.ItemRegistry;

/**
 * Overdose: every mood stabilizer's cooldown is shortened by
 * {@link ThoughtEffects#OVERDOSE_COOLDOWN_CUT}, and using one costs a short bout of nausea.
 *
 * <h2>One place owns the number</h2>
 * The shortened cooldown is computed from the kind's own constant, so the four stabilizers keep their
 * relative lengths (15 s / 5 min / 2 min / 5 s) instead of each growing a second copy of the number that
 * would have to be kept in step by hand.
 *
 * <h2>The price is a vanilla effect, not one of ours</h2>
 * The thoughts in this mod never register status effects of their own - nothing here may appear in the HUD's
 * effect row on a thought's behalf. Nausea is a plain vanilla effect that the player is meant to see and
 * feel, so it is applied as one.
 */
public final class Overdose
{
    private Overdose() {}

    /**
     * The cooldown a stabilizer starts for this player: its own length, shortened while the thought is in
     * the chain.
     *
     * @param player    the player using the item
     * @param baseTicks the kind's own cooldown
     */
    public static int cooldownTicks(ServerPlayer player, int baseTicks)
    {
        if (player == null || !ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_OVERDOSE.get()))
            return baseTicks;

        return Math.max(1, Math.round(baseTicks * (1f - ThoughtEffects.OVERDOSE_COOLDOWN_CUT)));
    }

    /** The price of the shortcut: nausea for {@link ThoughtEffects#OVERDOSE_NAUSEA_TICKS} ticks. */
    public static void applyCost(ServerPlayer player)
    {
        if (player == null || !ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_OVERDOSE.get()))
            return;

        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ThoughtEffects.OVERDOSE_NAUSEA_TICKS, 0,
                false /* ambient */, false /* no particles */, true /* show icon */));
    }
}
