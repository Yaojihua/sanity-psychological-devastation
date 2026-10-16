package piloser.sanitypd.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The tape hiss bed: the sound of a blank tape running, looping for as long as the tape screen is up.
 *
 * <p>The clip is 16.63 s of steady noise whose level only moves by about 1 dB and whose loop seam
 * differs by 0.5 dB, so looping it is inaudible (measured when the author supplied it).
 *
 * <p><b>Relative and unattenuated on purpose.</b> The tape screen is a pause screen, and vanilla only
 * ticks {@code TickableSoundInstance}s while the game is <i>not</i> paused: {@code SoundEngine#tick}
 * skips the whole non-paused half, which is where ticking instances are advanced. A world-positioned
 * instance therefore keeps whatever coordinates it was last given - and a freshly built one sits at the
 * world origin - for the entire playback, so distance attenuation turns it into silence no matter how
 * loud the sliders are. Anchoring the bed to the listener instead makes it audible wherever the player
 * happens to stand, needs no ticking at all, and matches how vanilla itself plays sounds that belong to
 * the player rather than to a point in the world.
 */
public final class MemoryHissSoundInstance extends AbstractTickableSoundInstance
{
    public MemoryHissSoundInstance()
    {
        // PLAYERS: every clip of the tape (charge-up, hiss bed, eject clicks) follows the same slider, so
        // no single one of them can go missing on its own.
        super(SoundRegistry.MEMORY_TAPE_HISS.get(), SoundSource.PLAYERS, RandomSource.create());
        volume = 0.8f;
        pitch = 1f;
        looping = true;
        delay = 0;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick()
    {
        // Nothing to keep in sync: the bed is relative to the listener, so it has no world position.
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
