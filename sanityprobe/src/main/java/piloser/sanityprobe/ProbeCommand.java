package piloser.sanityprobe;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Minimal command set for timestamping entries in the probe log, e.g. to mark the start of a
 * specific test.
 *
 * <pre>
 *   /sanityprobe on             enable the log
 *   /sanityprobe off            disable the log (the overlay stays visible)
 *   /sanityprobe mark &lt;text&gt;    append a MARK line to the log
 * </pre>
 *
 * <p>Note: command feedback goes through {@code CommandSourceStack#sendSystemMessage} and never
 * through {@code MinecraftServer#sendSystemMessage}, so it does not disturb the
 * {@code ProbeStats} counter.
 */
public final class ProbeCommand
{
    private ProbeCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("sanityprobe")
                .then(Commands.literal("on").executes(ctx ->
                {
                    ProbeLog.setEnabled(true);
                    ctx.getSource().sendSystemMessage(Component.literal("[probe] logging enabled"));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx ->
                {
                    ProbeLog.setEnabled(false);
                    ctx.getSource().sendSystemMessage(Component.literal("[probe] logging disabled (HUD stays visible)"));
                    return 1;
                }))
                .then(Commands.literal("mark")
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx ->
                        {
                            String text = StringArgumentType.getString(ctx, "text");
                            ProbeLog.log("MARK", text);
                            ctx.getSource().sendSystemMessage(Component.literal("[probe] marked: " + text));
                            return 1;
                        })))
                .executes(ctx ->
                {
                    ctx.getSource().sendSystemMessage(Component.literal(
                            "[probe] sanityprobe diagnostic mod | log: logs/sanityprobe.log | usage: /sanityprobe on|off|mark <text>"));
                    return 1;
                }));
    }
}
