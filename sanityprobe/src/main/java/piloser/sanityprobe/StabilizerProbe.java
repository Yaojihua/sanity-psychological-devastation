package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side probe for the mind stabilizers alpha / beta / gamma.
 *
 * <h2>Why these need a probe</h2>
 * <ol>
 *   <li><b>Cooldown</b> is a client HUD effect (the grey sweep over the item icon). A server
 *       self-test can only show that the constant is right and that {@code ItemCooldowns} is
 *       keyed by item; it cannot show that the cooldown is displayed and blocked as intended
 *       in game, nor that all stacks of one stabilizer share it while the three are independent.</li>
 *   <li><b>Buff start and end</b>: the real duration and icon of mania immunity and inner
 *       immunity are also client-side.</li>
 *   <li>The AI effect of inner immunity is additionally observable server-side, through the
 *       {@code viewerInnerImmune=} field logged by {@link WalkProbe}.</li>
 * </ol>
 *
 * <p>Only state transitions are logged (cooldown start/end, buff gain/loss), never one line
 * per tick.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class StabilizerProbe
{
    private static final String[] ITEM_FIELDS = { "STABILIZER_A", "STABILIZER_B", "STABILIZER_C" };
    private static final String[] EFFECT_FIELDS = { "MANIA_IMMUNITY", "INNER_IMMUNITY" };

    /** Item path to whether it was on cooldown last time. */
    private static final Map<String, Boolean> LAST_COOLDOWN = new HashMap<>();
    /** Effect path to its last remaining duration in ticks. */
    private static final Map<String, Integer> LAST_EFFECT_DURATION = new HashMap<>();

    private static int s_tick;
    private static String s_hud = "(no stabilizer activity yet)";

    private StabilizerProbe() {}

    static
    {
        // Since v2.7.0 this line goes through the shared HUD layout; it used to be drawn at a
        // hardcoded y that collided with the FLOAT line.
        ProbeHud.registerLine(() -> "\u0000DD88FF" + "[STAB] " + s_hud);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 4 != 0)
            return;

        try
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null)
                return;

            // ---------- cooldown: only the start/end transitions of our three items ----------
            for (String field : ITEM_FIELDS)
            {
                Item item = SanityReflect.item(field);

                if (item == null)
                    continue;

                boolean on = mc.player.getCooldowns().isOnCooldown(item);
                Boolean previous = LAST_COOLDOWN.put(field, on);

                if (previous == null || previous != on)
                {
                    float percent = mc.player.getCooldowns().getCooldownPercent(item, 0.0f);

                    ProbeLog.log("STAB-C", "cooldown " + (on ? "START" : "END") + " " + field
                            + (on ? " (percent=" + ProbeLog.fmt(percent) + "; the cooldown is keyed by Item ⇒ all stacks of the same item share it)" : ""));

                    s_hud = field + (on ? " cooling " + (int)(percent * 100) + "%" : " ready");
                }
            }

            // ---------- effects: gain / loss / expiry of mania immunity and inner immunity ----------
            for (String field : EFFECT_FIELDS)
            {
                MobEffect effect = SanityReflect.effect(field);

                if (effect == null)
                    continue;

                MobEffectInstance instance = mc.player.getEffect(effect);
                int duration = instance == null ? -1 : instance.getDuration();
                Integer previous = LAST_EFFECT_DURATION.put(field, duration);

                if (previous == null)
                    continue;   // first sample only establishes the baseline

                if (previous < 0 && duration >= 0)
                {
                    // WARNING: do not confuse isVisible() (particles) with showIcon() (the icon in
                    // the top-right corner). Conflating them makes a log line report showIcon=false
                    // when in fact only the particles were absent.
                    ProbeLog.log("STAB-C", "effect GAINED " + field + " duration=" + duration + "t"
                            + " visible(particles)=" + instance.isVisible()
                            + " showIcon(icon)=" + instance.showIcon()
                            + " ambient=" + instance.isAmbient());
                    s_hud = field + " gained " + (duration / 20) + "s";
                }
                else if (previous >= 0 && duration < 0)
                {
                    ProbeLog.log("STAB-C", "effect LOST " + field + " (last remaining " + previous + "t)");
                    s_hud = field + " ended";
                }
            }
        }
        catch (Throwable ignored)
        {
            // The probe must never affect gameplay.
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        // This class no longer draws anything itself: the line is registered in the static
        // block and laid out by {@link HudLayout}. Kept empty on purpose; do not add drawing here.
    }
}
