package piloser.sanitypd.client;

import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import piloser.sanitypd.SanityMod;

/**
 * Hidden easter egg: one particular name must not be spoken - the half that shuts the game down.
 *
 * <h2>What the player sees</h2>
 * Submitting a custom inner hint containing the hidden name through
 * {@code /sanity hint <tier> add <text>} closes the game on the spot: no crash screen, the window just
 * disappears. Afterwards {@code crash-reports/crash-<timestamp>-client.txt} contains a
 * {@code Description:} line reading the watcher message (the key is {@value #MESSAGE_KEY}, one entry per
 * language).
 *
 * <p>The name itself is not written anywhere in this source tree: see
 * {@link HiddenNameDetector#NAME_ZH}. It is the mod's only hidden content and must stay that way - do not
 * document it, do not spell it out, and do not add a second route to it.
 *
 * <h2>Split of responsibilities</h2>
 * Detecting the word lives in {@link HiddenNameDetector}, a side-safe pure function class, so the
 * detection logic is not locked to the client the way this class's {@code @OnlyIn(CLIENT)} is.
 * This class only performs the actual crash.
 *
 * <h2>How it shuts down</h2>
 * It builds a {@link CrashReport} itself, using the same translation for both the report
 * <b>title</b> and the exception <b>message</b> (see {@link WatcherException}), and always in this
 * order: {@code saveToFile(crash-reports/...)} -> {@code LOGGER.error(...)} ->
 * {@code Runtime.getRuntime().halt(-1)}.
 *
 * <p><b>Never use {@code Minecraft.crash(...)}</b>: it only calls {@code System.exit} and hangs the
 * window, as explained in {@link #crash()}. The exception carries no stack trace, keeping the report
 * short and the message line prominent.
 *
 * <h2>Why this must be client side</h2>
 * The inner hint pool is client-only (see {@link MentalHintManager}) and the command runs locally. On a
 * dedicated server {@code SanityCommand#runOnClient} returns at its dist guard and never reaches here,
 * which is also why detection and crashing are two separate classes.
 *
 * <p>This is <b>the only place in the mod that deliberately crashes the player's game</b>. Apart from
 * that single name it must never trigger.
 */
@OnlyIn(Dist.CLIENT)
public final class HiddenNameWatcher
{
    private HiddenNameWatcher() {}

    /**
     * Entry point: mentioning the name in the text shuts the game down immediately.
     *
     * <p>The only caller is {@code SanityCommand#addHint} - submitting a hint is the only route.
     * Normally this never returns; if the process somehow survives the crash, the exception propagates
     * so the caller can log it as an error rather than letting the easter egg fail silently.
     */
    public static void mentionCheck(String text)
    {
        if (HiddenNameDetector.mentionsTheName(text))
            throw crash();
    }

    /**
     * Crashes the game. The intent is that the game closes outright rather than showing a crash screen.
     *
     * <h3>Why {@code halt} is required and why the first version hung</h3>
     * The first version called {@code Minecraft.crash(report)}, which is: print the report, save it to
     * {@code crash-reports/}, {@code ServerLifecycleHooks.handleExit(-1)} and return. That handleExit
     * call is nothing but {@code System.exit(code)}.
     * <pre>
     * ⇒ System.exit starts the JVM shutdown sequence and **waits for every shutdown hook to finish**.
     *   The client threads (render thread, integrated server thread, resource and network teardown)
     *   and the main thread wait on each other, so the process wedges: the window neither exits nor
     *   responds. Worse, System.exit "returned" with the shutdown still stuck and the code then threw,
     *   disrupting a shutdown that was already in progress.
     * </pre>
     * <b>The fix</b> is {@code Runtime.getRuntime().halt(-1)}: it kills the JVM immediately, runs no
     * shutdown hooks and waits for no thread, which is exactly the "crash means the game closes"
     * semantics wanted here. The crash report is still written to {@code crash-reports/} first.
     *
     * <p>Do not change this back to {@link Minecraft#crash(CrashReport)}, nor to throwing and letting
     * the main loop handle it: the latter shows a crash screen and requires the player to click exit.
     *
     * @return only so call sites can write {@code throw crash()}; in practice this never returns
     */
    public static RuntimeException crash()
    {
        // (1) Write the report to disk first, so the crash is recorded even after the window is gone
        try
        {
            CrashReport report = new CrashReport(message(), new WatcherException());
            java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "crash-reports");
            java.io.File out = new java.io.File(dir, "crash-" + net.minecraft.Util.getFilenameFormattedDateTime() + "-client.txt");
            report.saveToFile(out);

            // Log only that one line and nothing else
            SanityMod.LOGGER.error(message());
        }
        catch (Throwable t)
        {
            logFallback(t);
        }

        // (2) Kill the JVM immediately: no shutdown hooks, no waiting on threads, so the window vanishes
        //     instead of hanging
        Runtime.getRuntime().halt(-1);

        // (3) halt is native and does not return; if it ever did, fall back to System.exit, then throw
        System.exit(-1);
        return new WatcherException();
    }

    /**
     * The message line, resolved through the language key so it follows the player's language
     * ({@code gui.sanitypd.egg.watcher}).
     *
     * <p>Both languages must define it: a missing entry prints the raw key instead of the text.
     *
     * <p>This is a private method rather than a {@code public static final String} constant because the
     * text now comes from the language file and is not a compile-time constant. A constant would force
     * it to be hard-coded, and the exception and the report both need the same single source so the two
     * can never drift.
     */
    private static String message()
    {
        try
        {
            return Component.translatable(MESSAGE_KEY).getString();
        }
        catch (Throwable t)
        {
            // Fall back to the hard-coded text if the language manager throws, so the easter egg never
            // fails just because a translation could not be resolved
            return FALLBACK_MESSAGE;
        }
    }

    /** Language key, defined for every supported language in the lang files. */
    public static final String MESSAGE_KEY = "gui." + SanityMod.MODID + ".egg.watcher";

    /** Fallback text used when the translation cannot be resolved; matches the zh_cn value. */
    private static final String FALLBACK_MESSAGE = "\u7942\u5728\u770B\u7740\u4F60...";

    /**
     * The exception must carry the message line, otherwise that line renders as
     * {@code ExceptionClass: null} and the Java stack head says nothing about what happened.
     *
     * <p>Note that what actually establishes the line in the report is the report <b>title</b>, not the
     * exception message: {@code getExceptionMessage()} only falls back to the title for
     * {@code NullPointerException}, {@code StackOverflowError} and {@code OutOfMemoryError}; every other
     * exception goes through Forge's {@code generateEnhancedStackTrace} and prints
     * {@code class name: message + stack trace}.
     *
     * <p>Writing no stack ({@code writableStackTrace = false}) keeps the report short and leaves no stack
     * trace of this mod behind, so the message line stays prominent.
     */
    public static final class WatcherException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private WatcherException()
        {
            super(message(), null, false, false);   // message line; no cause, no stack trace, no suppression
        }
    }

    /** Fallback logging: if the shutdown path goes wrong, at least record the real cause (message line plus reason). */
    public static void logFallback(Throwable t)
    {
        SanityMod.LOGGER.error("{} ({})", message(), t.toString());
    }
}
