package piloser.sanityprobe;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the low-sanity scream check (tag {@code SCREAM-52}).
 *
 * <h2>What this group is for</h2>
 * The scream is one face of the ambient sound pool that already plays at low sanity, and it is supposed to
 * exist only for a player carrying a chaos-restraint thought deep into madness. Three different things can
 * go wrong with that and only one of them is visible on a dedicated server:
 *
 * <ul>
 *   <li>the sound is not registered, or {@code sounds.json} has no key, or the ogg is missing - checked here
 *       once per session, so it fails even in a session where the scream never happens to be drawn;</li>
 *   <li>the gate is wrong - checked by calling the mod's own {@code screamAllowed} with made-up numbers and
 *       reading the boundary back, because no probe can wait for a real player to land exactly on 25%
 *       sanity with exactly one such thought equipped;</li>
 *   <li>the scream is heard when the gate was <b>not</b> open - checked live, on every play.</li>
 * </ul>
 *
 * <p>Everything mod-side is reached by reflection on purpose: the probe has to load and stay quiet when
 * sanitypd is absent. Only vanilla members are called directly, and those go through the build-time
 * remapper.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScreamProbe
{
    private static final String TAG = "SCREAM-52";
    private static final String MODID = "sanitypd";
    private static final String NAME = "screaming_woman";

    /** Sanity at or below 25%, the tier this axis uses for its hint lines. */
    private static final float EXPECTED_MADNESS = .75f;

    private static boolean s_staticReported;
    private static boolean s_reportedError;
    private static int s_plays;
    private static String s_hud = "-";

    private ScreamProbe() {}

    static
    {
        ProbeHud.registerLine(() -> "\u0000AA55FF" + "[Scream] " + s_hud);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || s_staticReported)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
            return;

        try
        {
            reportStatic(mc);
            s_staticReported = true;
        }
        catch (Throwable t)
        {
            reportOnce("the static check could not run", t);
            reportDark(t);
            s_staticReported = true;
        }
    }

    /**
     * Registration, resource and gate check, once per session.
     *
     * <p>These are the properties a server-side self-test cannot reach at all (resolution and the sound
     * registry both live on the client), so they are asserted here instead and the verdict is printed.
     */
    private static void reportStatic(Minecraft mc) throws Exception
    {
        ResourceLocation location = reflectionLocation();
        boolean resolveKey = mc.getSoundManager().getSoundEvent(location) != null;
        boolean file = mc.getResourceManager()
                .getResource(new ResourceLocation(MODID, "sounds/" + NAME + ".ogg")).isPresent();

        // the pool: the scream is not a member of it, it is added to the draw when the gate is open
        Field poolField = Class.forName("piloser.sanitypd.client.SoundPlayback").getDeclaredField("MISC_SOUNDS");
        poolField.setAccessible(true);
        int pool = Array.getLength(poolField.get(null));

        float madness = Class.forName("piloser.sanitypd.client.SoundPlayback").getField("SCREAM_MADNESS").getFloat(null);

        boolean below = allowed(1, EXPECTED_MADNESS - .01f);
        boolean at = allowed(1, EXPECTED_MADNESS);
        boolean noThought = allowed(0, 1f);
        boolean shallow = allowed(1, .5f);
        boolean gate = !below && at && !noThought && !shallow && Math.abs(madness - EXPECTED_MADNESS) < 1e-6f;

        boolean ok = location != null && resolveKey && file && gate && pool >= 13;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "scream=%s in sounds.json=%s ogg=%s | pool=%d entries | threshold=%.2f | allowed(no thought)=%s "
                        + "allowed(madness .50)=%s allowed(%.2f)=%s allowed(%.2f)=%s | plays=%d | VERDICT=%s",
                location, resolveKey, file, pool, madness, noThought, shallow,
                EXPECTED_MADNESS - .01f, below, EXPECTED_MADNESS, at, s_plays,
                ok ? "PASS" : "CHECK"));

        s_hud = NAME + " key=" + resolveKey + " ogg=" + file + " gate=" + gate + " plays=" + s_plays;
    }

    /**
     * Every play of the scream, with the numbers the gate looked at.
     *
     * <p>If the scream is heard while the gate was shut, that is the failure this group exists to catch, and
     * it is reported as such rather than as a play.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event)
    {
        try
        {
            if (event.getSound() == null || !MODID.equals(event.getSound().getLocation().getNamespace()))
                return;
            if (!NAME.equals(event.getSound().getLocation().getPath()))
                return;

            s_plays++;

            LocalPlayer player = Minecraft.getInstance().player;
            float madness = player == null ? -1f : madnessOf(player);
            int chaos = chaosThoughts();
            boolean open = allowed(chaos, madness);

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "screaming_woman play #%d at madness=%.3f with chaosRestraint=%d thought(s) => %s",
                    s_plays, madness, chaos, open ? "OK (the gate was open)" : "CHECK (the gate was SHUT - the scream should not have been offered)"));

            s_hud = "play #" + s_plays + " madness=" + ProbeLog.fmt(madness) + " chaos=" + chaos;
        }
        catch (Throwable t)
        {
            reportOnce("a play could not be checked", t);
        }
    }

    /** Calls the mod's own pure gate function. */
    private static boolean allowed(int chaosThoughts, float madness) throws Exception
    {
        Method m = Class.forName("piloser.sanitypd.client.SoundPlayback")
                .getMethod("screamAllowed", int.class, float.class);

        return (Boolean) m.invoke(null, chaosThoughts, madness);
    }

    /** How many chaos-restraint thoughts the local player carries, or -1 when the mod is absent. */
    private static int chaosThoughts() throws Exception
    {
        Class<?> typeClass = Class.forName("piloser.sanitypd.thought.ThoughtType");
        Object chaos = typeClass.getField("CHAOS_RESTRAINT").get(null);
        Class<?> pools = Class.forName("piloser.sanitypd.client.TypeHintPools");
        Method heldCount = pools.getMethod("heldCount", typeClass);

        return (Integer) heldCount.invoke(null, chaos);
    }

    /** Reads ISanity#getMadness() through the capability, or -1 when it is not available. */
    private static float madnessOf(LocalPlayer player) throws Exception
    {
        Class<?> providerClass = Class.forName("piloser.sanitypd.capability.SanityProvider");
        Object capToken = providerClass.getField("CAP").get(null);

        if (capToken == null)
            return -1f;

        Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
        Object cap = LocalPlayer.class.getMethod("getCapability", capabilityClass).invoke(player, capToken);

        if (cap == null)
            return -1f;

        Object orElse = cap.getClass().getMethod("orElse", Object.class).invoke(cap, (Object) null);

        return orElse == null ? -1f : (Float) orElse.getClass().getMethod("getMadness").invoke(orElse);
    }

    /** The registered location of the scream, or null when sanitypd is absent. */
    private static ResourceLocation reflectionLocation() throws Exception
    {
        Class<?> registry = Class.forName("piloser.sanitypd.sound.SoundRegistry");
        Object holder = registry.getField("SCREAMING_WOMAN").get(null);
        Object sound = holder.getClass().getMethod("get").invoke(holder);

        // Vanilla members are called through their type, never by a reflective method name: the production
        // runtime keeps their SRG names, so getMethod("getLocation") is a NoSuchMethodException waiting to
        // happen while a direct call is remapped at build time. This is the same rule as the rest of the probe.
        if (!(sound instanceof SoundEvent))
            throw new IllegalStateException("the scream is not a SoundEvent but "
                    + (sound == null ? "null" : sound.getClass().getName()));

        return ((SoundEvent) sound).getLocation();
    }

    /**
     * Printed when the static check cannot run, so a dark group leaves a line instead of silence.
     *
     * <p>The first version of this group reported its failure into the same once-per-session slot as every
     * other error and printed no verdict at all, so a session whose static check died looked exactly like a
     * session with nothing to report - which is how this method's own caller stayed broken for several
     * sessions without anyone noticing.
     */
    private static void reportDark(Throwable t)
    {
        ProbeLog.log(TAG, "static check DID NOT RUN: " + t + " => VERDICT=CHECK");
        s_hud = NAME + " static check failed";
    }

    /**
     * Reports a swallowed failure once per session.
     *
     * <p>A probe group that fails silently is indistinguishable from a group that had nothing to say, so this
     * one names its own failures instead of hiding behind a bare verdict.
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
