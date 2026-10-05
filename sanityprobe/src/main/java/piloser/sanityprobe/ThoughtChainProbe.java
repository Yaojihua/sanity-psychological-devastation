package piloser.sanityprobe;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thought chain probe - the client-visible half of the thought chain work.
 *
 * <h2>Why this class names no sanitypd type</h2>
 * The probe's {@code build.gradle} deliberately has <b>no compile-time dependency</b> on sanitypd: it is only
 * put next to a development build of sanitypd at run time. Anything that imported {@code piloser.sanitypd.*}
 * would therefore not compile here, which is why the existing probes reach into the mod by reflection
 * ({@code SanityReflect}). This probe needs none of that, because everything a thought chain does that a
 * dedicated server cannot show is visible from vanilla types alone:
 *
 * <ol>
 *   <li><b>Screen</b>: whether the thought chain window opens and closes. Recognised by class name rather than
 *       by importing the class, which keeps the compile-time independence intact.</li>
 *   <li><b>Tooltip</b>: the lines an item actually renders, joined into one log line. This is the strongest
 *       check available for the tooltip work, because the text, the tier that is in force and the effect of
 *       holding shift all end up in the log - a screenshot is no longer the only way to see them.</li>
 *   <li><b>Tick</b>: a heartbeat that reports once per screen session, so a log with no tooltip lines can be
 *       told apart from a client that never got that far.</li>
 * </ol>
 *
 * <p>Nothing here changes behaviour: it only writes to {@code logs/sanityprobe.log} and the {@code [PROBE]}
 * lines of latest.log.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ThoughtChainProbe
{
    /** The window class name this probe watches for; matched as text so the class need not exist here. */
    private static final String SCREEN_CLASS = "ThoughtChainScreen";

    /** How many tooltip lines have been logged, so a long session can be judged from one number. */
    private static int s_tooltips;

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event)
    {
        Screen screen = event.getNewScreen();

        if (screen != null && screen.getClass().getSimpleName().contains(SCREEN_CLASS))
            ProbeLog.log("THOUGHT", "screen OPEN  (" + screen.getClass().getName() + ")");
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event)
    {
        Screen screen = event.getScreen();

        if (screen != null && screen.getClass().getSimpleName().contains(SCREEN_CLASS))
            ProbeLog.log("THOUGHT", "screen CLOSE");
    }

    /**
     * Logs the tooltip of any item whose registry name starts with {@code sanitypd:thought_}.
     *
     * <h2>Styled output, not a flattened string</h2>
     * The lines used to be joined with {@code getString()}, which throws away the colour and the bold flag -
     * and colour is exactly what the owner's reports were about ("the mindset tooltip is too drab", "the
     * untiered values have no colour", "the numbers still show a key"). A flat line cannot answer any of
     * those: the mod's own self-check proves colour with {@code Component#visit}, so the probe must read the
     * same way. Each styled run is therefore logged as a tagged token:
     *
     * <pre>
     *   &lt;colour[,b]&gt;text&lt;/&gt;      a run with a colour and/or bold
     *   plain text                 a run with no styling at all
     * </pre>
     *
     * <p>That makes three failures visible in one line: a raw {@code %1$s} (the substitution did not happen),
     * a wrong or missing colour, and a run that should have been bold and is not.
     *
     * <p><b>{@code LOWEST} priority is load-bearing.</b> The mod adds its own lines from another handler for
     * the same event. This probe used to run at default priority, which meant it usually logged the list
     * <b>before</b> those lines were appended - every thought tooltip came out as a single line holding just
     * the item name, and the log looked like the mod's tooltip was broken when it was not. Running last makes
     * the log show what the player actually sees.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event)
    {
        ItemStack stack = event.getItemStack();

        if (stack.isEmpty())
            return;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        if (!id.startsWith("sanitypd:thought_"))
            return;

        List<Component> lines = event.getToolTip();
        StringBuilder flat = new StringBuilder();

        for (Component line : lines)
        {
            if (flat.length() > 0)
                flat.append(" ; ");

            flat.append(styled(line));
        }

        s_tooltips++;

        // The item id leads, so a log can be filtered per thought without reading the sentence.
        ProbeLog.log("THOUGHT", "tooltip #" + s_tooltips + " " + id + " -> [" + flat + "]");
    }

    /**
     * One component as text plus its styling, run by run.
     *
     * <p>{@code visit} walks the component exactly as the client's renderer does, handing each run its
     * <b>resolved</b> style - so an inherited grey shows up on the run that inherits it, which is the whole
     * point: "the value is green even though the sentence around it is grey" is visible here.
     */
    private static String styled(Component component)
    {
        StringBuilder out = new StringBuilder();

        component.visit((style, text) ->
        {
            if (text.isEmpty())
                return Optional.empty();

            String tag = styleTag(style);

            if (tag.isEmpty())
                out.append(text);
            else
                out.append('<').append(tag).append('>').append(text).append("</>");

            return Optional.empty();
        }, Style.EMPTY);

        return out.toString();
    }

    /** {@code #RRGGBB} for the run's colour (plus {@code ,b} when bold), or an empty string when unstyled. */
    private static String styleTag(Style style)
    {
        StringBuilder tag = new StringBuilder();
        TextColor colour = style.getColor();

        if (colour != null)
            tag.append(String.format(java.util.Locale.ROOT, "#%06X", colour.getValue()));

        if (style.isBold())
            tag.append(tag.length() > 0 ? ",b" : "b");

        return tag.toString();
    }

    /**
     * Reports the count once a minute while the client is in a world.
     *
     * <p>Deliberately not once a tick: the log is meant to be read after a test, and a per-tick heartbeat
     * would bury the tooltip lines that matter.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (net.minecraft.client.Minecraft.getInstance().level == null)
            return;

        if (s_tooltips > 0 && net.minecraft.client.Minecraft.getInstance().level.getGameTime() % 1200 == 0)
            ProbeLog.log("THOUGHT", "heartbeat: " + s_tooltips + " thought tooltips logged so far");
    }
}
