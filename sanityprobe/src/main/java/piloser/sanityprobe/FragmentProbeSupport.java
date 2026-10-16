package piloser.sanityprobe;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The two things every "hidden fragment" check needs, in one place: reading another mod's script files, and
 * flattening an item's real tooltip.
 *
 * <h2>Why this class exists</h2>
 * Both of these were got wrong once, and both failed <b>silently</b> - the group still printed a line, it just
 * reported nothing and said {@code CHECK}:
 *
 * <ul>
 *   <li><b>Reading a script.</b> {@code getResourceAsStream} on a probe class cannot see sanitypd's files:
 *       each mod has its own class loader in a ModLauncher environment, so the lookup returned {@code null}
 *       while the mod itself was loading the very same file without trouble. The route that works is asking
 *       the mod's own file system, and this class records which route answered so the next failure explains
 *       itself.</li>
 *   <li><b>Flattening a tooltip.</b> Posting {@code ItemTooltipEvent} by hand reproduces what a mod that adds
 *       its lines <i>through that event</i> would do - which is how the thought items work, and why the same
 *       idiom looked proven. An item that adds its lines in {@code appendHoverText} (every memory fragment
 *       does) contributes nothing to a hand-posted event, so the list came back empty. The real path is
 *       {@link ItemStack#getTooltipLines}, which runs both.</li>
 * </ul>
 *
 * <p>Nothing here reads the player's world or changes anything; it is a reading instrument for the probe only.
 */
final class FragmentProbeSupport
{
    static final String MODID = "sanitypd";

    private FragmentProbeSupport() {}

    /** A script read: the non-blank lines, and which route answered (or that none did). */
    record Script(List<String> lines, String route)
    {
        boolean found()
        {
            return !lines.isEmpty();
        }
    }

    /**
     * One of the mod's script files, asked for by path inside the mod file.
     *
     * @param path e.g. {@code assets/sanitypd/memory/lost_memory_fragment_self_zh.txt}
     */
    static Script script(String path)
    {
        // Route 1: the mod's own file system. Works for a jar and for a folder, and is the documented way to
        // read a mod that is not ours.
        try
        {
            var info = net.minecraftforge.fml.ModList.get().getModFileById(MODID);

            if (info != null)
            {
                Path file = info.getFile().findResource(path);

                if (file != null && Files.exists(file))
                    return new Script(nonBlank(Files.readAllLines(file, StandardCharsets.UTF_8)), "mod file");
            }
        }
        catch (Throwable ignored)
        {
            // Fall through: the next route is reported if it answers.
        }

        // Route 2: the class loader of the class that loads these scripts inside the mod.
        try
        {
            Class<?> owner = Class.forName("piloser.sanitypd.client.MemoryScript");

            try (InputStream in = owner.getResourceAsStream("/" + path))
            {
                if (in != null)
                    return new Script(nonBlank(split(new String(in.readAllBytes(), StandardCharsets.UTF_8))), "mod class");
            }
        }
        catch (Throwable ignored)
        {
            // Fall through.
        }

        // Route 3: our own class loader. Recorded on purpose - this is the route that silently returns nothing,
        // so a future reader can see which one answered.
        try (InputStream in = FragmentProbeSupport.class.getResourceAsStream("/" + path))
        {
            if (in != null)
                return new Script(nonBlank(split(new String(in.readAllBytes(), StandardCharsets.UTF_8))), "probe class");
        }
        catch (Throwable ignored)
        {
            // Fall through to the report.
        }

        return new Script(List.of(), "not found (tried mod file, mod class, probe class)");
    }

    private static List<String> split(String text)
    {
        return List.of(text.split("\r?\n"));
    }

    /**
     * The raw text of one of the mod's files, or {@code null} when no route found it.
     *
     * <p>Only for text: {@link Files#readString} throws on bytes that are not valid UTF-8, which every PNG
     * is. Ask {@link #resourceExists} about binary files instead.
     */
    static String resourceText(String path)
    {
        try
        {
            var info = net.minecraftforge.fml.ModList.get().getModFileById(MODID);

            if (info != null)
            {
                Path file = info.getFile().findResource(path);

                if (file != null && Files.exists(file))
                    return Files.readString(file, StandardCharsets.UTF_8);
            }
        }
        catch (Throwable ignored)
        {
            // Fall through to the class-loader route.
        }

        try (InputStream in = FragmentProbeSupport.class.getResourceAsStream("/" + path))
        {
            if (in != null)
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Throwable ignored)
        {
            // Fall through to the report.
        }

        return null;
    }

    /**
     * Whether one of the mod's files exists at all.
     *
     * <p>Decoding is deliberately not attempted here: asking for a PNG as text throws, and a check that
     * throws looks exactly like a check that fails.
     */
    static boolean resourceExists(String path)
    {
        try
        {
            var info = net.minecraftforge.fml.ModList.get().getModFileById(MODID);

            if (info != null)
            {
                Path file = info.getFile().findResource(path);

                if (file != null && Files.exists(file))
                    return true;
            }
        }
        catch (Throwable ignored)
        {
            // Fall through to the class-loader route.
        }

        try (InputStream in = FragmentProbeSupport.class.getResourceAsStream("/" + path))
        {
            return in != null;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private static List<String> nonBlank(List<String> lines)
    {
        List<String> out = new ArrayList<>();

        for (String line : lines)
        {
            if (!line.isBlank())
                out.add(line);
        }

        return out;
    }

    /**
     * An item's real tooltip, flattened to one {@code colour:text} entry per screen span.
     *
     * @see ItemStack#getTooltipLines
     */
    static List<String> tooltipSpans(Item item)
    {
        List<String> spans = new ArrayList<>();

        if (item == null)
            return spans;

        for (Component line : new ItemStack(item).getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL))
        {
            line.visit((style, text) ->
            {
                spans.add(colourOf(style) + ":" + text);
                return Optional.empty();
            }, Style.EMPTY);
        }

        return spans;
    }

    private static String colourOf(Style style)
    {
        TextColor colour = style.getColor();

        return colour == null ? "-" : String.format(Locale.ROOT, "%06X", colour.getValue());
    }

    static String join(List<String> spans)
    {
        return String.join(" / ", spans);
    }

    /** Whether any flattened piece contains one of these texts. */
    static boolean anyContains(List<String> spans, String... needles)
    {
        for (String span : spans)
        {
            for (String needle : needles)
            {
                if (span.contains(needle))
                    return true;
            }
        }

        return false;
    }

    /**
     * The saved-data key of a word trigger, read off the shared decision's enum through a wrapper class.
     *
     * @param wrapperClass {@code HiddenSelfGrant} or {@code HiddenMacaronGrant}
     */
    static String triggerKey(String wrapperClass) throws Exception
    {
        Object trigger = Class.forName("piloser.sanitypd.item." + wrapperClass).getMethod("trigger").invoke(null);

        return (String) trigger.getClass().getMethod("key").invoke(trigger);
    }

    /** The local player's server-side self, in single player only. */
    static ServerPlayer localHost()
    {
        net.minecraft.server.MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();

        if (server == null || server.getPlayerList() == null)
            return null;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        return players.isEmpty() ? null : players.get(0);
    }

    /** Whether this script line is wrapped in the project's dialogue quotes (either glyph pair). */
    static boolean quoted(String line)
    {
        String s = line.replaceFirst("^\\[[a-z]+\\]", "");

        return (s.startsWith("\u201C") && s.endsWith("\u201D"))
                || (s.startsWith("\u300C") && s.endsWith("\u300D"))
                || (s.startsWith("\"") && s.endsWith("\""));
    }

    /** The project's redaction mark, which the screen renders as garbled text. */
    static boolean marked(String line)
    {
        return line.contains("\u25A0");
    }

    static long count(List<String> lines, java.util.function.Predicate<String> test)
    {
        return lines.stream().filter(test).count();
    }
}
