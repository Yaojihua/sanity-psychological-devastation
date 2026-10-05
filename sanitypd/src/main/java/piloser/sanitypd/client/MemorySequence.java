package piloser.sanitypd.client;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.config.ConfigProxy;
import piloser.sanitypd.item.LostMemoryFragmentItem;
import piloser.sanitypd.net.MemoryTapePacket;
import piloser.sanitypd.net.PacketHandler;
import piloser.sanitypd.sound.MemoryChargeSoundInstance;
import piloser.sanitypd.sound.MemoryHissSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the "Lost Memory Fragment" story item - part 1 of the sequence.
 *
 * <h2>The owner's spec (2026-10-05)</h2>
 * <ol>
 *   <li>Hold right click to charge; the item has no other function</li>
 *   <li>The vanilla HUD is hidden for the <b>whole</b> stretch: charging <i>and</i> the black tape screen
 *       that follows, "until playback finishes"</li>
 *   <li>After {@link #CHARGE_DELAY_TICKS} (1 s) the charge clip starts, at the player</li>
 *   <li>The moment that clip ends the screen goes black and the tape starts playing</li>
 *   <li>Releasing the button anywhere before the black screen cancels everything, silently</li>
 * </ol>
 *
 * <h2>Why all of this is client side</h2>
 * The sequence is presentation, and the "am I holding it" state is already synchronised by vanilla's own
 * item-use pipeline, so it is read locally instead of adding a packet. No server behaviour depends on it.
 *
 * <h2>Two rules that are easy to break later</h2>
 * <ul>
 *   <li>The previous value of {@code options.hideGui} is <b>remembered and put back</b>: F1 is the
 *       player's own toggle, and a player who had the HUD hidden must still have it hidden afterwards.</li>
 *   <li>Every path out of the sequence goes through {@link #closeTape(Minecraft)} / {@link #cancel(Minecraft)},
 *       which are idempotent - a released button, a closed screen, a removed screen and a world unload all
 *       end up in the same place instead of leaking a hidden HUD.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MemorySequence
{
    /** Silence before the charge clip starts: the owner asked for the sound to begin after 1 second. */
    public static final int CHARGE_DELAY_TICKS = 20;

    /** Length of the charge clip. The file is 3.65 s long, which is 73 ticks at 20 tps. */
    public static final int CHARGE_SOUND_TICKS = 73;

    /** Hold time at which the black tape screen replaces the charging state. */
    public static final int TAPE_SCREEN_TICK = CHARGE_DELAY_TICKS + CHARGE_SOUND_TICKS;

    /** Whether the vanilla HUD is being suppressed right now (charge and tape). */
    private static boolean s_hudHidden;
    private static boolean s_chargeStarted;
    private static MemoryChargeSoundInstance s_charge;
    private static MemoryHissSoundInstance s_hiss;
    private static MemoryTapeScreen s_tape;

    private MemorySequence() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null)
        {
            cancel(mc);
            return;
        }

        // Once the tape screen is up it owns the presentation: the button is irrelevant from here on, and
        // the UI stays hidden until the screen goes away.
        if (s_tape != null)
        {
            if (mc.screen != s_tape)
                closeTape(mc);              // replaced by another screen (pause menu, death, disconnect)
            return;
        }

        // Any fragment counts: the sequence is shared by all of them, and which script plays is decided
        // from the item's own id when the tape opens. Matching one specific item here is exactly what once
        // left the second fragment with no HUD hiding and no story - the sequence simply never started.
        boolean holding = player.isUsingItem()
                && player.getUseItem().getItem() instanceof LostMemoryFragmentItem;

        if (!holding)
        {
            cancel(mc);                     // released before the black screen: abort silently
            return;
        }

        // Derived from the item-use state instead of a counter of our own: it cannot drift, and it resets
        // by itself the moment the use ends.
        int elapsed = LostMemoryFragmentItem.USE_DURATION - player.getUseItemRemainingTicks();

        hideHud();

        if (!s_chargeStarted && elapsed >= CHARGE_DELAY_TICKS)
        {
            s_chargeStarted = true;
            if (ConfigProxy.getPlaySounds(player.level().dimension().location()))
            {
                s_charge = new MemoryChargeSoundInstance();
                mc.getSoundManager().play(s_charge);
            }
        }

        if (elapsed >= TAPE_SCREEN_TICK)
            openTape(mc);
    }

    /** Called by {@link MemoryTapeScreen#onClose()} when the player ends the tape. */
    public static void onTapeScreenClosed(Minecraft mc)
    {
        closeTape(mc);
    }

    private static void openTape(Minecraft mc)
    {
        // Which fragment was used decides which script plays; both items share this one sequence.
        LocalPlayer player = mc.player;
        String scriptKey = player != null && player.getUseItem().getItem() instanceof LostMemoryFragmentItem fragment
                ? fragment.scriptKey()
                : "lost_memory_fragment";

        // Black screen first, then the bed: the hiss is what "the tape is playing" sounds like.
        s_tape = new MemoryTapeScreen(scriptKey);
        mc.setScreen(s_tape);
        s_chargeStarted = false;
        sendTapeState(mc, true);            // the server grants the creative-like protection

        if (s_charge != null)
        {
            s_charge.doStop();              // the clip has just ended; make sure nothing is left over
            s_charge = null;
        }

        if (player != null && ConfigProxy.getPlaySounds(player.level().dimension().location()))
        {
            s_hiss = new MemoryHissSoundInstance();
            mc.getSoundManager().play(s_hiss);
        }
    }

    /**
     * Ends the tape side of the sequence and hands the HUD back, exactly once.
     *
     * <p>Idempotent on purpose: it is reached from the screen closing, from the screen being replaced, and
     * from a world unload.
     */
    private static void closeTape(Minecraft mc)
    {
        if (s_tape == null && s_hiss == null && !s_hudHidden)
            return;

        sendTapeState(mc, false);           // give the protection back

        if (s_hiss != null)
        {
            s_hiss.doStop();
            s_hiss = null;
        }

        if (mc.screen == s_tape)
            mc.setScreen(null);

        s_tape = null;
        showHud();
    }

    /** Aborts the charging half: the clip stops, the HUD comes back and nothing else happens. */
    private static void cancel(Minecraft mc)
    {
        if (!s_hudHidden && s_charge == null && s_hiss == null && s_tape == null && !s_chargeStarted)
            return;

        if (s_charge != null)
        {
            s_charge.doStop();
            s_charge = null;
        }

        s_chargeStarted = false;
        closeTape(mc);
    }

    /**
     * Suppresses the vanilla HUD for as long as the sequence runs.
     *
     * <p>Deliberately <b>not</b> {@code options.hideGui}: that is what F1 toggles, and it also hides the
     * <b>first-person hand and the held item</b> - which would make the charge's drawn-bow pose, the whole
     * point of the charge, invisible. The hand is drawn in the level pass and the HUD in the GUI pass, so
     * cancelling only the latter hides every piece of interface and nothing of the player. It also leaves
     * the player's own F1 setting untouched, so there is no value to save and restore.
     *
     * <p>One consequence worth knowing: because this cancels the GUI pass entirely, the mod's own centre
     * hint and the probe's HUD are not drawn while the sequence runs. The probe still writes its log lines,
     * which is what its diagnostics are read from.
     */
    private static void hideHud()
    {
        s_hudHidden = true;
    }

    private static void showHud()
    {
        s_hudHidden = false;
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event)
    {
        if (s_hudHidden)
            event.setCanceled(true);
    }

    /**
     * Tells the server whether the tape is being watched.
     *
     * <p>Only sent when the state actually changes, and only with a connection: the tape's protection is
     * the one part of this sequence the server cannot derive for itself, and sending without a connection
     * would simply throw, which is the trap the thought-chain sync fell into once.
     */
    private static void sendTapeState(Minecraft mc, boolean playing)
    {
        if (mc.getConnection() == null)
            return;

        PacketHandler.CHANNEL_INSTANCE.sendToServer(new MemoryTapePacket(playing));
    }
}
