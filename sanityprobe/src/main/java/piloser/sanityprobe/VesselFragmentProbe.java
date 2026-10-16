package piloser.sanityprobe;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import piloser.sanityprobe.FragmentProbeSupport.Script;

/**
 * The hidden fragment of the vessel, checked from the client (tag {@code VESSEL-65}).
 *
 * <h2>What it answers</h2>
 * <ul>
 *   <li><b>The trigger words are words the story already uses.</b> The word is a Chinese literal in the source,
 *       and this project has already shipped a wrong character written as a code point (the wrong character is
 *       perfectly legal, so nothing about "does the fragment fire" looks wrong). The check is therefore that
 * the word really is the one the scripts carry: the Chinese word ("容器") and "vessel" in this
 *       fragment's own two files and in the story fragment that names the vessel first.</li>
 *   <li><b>The two scripts as the installed jar carries them</b>: {@link #LINES} lines each, quotes on every line,
 *       the {@code {player}} placeholder on {@link #PLAYER_LINES} lines in both ({@link #PLAYER_OCCURRENCES}
 *       times in total), a redaction run on exactly one line, and <b>no</b> dark-red marker - this fragment has
 * none (the design requires one on the inner void's, not on this one).</li>
 *   <li><b>The tooltip</b>, flattened span by span: the name carries the fragment's word and the detail line
 *       its one-word detail.</li>
 *   <li><b>The hidden-fragment mask, both copies.</b> This fragment's prerequisite is "the inner void's
 *       fragment was watched", which the server can only know if the client reports it - so the client's mask
 *       and the server's copy of it are printed next to each other, with the void bit's position spelled out.
 *       A save whose client watched the void fragment but never reported it is a silent dead end: the vessel
 *       fragment simply never arrives.</li>
 *   <li><b>The four allowances</b>, read out of the local player's saved data in single player: one flag per
 *       word trigger, which is what "each trigger has its own, not shared, not inherited" means in practice.</li>
 * </ul>
 *
 * <p>Mod members are reached by reflection (the probe has to load with sanitypd absent); vanilla members are
 * called directly, so the build-time remapper handles their names.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VesselFragmentProbe
{
    private static final String TAG = "VESSEL-65";
    private static final String ID = "lost_memory_fragment_container";
    private static final String GRANT_CLASS = "piloser.sanitypd.item.HiddenMemoryGrant";
    private static final String REGISTRY_CLASS = "piloser.sanitypd.item.ItemRegistry";

 /** The number of lines the text has. */
    private static final int LINES = 53;

    /** The lines carrying the {@code {player}} placeholder, and how many times it appears in total. */
    private static final int PLAYER_LINES = 19;
    private static final int PLAYER_OCCURRENCES = 21;

    /** How long the "the client knows but the server was never told" answer is allowed to be on its way. */
    private static final int GRACE_TICKS = 100;

    private static boolean s_reported;
    private static boolean s_reportedError;
    private static int s_ticks;
    private static String s_lastMaskLine;

    private VesselFragmentProbe() {}

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
            // The mask line is a pairing, not a one-shot: it is re-printed whenever it changes.
            try
            {
                maskCheck();
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
            wordCheck();
            scriptChecks();
            tooltipChecks();
            allowanceCheck();
            maskCheck();
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
     * The two trigger words against the text the mod really ships.
     *
     * <p>A wrong code point cannot pass this: the word is compared with the script files that were written by
 * hand from the own text.
     */
    private static void wordCheck()
    {
        try
        {
            Class<?> grant = Class.forName("piloser.sanitypd.item.HiddenWordGrant");
            String[] words = (String[]) grant.getField("CONTAINER_WORDS").get(null);

            Script ownZh = FragmentProbeSupport.script("assets/sanitypd/memory/" + ID + "_zh.txt");
            Script ownEn = FragmentProbeSupport.script("assets/sanitypd/memory/" + ID + "_en.txt");

            // The fragment of Bewilderment is where the story already calls the player a vessel.
            Script storyZh = FragmentProbeSupport.script("assets/sanitypd/memory/lost_memory_fragment_end_zh.txt");
            Script storyEn = FragmentProbeSupport.script("assets/sanitypd/memory/lost_memory_fragment_end_en.txt");

            boolean ok = words.length == 2
                    && contains(ownZh, words[0]) && contains(ownEn, words[1])
                    && contains(storyZh, words[0]) && contains(storyEn, words[1]);

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "trigger words: mod=%s | own script zh=%s en=%s | the story's own text zh=%s en=%s => %s",
                    String.join("/", words), contains(ownZh, words[0]), contains(ownEn, words[1]),
                    contains(storyZh, words[0]), contains(storyEn, words[1]), ok ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "trigger words: could not be read (" + t + ")");
        }
    }

    private static boolean contains(Script script, String word)
    {
        return word != null && !word.isEmpty() && script.lines().stream().anyMatch((l) -> l.contains(word));
    }

    /** The two scripts as the installed jar carries them. */
    private static void scriptChecks()
    {
        Script zh = FragmentProbeSupport.script("assets/sanitypd/memory/" + ID + "_zh.txt");
        Script en = FragmentProbeSupport.script("assets/sanitypd/memory/" + ID + "_en.txt");

        long zhPlayer = FragmentProbeSupport.count(zh.lines(), (l) -> l.contains("{player}"));
        long enPlayer = FragmentProbeSupport.count(en.lines(), (l) -> l.contains("{player}"));
        long zhCount = zh.lines().stream().mapToLong((l) -> occurrences(l, "{player}")).sum();
        long enCount = en.lines().stream().mapToLong((l) -> occurrences(l, "{player}")).sum();

        boolean quoted = zh.lines().stream().allMatch(FragmentProbeSupport::quoted)
                && en.lines().stream().allMatch(FragmentProbeSupport::quoted);

        long zhMarked = FragmentProbeSupport.count(zh.lines(), FragmentProbeSupport::marked);
        long enMarked = FragmentProbeSupport.count(en.lines(), FragmentProbeSupport::marked);
        long zhBlocks = zh.lines().stream().mapToLong((l) -> occurrences(l, "\u25a0")).sum();
        long enBlocks = en.lines().stream().mapToLong((l) -> occurrences(l, "\u25a0")).sum();

        boolean noDarkRed = zh.lines().stream().noneMatch((l) -> l.contains("[darkred]"))
                && en.lines().stream().noneMatch((l) -> l.contains("[darkred]"));

        boolean ok = zh.lines().size() == LINES && en.lines().size() == LINES && quoted
                && zhPlayer == PLAYER_LINES && enPlayer == PLAYER_LINES
                && zhCount == PLAYER_OCCURRENCES && enCount == PLAYER_OCCURRENCES
                && zhMarked == 1 && enMarked == 1 && zhBlocks == 5 && enBlocks == 5 && noDarkRed;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "scripts: zh=%d en=%d (expected %d) | quotes=%s | {player} zh=%d/%d en=%d/%d (expected %d lines / %d) "
                        + "| redaction zh=%d/%d en=%d/%d (one line each) | darkred=%s | read via %s | last: %s => %s",
                zh.lines().size(), en.lines().size(), LINES, quoted,
                zhPlayer, zhCount, enPlayer, enCount, PLAYER_LINES, PLAYER_OCCURRENCES,
                zhMarked, zhBlocks, enMarked, enBlocks, noDarkRed ? "none (correct)" : "PRESENT",
                zh.route() + (zh.route().equals(en.route()) ? "" : " / " + en.route()),
                zh.lines().isEmpty() ? "<missing>" : zh.lines().get(zh.lines().size() - 1),
                ok ? "OK" : "CHECK"));
    }

    private static long occurrences(String text, String needle)
    {
        long n = 0;
        int at = text.indexOf(needle);

        while (at >= 0)
        {
            n++;
            at = text.indexOf(needle, at + needle.length());
        }

        return n;
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
        boolean name = FragmentProbeSupport.anyContains(spans, "\u5bb9\u5668", "Vessel");
        boolean detail = FragmentProbeSupport.anyContains(spans, "\u5b58\u5728", "Existence");

        ProbeLog.log(TAG, String.format(Locale.ROOT, "tooltip [%s] => %s",
                FragmentProbeSupport.join(spans), name && detail ? "OK" : "CHECK"));
    }

    /** The four word-trigger allowances, in single player only (a remote server keeps its own books). */
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
            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "allowance: vessel spent=%s | inner void spent=%s | own name spent=%s | macaron spent=%s => OK",
                    spent(host, "HiddenContainerGrant"), spent(host, "HiddenVoidGrant"),
                    spent(host, "HiddenSelfGrant"), spent(host, "HiddenMacaronGrant")));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "allowance: could not be read (" + t + ")");
        }
    }

    private static boolean spent(ServerPlayer host, String wrapperClass) throws Exception
    {
        return host.getPersistentData().getBoolean(FragmentProbeSupport.triggerKey(wrapperClass));
    }

    /**
     * The hidden-fragment mask, both copies, plus the position of the void's bit.
     *
     * <p>The one failure this is built to make visible: the client watched the inner void's fragment, the
     * server was never told, and the vessel fragment can therefore never be handed over - with nothing
     * anywhere else saying so. The bit's position is printed because the mask means nothing without it.
     */
    private static void maskCheck() throws Exception
    {
        int client = watchedHiddenMask();
        int total = hiddenFragmentCount();
        int all = (1 << total) - 1;
        int voidBit = voidBitIndex();
        int modAll = (Integer) constant("ALL_HIDDEN");

        ServerPlayer host = FragmentProbeSupport.localHost();

        int server = -1;
        boolean serverKnown = false;

        if (host != null)
        {
            CompoundTag data = host.getPersistentData();
            String key = (String) constant("HIDDEN_MASK");

            serverKnown = data.contains(key);
            server = data.getInt(key) & all;
        }

        String verdict;

        if (host == null)
            verdict = "PASS (no local world to compare: a remote server keeps its own books)";
        else if (modAll != all)
            verdict = "CHECK (the all-hidden constant no longer matches the number of hidden fragments)";
        else if (!serverKnown && client != 0 && s_ticks > GRACE_TICKS)
            verdict = "CHECK (the client has hidden fragments watched and never told the server - the vessel fragment cannot be handed over)";
        else if (serverKnown && server != client)
            verdict = "CHECK (the two copies of the hidden mask disagree)";
        else if (!serverKnown)
            verdict = "PASS (pending: the answer is still on its way)";
        else
            verdict = "PASS";

        String line = String.format(Locale.ROOT,
                "hidden mask: client=0b%s (%d of %d) | server=%s | all=%d (%d fragments) | void bit=%d | %s",
                pad(client, total), Integer.bitCount(client), total,
                serverKnown ? "0b" + pad(server, total) : "not reported yet",
                modAll, total, voidBit, verdict);

        if (line.equals(s_lastMaskLine))
            return;

        s_lastMaskLine = line;
        ProbeLog.log(TAG, line);
    }

    private static int watchedHiddenMask() throws Exception
    {
        return (Integer) Class.forName("piloser.sanitypd.client.MemoryTapeRecord")
                .getMethod("watchedHiddenMask").invoke(null);
    }

    /** How many hidden fragments the mod declares, read off the mod's own list. */
    private static int hiddenFragmentCount() throws Exception
    {
        List<?> order = (List<?>) Class.forName(REGISTRY_CLASS).getField("HIDDEN_FRAGMENT_ORDER").get(null);

        return order.size();
    }

    /** Which bit of the hidden mask the inner void's fragment answers for, read off the mod's own list. */
    private static int voidBitIndex() throws Exception
    {
        Class<?> registry = Class.forName(REGISTRY_CLASS);
        List<?> order = (List<?>) registry.getField("HIDDEN_FRAGMENT_ORDER").get(null);

        // The fragment's own declared name, so the probe does not depend on the field it happens to sit in.
        String wanted = "lost_memory_fragment_void";

        for (int i = 0; i < order.size(); i++)
        {
            Object holder = order.get(i);
            Object item = holder.getClass().getMethod("get").invoke(holder);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey((Item) item);

            if (id != null && wanted.equals(id.getPath()))
                return i;
        }

        return -1;
    }

    /** The mod's own constant, read from one place instead of being copied into the probe. */
    private static Object constant(String name) throws Exception
    {
        java.lang.reflect.Field field = Class.forName(GRANT_CLASS).getDeclaredField(name);
        field.setAccessible(true);

        return field.get(null);
    }

    /** A mask as a fixed-width bit string, so "0b0111" and "0b111" cannot be confused for one another. */
    private static String pad(int mask, int bits)
    {
        String raw = Integer.toBinaryString(mask & ((1 << bits) - 1));

        return "0".repeat(Math.max(0, bits - raw.length())) + raw;
    }
}
