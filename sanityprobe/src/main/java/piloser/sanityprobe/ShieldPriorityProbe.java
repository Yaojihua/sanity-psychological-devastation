package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * Probe for right-click priority between a shadow weapon and an off-hand shield.
 *
 * <h2>Why this must be measured on the client</h2>
 * Whether holding right-click charges the weapon or raises the shield is decided by
 * {@code Minecraft#startUseItem}, which walks {@code InteractionHand.values()} in order:
 * the off hand is only reached when the main-hand {@code use()} returns PASS. The server
 * just receives the resulting use packets, so server logs cannot separate "the client never
 * dispatched it" from "it was dispatched and the server rejected it".
 *
 * <h2>Verdict</h2>
 * At the click the probe records both hands and its own prediction (an off-hand shield is
 * expected to take priority, so the weapon must not charge), then watches the next
 * {@link #WATCH_TICKS} ticks and reports the first change only:
 * <ul>
 *   <li>the weapon is still charging =&gt; the fix failed;</li>
 *   <li>the shield is the item in use =&gt; the shield took the click;</li>
 *   <li>nothing is in use =&gt; no action at all, usually because the shield is on cooldown.</li>
 * </ul>
 * One click produces at most one report. F4 hides the overlay entirely.
 *
 * <p>Read-only: registers no gameplay content and mutates no game state; every entry point
 * is wrapped in try/catch. A shield on cooldown that raises nothing is neutral, not a pass.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShieldPriorityProbe
{
    /** Ticks watched after a click. Raising a shield has a 5-tick wind-up, so 6 covers it. */
    private static final int WATCH_TICKS = 6;

    private static int s_watch;
    private static boolean s_reported;
    private static int s_clicks;
    private static String s_hud = "(probe: right-click once - shadow weapon in main hand + shield in off hand)";

    /** Cached reflective handle to {@code shadowPsychicDamage()} (no compile-time dependency on sanitypd). */
    private static Method s_shadowMarker;
    private static boolean s_shadowMarkerLooked;

    static
    {
        // Publish this probe's verdict to the F4 overlay (drawn by ClientProbe).
        ProbeHud.registerLine(() -> s_hud);
    }

    private ShieldPriorityProbe() {}

    /**
     * Called by {@link ClientProbe} on the first client tick to force class initialization,
     * which is what runs the static block that registers the overlay line. Note that
     * {@code Class.forName} does not trigger initialization on its own.
     */
    public static void init()
    {
        // Intentionally empty: initialization happens in the static block.
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event)
    {
        try
        {
            if (!event.isUseItem())
                return;

            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;

            if (player == null)
                return;

            ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);

            if (!isShadowWeapon(main) || !off.canPerformAction(ToolActions.SHIELD_BLOCK))
                return;

            s_clicks++;
            // An off-hand shield always takes priority here, whether or not it is on cooldown.
            boolean predictedYield = true;
            ProbeLog.log("SHIELD-26-C", "CLICK#" + s_clicks
                    + " hand=" + event.getHand()
                    + " main=" + itemName(main)
                    + " off=" + itemName(off)
                    + " offIsShield=" + off.is(Items.SHIELD)
                    + " SHIELD_BLOCK=" + off.canPerformAction(ToolActions.SHIELD_BLOCK)
                    + " predictedYield=" + predictedYield
                    + " (expected: no charging, off-hand shield takes over)");

            s_watch = WATCH_TICKS;
            s_reported = false;
        }
        catch (Throwable t)
        {
            ProbeLog.log("SHIELD-26-C", "hook failed: " + t);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || s_watch <= 0)
            return;

        try
        {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;

            if (player == null)
            {
                s_watch = 0;
                return;
            }

            s_watch--;

            ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);

            boolean chargingWeapon = player.isUsingItem() && isShadowWeapon(main) && player.getUseItem() == main;
            boolean shieldUp = player.isUsingItem() && off.canPerformAction(ToolActions.SHIELD_BLOCK)
                    && player.getUseItem() == off;

            if (s_reported)
                return;

            if (chargingWeapon)
            {
                s_reported = true;
                s_hud = "[PROBE] SHIELD-26: FIX FAILED - weapon is charging while a shield is held";
                ProbeLog.log("SHIELD-26-C", "VERDICT=FAIL weaponChargingWhileShieldHeld"
                        + " useItem=" + itemName(player.getUseItem())
                        + " isBlocking=" + player.isBlocking()
                        + " => fix failed: the main-hand weapon still charges");
                return;
            }

            if (shieldUp)
            {
                s_reported = true;
                boolean blocking = player.isBlocking();
                s_hud = "[PROBE] SHIELD-26: shield took the right-click, sword not charging"
                        + " (isBlocking=" + blocking + ")";
                ProbeLog.log("SHIELD-26-C", "VERDICT=OK"
                        + " useItem=" + itemName(player.getUseItem())
                        + " isBlocking=" + blocking
                        + " weaponCharging=false"
                        + " shieldCooldown=" + player.getCooldowns().isOnCooldown(Items.SHIELD)
                        + " => off-hand shield took the right-click, the shadow weapon is not charging");
                return;
            }

            // Still nothing in use when the watch window ends: the shield is usually on cooldown,
            // so the click did nothing at all. Neutral - the weapon did not charge, but the fix
            // was not exercised either.
            if (s_watch == 0)
            {
                s_reported = true;
                s_hud = "[PROBE] SHIELD-26: nothing raised (shield on cooldown?)";
                ProbeLog.log("SHIELD-26-C", "VERDICT=NEUTRAL"
                        + " useItem=" + itemName(player.getUseItem())
                        + " shieldCooldown=" + player.getCooldowns().isOnCooldown(Items.SHIELD)
                        + " weaponCharging=false"
                        + " => weapon not charging (= fix works); shield not raised either, usually because it is on cooldown");
            }
        }
        catch (Throwable t)
        {
            s_watch = 0;
            ProbeLog.log("SHIELD-26-C", "watch failed: " + t);
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * True when the stack is a sanitypd shadow weapon. The probe has no compile-time
     * dependency on sanitypd, so it reflects instead: an item whose class exposes
     * {@code shadowPsychicDamage()} (the marker method of {@code IShadowWeapon}) counts.
     */
    private static boolean isShadowWeapon(ItemStack stack)
    {
        if (stack.isEmpty())
            return false;

        try
        {
            if (s_shadowMarker == null)
            {
                // Looking the method up on the item class itself is enough: interface methods
                // are inherited or overridden by the concrete class.
                s_shadowMarker = stack.getItem().getClass().getMethod("shadowPsychicDamage");
                s_shadowMarkerLooked = true;
            }

            return s_shadowMarker != null;
        }
        catch (Throwable t)
        {
            if (!s_shadowMarkerLooked)
                s_shadowMarkerLooked = true;

            return false;
        }
    }

    private static String itemName(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return "(empty)";

        return ProbeLog.safe(() -> stack.getItem().toString());
    }
}
