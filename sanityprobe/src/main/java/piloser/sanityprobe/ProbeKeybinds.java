package piloser.sanityprobe;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Probe key bindings: F4 shows or hides the probe's overlay text.
 *
 * <p>This is the probe's own on-screen text, not vanilla sound subtitles and not the main mod.
 *
 * <p>Defaults to F4 (unused by vanilla) and can be rebound in the controls screen under the
 * "SanityPD Probe" category.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ProbeKeybinds
{
    /** F4: shows or hides the probe overlay. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.sanityprobe.toggle_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F4,
            "key.categories.sanityprobe");

    private ProbeKeybinds() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(TOGGLE_HUD);
    }
}
