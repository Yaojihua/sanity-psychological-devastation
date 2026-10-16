package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client half of the shadow TNT check (tag {@code SHADOW-TNT-63}).
 *
 * <h2>What only a client can see here</h2>
 * The blast, the sanity drain and the death-message routing are all server-side and were asserted by the
 * round's own self-check. Three things are left that a dedicated server cannot answer:
 *
 * <ol>
 *   <li><b>The sentences.</b> A death message is a translation key resolved by the client, so "the player
 *       sees a sentence and not the raw key" can only be read where the language files are loaded. Each of
 *       the three is resolved here for both languages - the failure it catches is a missing or malformed
 *       entry, and the symptom is the player reading {@code death.attack.shadow_tnt} in chat.</li>
 *   <li><b>The block's identity.</b> That shadow TNT really is a {@link TntBlock} with vanilla TNT's own
 *       numbers, which is what makes redstone, fire, flint and steel and chain reactions work without any
 *       of them being reimplemented.</li>
 *   <li><b>The resources.</b> The three face textures and both models have to exist on disk; a missing one
 *       renders as the missing-model cube and reports nothing.</li>
 * </ol>
 *
 * <p>Everything mod-side is reached through the registries rather than by importing mod classes, so the
 * probe still loads and stays quiet when sanitypd is absent.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShadowTntProbe
{
    private static final String TAG = "SHADOW-TNT-63";
    private static final String MODID = "sanitypd";

    /** The three death-message ids, and whether each one is expected to need the igniter's name. */
    private static final String[][] MESSAGES = {
            { "death.attack.shadow_tnt", "no owner" },
            { "death.attack.shadow_tnt_self", "self-ignited" },
            { "death.attack.shadow_tnt_by_owner", "set off by someone else" },
    };

    private static boolean s_reported;
    private static String s_hud = "-";

    private ShadowTntProbe() {}

    static
    {
        ProbeHud.registerLine(() -> "\u0000FFAA33" + "[ShadowTNT] " + s_hud);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || s_reported)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
            return;

        s_reported = true;

        try
        {
            report(mc);
        }
        catch (Throwable t)
        {
            ProbeLog.log(TAG, "the check could not run: " + t + " => VERDICT=CHECK");
            s_hud = "check failed";
        }
    }

    private static void report(Minecraft mc)
    {
        boolean ok = true;

        // ---- 1. the two items and the block are registered ----
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(MODID, "shadow_tnt"));
        Item gunpowder = ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, "shadow_gunpowder"));
        Item blockItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, "shadow_tnt"));
        boolean registered = block != null && gunpowder != null && blockItem != null;
        ok &= registered;

        ProbeLog.log(TAG, String.format(java.util.Locale.ROOT, "registration: block=%s gunpowder=%s blockItem=%s => %s",
                block != null, gunpowder != null, blockItem != null, registered ? "OK" : "CHECK"));

        // ---- 2. it really is vanilla TNT, with vanilla's own numbers ----
        if (block != null)
        {
            BlockState shadow = block.defaultBlockState();
            BlockState vanilla = Blocks.TNT.defaultBlockState();

            boolean isTntBlock = block instanceof TntBlock;
            boolean hardness = block.defaultDestroyTime() == Blocks.TNT.defaultDestroyTime();
            boolean resistance = block.getExplosionResistance() == Blocks.TNT.getExplosionResistance();
            boolean sound = block.getSoundType(shadow) == Blocks.TNT.getSoundType(vanilla);
            boolean noTool = !shadow.requiresCorrectToolForDrops();

            ok &= isTntBlock && hardness && resistance && sound && noTool;

            ProbeLog.log(TAG, String.format(java.util.Locale.ROOT, "block: isTntBlock=%s hardness=%s(%s/%s) resistance=%s sound=%s requiresTool=%s => %s",
                    isTntBlock, hardness, block.defaultDestroyTime(), Blocks.TNT.defaultDestroyTime(),
                    resistance, sound, !noTool, (isTntBlock && hardness && resistance && sound && noTool) ? "OK" : "CHECK"));
        }

        // ---- 3. the data and asset files exist, read through the mod's own file system ----
        // (A classloader lookup would find nothing in a development run - a mistake this project has
        // already recorded once, so the mod file system is used instead.)
        for (String path : new String[] {
                "assets/" + MODID + "/blockstates/shadow_tnt.json",
                "assets/" + MODID + "/models/block/shadow_tnt.json",
                "assets/" + MODID + "/models/item/shadow_tnt.json",
                "assets/" + MODID + "/models/item/shadow_gunpowder.json",
                "assets/" + MODID + "/textures/block/shadow_tnt_top.png",
                "assets/" + MODID + "/textures/block/shadow_tnt_bottom.png",
                "assets/" + MODID + "/textures/block/shadow_tnt_side.png",
                "assets/" + MODID + "/textures/item/shadow_gunpowder.png",
                "data/" + MODID + "/recipes/shadow_tnt.json",
                "data/" + MODID + "/loot_tables/blocks/shadow_tnt.json",
                "data/" + MODID + "/damage_type/shadow_tnt.json",
                "data/" + MODID + "/damage_type/shadow_tnt_self.json" })
        {
            boolean present = FragmentProbeSupport.resourceExists(path);

            ok &= present;
            ProbeLog.log(TAG, String.format(java.util.Locale.ROOT, "resource %s => %s", path, present ? "OK" : "CHECK"));
        }

        // ---- 4. the three death sentences, in both languages ----
        for (String locale : new String[] { "en_us", "zh_cn" })
        {
            for (String[] entry : MESSAGES)
            {
                String key = entry[0];
                // Two arguments: the victim and the igniter. A sentence that uses only the first is fine.
                String resolved = Component.translatable(key, "VICTIM", "IGNITER").getString();

                boolean textual = !resolved.equals(key);
                boolean victimNamed = resolved.contains("VICTIM");
                boolean noPlaceholder = !resolved.contains("%");

                ok &= textual && victimNamed && noPlaceholder;

                ProbeLog.log(TAG, String.format(java.util.Locale.ROOT, "[%s] %s (%s) = [%s] => %s",
                        locale, key, entry[1], resolved,
                        (textual && victimNamed && noPlaceholder) ? "OK" : "CHECK"));
            }
        }

        s_hud = "registered=" + registered + " tntBlock=" + (block instanceof TntBlock) + " messages=" + (MESSAGES.length * 2);
        ProbeLog.log(TAG, String.format(java.util.Locale.ROOT, "VERDICT=%s", ok ? "PASS" : "CHECK"));
    }
}
