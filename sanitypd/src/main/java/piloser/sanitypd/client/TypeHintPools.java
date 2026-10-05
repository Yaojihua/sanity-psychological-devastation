package piloser.sanitypd.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import piloser.sanitypd.thought.IThoughtChain;
import piloser.sanitypd.thought.ThoughtChainProvider;
import piloser.sanitypd.thought.ThoughtType;
import piloser.sanitypd.thought.TypeHintSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Client half of the type hint pools: resolves their language values and reports which of them are live
 * for the local player right now.
 *
 * <p>A pool is live while the player's thought chain holds at least one thought of its type. The counts
 * arrive through {@code ThoughtChainPacket} and are applied to the local player's copy of the chain by
 * {@link ThoughtChainClientHandler}, so no extra packet is needed for this feature.
 *
 * <p>The pools are read-only by construction: they are not stored in
 * {@link MentalHintManager}'s editable lists, they never enter {@code effectiveHintEntries} (so
 * {@code /sanity hint list}, its counts and the preview cannot see them), and a player text that repeats
 * one of their lines is refused silently. The table itself lives in {@link TypeHintSpec}, which stays
 * loadable on a dedicated server so the rules can be asserted headlessly.
 */
@OnlyIn(Dist.CLIENT)
public final class TypeHintPools
{
    /** Built-in lines per pool, indexed exactly like {@link TypeHintSpec#POOLS}; built lazily. */
    private static MutableComponent[][] s_lines;

    private TypeHintPools() {}

    private static void build()
    {
        if (s_lines != null)
            return;

        List<TypeHintSpec.Pool> pools = TypeHintSpec.POOLS;
        s_lines = new MutableComponent[pools.size()][];

        for (int p = 0; p < pools.size(); p++)
        {
            TypeHintSpec.Pool pool = pools.get(p);
            MutableComponent[] lines = new MutableComponent[pool.count()];

            for (int i = 0; i < pool.count(); i++)
                lines[i] = Component.translatable(pool.key(i));

            s_lines[p] = lines;
        }
    }

    /** One drawable candidate of a type pool: resolved text, draw colour and the type it belongs to. */
    public record Line(String text, int color, ThoughtType type) {}

    /**
     * The type-pool lines that should join the given tier's draw right now.
     *
     * <p>Empty unless the tier is shared by at least one pool <b>and</b> the player currently holds a
     * thought of that pool's type. Called once per pick, not per frame.
     */
    public static List<Line> activeLines(int tier)
    {
        List<TypeHintSpec.Pool> pools = TypeHintSpec.poolsForTier(tier);

        if (pools.isEmpty())
            return List.of();

        build();

        List<Line> out = new ArrayList<>();

        for (int p = 0; p < TypeHintSpec.POOLS.size(); p++)
        {
            TypeHintSpec.Pool pool = TypeHintSpec.POOLS.get(p);

            if (pool.tier() != tier || heldCount(pool.type()) <= 0)
                continue;

            for (MutableComponent line : s_lines[p])
            {
                String text = MentalHintManager.resolveHintText(line);

                if (text != null && !text.isBlank())
                    out.add(new Line(text, pool.color(), pool.type()));
            }
        }

        return out;
    }

    /** How many type-pool lines are live for a tier right now; used by the probe and by log lines. */
    public static int activeLineCount(int tier)
    {
        return activeLines(tier).size();
    }

    /** How many thoughts of the given type the local player's chain holds; {@code 0} when unknown. */
    public static int heldCount(ThoughtType type)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc == null || mc.player == null)
            return 0;

        Player player = mc.player;
        IThoughtChain chain = player.getCapability(ThoughtChainProvider.CAP).orElse(null);

        return chain == null ? 0 : chain.countOf(type);
    }

    /** {@code type=count} pairs for every pooled type, for a log line; never throws. */
    public static String heldSummary()
    {
        StringBuilder sb = new StringBuilder();

        for (TypeHintSpec.Pool pool : TypeHintSpec.POOLS)
        {
            if (sb.length() > 0)
                sb.append(',');

            sb.append(pool.type().id()).append('=').append(heldCount(pool.type()));
        }

        return sb.toString();
    }

    /** Last value reported by {@link #heldSummaryIfChanged()}. */
    private static String s_lastHeld = "";

    /**
     * The held summary, but only when it differs from the last one reported; an empty string otherwise.
     *
     * <p>Exists because the type pools are switched by a synced number, so "the lines never showed up" has two
     * very different causes: the client never received the counts (all zeros here), or the counts are right
     * and the draw simply did not deal a type line. Without this line a real-machine session cannot tell
     * those apart - and the type pools are invisible to {@code /sanity hint list} by design, so the log is
     * the only place the answer can come from.
     */
    public static String heldSummaryIfChanged()
    {
        String now = heldSummary();

        if (now.equals(s_lastHeld))
            return "";

        s_lastHeld = now;
        return now;
    }

    /** Forgets the last reported summary, so the next change (or world join) reports it again. */
    public static void resetHeldLog()
    {
        s_lastHeld = "";
    }

    /**
     * Raw values of every type-pool line, with the obfuscation markers removed.
     *
     * <p>Read by the silent-refusal rule: the comparison must run against what a player could type, and
     * the chaos line's garbled run is a style, not text.
     */
    public static List<String> rawLines()
    {
        build();

        List<String> out = new ArrayList<>();

        for (MutableComponent[] pool : s_lines)
        {
            for (MutableComponent line : pool)
            {
                try
                {
                    out.add(TypeHintSpec.plain(line.getString()));
                }
                catch (Throwable ignored)
                {
                    // A line that cannot be resolved simply takes no part in the rule.
                }
            }
        }

        return out;
    }

    /** Whether a text the player wants to add repeats one of the type-pool lines (refused silently). */
    public static boolean matchesForbiddenText(String typed)
    {
        return TypeHintSpec.matchesForbiddenText(typed, rawLines(), playerName());
    }

    /** Account name, or an empty string when it is not available. */
    private static String playerName()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.getDisplayName().getString() : "";
    }
}
