package piloser.sanityprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import piloser.sanityprobe.FragmentProbeSupport.Script;

/**
 * The void lifting its limits on the player's understanding, checked from the client (tag {@code UNBOUND-66}).
 *
 * <h2>What it answers</h2>
 * <ul>
 *   <li><b>The two states of the scripts are really paired.</b> A replacement line is written under the line it
 *       replaces and marked with the mod's own marker. The probe parses all nine pairs itself (a second
 *       reading, not the mod's) and requires, per fragment: the same number of playable lines in both
 *       languages, the same number of replacements, the replacements on the same lines, and <b>every
 *       replacement really different from the line it replaces</b> - a replacement that changes nothing would
 *       pass a count-only check and still do nothing on screen.</li>
 *   <li><b>The marker the mod strips is the marker the files carry.</b> The literal is read off the mod's own
 *       constant and compared with the files, the same "the word and the text must be the same word" rule the
 *       word-trigger probes follow.</li>
 * <li><b>The condition is the condition.</b> "The eight fragments other than the vessel's" is
 *       computed twice: once from the mod's own constant, once from the fragment counts and the vessel's
 *       position, and the mod's own answer for this save is compared with both.</li>
 *   <li><b>The line the player is shown, and whether this save has been shown it.</b> The language key is
 *       resolved in the client's own language, both shipped files are searched for it, and the one-shot marker
 *       is looked for in the scope the watch markers use (a save folder, or the config fallback).</li>
 * </ul>
 *
 * <p>Mod members are reached by reflection (the probe has to load with sanitypd absent); vanilla members are
 * called directly, so the build-time remapper handles their names. Nothing here writes anything: the marker is
 * only ever looked for.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UnboundProbe
{
    private static final String TAG = "UNBOUND-66";
    private static final String RECORD_CLASS = "piloser.sanitypd.client.MemoryTapeRecord";
    private static final String GRANT_CLASS = "piloser.sanitypd.item.HiddenMemoryGrant";
    private static final String REGISTRY_CLASS = "piloser.sanitypd.item.ItemRegistry";
    private static final String SCRIPT_CLASS = "piloser.sanitypd.client.MemoryScript";
    private static final String UNBOUND_CLASS = "piloser.sanitypd.client.VoidUnbound";

    /** The one-shot marker of the announcement, as the mod names it. */
    private static final String MARKER_FILE = "sanitypd_void_unbound.json";

    /** The notice, exactly as the two language files carry it. */
    private static final String NOTICE_ZH = "\u4f60\u611f\u5230\u865a\u7a7a\u89e3\u9664\u4e86\u5bf9\u4f60\u7406\u89e3\u7684\u4e00\u4e9b\u9650\u5236......";
    private static final String NOTICE_EN = "You feel the void loosening some of the limits on your understanding......";
    private static final String NOTICE_KEY = "gui.sanitypd.memory.unbound";

    /** How long an announcement that is due may take to appear before it counts as missing. */
    private static final int GRACE_TICKS = 100;

    /** The nine fragments: item id, playable lines, and the lines carrying a replacement. */
    private static final String[][] FRAGMENTS = {
            {"lost_memory_fragment", "19", "2"},
            {"lost_memory_fragment_nether", "13", "2"},
            {"lost_memory_fragment_end", "20", "6"},
            {"lost_memory_fragment_finale", "31", "8"},
            {"lost_memory_fragment_hidden", "48", "3"},
            {"lost_memory_fragment_self", "42", "3"},
            {"lost_memory_fragment_macaron", "41", "6"},
            {"lost_memory_fragment_void", "70", "0"},
            {"lost_memory_fragment_container", "53", "0"},
    };

    private static boolean s_reported;
    private static boolean s_reportedError;
    private static int s_ticks;
    private static String s_lastConditionLine;

    private UnboundProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
            return;

        s_ticks++;

        if (s_reported)
        {
            // The condition is a pairing of masks, not a one-shot: it is re-printed whenever it changes.
            try
            {
                conditionCheck();
            }
            catch (Throwable ignored)
            {
                // The one-shot report below already said what could not be read.
            }

            return;
        }

        s_reported = true;

        try
        {
            markerCheck();
            scriptChecks();
            noticeCheck();
            conditionCheck();
        }
        catch (Throwable t)
        {
            if (!s_reportedError)
            {
                s_reportedError = true;
                ProbeLog.log(TAG, "the check could not run (expected when sanitypd is absent): " + t);
            }
        }
    }

    // ---------------------------------------------------------------- the marker

    /** The marker the mod strips, against the marker the nine pairs are written with. */
    private static void markerCheck()
    {
        String modMarker;

        try
        {
            modMarker = (String) Class.forName(SCRIPT_CLASS).getField("UNBOUND_MARKER").get(null);
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "marker: could not be read off the mod (" + t + ") => CHECK");
            return;
        }

        // Counted <b>per language</b>: the two files of a pair each carry their own replacements, so a total
        // over both would be exactly twice the expected number and would read as a failure on a good build
        // (which is what the first live session showed). Asking each language separately also catches the
        // case that matters - one side of a pair missing its markers.
        int zh = 0;
        int en = 0;

        for (String[] fragment : FRAGMENTS)
        {
            zh += marked(FragmentProbeSupport.script("assets/sanitypd/memory/" + fragment[0] + "_zh.txt"), modMarker);
            en += marked(FragmentProbeSupport.script("assets/sanitypd/memory/" + fragment[0] + "_en.txt"), modMarker);
        }

        boolean ok = "[unbound]".equals(modMarker)
                && zh == expectedReplacements() && en == expectedReplacements();

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "marker: mod=%s | files carry zh=%d en=%d marked line(s) (each must be %d) => %s",
                modMarker, zh, en, expectedReplacements(), ok ? "OK" : "CHECK"));
    }

    /** How many lines of one script carry the marker, at the start or after the dark-red marker. */
    private static long marked(Script script, String marker)
    {
        return FragmentProbeSupport.count(script.lines(),
                (l) -> l.startsWith(marker) || l.startsWith("[darkred]" + marker)
                        || l.startsWith(marker + "[darkred]"));
    }

    // ---------------------------------------------------------------- the nine pairs

    /** One fragment's shape, as the probe reads it: playable lines, and each line's replacement or null. */
    private record Shape(List<String> bodies, List<String> alternates, boolean broken)
    {
        long replacements()
        {
            return alternates.stream().filter((a) -> a != null).count();
        }
    }

    private static final class Counting
    {
        int pairs;
        int playablePairs;
        long replacements;
        String detail = "";
        boolean differs = true;
    }

    /**
     * All nine pairs, parsed by the probe itself: the same playable lines in both languages, the replacements
     * on the same lines, and every replacement really saying something else.
     */
    private static void scriptChecks()
    {
        String marker = "[unbound]";
        Counting counting = new Counting();
        List<String> replaced = new ArrayList<>();

        for (String[] fragment : FRAGMENTS)
        {
            Script zh = FragmentProbeSupport.script("assets/sanitypd/memory/" + fragment[0] + "_zh.txt");
            Script en = FragmentProbeSupport.script("assets/sanitypd/memory/" + fragment[0] + "_en.txt");

            Shape zhShape = shape(zh.lines(), marker);
            Shape enShape = shape(en.lines(), marker);

            int expectedLines = Integer.parseInt(fragment[1]);
            int expectedRepl = Integer.parseInt(fragment[2]);

            boolean paired = !zhShape.broken() && !enShape.broken()
                    && zhShape.bodies().size() == enShape.bodies().size()
                    && zhShape.bodies().size() == expectedLines
                    && zhShape.replacements() == enShape.replacements()
                    && zhShape.replacements() == expectedRepl
                    && positions(zhShape).equals(positions(enShape));

            if (paired)
                counting.playablePairs++;

            counting.pairs++;
            counting.replacements += zhShape.replacements();

            if (zhShape.replacements() != 0)
                replaced.add(String.format(Locale.ROOT, "%d:%d", expectedLines, zhShape.replacements()));

            // A replacement that is the same text as the line above it changes nothing on screen; comparing
            // the two states is the only way to see it.
            for (int i = 0; i < zhShape.alternates().size(); i++)
            {
                String alt = zhShape.alternates().get(i);
                String enAlt = i < enShape.alternates().size() ? enShape.alternates().get(i) : null;

                if (alt != null && alt.equals(zhShape.bodies().get(i)))
                    counting.differs = false;

                if (alt != null && enAlt != null && enAlt.equals(enShape.bodies().get(i)))
                    counting.differs = false;

                if (alt != null && enAlt == null || alt == null && enAlt != null)
                    counting.differs = false;
            }
        }

        boolean ok = counting.playablePairs == FRAGMENTS.length
                && counting.replacements == expectedReplacements()
                && counting.differs;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "scripts: %d/%d pairs paired (same playable lines, same replacement lines in both languages) "
                        + "| replacements total=%d (expected %d) | every replacement differs from its line=%s "
                        + "| with replacements: %s => %s",
                counting.playablePairs, counting.pairs, counting.replacements, expectedReplacements(),
                counting.differs, String.join(" ", replaced), ok ? "OK" : "CHECK"));
    }

    private static int expectedReplacements()
    {
        int total = 0;

        for (String[] fragment : FRAGMENTS)
            total += Integer.parseInt(fragment[2]);

        return total;
    }

    /** Where the replacement lines sit, as a string, so two languages can be compared position by position. */
    private static String positions(Shape shape)
    {
        StringBuilder out = new StringBuilder();

        for (String alternate : shape.alternates())
            out.append(alternate == null ? '0' : '1');

        return out.toString();
    }

    /**
     * The probe's own reading of one script: the playable lines, each line's replacement or {@code null}, and
     * whether the file breaks the marker's one rule (a replacement with no line above it, or two for one line).
     */
    private static Shape shape(List<String> raw, String marker)
    {
        List<String> bodies = new ArrayList<>();
        List<String> alternates = new ArrayList<>();
        boolean broken = false;

        for (String line : raw)
        {
            String body = line;
            boolean unbound = false;

            for (;;)
            {
                if (body.startsWith("[darkred]"))
                {
                    body = body.substring("[darkred]".length());
                    continue;
                }

                if (body.startsWith(marker))
                {
                    unbound = true;
                    body = body.substring(marker.length());
                    continue;
                }

                break;
            }

            if (!unbound)
            {
                bodies.add(body);
                alternates.add(null);
                continue;
            }

            if (bodies.isEmpty() || alternates.get(alternates.size() - 1) != null)
            {
                broken = true;
                continue;
            }

            alternates.set(alternates.size() - 1, body);
        }

        return new Shape(bodies, alternates, broken);
    }

    // ---------------------------------------------------------------- the notice

    /** The line the player is shown, and the one-shot marker that keeps it to one line per save. */
    private static void noticeCheck()
    {
        try
        {
            String resolved = Component.translatable(NOTICE_KEY).getString();
            boolean zhFile = String.valueOf(FragmentProbeSupport.resourceText("assets/sanitypd/lang/zh_cn.json"))
                    .contains(NOTICE_ZH);
            boolean enFile = String.valueOf(FragmentProbeSupport.resourceText("assets/sanitypd/lang/en_us.json"))
                    .contains(NOTICE_EN);
            boolean resolvedOk = NOTICE_ZH.equals(resolved) || NOTICE_EN.equals(resolved);

            boolean announced = announced();
            String scope = (String) Class.forName(RECORD_CLASS).getMethod("scope").invoke(null);

            boolean ok = zhFile && enFile && resolvedOk;

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "notice: key=%s | on screen now=%s | zh_cn=%s en_us=%s | announced=%s (scope=%s) => %s",
                    NOTICE_KEY, resolved, zhFile, enFile, announced, scope, ok ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "notice: could not be read (" + t + ") => CHECK");
        }
    }

    /** Whether the one-shot marker is present, read only. */
    private static boolean announced() throws Exception
    {
        Path dir = (Path) Class.forName(RECORD_CLASS).getMethod("markerFolder").invoke(null);

        return dir != null && Files.exists(dir.resolve(MARKER_FILE));
    }

    // ---------------------------------------------------------------- the condition

    /**
     * The condition, both ways: the mod's own answer, the constant it keeps, and the mask arithmetic derived
     * here from the fragment counts. A disagreement between any two of them is the failure this exists for.
     */
    private static void conditionCheck() throws Exception
    {
        Class<?> record = Class.forName(RECORD_CLASS);
        Class<?> grant = Class.forName(GRANT_CLASS);

        int story = (Integer) record.getMethod("watchedMask").invoke(null);
        int hidden = (Integer) record.getMethod("watchedHiddenMask").invoke(null);

        int all = (Integer) grant.getField("ALL").get(null);
        int allHidden = (Integer) grant.getField("ALL_HIDDEN").get(null);
        int exceptVessel = (Integer) grant.getField("ALL_HIDDEN_EXCEPT_VESSEL").get(null);

        int vesselBit = bitOf("lost_memory_fragment_container");
        int derived = allHidden & ~(1 << vesselBit);

        boolean modUnbound = (Boolean) Class.forName(UNBOUND_CLASS).getMethod("unbound").invoke(null);

        int storyNeeded = all;
        boolean derivedUnbound = (story & all) == all && exceptVessel != 0 && (hidden & exceptVessel) == exceptVessel;

        ServerPlayer host = FragmentProbeSupport.localHost();
        int serverStory = -1;
        boolean serverKnown = false;

        if (host != null)
        {
            serverKnown = host.getPersistentData().contains("sanitypd_memory_watched");
            serverStory = host.getPersistentData().getInt("sanitypd_memory_watched") & all;
        }

        String verdict;

        if (exceptVessel != derived)
            verdict = "CHECK (the mod's \"all but the vessel\" constant no longer equals the mask derived here)";
        else if (modUnbound != derivedUnbound)
            verdict = "CHECK (the mod's answer and the mask arithmetic disagree)";
        else if (modUnbound && !announced() && s_ticks > GRACE_TICKS)
            verdict = "CHECK (the condition holds and this save was never told - the line is due on join)";
        else if (host != null && serverKnown && serverStory != story)
            verdict = "CHECK (the client's story mask and the server's copy disagree)";
        else
            verdict = "PASS";

        String line = String.format(Locale.ROOT,
                "condition: story=0b%s (need 0b%s) | hidden=0b%s | server copy=%s | required=%d (0b%s, all %d hidden "
                        + "minus the vessel's bit %d) | unbound=%s | announced=%s | %s",
                pad(story, count("MEMORY_FRAGMENT_ORDER")), pad(storyNeeded, count("MEMORY_FRAGMENT_ORDER")),
                pad(hidden, count("HIDDEN_FRAGMENT_ORDER")),
                serverKnown ? "0b" + pad(serverStory, count("MEMORY_FRAGMENT_ORDER")) : "not reported yet",
                exceptVessel, pad(exceptVessel, count("HIDDEN_FRAGMENT_ORDER")), count("HIDDEN_FRAGMENT_ORDER"),
                vesselBit, modUnbound, announced(), verdict);

        if (line.equals(s_lastConditionLine))
            return;

        s_lastConditionLine = line;
        ProbeLog.log(TAG, line);
    }

    /** Which bit of the hidden mask one fragment answers for, read off the mod's own list. */
    private static int bitOf(String path) throws Exception
    {
        List<?> order = (List<?>) Class.forName(REGISTRY_CLASS).getField("HIDDEN_FRAGMENT_ORDER").get(null);

        for (int i = 0; i < order.size(); i++)
        {
            Object holder = order.get(i);
            Object item = holder.getClass().getMethod("get").invoke(holder);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey((Item) item);

            if (id != null && path.equals(id.getPath()))
                return i;
        }

        return -1;
    }

    /** How many entries one of the mod's own orders declares. */
    private static int count(String field) throws Exception
    {
        return ((List<?>) Class.forName(REGISTRY_CLASS).getField(field).get(null)).size();
    }

    /** A mask as a fixed-width bit string, so "0b0111" and "0b111" cannot be confused for one another. */
    private static String pad(int mask, int bits)
    {
        String raw = Integer.toBinaryString(mask & ((1 << bits) - 1));

        return "0".repeat(Math.max(0, bits - raw.length())) + raw;
    }
}
