package piloser.sanityprobe.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import piloser.sanityprobe.ProbeLog;

/**
 * Captures the real player death entry point: {@code ServerPlayer#die(DamageSource)}.
 *
 * <p>Vanilla calls {@code getCombatTracker().getDeathMessage()} here and sends the resulting
 * component both to the client (death screen packet) and through broadcastSystemMessage (chat plus
 * server log). Two different death messages therefore mean that die() ran twice or that some other
 * code broadcast a second one. Each call is logged together with the resulting text, the health and
 * the call stack.
 */
@Mixin(ServerPlayer.class)
public class MixinServerPlayer
{
    @Inject(method = "die", at = @At("HEAD"), require = 0)
    private void sanityprobe$onDie(DamageSource cause, CallbackInfo ci)
    {
        try
        {
            ServerPlayer self = (ServerPlayer)(Object)this;

            ProbeLog.logStack("PLAYERDIE", "die() called"
                    + " msgId=" + ProbeLog.safe(cause::getMsgId)
                    + " hp=" + ProbeLog.fmt(self.getHealth())
                    + " alreadyDead=" + self.isDeadOrDying()
                    + " combatTracker='" + ProbeLog.safe(() -> self.getCombatTracker().getDeathMessage().getString()) + "'"
                    + " localized='" + ProbeLog.safe(() -> cause.getLocalizedDeathMessage(self).getString()) + "'", 14);
        }
        catch (Throwable ignored)
        {
            // The probe must never affect the game.
        }
    }
}
