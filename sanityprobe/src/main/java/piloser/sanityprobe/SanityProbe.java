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

    /** Probe version - must match `mod_version` in `gradle.properties` and the jar file name. */
    public static final String VERSION = "2.9.0";

    /** Content revision of this build; printed in the HUD header only, to identify a build in a screenshot. */
    public static final String ROUND_TAG = "crawler-gates";

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
        // HintWindowProbe does the same (round 28 - the pre-damage and immunity-expiry warning windows)
        MinecraftForge.EVENT_BUS.register(HintWindowProbe.class);
        // CrawlerSanityProbe does the same (round 29 - the 50% / 75% crawler gates and the boss exemption)
        MinecraftForge.EVENT_BUS.register(CrawlerSanityProbe.class);
    }
}
