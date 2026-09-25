package piloser.sanityprobe.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import piloser.sanityprobe.ProbeLog;
import piloser.sanityprobe.ProbeStats;

/**
 * Records every server-side system broadcast, with a full call stack for death messages.
 *
 * <p>In vanilla, {@code MinecraftServer#sendSystemMessage(Component)} only writes the message
 * to the server log:
 * <pre>
 *   public void sendSystemMessage(Component c) { LOGGER.info(c.getString()); }
 * </pre>
 * So log lines such as
 * <pre>
 *   15:39:12.473 [Server thread/INFO] [net.minecraft.server.MinecraftServer/] &lt;player&gt; was blown up by Screaming Crawler
 *   15:39:12.474 [Server thread/INFO] [net.minecraft.server.MinecraftServer/] &lt;player&gt; died
 * </pre>
 * are produced here. {@code PlayerList#broadcastSystemMessage} funnels into the three-argument
 * overload and ends at this method, which makes it the single exit point for server broadcasts.
 *
 * <p>Logging a stack trace at HEAD therefore shows which code path broadcasts a given message,
 * for example the generic death message ({@code death.attack.generic}).
 *
 * <p>{@code require = 0}: if the target signature ever changes, a failed injection must not crash
 * the game; it only costs diagnostic detail in the log.
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer
{
    @Inject(method = "sendSystemMessage", at = @At("HEAD"), require = 0)
    private void sanityprobe$onSendSystemMessage(Component message, CallbackInfo ci)
    {
        try
        {
            ProbeStats.SEND_SYSTEM_MESSAGE_HITS.incrementAndGet();

            String text = message == null ? "null" : message.getString();

            // Only death-related messages get a full stack trace. Join/progress/command feedback
            // messages are logged as a single line to keep the log readable.
            boolean interesting = text.contains("死") || text.contains("炸") || text.contains("尸")
                    || text.contains("died") || text.contains("death") || text.contains("Death")
                    || text.contains("slain") || text.contains("killed") || text.contains("blown")
                    || text.contains("was ");

            if (interesting)
                ProbeLog.logStack("SYSMSG", "broadcast text='" + text + "'", 16);
            else
                ProbeLog.log("SYSMSG", "broadcast text='" + text + "' (no stack)");
        }
        catch (Throwable ignored)
        {
            // The probe must never affect the game.
        }
    }
}
