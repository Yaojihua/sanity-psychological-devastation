package piloser.sanitypd.sound;

import piloser.sanitypd.SanityMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundRegistry
{
    public static DeferredRegister<SoundEvent> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SanityMod.MODID);

    public static final RegistryObject<SoundEvent> INSANITY             = registerSoundEvent("insanity");
    public static final RegistryObject<SoundEvent> HEARTBEAT            = registerSoundEvent("heartbeat");
    public static final RegistryObject<SoundEvent> SWISH                = registerSoundEvent("swish");
    public static final RegistryObject<SoundEvent> FLOWERS_EQUIP        = registerSoundEvent("flowers_equip");
    public static final RegistryObject<SoundEvent> INNER_ENTITY_HURT    = registerSoundEvent("inner_entity_hurt");
    public static final RegistryObject<SoundEvent> INNER_ENTITY_DEATH   = registerSoundEvent("inner_entity_death");

    // ---- Three dedicated screaming crawler sounds ----
    // All three are MONO OGG files: Minecraft's OpenAL backend only does 3D positioning for mono
    // sources, while stereo degrades to full volume at any distance. Mob sounds must be mono to
    // attenuate with distance.
    /** Roar when the crawler spots a player and starts chasing. */
    public static final RegistryObject<SoundEvent> SCREAMING_CRAWLER_ROAR    = registerSoundEvent("screaming_crawler_roar");
    /** Ambient hum played at random through the vanilla mob ambient mechanic. */
    public static final RegistryObject<SoundEvent> SCREAMING_CRAWLER_AMBIENT = registerSoundEvent("screaming_crawler_ambient");
    /** Self-destruct. */
    public static final RegistryObject<SoundEvent> SCREAMING_CRAWLER_EXPLODE = registerSoundEvent("screaming_crawler_explode");

    // ---- Lost memory fragment (story item, part 1) ----
    // Both are played client-side only, anchored to the listener, so the mono rule of the crawler sounds
    // above does not apply: the charge clip is stereo and stays stereo (it is heard by the user alone),
    // while the hiss bed is the owner's own mono recording.
    /** Charge-up clip: starts one second into the hold and ends exactly when the tape screen opens. */
    public static final RegistryObject<SoundEvent> MEMORY_CHARGE = registerSoundEvent("memory_charge");
    /** Blank-tape hiss, looped for as long as the tape screen is up. */
    public static final RegistryObject<SoundEvent> MEMORY_TAPE_HISS = registerSoundEvent("memory_tape_hiss");
    /** The tape-eject click that announces every script line (the first half of the split eject recording). */
    public static final RegistryObject<SoundEvent> MEMORY_TAPE_EJECT = registerSoundEvent("memory_tape_eject");

    public static RegistryObject<SoundEvent> registerSoundEvent(String name)
    {
        return DEFERRED_REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SanityMod.MODID, name)));
    }

    public static void register(IEventBus eventBus)
    {
        DEFERRED_REGISTER.register(eventBus);
    }
}