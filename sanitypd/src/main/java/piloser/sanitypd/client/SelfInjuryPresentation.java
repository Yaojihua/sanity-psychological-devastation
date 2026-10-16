package piloser.sanitypd.client;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.config.ConfigProxy;
import piloser.sanitypd.net.SelfInjuryPacket;
import piloser.sanitypd.sound.SoundRegistry;
import piloser.sanitypd.thought.ThoughtEffects;

/**
 * The client half of the non-suicidal self-injury act: the five seconds before it and the twenty after.
 *
 * <h2>Why the client owns all of this</h2>
 * Every piece here is presentation - the inner lines fading out, the view losing focus, the sound ducking, the
 * blood tendrils, the double image and the three clips - and none of it can be decided on the server, which
 * has no view to blur and no volume to lower. The server decides <b>when</b> (see {@code thought.SelfInjury})
 * and says so with one packet per moment; this class turns those moments into a timeline.
 *
 * <h2>The timeline</h2>
 * <pre>
 *   WARN   ├── 5 s ──────────────────────────────┤
 *          inner lines fade to nothing, the view blurs to 15% of the sharp value,
 *          everything but the wound's own clips and the heartbeat drops 20%
 *
 *   ACT    ├─ 0.2 s rise ─┤├─ 0.3 s fall ─┤        blood tendrils flash
 *          ├── 5 s ghosting, decaying ──┤
 *          ├── 5 s of sound 25% and recovering ──┤
 *          ├── 15 s in which no other hint pool may speak ──┤
 *          ├── the self-injury pool speaks from +1 tick for 6 s ──┤
 *          stab at once, tinnitus at 3 ticks, the drop of blood at 1 s
 * </pre>
 *
 * <p>The two counters that matter to the rest of the client are exposed as questions rather than as fields:
 * {@link #blurStrength()} and {@link #ghostStrength()} are read by the post-process passes, {@link #duckFactor()}
 * by the sound engine, {@link #hintFadeFactor()} and {@link #suppressesOtherPools()} by {@code GuiHandler}.
 */
@OnlyIn(Dist.CLIENT)
public final class SelfInjuryPresentation
{
    /** Blur at the peak of the sharp moment, in screen texels (the box blur's radius). */
    public static final float BLUR_MAX_TEXELS = 18.0f;

    /**
     * The wind-down's blur, as a fraction of {@link #BLUR_MAX_TEXELS}.
     *
 * <p>The design requires the view to blur <b>15%</b> over the five seconds before the act, so the number is
     * read as "15% of the blur this mechanic can produce" - which keeps the two moments on one scale instead of
     * inventing a second unit for the gentle one.
     */
    public static final float PRE_BLUR_FRACTION = 0.15f;

    /** Ticks from the act to the peak of the sharp blur (0.2 s). */
    public static final int BLUR_RISE_TICKS = 4;

    /** Ticks from that peak back to a sharp view (0.3 s). */
    public static final int BLUR_FALL_TICKS = 6;

    /** How long the double image lasts (5 s). */
    public static final int GHOST_TICKS = 100;

    /** The double image's displacement at the moment of the act, in screen texels. */
    public static final float GHOST_MAX_TEXELS = 9.0f;

    /** How long the other sounds stay ducked after the act (5 s). */
    public static final int DUCK_TICKS = 100;

 /** How far they drop at the moment of the act (the 75%). */
    public static final float DUCK_DEPTH = 0.75f;

 /** How far they drop by the end of the wind-down (the 20%). */
    public static final float PRE_DUCK_DEPTH = 0.20f;

    /** How long no other hint pool may speak after the act (15 s). */
    public static final int QUIET_TICKS = 300;

    /**
     * Ticks after the act at which the self-injury hint pool starts speaking (1 tick).
     *
 * <p>Specification: the pool answers the wound one tick later, never at the same moment - the stab, the
     * blur and the flash are the act, and the sentence about it comes just after.
     */
    public static final int HINT_DELAY_TICKS = 1;

    /** How long the self-injury pool speaks (6 s), measured from the end of {@link #HINT_DELAY_TICKS}. */
    public static final int HINT_WINDOW_TICKS = 120;

 /** How many blood pools one act leaves (one per second for five seconds). */
    public static final int BLOOD_DROPS = 5;

    /** Ticks between two pools (one second). */
    public static final int BLOOD_INTERVAL_TICKS = 20;

    /** Ticks after the act at which the tinnitus plays (3 ticks). */
    public static final int TINNITUS_DELAY_TICKS = 3;

    /** Ticks after the act at which the drop of blood plays (1 s). */
    public static final int DRIP_DELAY_TICKS = 20;

    /** Volume of the three clips. They are the act itself, so they are never ducked by it. */
    public static final float CLIP_VOLUME = 1.0f;

    /**
     * The sounds this act exempts from its own ducking: its three clips and the mod's heartbeat.
     *
     * <p>Read from the sound <b>event</b> location, which is what a {@code SoundInstance} carries, so the rule
     * holds for the looping heartbeat instance and for a one-shot clip alike.
     */
    private static final Set<String> DUCK_EXEMPT = Set.of(
            "self_injury_stab", "self_injury_tinnitus", "self_injury_drip", "heartbeat");

    private static float m_warnTicks;
    private static float m_blurTicks;
    private static float m_ghostTicks;
    private static float m_duckTicks;
    private static float m_quietTicks;

    /**
     * The self-injury pool's window, counted down from {@code HINT_WINDOW_TICKS + HINT_DELAY_TICKS}.
     *
     * <p>One counter for both phases: while it is above {@link #HINT_WINDOW_TICKS} the delay is still running,
     * and {@link #hintWindowOpen()} is false; once it drops to that value the pool may speak for the rest of
     * the count. Keeping the countdown here - and decrementing it in {@link #tick}, which runs before every
     * early exit of the caller - is deliberate: a counter that is only advanced inside the draw branch freezes
     * whenever that branch is skipped, which is exactly how the "all hints disappear" bug (the frozen-counter trap) was built.
     */
    private static float m_hintTicks;

    /**
     * Counts the acts, so a consumer can tell "the same window" from "a new one".
     *
     * <p>Added after a live session: the hint pool keyed its "already picked" test on the shared centre-screen
     * slot, which the two warning windows rewrite every frame - so the pool re-rolled on every frame of those
     * windows (55 picks for 23 acts, the pit this fix records). A generation is the one signal that cannot be overwritten by
     * anybody else.
     */
    private static int m_hintGeneration;

    /** How many times the hint pool has picked a line this session; the probe asserts one pick per act (the pit this fix records). */
    private static int m_hintPicks;

    /** Pools still to place for the current act, and the countdown to the next one. */
    private static int m_bloodDrops;
    private static float m_bloodTimer;
    private static float m_tinnitusTicks = -1f;
    private static float m_dripTicks = -1f;
    private static boolean m_stabPending;

    private SelfInjuryPresentation() {}

    /** One of the three announcements arrived. */
    public static void onPacket(byte mode)
    {
        switch (mode)
        {
            case SelfInjuryPacket.WARN ->
            {
                m_warnTicks = ThoughtEffects.SELF_INJURY_WARN_TICKS;
                SanityMod.LOGGER.info("[SELF-INJURY-C] announced: winding down over {} ticks", ThoughtEffects.SELF_INJURY_WARN_TICKS);
            }
            case SelfInjuryPacket.ACT ->
            {
                // The wound is here: the wind-down ends, the sharp blur, the double image, the ducking and the
                // silence all start counting from this tick, and the three clips are scheduled rather than
                // played in a row.
                m_warnTicks = 0f;
                m_blurTicks = BLUR_RISE_TICKS + BLUR_FALL_TICKS;
                m_ghostTicks = GHOST_TICKS;
                m_duckTicks = DUCK_TICKS;
                m_quietTicks = QUIET_TICKS;
                m_hintTicks = HINT_WINDOW_TICKS + HINT_DELAY_TICKS;
                m_hintGeneration++;

 // The first pool lands one second after the wound, then one per second (the schedule).
                m_bloodDrops = BLOOD_DROPS;
                m_bloodTimer = BLOOD_INTERVAL_TICKS;
                m_stabPending = true;
                m_tinnitusTicks = TINNITUS_DELAY_TICKS;
                m_dripTicks = DRIP_DELAY_TICKS;
                GuiHandler.requestBloodTendrilFlash();
                SanityMod.LOGGER.info("[SELF-INJURY-C] act: blur {} texels, ghost {} ticks, duck {} ticks, quiet {} ticks",
                        BLUR_MAX_TEXELS, GHOST_TICKS, DUCK_TICKS, QUIET_TICKS);
            }
            case SelfInjuryPacket.CANCEL ->
            {
                // A wound that was announced is not coming: the wind-down has to stop where it is, or the view
                // would blur and the sound would duck for nothing.
                if (m_warnTicks > 0f)
                    SanityMod.LOGGER.info("[SELF-INJURY-C] announcement withdrawn at {} ticks left", Math.round(m_warnTicks));

                m_warnTicks = 0f;
            }
            default -> SanityMod.LOGGER.warn("[SELF-INJURY-C] unknown self-injury packet mode {}", mode);
        }
    }

    /**
     * Advances the timeline. Called once per client tick from {@code GuiHandler#tick}, before that method's own
     * early exits: the counters have to keep draining while the game is paused or the player is in creative, or
     * a wound's tail would resume from where it was frozen after the pause screen is closed.
     */
    public static void tick(float dt)
    {
        m_warnTicks = Math.max(0f, m_warnTicks - dt);
        m_blurTicks = Math.max(0f, m_blurTicks - dt);
        m_ghostTicks = Math.max(0f, m_ghostTicks - dt);
        m_duckTicks = Math.max(0f, m_duckTicks - dt);
        m_quietTicks = Math.max(0f, m_quietTicks - dt);
        m_hintTicks = Math.max(0f, m_hintTicks - dt);

 // Blood pools: one per second for the five seconds. This runs on the same unconditional tick as
        // everything above - a schedule advanced inside a draw branch would freeze whenever that branch is
        // skipped, and the pools after the freeze would arrive in a burst.
        if (m_bloodDrops > 0)
        {
            m_bloodTimer -= dt;

            if (m_bloodTimer <= 0f)
            {
                m_bloodDrops--;
                m_bloodTimer += BLOOD_INTERVAL_TICKS;
                BloodDecals.spawnNearPlayer();
            }
        }

        if (m_stabPending)
        {
            m_stabPending = false;
            play(SoundRegistry.SELF_INJURY_STAB.get(), 1.0f);
        }

        if (m_tinnitusTicks >= 0f)
        {
            m_tinnitusTicks -= dt;

            if (m_tinnitusTicks < 0f)
                play(SoundRegistry.SELF_INJURY_TINNITUS.get(), 1.0f);
        }

        if (m_dripTicks >= 0f)
        {
            m_dripTicks -= dt;

            if (m_dripTicks < 0f)
                play(SoundRegistry.SELF_INJURY_DRIP.get(), 1.0f);
        }
    }

    /** Drops every counter; called when the client leaves a world, so a tail cannot leak into the next one. */
    public static void reset()
    {
        m_warnTicks = 0f;
        m_blurTicks = 0f;
        m_ghostTicks = 0f;
        m_duckTicks = 0f;
        m_quietTicks = 0f;
        m_hintTicks = 0f;
        m_bloodDrops = 0;
        m_bloodTimer = 0f;
        m_tinnitusTicks = -1f;
        m_dripTicks = -1f;
        m_stabPending = false;
    }

    /**
     * How much the view is blurred right now, as a fraction of {@link #BLUR_MAX_TEXELS}.
     *
     * <p>The sharp moment wins over the wind-down: during its 0.5 s the value runs up to 1 and back down, and
     * the gentle 15% ramp is only what remains when there is no act in flight.
     */
    public static float blurStrength()
    {
        if (m_blurTicks > 0f)
        {
            float elapsed = (BLUR_RISE_TICKS + BLUR_FALL_TICKS) - m_blurTicks;

            return elapsed <= BLUR_RISE_TICKS
                    ? Math.min(1f, elapsed / BLUR_RISE_TICKS)
                    : Math.max(0f, m_blurTicks / (float) BLUR_FALL_TICKS);
        }

        if (m_warnTicks > 0f)
            return PRE_BLUR_FRACTION * (1f - m_warnTicks / (float) ThoughtEffects.SELF_INJURY_WARN_TICKS);

        return 0f;
    }

    /** How strong the double image is right now; {@code 0} when there is none. */
    public static float ghostStrength()
    {
        return m_ghostTicks <= 0f ? 0f : m_ghostTicks / (float) GHOST_TICKS;
    }

    /**
     * What every other sound is multiplied by right now.
     *
 * <p>Two stages, and the jump between them is the: the wind-down takes everything down 20% by the
     * moment of the act, and the act itself drops it to 25% and recovers over five seconds.
     */
    public static float duckFactor()
    {
        if (m_duckTicks > 0f)
            return 1f - DUCK_DEPTH * (m_duckTicks / (float) DUCK_TICKS);

        if (m_warnTicks > 0f)
            return 1f - PRE_DUCK_DEPTH * (1f - m_warnTicks / (float) ThoughtEffects.SELF_INJURY_WARN_TICKS);

        return 1f;
    }

    /** Whether a sound must keep its own volume while this act ducks everything else. */
    public static boolean isDuckExempt(ResourceLocation location)
    {
        return location != null
                && SanityMod.MODID.equals(location.getNamespace())
                && DUCK_EXEMPT.contains(location.getPath());
    }

    /**
     * Multiplier for the alpha of any inner line that is on screen.
     *
     * <p>Falls from 1 to 0 across the wind-down, which is how "the lines already on screen fade out before the
     * act" is implemented: the line keeps its own fade curve and this only scales it down, so nothing else about
     * the way lines appear has to change.
     */
    public static float hintFadeFactor()
    {
        if (m_warnTicks <= 0f)
            return 1f;

        return m_warnTicks / (float) ThoughtEffects.SELF_INJURY_WARN_TICKS;
    }

    /**
     * Whether an act is announced, in which case the centre deals no new line: the one on screen is on its way
     * out (see {@link #hintFadeFactor}) and a fresh sentence appearing under it would be the opposite of the
 * quiet the design requires.
     */
    public static boolean holdsTheCentre()
    {
        return m_warnTicks > 0f;
    }

    /**
     * Whether every hint pool except the self-injury one has to stay silent (the act's 15-second tail).
     *
     * <p>The self-injury pool plugs in at exactly one place - the branch in {@code GuiHandler#tickHint} that
     * reads this method - and it is the only voice allowed inside that tail; see {@link #hintWindowOpen()}.
     */
    public static boolean suppressesOtherPools()
    {
        return m_quietTicks > 0f;
    }

    /**
     * Whether the self-injury hint pool may speak right now: from {@link #HINT_DELAY_TICKS} after the wound
     * until {@link #HINT_WINDOW_TICKS} later.
     *
     * <p>Only the <b>when</b> lives here; the line itself is picked and drawn by {@code GuiHandler}, which
     * keeps this class free of the hint pools and lets the same six seconds be verified from the log.
     */
    public static boolean hintWindowOpen()
    {
        return m_hintTicks > 0f && m_hintTicks <= HINT_WINDOW_TICKS;
    }

    /** Called once per pick by {@code GuiHandler}; see {@link #hintPickCount()}. */
    public static void noteHintPicked()
    {
        m_hintPicks++;
    }

    /** Picks so far, so "one line per act" can be judged from a live session instead of by eye (the pit this fix records). */
    public static int hintPickCount()
    {
        return m_hintPicks;
    }

    /** Which act the current window belongs to; changes exactly when a new window opens. */
    public static int hintWindowGeneration()
    {
        return m_hintGeneration;
    }

    /** Ticks left in the self-injury pool's window; {@code 0} when it is not open. Used by the probe. */
    public static float hintWindowTicks()
    {
        return hintWindowOpen() ? m_hintTicks : 0f;
    }

    /** Whether anything of this act is still running; used by the log line and by the probe. */
    public static boolean isActive()
    {
        return m_warnTicks > 0f || m_blurTicks > 0f || m_ghostTicks > 0f || m_duckTicks > 0f || m_quietTicks > 0f
                || m_hintTicks > 0f || m_stabPending || m_tinnitusTicks >= 0f || m_dripTicks >= 0f;
    }

    /**
     * The whole state as one line, for the probe.
     *
     * <p>Machine-readable on purpose: the two things that can only be verified by ear (the ducking) and by eye
     * (the blur) still need a verdict line that says the timeline ran, which is what this is.
     */
    public static String stateLine()
    {
        return "warn=" + Math.round(m_warnTicks)
                + " blur=" + Math.round(m_blurTicks) + "(" + String.format(java.util.Locale.ROOT, "%.3f", blurStrength()) + ")"
                + " ghost=" + Math.round(m_ghostTicks) + "(" + String.format(java.util.Locale.ROOT, "%.2f", ghostStrength()) + ")"
                + " duck=" + Math.round(m_duckTicks) + "(" + String.format(java.util.Locale.ROOT, "%.2f", duckFactor()) + ")"
                + " quiet=" + Math.round(m_quietTicks)
                + " hint=" + Math.round(m_hintTicks) + (hintWindowOpen() ? "(open)" : "(closed)")
                + " stab=" + m_stabPending + " tinnitus=" + Math.round(m_tinnitusTicks) + " drip=" + Math.round(m_dripTicks);
    }

    /** Plays one clip at the listener: these are the player's own body, not a point in the world. */
    private static void play(SoundEvent event, float pitch)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc == null || mc.player == null || mc.getSoundManager() == null || event == null)
            return;

        if (!ConfigProxy.getPlaySounds(mc.player.level().dimension().location()))
            return;

        // Anchored at the listener (relative = true, no attenuation) like the mod's other own-body sounds: the
        // wound is not a thing happening somewhere in the world, it is happening to the player.
        mc.getSoundManager().play(new SimpleSoundInstance(event.getLocation(), SoundSource.MASTER, CLIP_VOLUME,
                pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true));

        SanityMod.LOGGER.info("[SELF-INJURY-C] clip played: {}", event.getLocation());
    }
}
