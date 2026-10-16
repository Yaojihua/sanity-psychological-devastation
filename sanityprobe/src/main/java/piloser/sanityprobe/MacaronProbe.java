package piloser.sanityprobe;

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
 * The hidden fragment of the macaron's name, checked from the client (tag {@code MACARON-60}).
 *
 * <h2>What it answers</h2>
 * <ul>
 *   <li><b>The word really is the game's word.</b> The trigger is a Chinese literal in the source, and this
 *       project has already shipped a wrong character written as a code point. Here the mod's own word is
 *       compared against what the client actually renders for the macaron item's name: if the two ever drift
 *       apart, this line says so instead of the fragment quietly never firing (or firing on the wrong word).</li>
 *   <li><b>The two scripts as the installed jar carries them</b>, read out of sanitypd and not out of this
 *       probe: 41 lines each, quotes on every line, the redaction marks still present, and the
 *       {@code {player}} placeholder on the same number of lines.</li>
 *   <li><b>The tooltip</b>, flattened span by span: the name says Sweetness and the detail line says Love.</li>
 *   <li><b>The two allowances</b>, read out of the local player's saved data in single player: the macaron
 *       fragment and the own-name fragment each have their own flag, which is what "not shared, not inherited"
 *       means in practice.</li>
 * </ul>
 *
 * <p>Mod members are reached by reflection (the probe has to load with sanitypd absent); vanilla members are
 * called directly, so the build-time remapper handles their names.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MacaronProbe
{
    private static final String TAG = "MACARON-60";
    private static final String ID = "lost_memory_fragment_macaron";

 /** The number of lines the text has. */
    private static final int LINES = 41;

    private static boolean s_reported;
    private static boolean s_reportedError;

    private MacaronProbe() {}

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
     * The mod's trigger word against the name the client renders for a macaron.
     *
     * <p>This is the check that a wrong character cannot survive: the word is a literal, and the client's own
     * text is produced from the shipped language file, so anything but the real word shows up as a mismatch.
     */
    private static void wordCheck()
    {
        try
        {
            Class<?> grant = Class.forName("piloser.sanitypd.item.HiddenWordGrant");
            String[] words = (String[]) grant.getField("MACARON_WORDS").get(null);

            // The name the client really renders, in whichever language is selected: that is the text the
            // player would have to type, so that is what the word has to agree with.
            String name = Component.translatable("item.sanitypd.macaron_red").getString();
            boolean ok = false;

            for (String word : words)
            {
                if (!word.isEmpty() && name.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT)))
                    ok = true;
            }

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "trigger word: mod=%s | macaron item name (client, %s): \"%s\" => %s",
                    String.join("/", words), Minecraft.getInstance().getLanguageManager().getSelected(),
                    name, ok ? "OK" : "CHECK"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "trigger word: could not be read (" + t + ")");
        }
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
        boolean marked = zh.lines().stream().anyMatch(FragmentProbeSupport::marked);

        boolean ok = zh.lines().size() == LINES && en.lines().size() == LINES && quoted && marked
                && zhPlayer > 0 && zhPlayer == enPlayer;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "scripts: zh=%d en=%d (expected %d) | quotes=%s redaction=%s | {player} zh=%d en=%d | read via %s | first: %s => %s",
                zh.lines().size(), en.lines().size(), LINES, quoted, marked, zhPlayer, enPlayer,
                zh.route() + (zh.route().equals(en.route()) ? "" : " / " + en.route()),
                zh.lines().isEmpty() ? "<missing>" : zh.lines().get(0), ok ? "OK" : "CHECK"));
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
        boolean name = FragmentProbeSupport.anyContains(spans, "\u751C", "Sweetness");
        boolean detail = FragmentProbeSupport.anyContains(spans, "\u7231", "Love");

        ProbeLog.log(TAG, String.format(Locale.ROOT, "tooltip [%s] => %s",
                FragmentProbeSupport.join(spans), name && detail ? "OK" : "CHECK"));
    }

    /** Both allowances, in single player only (a remote server keeps its own books). */
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
            String macaronKey = FragmentProbeSupport.triggerKey("HiddenMacaronGrant");
            String selfKey = FragmentProbeSupport.triggerKey("HiddenSelfGrant");

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "allowance: macaron spent=%s available=%s | own name spent=%s available=%s => OK",
                    host.getPersistentData().getBoolean(macaronKey), !host.getPersistentData().getBoolean(macaronKey),
                    host.getPersistentData().getBoolean(selfKey), !host.getPersistentData().getBoolean(selfKey)));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "allowance: could not be read (" + t + ")");
        }
    }
}
