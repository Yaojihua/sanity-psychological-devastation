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
 * The hidden fragment of the player's own name, checked from the client (tag {@code SELF-59}).
 *
 * <h2>What it answers</h2>
 * <ul>
 *   <li><b>The two scripts as they really shipped</b> - read out of the installed sanitypd jar, not out of this
 *       probe: 42 lines each, the same number in both languages, quotes on every line, and the
 *       {@code {player}} placeholder on the same number of lines. The log names the route that answered, so a
 *       read that finds nothing again cannot look like a script that is empty.</li>
 *   <li><b>The tooltip</b> through the item's real tooltip path: the name carries the fragment's word, and the
 *       detail line carries its one-word detail.</li>
 *   <li><b>The allowance itself</b>, read out of the local player's saved data in single player - the one
 *       number a retest needs, because "handed over once, and an ordinary line afterwards" is entirely about
 *       that flag.</li>
 * </ul>
 *
 * <p>Mod members are reached by reflection (the probe has to load with sanitypd absent); vanilla members are
 * called directly, so the build-time remapper handles their names.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SelfFragmentProbe
{
    private static final String TAG = "SELF-59";
    private static final String ID = "lost_memory_fragment_self";

 /** The number of lines the text has. */
    private static final int LINES = 42;

    private static boolean s_reported;
    private static boolean s_reportedError;

    private SelfFragmentProbe() {}

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
        boolean name = FragmentProbeSupport.anyContains(spans, "\u6267\u5FF5", "Obsession");
        boolean detail = FragmentProbeSupport.anyContains(spans, "\u8FFD\u5BFB", "Seeking");

        ProbeLog.log(TAG, String.format(Locale.ROOT, "tooltip [%s] => %s",
                FragmentProbeSupport.join(spans), name && detail ? "OK" : "CHECK"));
    }

    /** The allowance, in single player only (a remote server keeps its own books). */
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
            String key = FragmentProbeSupport.triggerKey("HiddenSelfGrant");
            boolean spent = host.getPersistentData().getBoolean(key);

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "allowance: spent=%s available=%s (write nothing but your own name after the end poem) => OK",
                    spent, !spent));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "allowance: could not be read (" + t + ")");
        }
    }
}
