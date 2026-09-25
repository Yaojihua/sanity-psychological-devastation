package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Group W: the "do not say that name" easter egg.
 *
 * <p>After {@code /sanity hint <tier> add <text>} the local chat receipt reaches the client
 * first. The probe watches it: text containing {@code cacomorth} or the Chinese spelling of the
 * name (same rule as the mod — strip non-alphanumerics, then substring match) must make the egg
 * fire at once and the game crash. A {@code HIT … EXPECT=CRASH} line followed by a still-running
 * game means the egg failed; the same text written twice means the mod failed to crash while
 * saving, before any receipt; {@code MISS} means the name was absent from the submitted text.
 *
 * <p>The check is duplicated here on purpose: the mod's watcher class is {@code @OnlyIn(CLIENT)}
 * with no dual-side entry point, and the probe must not depend on sanitypd at compile time. Raw
 * and normalized text are both logged, so drift between the two rules stays visible.
 *
 * <p>Read-only: reads chat, writes the log, never touches game state.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EggProbe26
{
    /** Prefixes of the main mod's success receipts (one per language, see sanitypd lang). */
    private static final String[] ADD_SUCCESS_MARKERS =
    {
        "自定义标语",          // zh_cn success receipt
        "custom hint",          // en_us fallback
    };

    private static int s_hits;

    static
    {
        ProbeHud.registerLine(() -> s_hits > 0
                ? "EGG-26: " + s_hits + " hit(s) - EXPECT=CRASH (if it did not crash, report that log line)"
                : null);
    }

    private EggProbe26() {}

    /** Called from {@link ClientProbe} on the first tick to force class init, which registers the HUD line. */
    public static void init()
    {
        // Intentionally empty.
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event)
    {
        try
        {
            String text = event.getMessage().getString();

            if (!looksLikeAddSuccess(text))
                return;

            String normalized = normalize(text);
            boolean ascii = normalized.contains("cacomorth");
            // Chinese spelling as escapes on purpose: see HiddenNameDetector on the mod side - the public
            // source tree should not spell the name out for a casual reader.
            boolean chinese = normalized.contains("\u5361\u5580\u83AB\u65AF");

            if (!ascii && !chinese)
            {
                ProbeLog.log("EGG-26-C", "MISS chat receipt does not contain that word (no observation this run): " + clamp(text));
                return;
            }

            s_hits++;
            String which = ascii && chinese ? "both" : (ascii ? "ascii" : "chinese");

            ProbeLog.log("EGG-26-C", "HIT#" + s_hits
                    + " matched=" + which
                    + " EXPECT=CRASH" + " (the game must crash immediately)"
                    + " | raw=" + clamp(text)
                    + " | normalized=" + clamp(normalized)
                    + " => if the game did not crash, report this line (easter egg failed)");

            // The receipt was printed, so the mod's crash-while-saving did not happen.
            ProbeLog.log("EGG-26-C", "VERDICT=SUSPECT success receipt still arrived after submit: the mod should have crashed before saving");
        }
        catch (Throwable t)
        {
            ProbeLog.log("EGG-26-C", "hook failed: " + t);
        }
    }

    private static boolean looksLikeAddSuccess(String text)
    {
        if (text == null || text.isEmpty())
            return false;

        for (String marker : ADD_SUCCESS_MARKERS)
        {
            if (text.contains(marker))
                return true;
        }

        return false;
    }

    /** Same rule as the main mod: drop every non-alphanumeric character and lower-case the rest. */
    private static String normalize(String text)
    {
        StringBuilder sb = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c))
                sb.append(Character.toLowerCase(c));
        }

        return sb.toString();
    }

    private static String clamp(String text)
    {
        if (text == null)
            return "(null)";

        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }
}
