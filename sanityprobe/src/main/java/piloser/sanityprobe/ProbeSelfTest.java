package piloser.sanityprobe;

import net.minecraft.network.chat.Component;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Startup self-test that proves the most important mixin of the probe is actually applied.
 *
 * <p>It broadcasts a system message whose text contains {@code was slain} so that it matches the
 * death-keyword branch (which also logs a call stack), then checks whether
 * {@link ProbeStats#SEND_SYSTEM_MESSAGE_HITS} increased. An increase means the
 * {@code MinecraftServer#sendSystemMessage} injection is live.
 *
 * <p>This check matters because a failed mixin injection is silent: {@code require = 0} is used so a
 * failure can never crash the game, which also means nothing else would report it. Without the
 * self-test an entire test run can produce empty evidence before the problem is noticed.
 *
 * <p>Side effects: it only broadcasts one line explicitly marked as
 * {@code [PROBE] sanityprobe self-test} to the console and chat, and changes no game state.
 */
public final class ProbeSelfTest
{
    private ProbeSelfTest() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        try
        {
            int before = ProbeStats.SEND_SYSTEM_MESSAGE_HITS.get();

            event.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "[PROBE] sanityprobe self-test was slain by probe "
                            + "(this line itself proves the death-broadcast hook is alive)"), false);

            int after = ProbeStats.SEND_SYSTEM_MESSAGE_HITS.get();

            ProbeLog.log("SELFTEST", after > before
                    ? "PASS: MinecraftServer#sendSystemMessage mixin ACTIVE  -> death-message evidence chain available (hits=" + after + ")"
                    : "FAIL: MinecraftServer#sendSystemMessage mixin NOT APPLIED -> death-message evidence missing; report this line to the developer");

            ProbeLog.log("SELFTEST", "classpath sanitypd present = " + classpathHasSanitypd()
                    + " | log file = <gamedir>/logs/sanityprobe.log");
        }
        catch (Throwable t)
        {
            ProbeLog.log("SELFTEST", "self-test threw: " + t);
        }
    }

    private static boolean classpathHasSanitypd()
    {
        try
        {
            return Class.forName("piloser.sanitypd.SanityMod") != null;
        }
        catch (Throwable t)
        {
            return false;
        }
    }
}
