package piloser.sanitypd.client;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.config.ConfigProxy;
import piloser.sanitypd.sound.HeartbeatSoundInstance;
import piloser.sanitypd.sound.InsanitySoundInstance;
import piloser.sanitypd.sound.SoundRegistry;
import piloser.sanitypd.thought.ThoughtType;
import piloser.sanitypd.util.MathHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

public class SoundPlayback
{
    private static InsanitySoundInstance insanity;
    private static HeartbeatSoundInstance heartbeat;
    private static float insanityTarget;
    private static float heartbeatTarget;
    private static float insanityStart;
    private static float heartbeatStart;
    private static int insanityProgress;
    private static int heartbeatProgress;
    private static int stepCd;
    private static int currentStepCd;
    private static int miscCd;
    private static SoundType currentStepSoundType;
    private static BlockPos currentStepBlockPos;

    private static final int INSANITY_I11N_TIME = 20;
    private static final int HEARTBEAT_I11N_TIME = 60;
    private static final SoundEvent[] MISC_SOUNDS = new SoundEvent[]
    {
            SoundEvents.CREEPER_PRIMED,
            SoundEvents.TNT_PRIMED,
            SoundEvents.SKELETON_AMBIENT,
            SoundEvents.SKELETON_STEP,
            SoundEvents.ZOMBIE_AMBIENT,
            SoundEvents.ZOMBIE_STEP,
            SoundEvents.ENDERMAN_AMBIENT,
            SoundEvents.HOSTILE_BIG_FALL,
            SoundEvents.CHEST_OPEN,
            SoundEvents.CHEST_CLOSE,
            SoundEvents.WOODEN_DOOR_OPEN,
            SoundEvents.WOODEN_TRAPDOOR_OPEN,
            SoundEvents.WOLF_GROWL,
    };
    public static final int STEP_COOLDOWN_MIN = 600;
    public static final int STEP_COOLDOWN_MAX = 1200;
    public static final int MISC_COOLDOWN_MIN = 800;
    public static final int MISC_COOLDOWN_MAX = 1400;
    public static final RandomSource RAND = RandomSource.create();

    /**
     * How deep the madness has to be before the scream may join the pool, as a madness fraction
     * ({@code .75} = sanity at or below 25%).
     *
 * <p>The wording is "while you carry a chaos-restraint thought, at low sanity it may show up".
     * "Low sanity" is taken from the tier that axis already uses elsewhere: chaos-restraint joins the
     * <b>severe</b> madness tier for its hint lines, which is sanity at or below 25%. It is one constant on
     * purpose - moving this number is the whole job of making the scream rarer or more common.
     */
    public static final float SCREAM_MADNESS = .75f;

    /**
     * Whether the scream may join this draw of the ambient pool.
     *
     * <p>A pure function of its two inputs on purpose: the client probe calls it with made-up numbers to show
     * the boundary, because no probe can wait for a real player to reach exactly 25% sanity carrying exactly
     * one chaos-restraint thought.
     *
     * @param chaosThoughts how many chaos-restraint thoughts the player has in the chain
     * @param madness       0 while clear-headed, 1 at zero sanity
     */
    public static boolean screamAllowed(int chaosThoughts, float madness)
    {
        return chaosThoughts > 0 && madness >= SCREAM_MADNESS;
    }

    static
    {
        stepCd = RAND.nextInt(STEP_COOLDOWN_MAX - STEP_COOLDOWN_MIN) + STEP_COOLDOWN_MIN;
        miscCd = RAND.nextInt(MISC_COOLDOWN_MAX - MISC_COOLDOWN_MIN) + MISC_COOLDOWN_MIN;
    }

    private static BlockPos pickFakeStepPos(LocalPlayer player)
    {
        // TODO: make it random
        Vec3 backVec = player.position().add(player.getLookAngle().multiply(-2f, -2f, -2f));
        return new BlockPos((int)backVec.x, (int)backVec.y, (int)backVec.z);
    }

    private static void playFakeSteps(LocalPlayer player, ISanity cap)
    {
        if (--stepCd <= 0)
        {
            stepCd = RAND.nextInt(STEP_COOLDOWN_MAX - STEP_COOLDOWN_MIN) + STEP_COOLDOWN_MIN;
            if (cap.getMadness() >= .4f)
            {
                stepCd = (int)(stepCd * (1f - MathHelper.clampNorm((Mth.inverseLerp(cap.getMadness(), .4f, .9f))) * .4f));
                currentStepSoundType = player.getBlockStateOn().getSoundType();
                currentStepBlockPos = pickFakeStepPos(player);
                currentStepCd = (RAND.nextInt(3) + 2) * 7;
            }
        }
        if (--miscCd <= 0)
        {
            miscCd = RAND.nextInt(MISC_COOLDOWN_MAX - MISC_COOLDOWN_MIN) + MISC_COOLDOWN_MIN;
            if (cap.getMadness() >= .4f)
            {
                miscCd = (int)(miscCd * (1f - MathHelper.clampNorm((Mth.inverseLerp(cap.getMadness(), .4f, .8f))) * .5f));

                // The scream is not an event of its own: it is one more face of this same pool, and it only
                // exists for a player who carries a chaos-restraint thought deep into madness. Reading the
                // count here (instead of at the call site) keeps the pool a single draw.
                boolean scream = screamAllowed(TypeHintPools.heldCount(ThoughtType.CHAOS_RESTRAINT), cap.getMadness());
                int slots = MISC_SOUNDS.length + (scream ? 1 : 0);
                int pick = RAND.nextInt(slots);
                SoundEvent sound = pick < MISC_SOUNDS.length
                        ? MISC_SOUNDS[pick]
                        : SoundRegistry.SCREAMING_WOMAN.get();

                player.level().playLocalSound(
                        pickFakeStepPos(player),
                        sound,
                        SoundSource.AMBIENT,
                        1.0f,
                        .5f,
                        false
                );
            }
        }
        if (currentStepCd > 0)
        {
            if (currentStepCd % 7 == 0)
            {
                player.level().playLocalSound(
                        currentStepBlockPos,
                        currentStepSoundType.getStepSound(),
                        SoundSource.AMBIENT,
                        currentStepSoundType.getVolume() * .5f,
                        currentStepSoundType.getPitch(),
                        false);
            }
            currentStepCd--;
        }
    }

    public static void playSounds(LocalPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator() || !ConfigProxy.getPlaySounds(player.level().dimension().location()))
        {
            if (insanity != null)
                insanity.doStop();
            if (heartbeat != null)
                heartbeat.doStop();

            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (insanity == null || insanity.isStopped())
        {
            insanity = new InsanitySoundInstance();
            mc.getSoundManager().play(insanity);
        }
        if (heartbeat == null || heartbeat.isStopped())
        {
            heartbeat = new HeartbeatSoundInstance();
            mc.getSoundManager().play(heartbeat);
        }
        player.getCapability(SanityProvider.CAP).ifPresent(cap ->
        {
            float insanityFactor = MathHelper.clampNorm(Mth.inverseLerp(cap.getMadness(), .55f, .8f));
            float heartbeatFactor = MathHelper.clampNorm(Mth.inverseLerp(cap.getMadness(), .7f, .8f));
            if (Math.abs(insanityFactor - insanityTarget) >= .05f)
            {
                insanityTarget = insanityFactor;
                insanityStart = insanity.factor;
                insanityProgress = 0;
            }
            else
            {
                if (insanityProgress <= INSANITY_I11N_TIME)
                    insanityTarget = insanityFactor;
                else
                    insanity.factor = insanityFactor;
            }
            if (Math.abs(heartbeatFactor - heartbeatTarget) >= .05f)
            {
                heartbeatTarget = heartbeatFactor;
                heartbeatStart = heartbeat.factor;
                heartbeatProgress = 0;
            }
            else
            {
                if (heartbeatProgress <= HEARTBEAT_I11N_TIME)
                    heartbeatTarget = heartbeatFactor;
                else
                    heartbeat.factor = heartbeatFactor;
            }
            if (insanityProgress <= INSANITY_I11N_TIME)
            {
                insanity.factor = Mth.lerp((float)insanityProgress / INSANITY_I11N_TIME, insanityStart, insanityTarget);
                insanityProgress++;
            }
            if (heartbeatProgress <= HEARTBEAT_I11N_TIME)
            {
                heartbeat.factor = Mth.lerp((float)heartbeatProgress / HEARTBEAT_I11N_TIME, heartbeatStart, heartbeatTarget);
                heartbeatProgress++;
            }

            insanity.setPos(player.getEyePosition());
            heartbeat.setPos(player.getEyePosition());

            playFakeSteps(player, cap);
        });
    }

    public static void onClientLevelLoad(ClientLevel level)
    {
        insanity = null;
        heartbeat = null;
    }
}