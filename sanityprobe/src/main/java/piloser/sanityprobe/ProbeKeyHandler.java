package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles F4: toggles {@link ProbeHud} and prints an action bar message so the keypress is
 * visibly confirmed.
 *
 * <p>Affects only the probe's own text overlay; vanilla sound subtitles and the main mod are
 * never touched.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ProbeKeyHandler
{
    private ProbeKeyHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        try
        {
            while (ProbeKeybinds.TOGGLE_HUD.consumeClick())
            {
                ProbeHud.toggle();

                Minecraft mc = Minecraft.getInstance();

                if (mc.player != null)
                    mc.player.displayClientMessage(Component.translatable(
                            ProbeHud.isVisible() ? "msg.sanityprobe.hud.on" : "msg.sanityprobe.hud.off"), true);

                ProbeLog.log("SYS", "probe overlay " + (ProbeHud.isVisible() ? "SHOWN" : "HIDDEN") + " (F4)");
            }
        }
        catch (Throwable ignored)
        {
            // The probe must never be able to affect the game.
        }
    }
}
