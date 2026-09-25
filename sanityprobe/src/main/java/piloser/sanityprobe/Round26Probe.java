package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Aggregate probe for the round-26 feature set: one checklist run that writes every client-side
 * group into the log.
 *
 * <p>It does exactly three things and implements no verdict logic of its own, so every rule has
 * a single copy:
 * <ol>
 *   <li>print a startup checklist: which groups exist, how to test each one, what to expect;</li>
 *   <li>force the four client groups to initialize — {@code Class.forName} and constant
 *       references do not trigger static initializers, so without an explicit {@code init()}
 *       their overlay lines are never registered;</li>
 *   <li>watch for the title screen, where the splash group re-rolls its line (returning to the
 *       title screen or pressing F3+T also re-rolls it).</li>
 * </ol>
 *
 * <h2>Overlay order (top to bottom)</h2>
 * <pre>
 * [WALK] / [FLOAT] / [SND] / [STAB] / [SNEAK] …   older groups, unified layout since v2.7.0
 * [PROBE] SHIELD-26: shadow weapon + shield, right click
 * [PROBE] EGG-26:    /sanity hint mild add &lt;the name&gt; (the game is expected to crash)
 * [PROBE] ENCH-26:   wear armour with psychic protection
 * [PROBE] REFINE-26: shadow weapon + inner clump in an anvil
 * [PROBE] SPLASH-26: title screen (appears after the first visit)
 * </pre>
 * F4 hides every line; spacing, width and line limits are handled by {@link HudLayout}. All output
 * goes to {@code <instance>/logs/sanityprobe.log}.
 *
 * <p>Read-only: writes the log, mutates no game state.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Round26Probe
{
    /** Probe version; the single source of truth is {@link SanityProbe#VERSION}. */
    public static final String VERSION = SanityProbe.VERSION;

    private static boolean s_inited;
    private static boolean s_bannerDone;
    private static int s_tick;
    private static boolean s_warnedNoSanitypd;

    static
    {
        ensureInited();
    }

    private Round26Probe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        ensureInited();

        // Without the main mod every reading is meaningless, so warn once at startup.
        if (!s_warnedNoSanitypd && ++s_tick % 40 == 0)
        {
            s_warnedNoSanitypd = true;

            if (!sanitypdLoaded())
                ProbeLog.log("P26", "WARN sanitypd mod not detected - all five group readings are invalid (install the main jar first)");
        }

        // The splash line is rolled once in TitleScreen#init, so the splash group re-measures on every visit.
        if (Minecraft.getInstance().screen instanceof TitleScreen)
        {
            SplashProbe.onTitleScreen();

            if (!s_bannerDone)
            {
                s_bannerDone = true;
                banner();
            }
        }
        else
        {
            s_bannerDone = false;
        }
    }

    /** Idempotent: only the first call arms the groups. {@link ClientProbe} calls it too, in case only the client half is loaded. */
    public static void ensureInited()
    {
        if (s_inited)
            return;

        s_inited = true;

        // Class.forName and constant references do NOT trigger static initialization, so init()
        // must be called explicitly; otherwise ProbeHud.registerLine never runs for these groups.
        safeInit("SHIELD-26-C", ShieldPriorityProbe::init);
        safeInit("EGG-26-C", EggProbe26::init);
        safeInit("ENCH-26-C", EnchantProbe26::init);
        safeInit("REFINE-26-C", RefineProbe26::init);
        safeInit("SPLASH-26", SplashProbe::init);

        ProbeLog.log("P26", "round-26 probe v" + VERSION + " armed"
                + " | groups: V=SHIELD-26 W=EGG-26 X=ENCH-26 R=REFINE-26 Y=SPLASH-26"
                + " | " + RefineProbe26.constants());
    }

    private static void safeInit(String tag, Runnable action)
    {
        try
        {
            action.run();
        }
        catch (Throwable t)
        {
            ProbeLog.log("P26", tag + " init failed: " + t);
        }
    }

    /** Startup checklist: how to test each group and what it should print. */
    private static void banner()
    {
        ProbeLog.log("P26", "=== round-26 full check (probe v" + VERSION + ") ===");
        ProbeLog.log("P26", "V shield  : shadow sword/axe in the main hand + shield in the off hand, hold right click => expect [SHIELD-26-C] VERDICT=OK (shield raised, weapon not charging)");
        ProbeLog.log("P26", "W egg     : /sanity hint mild add 卡-喀-莫-斯 => expect [EGG-26-C] HIT...EXPECT=CRASH **and the game closes at once**; "
                + "negative control: add nice weather today / cacomort must only give MISS");
        ProbeLog.log("P26", "X enchant : wear armour with that enchantment => the ENCH-26 overlay shows Lv and damage reduction% (full set IV = 16 points = 64% reduction)");
        ProbeLog.log("P26", "R refine  : shadow weapon in the left anvil slot + inner clump in the right slot => expect [REFINE-26-C] VERDICT=OK"
                + " (result = min(current+clump,100), xp cost = number of levels)");
        ProbeLog.log("P26", "Y splash  : yellow text on the title screen => expect [SPLASH-26] oursInPool=12/12 placeholderLeft=false"
                + " and [SPLASH-26-SAMPLE] oursHit>0 placeholderLeft=0");
    }

    private static boolean sanitypdLoaded()
    {
        try
        {
            Class.forName("piloser.sanitypd.SanityMod");
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }
}
