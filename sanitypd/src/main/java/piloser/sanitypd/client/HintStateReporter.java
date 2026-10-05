package piloser.sanitypd.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import piloser.sanitypd.net.HintStatePacket;
import piloser.sanitypd.net.PacketHandler;

/**
 * Reports to the server whether a non-mild inner line is on screen, so "Command Hallucination" can grant
 * its attack bonus server-side.
 *
 * <h2>Why the state is sampled rather than pushed</h2>
 * The line is picked, drawn and cleared in several places in {@link GuiHandler} (the regular draw, both
 * warning windows, the extra inner-voice pool and the immediate {@code /sanity hint show}). Sampling
 * {@link GuiHandler#isNonMildHintOnScreen()} once per client tick reads all of those through one rule and
 * cannot miss a path that forgets to notify. The cost is one boolean comparison per tick and one packet
 * per <b>change</b>.
 *
 * <p>Registered by hand from {@code SanityMod#clientSetup} rather than through a bus annotation: that
 * method only runs on a physical client, so the class is never even named on a dedicated server, and the
 * packet it sends carries a plain boolean. (A {@code @OnlyIn} marker on a class with event subscribers is
 * the shape that makes Forge refuse to start: the marker removes the class from the other physical side
 * entirely, so the bus scan that names it there fails during start-up.)
 */
public final class HintStateReporter
{
    /** Last value sent, so only changes produce traffic. */
    private static boolean s_lastSent;

    /** Whether anything has been sent this session; a fresh session always sends once. */
    private static boolean s_sentOnce;

    private HintStateReporter() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        boolean active = GuiHandler.isNonMildHintOnScreen();

        if (s_sentOnce && active == s_lastSent)
            return;

        s_sentOnce = true;
        s_lastSent = active;

        PacketHandler.CHANNEL_INSTANCE.send(PacketDistributor.SERVER.noArg(), new HintStatePacket(active));
    }

    /**
     * Forgets the last value when the player changes world, so the first tick in the new world sends it
     * again instead of assuming the server still knows.
     */
    @SubscribeEvent
    public static void onLoggedIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        s_sentOnce = false;
        s_lastSent = false;
    }
}
