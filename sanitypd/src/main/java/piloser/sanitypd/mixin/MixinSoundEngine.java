package piloser.sanitypd.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;

import piloser.sanitypd.client.SelfInjuryPresentation;

/**
 * Lowers the volume of everything except the wound's own clips and the heartbeat while the self-injury act
 * ducks the world (see {@code client.SelfInjuryPresentation}).
 *
 * <h2>Why this has to be a mixin</h2>
 * There is no API for "make everything quieter for five seconds": vanilla's own answer to that question is the
 * per-category volume sliders, which are global, persisted, and shared with the options screen. Ducking through
 * them would mean writing the player's audio settings and putting them back, and a crash in between would leave
 * the game permanently quiet. The engine's own volume computation is the narrow place to scale instead.
 *
 * <h2>Two halves, because sounds are two populations</h2>
 * <ul>
 *   <li>A sound that is <b>starting</b> asks {@code calculateVolume} for its volume, so scaling that answer is
 *       enough - and this is also the only place the volume of a one-shot is ever computed.</li>
 *   <li>A sound that is <b>already playing</b> has its volume in a channel and is not recomputed unless it
 *       loops, so music or a long ambience would simply ignore the ducking. The second injection walks the
 *       engine's own instance-to-channel map while a duck is in force (and once more on the tick it ends) and
 *       writes the recomputed volume back.</li>
 * </ul>
 *
 * <p>The recomputed volume comes from {@link #calculateVolume}, which already includes the ducking - so there is
 * exactly one place where the factor is applied and the sweep cannot double it.
 *
 * <p>The exemption list is read from the sound's <b>event</b> location ({@code SelfInjuryPresentation
 * #isDuckExempt}), which is the only thing a {@code SoundInstance} is guaranteed to carry: reading the sound's
 * file name would depend on how the pack resolved the event.
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine
{
    /** Every sound the engine currently has a channel for; vanilla's own field, walked read-only here. */
    @Shadow
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    /** Vanilla's per-instance volume, including the category slider; the duck is applied on its answer. */
    @Shadow
    public abstract float calculateVolume(SoundInstance instance);

    /** Whether the previous tick was ducking, so the last un-ducked write is not skipped. */
    @Unique
    private boolean sanitypd$wasDucking;

    /** Scales every sound that is about to start. */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void sanitypd$duckStartedSounds(SoundInstance instance, CallbackInfoReturnable<Float> cir)
    {
        if (sanitizeExempt(instance))
            return;

        float factor = SelfInjuryPresentation.duckFactor();

        if (factor < 1f)
            cir.setReturnValue(cir.getReturnValueF() * factor);
    }

    /**
     * Writes the ducked volume into the channels of the sounds that are already playing.
     *
     * <p>Runs at the end of the engine's own unpaused tick, so it cannot fight the engine's own writes earlier in
     * the same tick. It does nothing at all outside a duck, and exactly one extra pass on the tick a duck ends -
     * which is what puts the volume of a long sound back where it was.
     */
    @Inject(method = "tickNonPaused()V", at = @At("TAIL"))
    private void sanitypd$duckPlayingSounds(CallbackInfo ci)
    {
        boolean ducking = SelfInjuryPresentation.duckFactor() < 1f;

        if (!ducking && !sanitypd$wasDucking)
            return;

        try
        {
            for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : instanceToChannel.entrySet())
            {
                SoundInstance instance = entry.getKey();

                if (instance == null || sanitizeExempt(instance))
                    continue;

                float volume = calculateVolume(instance);
                entry.getValue().execute(channel -> channel.setVolume(volume));
            }
        }
        catch (Throwable ignored)
        {
            // Audio must never be able to break the game: a channel that died between the map read and the write
            // is the expected failure here, and the next sound simply computes its own volume.
        }

        sanitypd$wasDucking = ducking;
    }

    /** Whether this instance is one the act leaves alone, or is not identifiable at all. */
    @Unique
    private boolean sanitizeExempt(SoundInstance instance)
    {
        return instance == null || SelfInjuryPresentation.isDuckExempt(instance.getLocation());
    }
}
