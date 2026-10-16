package piloser.sanityprobe;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import piloser.sanityprobe.FragmentProbeSupport.Script;

/**
 * The hidden fragment of the inner void, checked from the client (tag {@code VOID-64}).
 *
 * <h2>What it answers</h2>
 * <ul>
 *   <li><b>The trigger words are the phrase the module already uses.</b> The word is a Chinese literal in the
 *       source, and this project has already shipped a wrong character written as a code point (the right
 *       character and a wrong one look the same in a console). The documented coupling is that the word is
 *       <b>the same phrase the thought transcription table's description uses</b>, so the word is checked
 *       against that table's own language values, one language each - a wrong character cannot satisfy it.</li>
 *   <li><b>The two scripts as the installed jar carries them</b>, read out of sanitypd and not out of this
 *       probe: <b>70 lines</b> each, quotes on every line, the {@code {player}} placeholder on the same 15
 * lines in both, and the dark-red marker on <b>one line only, the last one</b> - which is the
 *       "(the last sentence) that sentence is dark red" and the place the fake player says the name.</li>
 *   <li><b>The tooltip</b>, flattened span by span: the name carries the fragment's word and the detail line
 *       carries its one-word detail.</li>
 *   <li><b>The three allowances</b>, read out of the local player's saved data in single player: the inner
 *       void's flag next to the own-name and macaron ones, which is what "each trigger has its own, not shared,
 *       not inherited" means in practice.</li>
 * </ul>
 *
 * <p>Mod members are reached by reflection (the probe has to load with sanitypd absent); vanilla members are
 * called directly, so the build-time remapper handles their names.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VoidFragmentProbe
{
    private static final String TAG = "VOID-64";
    private static final String ID = "lost_memory_fragment_void";

 /** The number of lines the text has. */
    private static final int LINES = 70;

 /** How many of those lines carry the {@code {player}} placeholder (the text has 15). */
    private static final int PLAYER_LINES = 15;

    /** The line-level marker the screen renders in dark red. */
    private static final String DARK_RED = "[darkred]";

    /** The table whose description the trigger words are documented to be the same phrase as. */
    private static final String TABLE_TOOLTIP_KEY = "block.sanitypd.thought_transcription_table.tooltip";

    private static boolean s_reported;
    private static boolean s_reportedError;

    private VoidFragmentProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || s_reported)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
            return;

        s_reported = true;

        try
        {
            wordCheck();
            scriptChecks();
            tooltipChecks();
            allowanceCheck();
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

    /**
     * The two trigger words against the transcription table's own description, one language each.
     *
     * <p>A wrong character written as a code point is a legal character, so nothing about "does the fragment
     * fire" would look wrong - but the word would no longer be the phrase the table's description uses, and
     * that is exactly what this line reports.
     */
    private static void wordCheck()
    {
        try
        {
            Class<?> grant = Class.forName("piloser.sanitypd.item.HiddenWordGrant");
            String[] words = (String[]) grant.getField("VOID_WORDS").get(null);

            String zh = FragmentProbeSupport.resourceText("assets/sanitypd/lang/zh_cn.json");
            String en = FragmentProbeSupport.resourceText("assets/sanitypd/lang/en_us.json");

            boolean zhOk = words.length == 2 && usedIn(zh, words[0]);
            boolean enOk = words.length == 2 && usedIn(en, words[1]);

            // And the same words as the client really renders them, in whichever language is selected: that is
            // the text the player has to type, so at least one of the two has to be on that table's tooltip.
            Item table = BuiltInRegistries.ITEM.get(new ResourceLocation(FragmentProbeSupport.MODID,
                    "thought_transcription_table"));
            boolean onScreen = table != null && table != net.minecraft.world.item.Items.AIR
                    && FragmentProbeSupport.anyContains(FragmentProbeSupport.tooltipSpans(table), words);

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "trigger words: mod=%s | in the transcription table's own text: zh=%s en=%s | drawn on that table now=%s => %s",
                    String.join("/", words), zhOk, enOk, onScreen,
                    zhOk && enOk && onScreen ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "trigger words: could not be read (" + t + ")");
        }
    }

    /** Whether a language file uses this word inside the transcription table's description. */
    private static boolean usedIn(String languageFile, String word)
    {
        if (languageFile == null || word == null || word.isEmpty())
            return false;

        int key = languageFile.indexOf("\"" + TABLE_TOOLTIP_KEY + "\"");

        if (key < 0)
            return false;

        int at = languageFile.indexOf(word, key);

        // Inside the same value, not somewhere later in the file.
        return at > key && at - key < 400;
    }

    /** The two scripts as the installed jar carries them. */
    private static void scriptChecks()
    {
        Script zh = FragmentProbeSupport.script("assets/sanitypd/memory/" + ID + "_zh.txt");
        Script en = FragmentProbeSupport.script("assets/sanitypd/memory/" + ID + "_en.txt");

        long zhPlayer = FragmentProbeSupport.count(zh.lines(), (l) -> l.contains("{player}"));
        long enPlayer = FragmentProbeSupport.count(en.lines(), (l) -> l.contains("{player}"));
        boolean quoted = zh.lines().stream().allMatch(FragmentProbeSupport::quoted)
                && en.lines().stream().allMatch(FragmentProbeSupport::quoted);

        long zhRed = FragmentProbeSupport.count(zh.lines(), (l) -> l.contains(DARK_RED));
        long enRed = FragmentProbeSupport.count(en.lines(), (l) -> l.contains(DARK_RED));
        boolean lastRed = endsWithMarker(zh.lines()) && endsWithMarker(en.lines());
        boolean lastNames = endsWithPlayer(zh.lines()) && endsWithPlayer(en.lines());

        boolean ok = zh.lines().size() == LINES && en.lines().size() == LINES && quoted
                && zhPlayer == PLAYER_LINES && enPlayer == PLAYER_LINES
                && zhRed == 1 && enRed == 1 && lastRed && lastNames;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "scripts: zh=%d en=%d (expected %d) | quotes=%s | {player} zh=%d en=%d (expected %d) | "
                        + "darkred zh=%d en=%d, on the last line=%s and naming the player=%s | read via %s | last: %s => %s",
                zh.lines().size(), en.lines().size(), LINES, quoted, zhPlayer, enPlayer, PLAYER_LINES,
                zhRed, enRed, lastRed, lastNames,
                zh.route() + (zh.route().equals(en.route()) ? "" : " / " + en.route()),
                zh.lines().isEmpty() ? "<missing>" : zh.lines().get(zh.lines().size() - 1), ok ? "OK" : "CHECK"));
    }

    private static boolean endsWithMarker(List<String> lines)
    {
        return !lines.isEmpty() && lines.get(lines.size() - 1).contains(DARK_RED);
    }

    private static boolean endsWithPlayer(List<String> lines)
    {
        return !lines.isEmpty() && lines.get(lines.size() - 1).contains("{player}");
    }

    /** The item's tooltip, flattened span by span. */
    private static void tooltipChecks()
    {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(FragmentProbeSupport.MODID, ID));

        if (item == null || item == net.minecraft.world.item.Items.AIR)
        {
            ProbeLog.log(TAG, "tooltip: the item is not registered => CHECK");
            return;
        }

        List<String> spans = FragmentProbeSupport.tooltipSpans(item);

        // One check per language, so whichever one is on screen can be judged.
        boolean name = FragmentProbeSupport.anyContains(spans, "\u865A\u7A7A", "Void");
        boolean detail = FragmentProbeSupport.anyContains(spans, "\u5F52\u6765", "Return");

        ProbeLog.log(TAG, String.format(Locale.ROOT, "tooltip [%s] => %s",
                FragmentProbeSupport.join(spans), name && detail ? "OK" : "CHECK"));
    }

    /** All three word allowances, in single player only (a remote server keeps its own books). */
    private static void allowanceCheck()
    {
        ServerPlayer host = FragmentProbeSupport.localHost();

        if (host == null)
        {
            ProbeLog.log(TAG, "allowance: n/a (a remote server keeps its own books)");
            return;
        }

        try
        {
            String voidKey = FragmentProbeSupport.triggerKey("HiddenVoidGrant");
            String selfKey = FragmentProbeSupport.triggerKey("HiddenSelfGrant");
            String macaronKey = FragmentProbeSupport.triggerKey("HiddenMacaronGrant");

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "allowance: inner void spent=%s available=%s | own name spent=%s | macaron spent=%s => OK",
                    host.getPersistentData().getBoolean(voidKey), !host.getPersistentData().getBoolean(voidKey),
                    host.getPersistentData().getBoolean(selfKey),
                    host.getPersistentData().getBoolean(macaronKey)));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "allowance: could not be read (" + t + ")");
        }
    }
}
