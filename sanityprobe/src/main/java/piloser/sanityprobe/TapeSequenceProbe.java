package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Client-side probe for the lost-memory-fragment tape: the state of the sequence, the handle behind its
 * hiss bed, and whether the mod really does silence everything that is not the tape itself.
 *
 * <h2>Why these two things need a probe</h2>
 * <ul>
 *   <li><b>The bed can go silent without saying so.</b> The tape screen is a pause screen, and vanilla only
 *       advances ticking sound instances while the game is <i>not</i> paused. A bed anchored to a point in
 *       the world therefore keeps its coordinates - and a freshly built one sits at the world origin - for
 *       the whole playback, where distance attenuation quietly turns it into nothing: the play is counted,
 *       no error is logged, and the player hears silence. {@code relative=} on every {@code SOUND-C} line
 *       separates the two cases, and {@code hiss=alive} here says the handle still exists.</li>
 *   <li><b>Silencing the world is only observable at the sound events.</b> Every sound that arrives while
 *       the tape is up is classified: the mod's own {@code memory_} clips have to survive, everything else
 *       has to arrive already cancelled. A foreign sound that reaches the engine is a leak, and a tape clip
 *       that was cancelled means the mod muted itself.</li>
 * </ul>
 *
 * <p>Counters belong to one tape run: they reset when a tape opens and a verdict is printed when it closes,
 * so a single log is enough to judge a run.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TapeSequenceProbe
{
    private static final String TAG = "TAPE-49";

    /** The mod's own types, matched by name so the probe carries no compile dependency on the mod. */
    private static final String SCREEN_CLASS   = "piloser.sanitypd.client.MemoryTapeScreen";
    private static final String ITEM_CLASS     = "piloser.sanitypd.item.LostMemoryFragmentItem";
    private static final String SEQUENCE_CLASS = "piloser.sanitypd.client.MemorySequence";

    /** Sounds the mod owns that belong to the tape; everything under this namespace and prefix is allowed. */
    private static final String MOD_NAMESPACE = "sanitypd";
    private static final String TAPE_SOUND_PREFIX = "memory_";

    /** How often the state line is sampled while the sequence is up. */
    private static final int STATE_EVERY_TICKS = 40;

    /** How many blocked sounds are named in full, so the log shows what was silenced without flooding. */
    private static final int BLOCKED_SAMPLES = 3;

    private static int s_tick;
    private static String s_lastState = "";
    private static String s_hud = "idle";

    private static boolean s_tapeWasUp;
    private static int s_allowed;
    private static int s_blocked;
    private static int s_leaked;
    private static int s_ownMuted;
    private static String s_firstBlocked = "-";
    private static boolean s_reportedError;

    /** How many times a tape opened while the local player was dead: the livelock's signature, must stay 0. */
    private static int s_openedWhileDead;

    static
    {
        ProbeHud.registerLine(() -> "\u00A7e[TAPE] " + s_hud);
    }

    private TapeSequenceProbe() {}

    /**
     * Classifies every sound while the tape is up.
     *
     * <p>LOWEST on purpose: the mod cancels its foreign sounds at normal priority, so running last is what
     * makes the result observable. The untouched sound stays reachable through
     * {@link PlaySoundEvent#getOriginalSound()}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event)
    {
        try
        {
            if (!tapeUp())
                return;

            SoundInstance original = event.getOriginalSound();

            if (original == null)
                return;

            ResourceLocation id = original.getLocation();
            boolean ours = id != null && MOD_NAMESPACE.equals(id.getNamespace())
                    && id.getPath().startsWith(TAPE_SOUND_PREFIX);

            if (event.getSound() != null)
            {
                if (ours)
                {
                    s_allowed++;
                }
                else
                {
                    // A foreign sound reached the engine: the mute did not hold for it.
                    s_leaked++;
                    if ("-".equals(s_firstBlocked))
                        s_firstBlocked = "LEAK " + id;
                    ProbeLog.log(TAG, "LEAK " + id + " reached the engine while the tape was up");
                }
            }
            else if (ours)
            {
                // One of the tape's own clips was cancelled: the mod (or something else) muted the tape.
                s_ownMuted++;
                ProbeLog.log(TAG, "own clip muted: " + id);
            }
            else
            {
                s_blocked++;
                if ("-".equals(s_firstBlocked))
                    s_firstBlocked = String.valueOf(id);
                if (s_blocked <= BLOCKED_SAMPLES)
                    ProbeLog.log(TAG, "blocked #" + s_blocked + " " + id);
            }
        }
        catch (Throwable ignored)
        {
            // The probe must never affect gameplay.
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        try
        {
            boolean tape = tapeUp();
            boolean charging = charging();
            s_tick++;

            if (tape != s_tapeWasUp)
            {
                if (tape)
                {
                    s_allowed = 0;
                    s_blocked = 0;
                    s_leaked = 0;
                    s_ownMuted = 0;
                    s_firstBlocked = "-";

                    // The signature of the livelock that once froze a real session: the player dies inside the
                    // tape, the death screen replaces it, and the still-active item use reopens the tape on the
                    // next tick - the two screens then take turns forever. The mod now refuses to open while
                    // dead, so this counter has to stay at zero; if it ever moves, that is the bug back.
                    if (dead())
                    {
                        s_openedWhileDead++;
                        ProbeLog.log(TAG, "tape OPENED WHILE DEAD (#" + s_openedWhileDead
                                + ") - the death screen and the tape are fighting over the display");
                    }

                    ProbeLog.log(TAG, "tape opened - counting sounds until it closes");
                }
                else
                {
                    verdict();
                }

                s_tapeWasUp = tape;
            }

            if ((tape || charging) && s_tick % STATE_EVERY_TICKS == 0)
                state(tape, charging);
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "tick check failed (expected only when sanitypd is absent): " + t);
        }
    }

    /** The live sequence state, printed whenever it differs from the previous sample. */
    private static void state(boolean tape, boolean charging)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        int remaining = player == null || !charging ? 0 : player.getUseItemRemainingTicks();

        String line = String.format(Locale.ROOT, "tape=%s charging=%s remaining=%d hudHidden=%s hiss=%s",
                tape, charging, remaining, hudHidden(), hissState());

        if (!line.equals(s_lastState))
        {
            s_lastState = line;
            ProbeLog.log(TAG, line);
        }

        s_hud = String.format(Locale.ROOT, "tape=%s allowed=%d blocked=%d leak=%d",
                tape, s_allowed, s_blocked, s_leaked);
    }

    /**
     * One verdict per tape run.
     *
     * <p>The two failures that matter are asymmetric and both are fatal to the effect: a leaked foreign
     * sound means the world was still audible, and a muted clip of the tape's own means the tape silenced
     * itself. That nothing was blocked at all is only worth a note - a run can legitimately happen in a
     * quiet place.
     */
    private static void verdict()
    {
        boolean ok = s_leaked == 0 && s_ownMuted == 0 && s_openedWhileDead == 0;
        String note = s_blocked == 0 ? " (no outside sound was offered)" : "";

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "tape closed: allowed=%d blocked=%d leaked=%d ownMuted=%d openedWhileDead=%d firstBlocked=%s => %s%s",
                s_allowed, s_blocked, s_leaked, s_ownMuted, s_openedWhileDead, s_firstBlocked,
                ok ? "PASS" : "CHECK", note));

        s_hud = "closed leak=" + s_leaked + " deadOpen=" + s_openedWhileDead;
    }

    /** Whether the local player is dead or dying right now. */
    private static boolean dead()
    {
        LocalPlayer player = Minecraft.getInstance().player;

        return player != null && player.isDeadOrDying();
    }

    /** Whether the tape screen owns the display right now. */
    private static boolean tapeUp()
    {
        Minecraft mc = Minecraft.getInstance();

        return mc.screen != null && SCREEN_CLASS.equals(mc.screen.getClass().getName());
    }

    /** Whether the player is holding the story item down, i.e. inside the charging half of the sequence. */
    private static boolean charging()
    {
        LocalPlayer player = Minecraft.getInstance().player;

        return player != null && player.isUsingItem()
                && ITEM_CLASS.equals(player.getUseItem().getItem().getClass().getName());
    }

    /** Whether the mod is suppressing the HUD right now, read out of the sequence's own flag. */
    private static String hudHidden()
    {
        try
        {
            Field field = Class.forName(SEQUENCE_CLASS).getDeclaredField("s_hudHidden");
            field.setAccessible(true);

            return String.valueOf(field.getBoolean(null));
        }
        catch (Throwable t)
        {
            return "?";
        }
    }

    /**
     * Whether the hiss bed exists and is still running.
     *
     * <p>This is the handle the sequence keeps for the bed: {@code none} means it was never created (or was
     * already released), and {@code stopped} means something ended it early.
     */
    private static String hissState()
    {
        try
        {
            Field field = Class.forName(SEQUENCE_CLASS).getDeclaredField("s_hiss");
            field.setAccessible(true);
            Object hiss = field.get(null);

            if (hiss == null)
                return "none";

            // A compiled call, not a reflective one. The mod's own field is safe to look up by literal name
            // (a mod's members are never renamed), but isStopped() belongs to a vanilla interface, and every
            // vanilla member carries an SRG name in production - so looking that one up by name answers with
            // an exception rather than an answer.
            boolean stopped = ((net.minecraft.client.resources.sounds.TickableSoundInstance) hiss).isStopped();

            return stopped ? "stopped" : "alive";
        }
        catch (Throwable t)
        {
            reportOnce("the hiss handle could not be read", t);

            return "?";
        }
    }

    /**
     * Reports a swallowed failure once per session.
     *
     * <p>A probe group that fails silently is indistinguishable from a group that had nothing to say - this
     * one hid its own reflection failure behind a bare {@code ?} until a real session was read.
     */
    private static void reportOnce(String what, Throwable t)
    {
        if (!s_reportedError)
        {
            s_reportedError = true;
            ProbeLog.log(TAG, what + ": " + t);
        }
    }
}

