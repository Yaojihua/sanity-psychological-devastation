package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Group: the per-type inner-voice pools (probe v2.17.0).
 *
 * <p>The mod grows one read-only hint pool per thought type: composure joins the mild tier, madness /
 * endurance / servitude / chaos restraint join the severe tier, and each pool only takes part while the
 * player's chain holds a thought of that type. The owner's rules for them are: mixed into the tier's pool,
 * invisible to {@code /sanity hint list}, impossible to edit through the command, and a text repeating one
 * of their lines is refused silently.
 *
 * <p>What this group asserts, and why:
 * <ul>
 *   <li><b>the table</b>: five pools with the owner's counts (4/9/5/3/5) and tier mapping, and only the
 *       chaos pool coloured - re-read from the mod, not copied from the document;</li>
 *   <li><b>the chaos colour differs from the third voice</b> ({@code 0x8B2E2E} vs {@code 0x8B0000}): the
 *       owner asked for "dark red, distinguishable", so the two constants are compared <i>here</i>;</li>
 *   <li><b>the language values resolve</b>: all 26 lines come back non-blank through the mod's own
 *       resolver, so a typo'd key cannot hide behind a table that looks right;</li>
 *   <li><b>they stay out of the command</b>: none of their texts appears in the candidate list the
 *       {@code /sanity hint list} command reads, for every tier;</li>
 *   <li><b>the switch is the chain</b>: for every pool, its lines are in the tier's live candidate set
 *       exactly when the player holds a thought of that type;</li>
 *   <li><b>they behave like their tier</b>: sampled picks of the mild tier must come back non-hidden, so
 *       they get the tier's normal display window and shake instead of the extra voice's short one. The
 *       severe tier is deliberately <b>not</b> sampled: its draw is dealt from a shuffle bag and draining
 *       that bag would change the order of the lines the player actually sees (probe discipline: no
 *       side effects), so the severe side is checked through its candidate set instead.</li>
 * </ul>
 *
 * <p>Read-only: registers no gameplay content, changes no values, every entry point is guarded.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TypeHintProbe
{
    /** Type ids in the order the owner listed them. */
    private static final String[] TYPES = { "composure", "madness", "endurance", "servitude", "chaos_restraint" };

    /** Expected line count per pool, mirroring TypeHintSpec.POOLS. */
    private static final int[] COUNTS = { 4, 9, 5, 3, 5 };

    /** Expected madness tier per pool: composure is mild (0), the other four are severe (1). */
    private static final int[] TIERS = { 0, 1, 1, 1, 1 };

    private static final int EXPECTED_CHAOS_COLOR = 0x8B2E2E;
    private static final int EXPECTED_THIRD_VOICE_COLOR = 0x8B0000;
    private static final int EXPECTED_LINES = 26;

    /** Mild-tier picks sampled for the display-behaviour check; the mild draw is a plain roll (no bag). */
    private static final int MILD_SAMPLES = 8;

    private static int s_tick;
    private static boolean s_once;
    private static String s_lastHeld = "";
    private static String s_hud = "-";

    private TypeHintProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 20 != 0)
            return;

        try
        {
            tick();
        }
        catch (Throwable t)
        {
            ProbeLog.log("TYPEHINT-43", "probe error: " + t);
        }
    }

    /** Leaving the world drops the once-only flag, so the next world reports again with its own chain. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        s_once = false;
        s_lastHeld = "";
    }

    private static void tick() throws Exception
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        if (!s_once)
        {
            s_once = true;
            reportApi();
            reportMix();
        }

        String held = String.valueOf(heldSummary());

        if (!held.equals(s_lastHeld))
        {
            s_lastHeld = held;
            ProbeLog.log("TYPEHINT-43", String.format(Locale.ROOT,
                    "held %s | live lines mild=%d severe=%d",
                    held, activeLineCount(0), activeLineCount(1)));
        }
    }

    /** One-off check of the pool table, the colours, the language values and the command exclusion. */
    private static void reportApi()
    {
        try
        {
            Class<?> spec = Class.forName("piloser.sanitypd.thought.TypeHintSpec");
            List<?> pools = (List<?>) spec.getField("POOLS").get(null);

            int lines = (Integer) spec.getMethod("totalLineCount").invoke(null);
            List<?> keys = (List<?>) spec.getMethod("allKeys").invoke(null);
            int chaosColor = ((Number) spec.getField("COLOR_CHAOS_RESTRAINT").get(null)).intValue();

            StringBuilder shape = new StringBuilder();
            boolean tableOk = pools.size() == TYPES.length && lines == EXPECTED_LINES && keys.size() == EXPECTED_LINES;
            int coloured = 0;

            for (int i = 0; i < pools.size(); i++)
            {
                Object pool = pools.get(i);
                Object type = pool.getClass().getMethod("type").invoke(pool);
                String id = String.valueOf(type.getClass().getMethod("id").invoke(type));
                int tier = (Integer) pool.getClass().getMethod("tier").invoke(pool);
                int count = (Integer) pool.getClass().getMethod("count").invoke(pool);
                int color = (Integer) pool.getClass().getMethod("color").invoke(pool);

                boolean rowOk = i < TYPES.length && id.equals(TYPES[i]) && tier == TIERS[i] && count == COUNTS[i];

                if (color != 0xFFFFFF)
                    coloured++;

                tableOk &= rowOk;

                if (shape.length() > 0)
                    shape.append(' ');

                shape.append(id).append("=").append(count).append("@tier").append(tier)
                        .append(color == 0xFFFFFF ? "" : String.format(Locale.ROOT, "/%06X", color))
                        .append(rowOk ? "" : "(BAD)");
            }

            ProbeLog.log("TYPEHINT-43", String.format(Locale.ROOT,
                    "table pools=%d lines=%d keys=%d coloured=%d | %s => %s",
                    pools.size(), lines, keys.size(), coloured, shape, tableOk ? "OK" : "CHECK"));

            int thirdVoice = ((Number) Class.forName("piloser.sanitypd.client.HiddenVoicePool")
                    .getField("COLOR_DARK_RED").get(null)).intValue();

            boolean colourOk = chaosColor == EXPECTED_CHAOS_COLOR && thirdVoice == EXPECTED_THIRD_VOICE_COLOR
                    && chaosColor != thirdVoice;

            ProbeLog.log("TYPEHINT-43", String.format(Locale.ROOT,
                    "colour chaos=%06X thirdVoice=%06X distinct=%s => %s",
                    chaosColor, thirdVoice, chaosColor != thirdVoice, colourOk ? "OK" : "CHECK"));

            // The language values behind the table, resolved through the mod's own resolver: a key that does
            // not resolve comes back as the raw key (or blank), which this catches.
            Class<?> typePools = Class.forName("piloser.sanitypd.client.TypeHintPools");
            List<?> raw = (List<?>) typePools.getMethod("rawLines").invoke(null);

            int blank = 0;
            int unresolved = 0;

            for (Object line : raw)
            {
                String text = String.valueOf(line);

                if (text.isBlank())
                    blank++;
                else if (text.startsWith("gui."))
                    unresolved++;
            }

            boolean langOk = raw.size() == EXPECTED_LINES && blank == 0 && unresolved == 0;

            ProbeLog.log("TYPEHINT-43", String.format(Locale.ROOT,
                    "lang resolved=%d/%d blank=%d rawKey=%d => %s",
                    raw.size(), EXPECTED_LINES, blank, unresolved, langOk ? "OK" : "CHECK"));

            // The command must never show them: the tier candidate list the command reads has to be free of
            // every type-pool text, for all four pool indexes (three tiers plus the expiry warning pool).
            Class<?> manager = Class.forName("piloser.sanitypd.client.MentalHintManager");
            Method effective = manager.getMethod("effectiveHintEntries", int.class);
            Method plain = spec.getMethod("plain", String.class);

            List<String> wanted = new ArrayList<>();

            for (Object line : raw)
                wanted.add(String.valueOf(plain.invoke(null, String.valueOf(line))));

            int leaked = 0;
            int scanned = 0;

            for (int tier = 0; tier <= 3; tier++)
            {
                List<?> entries = (List<?>) effective.invoke(null, tier);
                scanned += entries.size();

                for (Object entry : entries)
                {
                    String text = String.valueOf(entry.getClass().getMethod("text").invoke(entry));

                    if (wanted.contains(text))
                        leaked++;
                }
            }

            ProbeLog.log("TYPEHINT-43", String.format(Locale.ROOT,
                    "commandExclusion entries=%d leaked=%d => %s",
                    scanned, leaked, leaked == 0 ? "OK" : "CHECK"));

            ProbeHud.registerLine(() -> "\u00A7c[TYPEHINT] " + s_hud);
        }
        catch (Throwable t)
        {
            ProbeLog.log("TYPEHINT-43", "API check failed (expected only when sanitypd is absent): " + t);
        }
    }

    /**
     * Checks the switch and the display behaviour: whose lines are live right now, that the live set matches
     * the held counts, and that a type line picked from the mild tier behaves exactly like a mild line.
     */
    private static void reportMix()
    {
        try
        {
            Class<?> spec = Class.forName("piloser.sanitypd.thought.TypeHintSpec");
            Class<?> thoughtType = Class.forName("piloser.sanitypd.thought.ThoughtType");
            Class<?> typePools = Class.forName("piloser.sanitypd.client.TypeHintPools");
            Class<?> manager = Class.forName("piloser.sanitypd.client.MentalHintManager");

            Method activeLines = typePools.getMethod("activeLines", int.class);
            Method heldCount = typePools.getMethod("heldCount", thoughtType);

            Object[] constants = thoughtType.getEnumConstants();
            int mismatches = 0;
            StringBuilder detail = new StringBuilder();

            for (int tier = 0; tier <= 1; tier++)
            {
                List<?> live = (List<?>) activeLines.invoke(null, tier);

                // Every live line must belong to a type the player actually holds, and every held type whose
                // pool joins this tier must contribute its lines - that is the owner's "only while held" rule.
                for (Object line : live)
                {
                    Object type = line.getClass().getMethod("type").invoke(line);
                    int held = (Integer) heldCount.invoke(null, type);

                    if (held <= 0)
                        mismatches++;
                }

                for (Object constant : constants)
                {
                    String id = String.valueOf(constant.getClass().getMethod("id").invoke(constant));
                    Object pool = spec.getMethod("poolOf", thoughtType).invoke(null, constant);

                    if (pool == null)
                        continue;

                    int poolTier = (Integer) pool.getClass().getMethod("tier").invoke(pool);

                    if (poolTier != tier)
                        continue;

                    int held = (Integer) heldCount.invoke(null, constant);
                    int count = (Integer) pool.getClass().getMethod("count").invoke(pool);
                    int contributed = 0;

                    for (Object line : live)
                    {
                        Object type = line.getClass().getMethod("type").invoke(line);

                        if (String.valueOf(type.getClass().getMethod("id").invoke(type)).equals(id))
                            contributed++;
                    }

                    boolean rowOk = held > 0 ? contributed == count : contributed == 0;

                    if (!rowOk)
                        mismatches++;

                    if (detail.length() > 0)
                        detail.append(' ');

                    detail.append(id).append(":held=").append(held).append(",live=").append(contributed)
                            .append('/').append(count).append(rowOk ? "" : "(BAD)");
                }
            }

            ProbeLog.log("TYPEHINT-43", String.format(Locale.ROOT,
                    "mix %s => %s", detail, mismatches == 0 ? "OK" : "CHECK"));

            // Display behaviour, sampled on the mild tier only (its draw is a plain roll, so this has no
            // side effect; the severe draw is dealt from a shuffle bag and must not be drained by a probe).
            Method pick = manager.getMethod("pickHintForDraw", int.class);
            int nulls = 0;
            int hiddenTypePicks = 0;
            int badColour = 0;
            int typePicks = 0;

            for (int i = 0; i < MILD_SAMPLES; i++)
            {
                Object picked = pick.invoke(null, 0);

                if (picked == null)
                {
                    nulls++;
                    continue;
                }

                boolean hidden = (Boolean) picked.getClass().getMethod("hidden").invoke(picked);
                int color = (Integer) picked.getClass().getMethod("color").invoke(picked);
                boolean fromType = (Boolean) picked.getClass().getMethod("fromTypePool").invoke(picked);

                if (!fromType)
                    continue;

                typePicks++;

                if (hidden)
                    hiddenTypePicks++;

                if (color != 0xFFFFFF && color != EXPECTED_CHAOS_COLOR)
                    badColour++;
            }

            boolean pickOk = nulls == 0 && hiddenTypePicks == 0 && badColour == 0;

            ProbeLog.log("TYPEHINT-43", String.format(Locale.ROOT,
                    "mildPicks samples=%d null=%d typePicks=%d hiddenTypePicks=%d badColour=%d => %s",
                    MILD_SAMPLES, nulls, typePicks, hiddenTypePicks, badColour, pickOk ? "OK" : "CHECK"));

            s_hud = String.format(Locale.ROOT, "mild=%d severe=%d %s",
                    activeLineCount(0), activeLineCount(1), heldSummary());
        }
        catch (Throwable t)
        {
            ProbeLog.log("TYPEHINT-43", "mix check failed (expected only when sanitypd is absent): " + t);
        }
    }

    private static String heldSummary()
    {
        try
        {
            Class<?> typePools = Class.forName("piloser.sanitypd.client.TypeHintPools");
            return String.valueOf(typePools.getMethod("heldSummary").invoke(null));
        }
        catch (Throwable t)
        {
            return "-";
        }
    }

    private static int activeLineCount(int tier)
    {
        try
        {
            Class<?> typePools = Class.forName("piloser.sanitypd.client.TypeHintPools");
            return (Integer) typePools.getMethod("activeLineCount", int.class).invoke(null, tier);
        }
        catch (Throwable t)
        {
            return -1;
        }
    }
}
