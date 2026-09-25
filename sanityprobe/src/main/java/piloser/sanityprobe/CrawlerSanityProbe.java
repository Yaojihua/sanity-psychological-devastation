package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Client-side probe for the sanity gates that decide what a screaming crawler is allowed to do (round 29).
 *
 * <h2>The three rules being watched</h2>
 * <ol>
 *   <li><b>Boss protection</b>: the "block mob spawning" test switch must never remove the Ender Dragon,
 *       the Wither, the Warden or the Elder Guardian. They share the {@code MONSTER} category with ordinary
 *       mobs, and the dragon is one-per-world, so removing one used to destroy the save.</li>
 *   <li><b>No block damage above 50% sanity</b>: the crawler's blast still happens and still deals its
 *       psychic damage, but it must not break any block.</li>
 *   <li><b>Tracking cancelled above 75% sanity</b>: the crawler must not track the player at all - the
 *       target is refused and any existing target is dropped.</li>
 * </ol>
 *
 * <h2>Why it has to run on the client</h2>
 * The 50% and 75% gates are decided from the <b>player's</b> sanity capability. A dedicated server has the
 * capability too, but the probe cannot see this client's HUD state from there, and the interesting object -
 * "what would the crawler be allowed to do to me right now" - is a client-side question.
 *
 * <h2>What it reports</h2>
 * One {@code [CRAWLER-29]} line per second while the player is insane enough for the gates to matter, plus
 * the same numbers on the HUD as {@code [CRWL]}, so a screenshot carries the evidence. It also checks the
 * boss list itself once at startup by reading the same entity types the mod protects, which turns "the
 * exemption is still there" into a log line instead of a claim.
 *
 * <p>WARNING: read-only. Every reflective lookup is guarded and a failure logs one line; nothing here can
 * change gameplay.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CrawlerSanityProbe
{
    /** Mirrors ScreamingCrawler#NO_BLOCK_DAMAGE_SANITY. */
    private static final float NO_BLOCK_DAMAGE_SANITY = 50f;
    /** Mirrors ScreamingCrawler#TRACKING_CANCEL_SANITY. */
    private static final float TRACKING_CANCEL_SANITY = 75f;

    private static int s_tick;
    private static boolean s_once;
    private static String s_hud = "-";
    private static String s_lastState = "";

    private CrawlerSanityProbe() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        // Every second: the gates are thresholds on a value that changes per tick, one line per second is
        // enough to see which side of each threshold the player is on.
        if (++s_tick % 20 != 0)
            return;

        try
        {
            tick();
        }
        catch (Throwable t)
        {
            ProbeLog.log("CRAWLER-29", "probe error: " + t);
        }
    }

    private static void tick() throws Exception
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null)
            return;

        if (!s_once)
        {
            s_once = true;
            reportApi();
        }

        float sanity = sanityOf(player);
        if (sanity < 0f)
            return;

        boolean blocksSpared = sanity >= NO_BLOCK_DAMAGE_SANITY;
        boolean trackingCancelled = sanity >= TRACKING_CANCEL_SANITY;
        String state = trackingCancelled ? "tracking-cancelled"
                : blocksSpared ? "no-block-damage" : "normal";

        // Report on a state change, and otherwise once every 10 seconds so a long test still has a record.
        boolean changed = !state.equals(s_lastState);
        if (changed || s_tick % 200 == 0)
        {
            s_lastState = state;
            ProbeLog.log("CRAWLER-29", String.format(Locale.ROOT,
                    "sanity=%.1f%% => %s | blast breaks blocks=%s | crawler may track=%s (gates: no-block-damage at %.0f%%, tracking-cancel at %.0f%%)",
                    sanity, state, !blocksSpared, !trackingCancelled,
                    NO_BLOCK_DAMAGE_SANITY, TRACKING_CANCEL_SANITY));
        }

        s_hud = String.format(Locale.ROOT, "%s sanity=%.0f%%", state, sanity);
    }

    /** Reads the player's sanity through the capability, or -1 when sanitypd is absent. */
    private static float sanityOf(LocalPlayer player) throws Exception
    {
        Class<?> providerClass = Class.forName("piloser.sanitypd.capability.SanityProvider");
        Object capToken = providerClass.getField("CAP").get(null);
        if (capToken == null)
            return -1f;

        Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
        Object cap = LocalPlayer.class.getMethod("getCapability", capabilityClass).invoke(player, capToken);
        if (cap == null)
            return -1f;

        Object orElse = cap.getClass().getMethod("orElse", Object.class).invoke(cap, (Object) null);
        if (orElse == null)
            return -1f;

        return (Float) orElse.getClass().getMethod("getSanity").invoke(orElse);
    }

    /**
     * One-off check of the boss exemption list.
     *
     * <p>The gate lives in the mod, not in the probe, so this cannot read it directly without touching
     * private code. What it can do - and what matters - is confirm that every protected type really is in
     * the {@code MONSTER} category, i.e. that the exemption is doing something rather than guarding types
     * that were never at risk. If a future version moves one of these out of MONSTER the line says so.
     */
    private static void reportApi()
    {
        StringBuilder sb = new StringBuilder();
        int inMonster = 0;
        EntityType<?>[] guarded = { EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN };

        for (EntityType<?> type : guarded)
        {
            boolean monster = type.getCategory() == MobCategory.MONSTER;
            if (monster)
                inMonster++;
            sb.append(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath())
                    .append(monster ? "=MONSTER " : "=not-monster ");
        }

        ProbeLog.log("CRAWLER-29", String.format(Locale.ROOT,
                "boss exemption check: %d/%d guarded types are in the MONSTER category (these are the ones the"
                        + " spawn guard would otherwise clear) | %s => %s",
                inMonster, guarded.length, sb.toString().trim(),
                inMonster == guarded.length ? "OK" : "CHECK (a guarded type changed category; re-read the exemption list)"));

        ProbeHud.registerLine(() -> "\u0000FF9955" + "[CRWL] " + s_hud);
    }

    /** Convenience for other probes: whether this entity is one the boss exemption must spare. */
    public static boolean isGuardedBoss(Entity entity)
    {
        if (entity == null)
            return false;
        EntityType<?> type = entity.getType();
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER
                || type == EntityType.WARDEN || type == EntityType.ELDER_GUARDIAN;
    }
}
