package piloser.sanityprobe;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The memory tape's gates and the hidden fragment's allowance, checked from the client (tag
 * {@code TAPE-GATE-53}).
 *
 * <h2>What it answers</h2>
 * "May this player watch the tapes at all" and "may they watch this one yet" are both decided on the client,
 * out of two things a dedicated server cannot see: vanilla's poem flag (which the mod now pushes down as a
 * packet) and the per-fragment watch markers (files next to the save). So the client probe is the only place
 * that can report them. Once per session it prints:
 *
 * <ul>
 *   <li>the poem flag the server last sent,</li>
 *   <li>which of the four fragments this client has watched through,</li>
 *   <li>the story order the mod derives (and therefore the fragment each one waits for),</li>
 *   <li>both refusal lines, rendered in the current language - a missing key would show up as the raw key
 *       string, which is exactly the failure this prints to catch.</li>
 * </ul>
 *
 * <h2>The mask pairing (sampled)</h2>
 * Whether the hidden fragment is handed over is decided on the <b>server</b>, out of a mask of watched
 * fragments that only the <b>client</b> can read off disk. Two copies of one fact, so they are compared here:
 * the local world's player data is read back (single player only - a remote server keeps its own books) and
 * the two masks must agree. The failure this exists to catch is the quiet one: the client watches all four, the
 * server is never told, the fragment is never handed over, and nothing anywhere says so. A few seconds of
 * grace are allowed for the answer to arrive, because it is sent as the world is loading.
 *
 * <p>Mod members are reached by reflection (the probe has to load with sanitypd absent); vanilla members are
 * called directly, so the build-time remapper handles their names.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TapeGateProbe
{
    private static final String TAG = "TAPE-GATE-53";
    private static final String MODID = "sanitypd";

    /** Class holding the allowance's two saved-data keys and the all-four constant. */
    private static final String GRANT_CLASS = "piloser.sanitypd.item.HiddenMemoryGrant";

    /** How often the two masks are compared, in client ticks (about every two seconds). */
    private static final int SAMPLE_TICKS = 40;

    /** Grace before "the client never told the server" counts as a failure, in client ticks (five seconds). */
    private static final int GRACE_TICKS = 100;

    private static boolean s_reported;
    private static boolean s_lastPoem;
    private static boolean s_reportedError;

    /** Client ticks spent in a world this session, used for the sampling cadence and the grace window. */
    private static int s_worldTicks;

    /** The last mask line printed, so the sampled line only appears when something actually changed. */
    private static String s_lastMaskLine;

    private TapeGateProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
            return;

        s_worldTicks++;

        try
        {
            reportGate();
        }
        catch (Throwable t)
        {
            reportFailureOnce(t);
        }

        // The masks are sampled rather than reported once: the client's answer is sent while the world is
        // loading, so the first samples may legitimately find the server without it yet.
        if (s_worldTicks % SAMPLE_TICKS == 0)
        {
            try
            {
                reportMaskState();
            }
            catch (Throwable t)
            {
                reportFailureOnce(t);
            }
        }
    }

    /**
     * The gate line: reported once, then again only when the poem flag changes - the one value that can flip
     * mid-session (the moment the credits finish).
     */
    private static void reportGate() throws Exception
    {
        boolean poem = poemSeen();

        if (s_reported && poem == s_lastPoem)
            return;

        boolean first = !s_reported;
        s_reported = true;
        s_lastPoem = poem;

        report(poem, first);
    }

    private static void reportFailureOnce(Throwable t)
    {
        if (s_reportedError)
            return;

        s_reportedError = true;
        ProbeLog.log(TAG, "the gate check could not run (expected when sanitypd is absent): " + t);
    }

    private static void report(boolean poem, boolean first) throws Exception
    {
        List<?> order = order();
        StringBuilder chain = new StringBuilder();
        StringBuilder watched = new StringBuilder();
        boolean orderOk = order.size() == 4;

        for (int i = 0; i < order.size(); i++)
        {
            String id = scriptKey(order.get(i));
            String previous = previous(id);

            chain.append(i == 0 ? "" : " -> ").append(shortName(id));
            watched.append(i == 0 ? "" : " ").append(shortName(id)).append('=')
                    .append(hasPlayed(id) ? "watched" : "no");

            // the declared chain has to agree with the list: the first waits for nobody, every other one waits
            // for the fragment before it
            if (i == 0 ? previous != null : !previous.equals(scriptKey(order.get(i - 1))))
                orderOk = false;
        }

        String needPoem = render("gui.sanitypd.memory.need_poem");
        String outOfOrder = render("gui.sanitypd.memory.out_of_order");
        boolean lines = resolved(needPoem, "gui.sanitypd.memory.need_poem")
                && resolved(outOfOrder, "gui.sanitypd.memory.out_of_order");

        boolean ok = orderOk && lines;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "%s poemSeen=%s | markers=%s | order: %s | %s | lines: \"%s\" / \"%s\" | VERDICT=%s",
                first ? "gate state" : "gate state changed,", poem, scope(), chain, watched, needPoem, outOfOrder,
                ok ? "PASS" : "CHECK"));
    }

    /** The four registered fragments, in the order the mod declares. */
    private static List<?> order() throws Exception
    {
        return (List<?>) Class.forName("piloser.sanitypd.item.ItemRegistry").getField("MEMORY_FRAGMENT_ORDER").get(null);
    }

    private static String scriptKey(Object holder) throws Exception
    {
        Object item = holder.getClass().getMethod("get").invoke(holder);

        return (String) item.getClass().getMethod("scriptKey").invoke(item);
    }

    private static String previous(String scriptKey) throws Exception
    {
        Class<?> registry = Class.forName("piloser.sanitypd.item.ItemRegistry");

        return (String) registry.getMethod("previousFragmentScriptKey", String.class).invoke(null, scriptKey);
    }

    private static boolean poemSeen() throws Exception
    {
        Class<?> gate = Class.forName("piloser.sanitypd.client.MemoryTapeGate");

        return (Boolean) gate.getMethod("poemSeen").invoke(null);
    }

    private static boolean hasPlayed(String scriptKey) throws Exception
    {
        Class<?> record = Class.forName("piloser.sanitypd.client.MemoryTapeRecord");

        return (Boolean) record.getMethod("hasPlayed", String.class).invoke(null, scriptKey);
    }

    /**
     * Where the watch markers live right now: "save" for a local world, "config" when there is no save folder.
     *
     * <p>Reported because getting this wrong is invisible in the numbers: markers that follow the client made a
     * brand new world start with everything already watched, which defeated both the story order and the
     * unskippable first viewing.
     */
    private static String scope() throws Exception
    {
        return (String) Class.forName("piloser.sanitypd.client.MemoryTapeRecord").getMethod("scope").invoke(null);
    }

    // ------------------------------------------------------------------ the mask pairing

    /**
     * Compares the mask the client holds against the one the server stored, and judges the pair.
     *
     * <p>One fact with two copies: the watch markers are client-side files, the server keeps the last mask it
     * was told. The hidden fragment is handed over only when the server's copy says all four, so a client that
     * watched all four without telling the server is a silent dead end - the fragment is never granted and
     * nothing is reported anywhere. That is what this line makes visible.
     */
    private static void reportMaskState() throws Exception
    {
        int client = watchedMask();
        int total = order().size();
        int all = (1 << total) - 1;
        boolean allowance = hiddenAllowance();

        ServerPlayer host = localHost();

        int server = -1;
        boolean serverKnown = false;
        boolean granted = false;

        if (host != null)
        {
            CompoundTag data = host.getPersistentData();

            serverKnown = data.contains(maskKey());
            server = data.getInt(maskKey()) & all;
            granted = data.getBoolean(spentKey());
        }

        // The mod's own all-four constant is one bit per story fragment; if a fifth fragment is ever added and
        // the constant is not moved with it, every save silently loses the allowance.
        int modAll = (Integer) constant("ALL");
        boolean constantOk = modAll == all;

        String verdict;

        if (host == null)
            verdict = "PASS (no local world to compare: a remote server keeps its own books)";
        else if (!serverKnown && client == all && s_worldTicks > GRACE_TICKS)
            verdict = "CHECK (the client watched all four and never told the server - the hidden fragment cannot be handed over)";
        else if (serverKnown && server != client)
            verdict = "CHECK (the two copies of the watched mask disagree)";
        else if (!serverKnown)
            verdict = "PASS (pending: the answer is still on its way)";
        else
            verdict = "PASS";

        if (!constantOk)
            verdict = "CHECK (the all-four constant no longer matches the number of story fragments)";

        String line = String.format(Locale.ROOT,
                "watched mask: client=0b%s (%d of %d) | server=%s granted=%s allowance=%s | all=%d (%d fragments) | %s",
                pad(client, total), Integer.bitCount(client), total,
                serverKnown ? "0b" + pad(server, total) : "not reported yet",
                host == null ? "n/a" : String.valueOf(granted),
                allowance ? "available" : "spent",
                modAll, total, verdict);

        if (line.equals(s_lastMaskLine))
            return;

        s_lastMaskLine = line;
        ProbeLog.log(TAG, line);
    }

    /** The player of the world this client is hosting, or null when the world is on a remote server. */
    private static ServerPlayer localHost()
    {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();

        if (server == null || server.getPlayerList() == null)
            return null;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        return players.isEmpty() ? null : players.get(0);
    }

    /** The mask the client holds, in bits. */
    private static int watchedMask() throws Exception
    {
        return (Integer) Class.forName("piloser.sanitypd.client.MemoryTapeRecord")
                .getMethod("watchedMask").invoke(null);
    }

    /** What the server last told this client about the allowance. */
    private static boolean hiddenAllowance() throws Exception
    {
        return (Boolean) Class.forName("piloser.sanitypd.client.MemoryTapeGate")
                .getMethod("hiddenAllowance").invoke(null);
    }

    /**
     * The mod's own constants, so the keys of the saved data are read from one place: the field's literal name
     * is the mod's own (the probe already looks up its registry fields the same way), never a vanilla name.
     */
    private static Object constant(String name) throws Exception
    {
        java.lang.reflect.Field field = Class.forName(GRANT_CLASS).getDeclaredField(name);
        field.setAccessible(true);

        return field.get(null);
    }

    private static String maskKey() throws Exception
    {
        return (String) constant("MASK");
    }

    private static String spentKey() throws Exception
    {
        return (String) constant("GRANTED");
    }

    /** A mask as a fixed-width bit string, so "0b0111" and "0b111" cannot be confused for one another. */
    private static String pad(int mask, int bits)
    {
        String raw = Integer.toBinaryString(mask & ((1 << bits) - 1));

        return "0".repeat(Math.max(0, bits - raw.length())) + raw;
    }

    /** Renders a language key in the current language; a missing key comes back as the key itself. */
    private static String render(String key)
    {
        return Component.translatable(key).getString();
    }

    private static boolean resolved(String rendered, String key)
    {
        return rendered != null && !rendered.isEmpty() && !rendered.equals(key);
    }

    /** The fragment's short name, so one line stays readable: lost_memory_fragment_end -> end. */
    private static String shortName(String scriptKey)
    {
        String stripped = scriptKey.replace("lost_memory_fragment", "");

        return stripped.startsWith("_") ? stripped.substring(1) : "doubt";
    }
}
