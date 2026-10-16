package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The two new thoughts' <b>sentences</b>, as the client would really draw them.
 *
 * <h2>[THOUGHT50-C]</h2>
 * More than "the key exists": this group asks the mod for the tooltip of each item, flattens what comes back
 * into (colour, text) pieces and checks the numbers that the player would actually read.
 *
 * <p>The check that matters is the unit one. A thought with no tier ladder prints a value that the tooltip
 * renders from the value and the item alone, and rendering an absolute amount through the percentage path is
 * invisible in every static check: the sentence would read "500%" where the code grants five points of
 * sanity. The colours are checked with the same flattening, which is the only way to see them without a
 * screenshot.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Thought50TooltipProbe
{
    private static final String TAG = "THOUGHT50-C";

    /** The colour this project prints a value that helps the player in. */
    private static final int BUFF = 0x55FF55;

    private static final String REGISTRY = "piloser.sanitypd.item.ItemRegistry";

    private static boolean s_done;

    private Thought50TooltipProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || s_done)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
            return;

        s_done = true;

        try
        {
            Item stockholm = item("THOUGHT_STOCKHOLM_SYNDROME");
            Item overdose = item("THOUGHT_OVERDOSE");

            List<String> stockholmSpans = tooltip(stockholm);
            List<String> overdoseSpans = tooltip(overdose);

            // The sanity payout is an absolute amount: a green "5", and no percentage anywhere near it.
            boolean sanity = hasGreenValue(stockholmSpans, "5")
                    && !contains(stockholmSpans, "500%")
                    && !contains(stockholmSpans, "%1$s");

            // The cooldown cut is a fraction: a green "45%".
            boolean cut = hasGreenValue(overdoseSpans, "45")
                    && !contains(overdoseSpans, "%1$s");

            boolean ok = sanity && cut;

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "stockholm=%s | overdose=%s | VERDICT=%s",
                    join(stockholmSpans), join(overdoseSpans),
                    ok ? "PASS(values render as 5 sanity and 45%, both green)"
                       : "CHECK(read the two sentences above: a value or its colour is not what the spec says)"));
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "failed (expected only when sanitypd is absent): " + t);
        }
    }

    /**
     * The flattened tooltip of an item: one entry per screen span, as {@code colour:text}.
     *
     * <p>The item's tooltip is produced by posting the same event Forge fires when a player hovers it, so the
     * mod's own handler builds it - this group does not re-implement the sentences.
     */
    private static List<String> tooltip(Item item)
    {
        List<String> spans = new ArrayList<>();

        if (item == null)
            return spans;

        List<Component> lines = new ArrayList<>();
        MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(new ItemStack(item), Minecraft.getInstance().player, lines,
                TooltipFlag.Default.NORMAL));

        for (Component line : lines)
            line.visit((style, text) ->
            {
                spans.add(colourOf(style) + ":" + text);
                return Optional.empty();
            }, Style.EMPTY);

        return spans;
    }

    private static String colourOf(Style style)
    {
        TextColor colour = style.getColor();

        return colour == null ? "-" : String.format(Locale.ROOT, "%06X", colour.getValue());
    }

    /** Whether some piece of the tooltip is a buff-green piece whose text carries the expected number. */
    private static boolean hasGreenValue(List<String> spans, String number)
    {
        String prefix = String.format(Locale.ROOT, "%06X:", BUFF);

        for (String span : spans)
        {
            if (span.startsWith(prefix) && span.contains(number))
                return true;
        }

        return false;
    }

    private static boolean contains(List<String> spans, String needle)
    {
        for (String span : spans)
        {
            if (span.contains(needle))
                return true;
        }

        return false;
    }

    private static String join(List<String> spans)
    {
        return spans.isEmpty() ? "(empty)" : String.join(" / ", spans);
    }

    private static Item item(String field)
    {
        try
        {
            Object holder = Class.forName(REGISTRY).getField(field).get(null);

            if (holder instanceof net.minecraftforge.registries.RegistryObject<?> ro && ro.isPresent())
                return (Item) ro.get();
        }
        catch (Throwable ignored)
        {
            // Reported as an empty tooltip by the verdict.
        }

        return null;
    }
}
