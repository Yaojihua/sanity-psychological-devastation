package piloser.sanitypd.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The tape hiss bed: the sound of a blank tape running, looping for as long as the tape screen is up.
 *
 * <p>The clip is 16.63 s of steady noise whose level only moves by about 1 dB and whose loop seam
 * differs by 0.5 dB, so looping it is inaudible (measured when the owner supplied it).
 */
public final class MemoryHissSoundInstance extends AbstractTickableSoundInstance
{
    public MemoryHissSoundInstance()
    {
        // AMBIENT: a background bed, so it follows the "Ambient" slider rather than the item sliders.
        super(SoundRegistry.MEMORY_TAPE_HISS.get(), SoundSource.AMBIENT, RandomSource.create());
        volume = 0.8f;
        pitch = 1f;
        looping = true;
        delay = 0;
    }

    @Override
    public void tick()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
        {
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
