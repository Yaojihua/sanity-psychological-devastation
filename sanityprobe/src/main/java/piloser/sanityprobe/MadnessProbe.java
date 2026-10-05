package piloser.sanityprobe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

/**
 * Reports the attribute modifiers the thoughts and mindsets put on the player.
 *
 * <h2>Why the client is the right place to read them</h2>
 * Every one of those bonuses is applied on the server (that is what makes them authoritative and visible to
 * the client's own movement prediction). The <b>modifier list of an attribute is synced to the client</b>,
 * so reading it here shows exactly what the server granted - which turns "the Command Hallucination packet
 * arrived" and "the Fight or Flight window flipped" into log lines instead of a feeling.
 *
 * <h2>Why the names matter, and what they look like now</h2>
 * The mod writes each modifier as {@code sanitypd:<source>/<effect>} - for example
 * {@code sanitypd:fight-or-flight/speed} or {@code sanitypd:madness/armor}. The name is the only thing that
 * tells two bonuses on the same attribute apart, and the mod used to give all of them one shared name
 * ({@code sanitypd:composure}), which made this probe's output unreadable: every line looked like
 * {@code {composure=0.550x,composure=0.300x}}. The names are now distinct and this probe prints them whole.
 *
 * <p>Only changes are logged, plus one HUD line, so a long session stays readable. The modifiers are sorted
 * before printing: the underlying collection has no order, and an unsorted line would look like a change
 * every time the set was rebuilt.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MadnessProbe
{
    /** Sampled once a second; the attribute map is cheap to read but the log is not. */
    private static final int INTERVAL_TICKS = 20;

    /** The mod prefixes every modifier it adds, which is how they are told apart from vanilla's own. */
    private static final String MOD_PREFIX = "sanitypd:";

    private static int s_tick;
    private static String s_last = "";
    private static String s_hud = "(no attributes yet)";

    static
    {
        ProbeHud.registerLine(() -> "\u0000FF8888" + "[MAD] " + s_hud);
    }

    private MadnessProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null)
        {
            s_last = "";
            s_hud = "(no player)";
            return;
        }

        if (++s_tick % INTERVAL_TICKS != 0)
            return;

        // A fixed order for the attributes, so the line reads the same way every time.
        StringBuilder line = new StringBuilder();
        append(line, mc, Attributes.ATTACK_DAMAGE, "attack");
        append(line, mc, Attributes.MOVEMENT_SPEED, "speed");
        append(line, mc, Attributes.ARMOR, "armor");

        String text = line.toString();

        if (text.equals(s_last))
            return;

        s_last = text;
        s_hud = text.isEmpty() ? "no sanitypd modifiers" : text;

        ProbeLog.log("THOUGHT-ATTR", s_hud);
    }

    /** Appends the total, the base value and every sanitypd modifier on one attribute. */
    private static void append(StringBuilder line, Minecraft mc, Attribute attribute, String label)
    {
        AttributeInstance instance = mc.player.getAttribute(attribute);

        if (instance == null)
            return;

        Collection<AttributeModifier> modifiers = instance.getModifiers();
        List<String> own = new ArrayList<>();

        for (AttributeModifier modifier : modifiers)
        {
            // Vanilla's own modifiers (sprinting, the held item) are deliberately left out: they would bury
            // the numbers this probe exists to show.
            if (modifier.getName() == null || !modifier.getName().startsWith(MOD_PREFIX))
                continue;

            // The whole name, not just the tail: "fight-or-flight/speed" says which bonus this is, and with
            // the names now unique there is nothing to disambiguate by stripping.
            own.add(modifier.getName().substring(MOD_PREFIX.length())
                    + "=" + String.format(java.util.Locale.ROOT, "%+.3f", modifier.getAmount())
                    + (modifier.getOperation() == AttributeModifier.Operation.ADDITION ? "f" : "x"));
        }

        if (own.isEmpty())
            return;

        java.util.Collections.sort(own);

        if (line.length() > 0)
            line.append(" | ");

        line.append(label)
                .append(" base=").append(String.format(java.util.Locale.ROOT, "%.3f", instance.getBaseValue()))
                .append(" -> ").append(String.format(java.util.Locale.ROOT, "%.3f", instance.getValue()))
                .append(" {").append(String.join(", ", own)).append("}");
    }
}
