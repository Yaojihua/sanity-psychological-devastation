package piloser.sanityprobe;

import java.lang.reflect.Method;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Round-75 probe: the non-suicidal self-injury act's client timeline.
 *
 * <h2>Why a probe and not a self-check</h2>
 * Everything this act adds is presentation - the blur, the double image, the sound ducking, the inner lines
 * fading out, the fifteen seconds of silence and the three clips - and the dedicated server refuses to load any
 * of it ({@code SelfInjuryPresentation} is client-only). The server half is covered by the round's self-check;
 * this group is the only machine-readable witness that the client half runs, and the only thing it cannot judge
 * is whether the blur and the ducking <b>look and sound</b> right, which stays the eye and ear.
 *
 * <h2>What it prints</h2>
 * Nothing at all while the act is idle. Once per second while it is running it prints the mod's own state line,
 * de-duplicated, and it counts the samples and the two transitions it saw, so "the act never happened in this
 * session" (a {@code CHECK} with a reason) is told apart from "the group never ran".
 *
 * <h2>Reflection, not a dependency</h2>
 * The probe ships alone and must load with sanitypd absent (see {@code SanityReflect}), so every read here is a
 * reflective call into {@code piloser.sanitypd.client.SelfInjuryPresentation} and a miss is reported as
 * {@code CHECK(mod absent)} rather than thrown.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SelfInjuryProbe
{
    /** Tag of this group; every line it writes starts with {@code [SELF-INJURY-75]}. */
    private static final String TAG = "SELF-INJURY-75";

    /** The mod's own overview method, and the four numbers this group judges. */
    private static final String MOD_CLASS = "piloser.sanitypd.client.SelfInjuryPresentation";

    /** Once a second, like the other timeline probes. */
    private static final int INTERVAL_TICKS = 20;

 /** The widest a duck may ever be, and the depth the author specified for the wound's five seconds. */
    private static final float DUCK_FLOOR_ACT = 0.25f;

 /** The depth the author specified for the wind-down. */
    private static final float DUCK_FLOOR_WARN = 0.80f;

 /** The window on the self-injury hint pool: one tick after the wound, six seconds long. */
    private static final int HINT_DELAY_SPEC = 1;
    private static final int HINT_WINDOW_SPEC = 120;

 /** The design requires a bigger double image and a bigger blur this round. */
    private static final float BLUR_TEXELS_SPEC = 18.0f;
    private static final float GHOST_TEXELS_SPEC = 9.0f;

 /** Sanity the act costs when it happens at or above the arm threshold (owner's rule this round). */
    private static final float SANITY_PENALTY_SPEC = 20.0f;

    private static int s_tick;
    private static int s_samples;
    private static int s_warnSamples;
    private static int s_actSamples;
    private static int s_bad;
    private static boolean s_wasWarn;
    private static boolean s_wasQuiet;
    private static String s_lastState = "";
    private static boolean s_verdictPrinted;
    private static Class<?> s_clazz;
    private static boolean s_lookedUp;
    private static boolean s_constantsReported;
    /** Samples taken while the self-injury hint pool's window was open; zero means it never opened. */
    private static int s_hintSamples;

    /** Picks seen so far, so a second pick inside one act is caught the moment it happens (the pit this fix records). */
    private static int s_picksSeen;

    private SelfInjuryProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % INTERVAL_TICKS != 0)
            return;

        sample();
    }

    private static void sample()
    {
        Class<?> clazz = modClass();

        if (clazz == null)
            return;

        Boolean active = callBoolean(clazz, "isActive");

        if (active == null || !active)
        {
            // The act ended (or never started): print the verdict once, when there was anything to judge.
            if (s_samples > 0)
            {
                verdict();
                s_samples = 0;
                s_warnSamples = 0;
                s_actSamples = 0;
                s_bad = 0;
                s_wasWarn = false;
                s_wasQuiet = false;
                s_lastState = "";
                s_verdictPrinted = false;
            }

            return;
        }

        s_samples++;

        float blur = number(clazz, "blurStrength");
        float ghost = number(clazz, "ghostStrength");
        float duck = number(clazz, "duckFactor");
        boolean warn = Boolean.TRUE.equals(callBoolean(clazz, "holdsTheCentre"));
        boolean quiet = Boolean.TRUE.equals(callBoolean(clazz, "suppressesOtherPools"));
        boolean hintOpen = Boolean.TRUE.equals(callBoolean(clazz, "hintWindowOpen"));
        String state = callString(clazz, "stateLine");

        if (!s_constantsReported)
        {
            s_constantsReported = true;
            reportPoolConstants(clazz);
        }

        if (hintOpen)
            s_hintSamples++;

        // One pick per act is the whole point of the fix for the pit this fix records: the pick used to be keyed on the shared
        // centre-screen slot, which the warning windows rewrite every frame, so a single act could re-roll the
        // pool dozens of times (55 picks for 23 acts in the session that found it).
        int picks = (int) number(clazz, "hintPickCount");

        if (picks > s_picksSeen + 1 && s_actSamples > 0)
            bad("the self-injury pool re-rolled without a new act: picks=" + picks + " acts=" + s_actSamples);

        s_picksSeen = picks;

        if (warn)
            s_warnSamples++;

        // The wound itself is the moment the quiet tail starts; the wind-down is the five seconds before it.
        if (quiet && !s_wasQuiet)
        {
            s_actSamples++;
            ProbeLog.log(TAG, "ACT: " + state);
        }

        if (warn && !s_wasWarn)
            ProbeLog.log(TAG, "WARN began: " + state);

        s_wasWarn = warn;
        s_wasQuiet = quiet;

 // --- judgements: ranges and the two relations the author specified ---------------------------------
        if (!(blur >= 0f && blur <= 1f))
            bad("blur strength out of range: " + blur);

        if (!(ghost >= 0f && ghost <= 1f))
            bad("ghost strength out of range: " + ghost);

        if (!(duck > 0f && duck <= 1f))
            bad("duck factor out of range: " + duck);

        if (quiet && duck < DUCK_FLOOR_ACT)
            bad("duck deeper than the wound's own five seconds allow: " + duck);

        if (warn && !quiet && duck < DUCK_FLOOR_WARN)
            bad("the wind-down ducks past the 20% the owner specified: " + duck);

        if (!warn && !quiet && (blur > 0f || duck < 1f))
            bad("an effect is live with no act in flight: blur=" + blur + " duck=" + duck);

        if (blur > 0f && ghost > 0f)
            bad("the sharp blur and the double image are on screen together: " + state);

        // The pool's window belongs to the act's tail and to nothing else: outside it, the centre is free.
        if (hintOpen && !quiet)
            bad("the self-injury pool's window is open outside the act's tail: " + state);

        if (!state.equals(s_lastState))
        {
            s_lastState = state;
            ProbeLog.log(TAG, "t=" + s_samples + "s " + state);
        }
    }

    /** One judgement per line, plus the counts, so "nothing happened" and "nothing ran" stay distinct. */
    private static void verdict()
    {
        String detail = "samples=" + s_samples + " warnSamples=" + s_warnSamples
                + " acts=" + s_actSamples + " hintSamples=" + s_hintSamples + " hintPicks=" + s_picksSeen
                + " bad=" + s_bad;

        s_verdictPrinted = true;

        if (s_bad > 0)
        {
            ProbeLog.log(TAG, detail + " => CHECK(read the lines above)");
            return;
        }

        if (s_actSamples > 0 && s_hintSamples == 0)
        {
            ProbeLog.log(TAG, detail + " => CHECK(an act was seen but the self-injury pool's six-second window"
                    + " was never open: the window is one sample a second, so a window that opened and closed"
                    + " inside two seconds would be missed - check the [SELF-INJURY-C] hint pool lines too)");
            return;
        }

        if (s_actSamples == 0 && s_warnSamples == 0)
        {
            ProbeLog.log(TAG, detail + " => CHECK(an act ran but neither its announcement nor its tail was seen:"
                    + " the observation window is one sample a second, so a very short wind-down can slip between"
                    + " two samples)");
            return;
        }

        ProbeLog.log(TAG, detail + " => OK(range and relation checks passed)");
    }

    private static void bad(String what)
    {
        s_bad++;
        ProbeLog.log(TAG, "bad: " + what);
    }

    // ------------------------------------------------------------------ reflection

    /**
 * Reads the two constants that define the hint pool's window and judges them against the specification.
     *
     * <p>Read once per session: they are constants, so repeating the line every act would only bury it.
     */
    private static void reportPoolConstants(Class<?> clazz)
    {
        int delay = field(clazz, "HINT_DELAY_TICKS");
        int window = field(clazz, "HINT_WINDOW_TICKS");
        float blur = floatField(clazz, "BLUR_MAX_TEXELS");
        float ghost = floatField(clazz, "GHOST_MAX_TEXELS");
        float penalty = floatField(thoughtEffects(), "SELF_INJURY_SANITY_PENALTY");

        ProbeLog.log(TAG, "hint pool window delay=" + delay + " ticks, window=" + window + " ticks => "
                + (delay == HINT_DELAY_SPEC && window == HINT_WINDOW_SPEC
                        ? "OK" : "CHECK(expected " + HINT_DELAY_SPEC + "/" + HINT_WINDOW_SPEC + ")"));

        ProbeLog.log(TAG, "magnitudes blur=" + blur + " texels ghost=" + ghost + " texels sanityPenalty=" + penalty
                + " => " + (blur == BLUR_TEXELS_SPEC && ghost == GHOST_TEXELS_SPEC && penalty == SANITY_PENALTY_SPEC
                        ? "OK" : "CHECK(expected " + BLUR_TEXELS_SPEC + "/" + GHOST_TEXELS_SPEC + "/"
                        + SANITY_PENALTY_SPEC + ")"));
    }

    /** The mod's tunables class, or {@code null} when it is not on this classpath. */
    private static Class<?> thoughtEffects()
    {
        try
        {
            return Class.forName("piloser.sanitypd.thought.ThoughtEffects");
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** A static float field, or {@code 0} when it cannot be read (the judgement then reports a CHECK). */
    private static float floatField(Class<?> clazz, String name)
    {
        if (clazz == null)
            return 0f;

        try
        {
            return ((Number) clazz.getField(name).get(null)).floatValue();
        }
        catch (Throwable t)
        {
            return 0f;
        }
    }

    /** A static int field, or {@code -1} when it cannot be read. */
    private static int field(Class<?> clazz, String name)
    {
        try
        {
            return ((Number) clazz.getField(name).get(null)).intValue();
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    private static Class<?> modClass()
    {
        if (!s_lookedUp)
        {
            s_lookedUp = true;

            try
            {
                s_clazz = Class.forName(MOD_CLASS);
            }
            catch (Throwable t)
            {
                ProbeLog.log(TAG, "CHECK(mod absent: " + MOD_CLASS + " is not on this classpath)");
            }
        }

        return s_clazz;
    }

    private static Float callNumber(Class<?> clazz, String name)
    {
        try
        {
            Method method = clazz.getMethod(name);
            Object value = method.invoke(null);
            return value instanceof Number number ? number.floatValue() : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static float number(Class<?> clazz, String name)
    {
        Float value = callNumber(clazz, name);

        if (value == null)
            bad("could not read " + name + "()");

        return value == null ? 0f : value;
    }

    private static Boolean callBoolean(Class<?> clazz, String name)
    {
        try
        {
            Object value = clazz.getMethod(name).invoke(null);
            return value instanceof Boolean flag ? flag : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String callString(Class<?> clazz, String name)
    {
        try
        {
            Object value = clazz.getMethod(name).invoke(null);
            return value == null ? "<null>" : value.toString();
        }
        catch (Throwable t)
        {
            return "<unreadable: " + t.getClass().getSimpleName() + ">";
        }
    }
}
