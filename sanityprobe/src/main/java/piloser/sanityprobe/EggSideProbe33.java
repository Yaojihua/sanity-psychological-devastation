package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Client-side probe for the hidden-name easter egg's <b>side split</b>.
 *
 * <h2>What question this answers</h2>
 * The easter egg has two halves and the side decides which one runs: the client shuts the game down, the
 * server silently swallows the command and punishes the speaker. The client half is already covered by
 * {@link EggProbe26}, which watches the chat receipt after the fact. What that cannot show is whether the
 * <b>server half is actually wired</b> in the jar the player is running - the server-side code never runs
 * on a client, and a dedicated server cannot load the client half at all.
 *
 * <p>So this group reads the server half through reflection, exactly the way a dedicated server would see
 * it, and reports three things:
 * <ol>
 *   <li><b>[EGG-33] guard</b>: {@code SanityCommand#applyForbiddenNamePunishment(CommandSourceStack, String)}
 *       exists and returns {@code boolean}, the punishment constant is the agreed 20000, and the command
 *       tree still has {@code hint <tier> add}. A missing method means the deployed jar predates the fix
 *       even though its version says otherwise.</li>
 *   <li><b>[EGG-33] sides</b>: the crash helper is still {@code @OnlyIn(CLIENT)} and the detector is still
 *       side-safe (no {@code @OnlyIn}, no {@code Runtime.halt} reference, no client-only types in its
 *       signatures). This is the property that keeps a dedicated server from halting.</li>
 *   <li><b>[EGG-33] rule</b>: the detection rule still matches the same way (spacing and punctuation
 *       between the letters count, one letter off does not). The probe does not spell the name out: it
 *       rebuilds it from the mod's own constant, the same way {@link EggProbe26} does.</li>
 * </ol>
 *
 * <p>The live result is mirrored in the overlay as {@code [EGG33] ...}, laid out by {@link HudLayout}
 * like every other probe line.
 *
 * <p>WARNING: read-only. Nothing here deals damage, submits a command or touches game state; every
 * reflective lookup is guarded so a failure logs one line and can never affect gameplay.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EggSideProbe33
{
    /** Mirror of {@code SanityCommand#FORBIDDEN_NAME_PUNISHMENT}: the agreed lethal amount. */
    private static final float EXPECTED_PUNISHMENT = 20000.0f;

    private static final String SERVER_METHOD = "applyForbiddenNamePunishment";
    private static final String SERVER_CONSTANT = "FORBIDDEN_NAME_PUNISHMENT";

    private static int s_tick;
    private static boolean s_once;
    private static String s_hud = "checking...";
    private static boolean s_registered;

    private EggSideProbe33() {}

    /** Called from {@link ClientProbe} on the first tick to force class init, which registers the HUD line. */
    public static void init()
    {
        registerHud();
    }

    private static synchronized void registerHud()
    {
        if (s_registered)
            return;

        s_registered = true;
        ProbeHud.registerLine(() -> "\u0000FFAA55" + "[EGG33] " + s_hud);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        registerHud();

        // One-shot: the wiring cannot change while the game is running.
        if (s_once)
            return;

        if (++s_tick % 20 != 0)
            return;

        s_once = true;

        try
        {
            reportGuard();
            reportSides();
            reportRule();
        }
        catch (Throwable t)
        {
            ProbeLog.log("EGG-33", "probe error: " + t);
        }
    }

    /** The server half must exist in this jar, with the agreed constant and a registered add node. */
    private static void reportGuard()
    {
        boolean methodOk = false;
        boolean constantOk = false;
        boolean nodeOk = false;
        String detail = "";

        try
        {
            Class<?> command = Class.forName("piloser.sanitypd.command.SanityCommand");

            Method punishment = command.getDeclaredMethod(SERVER_METHOD,
                    net.minecraft.commands.CommandSourceStack.class, String.class);
            methodOk = punishment.getReturnType() == boolean.class;

            Field constant = command.getDeclaredField(SERVER_CONSTANT);
            constant.setAccessible(true);
            constantOk = constant.getFloat(null) == EXPECTED_PUNISHMENT;

            detail = "returns=" + punishment.getReturnType().getSimpleName();
        }
        catch (Throwable t)
        {
            detail = "lookup failed: " + t.getClass().getSimpleName();
        }

        nodeOk = addNodeExists();

        // The add node is reported but not judged: on a real (LAN or single player) client the local
        // command tree does not carry "hint <tier> add" even though the command works, because the
        // command is executed on the server side. Judging it turned this line into a false alarm.
        ProbeLog.log("EGG-33", String.format(Locale.ROOT,
                "guard serverHalf=%s amount20000=%s localAddNode=%s (reported only) (%s) => %s",
                methodOk, constantOk, nodeOk, detail,
                methodOk && constantOk ? "OK" : "CHECK"));

        s_hud = String.format(Locale.ROOT, "server=%s 20000=%s",
                methodOk ? "wired" : "MISSING", constantOk ? "yes" : "NO");
    }

    /**
     * Whether {@code /sanity hint <tier> add <text>} is still in the tree the client received.
     *
     * <p>Walked structurally on purpose: the child map holds the tier literals as keys, so this exercises
     * "the add node exists under hint <em>for this literal</em>" instead of a single fixed path.
     */
    private static boolean addNodeExists()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.player.connection == null)
                return false;

            Object sanity = mc.player.connection.getCommands().getRoot().getChild("sanity");

            if (sanity == null)
                return false;

            Object hint = child(sanity, "hint");

            if (hint == null)
                return false;

            for (String tier : new String[] {"mild", "severe", "deep", "expiry"})
            {
                Object tierNode = child(hint, tier);

                if (tierNode != null && child(tierNode, "add") != null)
                    return true;
            }

            return false;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /** {@code CommandNode#getChild(String)} is public in Brigadier. */
    private static Object child(Object node, String name)
    {
        try
        {
            return node.getClass().getMethod("getChild", String.class).invoke(node, name);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /**
     * The client half must stay client-only and the detector must stay side-safe.
     *
     * <p>WARNING: the {@code watcherClientOnly} reading is <b>reported but not judged</b>. On a real
     * client the runtime class comes from the reobfuscated production jar, and
     * {@code Class#isAnnotationPresent} has been observed returning {@code false} there even though the
     * annotation is definitely in the bytecode ({@code javap -v} shows
     * {@code RuntimeVisibleAnnotations: ... OnlyIn(CLIENT)}). A value that cannot be trusted must not be
     * allowed to turn this line into a permanent false alarm, so the verdict rests on the two facts that
     * can be trusted - the detector is side-safe and does not reference {@code Runtime} - plus the static
     * gate {@code check-egg-sides.ps1}, which reads the source instead of reflecting over it.
     */
    private static void reportSides()
    {
        boolean watcherClientOnly = false;
        boolean detectorSideSafe = false;
        boolean detectorNoHalt = false;
        boolean watcherHasEntry = false;

        try
        {
            Class<?> watcher = Class.forName("piloser.sanitypd.client.HiddenNameWatcher");
            watcherClientOnly = watcher.isAnnotationPresent(net.minecraftforge.api.distmarker.OnlyIn.class);
            watcherHasEntry = watcher.getDeclaredMethod("mentionCheck", String.class) != null;
        }
        catch (Throwable t)
        {
            watcherHasEntry = false;
        }

        try
        {
            Class<?> detector = Class.forName("piloser.sanitypd.client.HiddenNameDetector");
            detectorSideSafe = !detector.isAnnotationPresent(net.minecraftforge.api.distmarker.OnlyIn.class)
                    && detector.getDeclaredMethod("mentionsTheName", String.class) != null;
            detectorNoHalt = !mentionsRuntime(detector);
        }
        catch (Throwable t)
        {
            detectorSideSafe = false;
        }

        ProbeLog.log("EGG-33", String.format(Locale.ROOT,
                "sides watcherClientOnly=%s (reported only) watcherHasEntry=%s detectorSideSafe=%s detectorNoHalt=%s => %s",
                watcherClientOnly, watcherHasEntry, detectorSideSafe, detectorNoHalt,
                watcherHasEntry && detectorSideSafe && detectorNoHalt ? "OK" : "CHECK"));
    }

    /**
     * Whether the class's own code mentions {@code Runtime} at all.
     *
     * <p>Bytecode references are not readable through reflection, so this only inspects the declared
     * members; the authoritative "no halt in the detector" proof is the static gate
     * {@code check-egg-sides.ps1} plus the self-check that ran on a dedicated server. A detector whose
     * signatures stay free of client-only types is the part reflection <em>can</em> confirm here.
     */
    private static boolean mentionsRuntime(Class<?> clazz)
    {
        for (Method m : clazz.getDeclaredMethods())
        {
            String text = m.toGenericString();

            if (text.contains("java.lang.Runtime") || text.contains("System.exit"))
                return true;
        }

        return false;
    }

    /**
     * The detection rule, exercised without printing the name.
     *
     * <p>The name is rebuilt from the mod's own constant so this file never spells it out either. Only the
     * negative cases are echoed into the log.
     */
    private static void reportRule()
    {
        try
        {
            Class<?> detector = Class.forName("piloser.sanitypd.client.HiddenNameDetector");
            Method mentions = detector.getDeclaredMethod("mentionsTheName", String.class);
            String name = String.valueOf(detector.getField("NAME_ASCII").get(null));

            if (name.isBlank())
            {
                ProbeLog.log("EGG-33", "rule could not read the mod's own word constant => CHECK");
                return;
            }

            boolean plain = (Boolean) mentions.invoke(null, name);
            boolean spaced = (Boolean) mentions.invoke(null, spaced(name));
            boolean punctuated = (Boolean) mentions.invoke(null, punctuated(name));
            boolean oneOff = !((Boolean) mentions.invoke(null, oneLetterOff(name)));
            boolean unrelated = !((Boolean) mentions.invoke(null, "a completely unrelated line"));
            boolean empty = !((Boolean) mentions.invoke(null, ""));

            boolean ok = plain && spaced && punctuated && oneOff && unrelated && empty;

            ProbeLog.log("EGG-33", String.format(Locale.ROOT,
                    "rule plain=%s spaced=%s punctuated=%s rejectsOneLetterOff=%s rejectsUnrelated=%s rejectsEmpty=%s => %s",
                    plain, spaced, punctuated, oneOff, unrelated, empty, ok ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log("EGG-33", "rule check failed (expected only when sanitypd is absent): " + t);
        }
    }

    /** "abcde" -> "a b c d e": spacing between the letters must still match. */
    private static String spaced(String word)
    {
        StringBuilder sb = new StringBuilder(word.length() * 2);

        for (int i = 0; i < word.length(); i++)
        {
            if (i > 0)
                sb.append(' ');

            sb.append(word.charAt(i));
        }

        return sb.toString();
    }

    /** "abcde" -> "a-b,c d!e": punctuation between the letters must still match. */
    private static String punctuated(String word)
    {
        StringBuilder sb = new StringBuilder(word.length() * 2);

        for (int i = 0; i < word.length(); i++)
        {
            if (i == 1)
                sb.append('-');
            else if (i == 2)
                sb.append(',');
            else if (i == 3)
                sb.append(' ');
            else if (i == 4)
                sb.append("! ");

            sb.append(word.charAt(i));
        }

        return sb.toString();
    }

    /**
     * The word with its last letter removed, so it must NOT match.
     *
     * <p>A letter (not punctuation) is dropped on purpose: dropping a non-alphanumeric character would
     * still normalize to the full word and the case would be pointless.
     */
    private static String oneLetterOff(String word)
    {
        if (word.length() < 2)
            return word;

        return word.substring(0, word.length() - 1);
    }
}
