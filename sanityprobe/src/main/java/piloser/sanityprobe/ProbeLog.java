package piloser.sanityprobe;

import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The probe's only log output channel.
 *
 * <p>Writes {@code <gamedir>/logs/sanityprobe.log} in UTF-8, flushing every line, and mirrors the
 * same line into {@code latest.log} as {@code [PROBE] ...} so that a single latest.log is enough for
 * a full report.
 *
 * <p>All entry points swallow exceptions: the probe must never crash the game.
 */
public final class ProbeLog
{
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static BufferedWriter s_writer;
    private static boolean s_enabled = true;
    private static int s_errors;

    private ProbeLog() {}

    public static synchronized void init()
    {
        if (s_writer != null)
            return;

        try
        {
            Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(dir);
            Path file = dir.resolve("sanityprobe.log");
            s_writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            raw("=== sanityprobe log opened: " + file.toAbsolutePath() + " ===");
        }
        catch (Throwable t)
        {
            s_errors++;
            SanityProbe.LOGGER.warn("[PROBE] could not open sanityprobe.log: {}", t.toString());
        }
    }

    public static boolean isEnabled() { return s_enabled; }

    public static void setEnabled(boolean value)
    {
        s_enabled = value;
        log("SYS", "logging " + (value ? "ENABLED" : "DISABLED"));
    }

    /** Regular one-line entry. The tag groups related lines (SYS / DEATH / SYSMSG / WALK-S / WALK-C / MARK). */
    public static void log(String tag, String message)
    {
        if (!s_enabled)
            return;

        try
        {
            raw("[" + tag + "] " + message);
        }
        catch (Throwable t)
        {
            s_errors++;
        }
    }

    /** One line with a captured call stack - the probe's most important diagnostic tool. */
    public static void logStack(String tag, String message, int depth)
    {
        if (!s_enabled)
            return;

        log(tag, message);

        try
        {
            StringBuilder sb = new StringBuilder();
            int shown = 0;

            for (StackTraceElement e : Thread.currentThread().getStackTrace())
            {
                String cn = e.getClassName();

                if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")
                        || cn.startsWith("org.spongepowered.asm.")
                        || cn.startsWith(ProbeLog.class.getName())
                        || cn.startsWith(SanityProbe.class.getName()))
                    continue;

                sb.append("\n      at ").append(cn).append('.').append(e.getMethodName())
                  .append('(').append(e.getFileName()).append(':').append(e.getLineNumber()).append(')');

                if (++shown >= depth)
                    break;
            }

            raw("[" + tag + " STACK]" + sb);
        }
        catch (Throwable t)
        {
            s_errors++;
        }
    }

    public static int errorCount() { return s_errors; }

    // ------------------------------------------------------------ small helpers

    public static String fmt(double v)
    {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    public static String vec(Vec3 v)
    {
        if (v == null)
            return "null";

        return String.format(java.util.Locale.ROOT, "(%.3f,%.3f,%.3f)", v.x, v.y, v.z);
    }

    public static String safe(java.util.function.Supplier<String> supplier)
    {
        try
        {
            return String.valueOf(supplier.get());
        }
        catch (Throwable t)
        {
            return "<err:" + t.getClass().getSimpleName() + ">";
        }
    }

    private static void raw(String line) throws Exception
    {
        String text = LocalTime.now().format(TIME) + " " + line;

        if (s_writer != null)
        {
            s_writer.write(text);
            s_writer.newLine();
            s_writer.flush();
        }

        SanityProbe.LOGGER.info(text);
    }
}
