package piloser.sanitypd.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The charge-up clip of the lost memory fragment: one shot, played at the player, three and a half
 * seconds long.
 *
 * <p>Played through a handle (like {@link InsanitySoundInstance}) rather than
 * {@code Level#playLocalSound} because the sequence has to be able to <b>cut it off</b> when the player
 * lets go of the button before the clip is over.
 */
public final class MemoryChargeSoundInstance extends AbstractTickableSoundInstance
{
    public MemoryChargeSoundInstance()
    {
        // SoundSource.PLAYERS: this is the user's own item, so it follows the "Players" slider.
        super(SoundRegistry.MEMORY_CHARGE.get(), SoundSource.PLAYERS, RandomSource.create());
        volume = 1f;
        pitch = 1f;
        looping = false;
        delay = 0;
    }

    @Override
    public void tick()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
        {
            // Anchored to the listener, so distance attenuation never eats the clip.
            Vec3 pos = mc.player.getEyePosition();
            x = pos.x;
            y = pos.y;
            z = pos.z;
        }
    }

    @Override
    public boolean canStartSilent()
    {
        return true;
    }

    public void doStop()
    {
        stop();
    }
}
