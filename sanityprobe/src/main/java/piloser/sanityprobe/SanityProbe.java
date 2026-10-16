package piloser.sanityprobe;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * SanityPD Probe - a pure diagnostic mod (modid: sanityprobe).
 *
 * <p>Its only purpose is to capture client-side and runtime facts that a dedicated server cannot
 * measure. It changes no gameplay behaviour and registers no items, entities or blocks:
 *
 * <ol>
 *   <li><b>Group A - death messages</b>: mixins hook {@code MinecraftServer.sendSystemMessage}
 *       (the only source of the {@code [Server thread/INFO] [net.minecraft.server.MinecraftServer/]}
 *       lines) and {@code ServerPlayer.die}, logging the call stack of every broadcast - used to
 *       track down death messages printed twice with different text.</li>
 *   <li><b>Group B - walking</b>: the server logs the crawler's target, fuse, navigation and running
 *       goals, and the client logs the {@code walkAnimation} value, recomputing with GeckoLib's own
 *       formula whether {@code move.walk} or {@code misc.idle} will play.</li>
 *   <li><b>Group C - floating</b>: position, bounding box minY, onGround and the block below, shown
 *       live in the top-left corner so they can be screenshotted.</li>
 * </ol>
 *
 * <p>Output goes to {@code logs/sanityprobe.log}, mirrored into the {@code [PROBE]} lines of
 * latest.log. Delete this jar once the diagnosis is done.
 */
@Mod(SanityProbe.MODID)
public class SanityProbe
{
    public static final String MODID = "sanityprobe";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Probe version - must match `mod_version` in `gradle.properties` and the jar file name.
     *
     * <p>This is a hand-maintained copy and has already drifted twice: builds shipped with this constant
     * one release behind (2.16.0 and 2.16.1 both reported {@code v2.15.0}), so the armed banner and every
     * log line named the previous version and a screenshot could not identify the build. Bump it together
     * with `gradle.properties` and the jar name, then run the probe version check script after building -
     * that gate compares this constant, `gradle.properties`, the jar file name and the constant actually
     * compiled into the jar, so the drift can no longer ship silently.
     */
    public static final String VERSION = "2.40.0";

    /** Content revision of this build; printed in the HUD header only, to identify a build in a screenshot. */
    public static final String ROUND_TAG = "deident-sweep";

    public SanityProbe()
    {
        ProbeLog.init();
        ProbeLog.log("SYS", "sanityprobe loaded - diagnostic only, no gameplay changes");

        MinecraftForge.EVENT_BUS.register(DeathProbe.class);
        MinecraftForge.EVENT_BUS.register(WalkProbe.class);
        MinecraftForge.EVENT_BUS.register(ProbeCommand.class);
        MinecraftForge.EVENT_BUS.register(ProbeSelfTest.class);
        // Group G (server side): psychic resistance, inner-mob damage bonus, new commands, lang keys
        MinecraftForge.EVENT_BUS.register(ProbeImpactProbe.class);
        // Group S (server side): inner-mob spawn eggs (registration / lookup / model / texture / lang)
        MinecraftForge.EVENT_BUS.register(EggProbe.class);
        // ClientProbe registers itself via @Mod.EventBusSubscriber(value = Dist.CLIENT); client side only
        // MentalHintProbe does the same (group H - the three inner-voice tiers)
        // HintWindowProbe does the same (the pre-damage and immunity-expiry warning windows)
        MinecraftForge.EVENT_BUS.register(HintWindowProbe.class);
        // CrawlerSanityProbe does the same (the 50% / 75% crawler gates and the boss exemption)
        MinecraftForge.EVENT_BUS.register(CrawlerSanityProbe.class);
        // HiddenVoiceProbe does the same: the extra inner-voice pool, its per-save unlock and its sound
        MinecraftForge.EVENT_BUS.register(HiddenVoiceProbe.class);
        // ThoughtChainProbe does the same (this round - the thought chain's window and its tooltips; it names
        // no sanitypd type on purpose, see its javadoc)
        // TapeSequenceProbe does the same (the lost-memory-fragment tape: the sequence state, the handle
        // behind its hiss bed, and whether everything that is not the tape was really silenced)
        // Thought50Probe is server side (the two newest thoughts read off the loaded mod), and
        // Thought50TooltipProbe is its client half (the sentences as the client would really draw them)
        // VisionProbe does the same as the other clients (the hallucination batch: the item shape, the
        // category tag, the multiplier's rounding, and every line of the five tooltips as really drawn)
        // BlockProbe61 does the same (the mod's first block: its properties against the vanilla enchanting
        // table field by field, its name and grey detail line, and blockstate / model / 13 textures /
        // recipe / loot table / mineable tag on disk)
        // VoidFragmentProbe does the same (the inner-void fragment: its trigger words against the phrase the
        // transcription table's own description uses, the two scripts' 70 lines / quotes / {player} count /
        // single dark-red last line, its tooltip, and the three word-trigger allowances)
        // VesselFragmentProbe does the same (the vessel fragment: its words against the text the mod really
        // ships, both scripts, the tooltip, the four allowances, and the hidden-fragment mask in both copies -
        // the one pairing that decides whether this fragment can ever arrive)
        // UnboundProbe does the same (the void lifting its limits on the player's understanding: the marker
        // the files and the mod must agree on, all nine pairs parsed twice - as written and as the lifted
        // text - the notice's own text and its one-shot marker, and the condition derived two ways)
        // HintPoolProbe does the same (this round's eighth pool: its index, its eight lines and whether every
        // one of their language keys resolves, plus the one-tick delay and six-second window it speaks in)
    }
}
