package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side probe for sanitypd sound playback.
 *
 * <h2>Why a client probe is required</h2>
 * A server-side self-test can only prove that the sound is registered, that {@code sounds.json}
 * holds the key and that the ogg file exists and is mono. It cannot prove that anything is
 * actually audible: resource resolution, the {@code sounds.json} category and subtitle,
 * distance attenuation and delivery after rate limiting all happen on the client.
 *
 * <h2>What it captures</h2>
 * <ol>
 *   <li>Every {@code sanitypd:*} sound that is played, with its name, distance to the player,
 *       volume, pitch and attenuation. The resource is resolved on the spot
 *       ({@code SoundManager.getSoundEvent()} then {@code WeighedSoundEvents.getSound()} then
 *       {@code Sound.getPath()}) and the client {@code ResourceManager} is asked whether that
 *       ogg is readable. The client resource manager does index mod resources while the server
 *       one does not, which is what makes this check meaningful.</li>
 *   <li>A cumulative play count, with a SUMMARY line every 30 seconds, answering how often an
 *       ambience sound fires or whether it ever fired at all.</li>
 *   <li>An overlay line showing the last sound, its count, distance and resolution result.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoundProbe
{
    private static final Map<String, Integer> PLAYS = new ConcurrentHashMap<>();
    private static String s_hud = "(no sanitypd sound yet)";
    private static int s_tick;

    private SoundProbe() {}

    static
    {
        // Since v2.7.0 this line goes through the shared HUD layout; drawing it at a hardcoded
        // y here used to collide with the WALK line.
        ProbeHud.registerLine(() -> "\u000088FFFF" + "[SND] " + s_hud);
    }

    /**
     * Observes every played sound.
     *
     * <p>WARNING: the priority must stay {@link EventPriority#LOWEST}. The mod's own
     * {@code CrawlerExplosionSoundGuard} cancels the vanilla explosion sound heard when a
     * crawler explodes by calling {@code setSound(null)} inside {@code PlaySoundEvent};
     * running after it is the only way to observe the suppressed result, which shows up as
     * {@code event.getSound() == null}. The untouched sound stays reachable through
     * {@link PlaySoundEvent#getOriginalSound()}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event)
    {
        try
        {
            SoundInstance original = event.getOriginalSound();

            if (original == null)
                return;

            ResourceLocation location = original.getLocation();

            if (location == null)
                return;

            boolean ours = "sanitypd".equals(location.getNamespace());
            boolean explosion = "minecraft:entity.generic.explode".equals(location.toString());

            // Only our own sounds and the vanilla explosion matter; the latter proves suppression works.
            if (!ours && !explosion)
                return;

            // Already cancelled by another listener, i.e. evidence that the vanilla explosion was muted.
            boolean suppressed = event.getSound() == null;

            Minecraft mc = Minecraft.getInstance();
            double distance = mc.player == null ? -1.0d
                    : Math.sqrt(mc.player.distanceToSqr(original.getX(), original.getY(), original.getZ()));

            String name = ours ? location.getPath() : "VANILLA generic.explode";
            int count = PLAYS.merge(name, 1, Integer::sum);

            String line = (suppressed ? "SUPPRESSED " : "play ") + "#" + count + " " + name
                    + " dist=" + ProbeLog.fmt(distance)
                    + " vol=" + ProbeLog.fmt(original.getVolume())
                    + " pitch=" + ProbeLog.fmt(original.getPitch())
                    + " attenuation=" + ProbeLog.safe(() -> String.valueOf(original.getAttenuation()));

            if (ours)
                line += " | " + resolve(location);

            ProbeLog.log("SOUND-C", line);

            s_hud = (suppressed ? "SUPPRESSED " : "") + name + " x" + count + " d=" + ProbeLog.fmt(distance)
                    + (ours && !resolve(location).contains("PASS") ? "  !! " + resolve(location) : "");
        }
        catch (Throwable ignored)
        {
            // The probe must never affect gameplay.
        }
    }

    /**
     * Resolves the sound resource on the spot, which is the authoritative check for whether the
     * client can really play that ogg.
     *
     * <p>The client {@code ResourceManager} does index mod resources (the server one does not),
     * so the file can actually be read here.
     */
    private static String resolve(ResourceLocation location)
    {
        try
        {
            SoundManager manager = Minecraft.getInstance().getSoundManager();
            WeighedSoundEvents weighed = manager.getSoundEvent(location);

            if (weighed == null)
                return "RESOLVE FAIL (no such key in sounds.json)";

            Sound sound = weighed.getSound(RandomSource.create());

            if (sound == null)
                return "RESOLVE FAIL (no usable sound in the entry)";

            ResourceLocation path = sound.getPath();
            boolean readable = Minecraft.getInstance().getResourceManager().getResource(path).isPresent();

            return "file=" + path + " readable=" + readable + (readable ? " -> PASS" : " -> FAIL");
        }
        catch (Throwable t)
        {
            return "RESOLVE ERR " + t.getClass().getSimpleName();
        }
    }

    /** Logs a cumulative count every 30 seconds, so even a single latest.log shows how often each sound played. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 600 != 0 || PLAYS.isEmpty())
            return;

        ProbeLog.log("SOUND-C SUMMARY", "plays so far = " + PLAYS);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        // This class no longer draws anything itself: the line is registered in the static
        // block and laid out by {@link HudLayout}. Kept empty on purpose; do not add drawing here.
    }
}
