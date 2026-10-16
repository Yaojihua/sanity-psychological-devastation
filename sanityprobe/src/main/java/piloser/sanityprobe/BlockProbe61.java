package piloser.sanityprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The thought transcription table, checked from the client (tag {@code BLOCK-61}).
 *
 * <h2>What it answers</h2>
 * This is the mod's first block, and a block has failure modes an item does not: a missing blockstate, a
 * block model whose texture paths are wrong, a loot table that was never written (the block places and
 * mines and simply leaves nothing behind), and {@code requiresCorrectToolForDrops} without a mineable tag
 * (no tool is ever "correct", so it also never drops). None of those raise an error anywhere - the block
 * just behaves wrongly - so each one is asserted here.
 *
 * <p>The property line does <b>not</b> compare against numbers written down in this file. The owner asked
 * for the enchanting table's properties, so the probe compares the mod's block with
 * {@code Blocks.ENCHANTING_TABLE} field by field: if either side ever moves, the line says so instead of
 * quietly agreeing with a stale copy.
 *
 * <h2>Conventions</h2>
 * Mod members are reached by reflection and vanilla members are called directly, so the build-time
 * remapper handles the vanilla names. A literal method name is not a name that survives remapping, which is
 * how an earlier version of a probe group ended up asking for a method that only existed in development.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlockProbe61
{
    private static final String TAG = "BLOCK-61";
    private static final String MODID = "sanitypd";
    private static final String ID = "thought_transcription_table";

    /** The two language keys this block carries: its name (from the block) and its permanent detail line. */
    private static final String DESC = "block." + MODID + "." + ID;
    private static final String TOOLTIP = DESC + ".tooltip";

    /** Model cuboids and distinct textures the imported Blockbench project produced. */
    private static final int ELEMENTS = 13;
    private static final int TEXTURES = 13;

    private static boolean s_reported;
    private static boolean s_reportedError;

    private BlockProbe61() {}

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
            propertiesLine(mc);
            itemLine();
            resourceLine();
        }
        catch (Throwable t)
        {
            if (!s_reportedError)
            {
                s_reportedError = true;
                ProbeLog.log(TAG, "the check could not run (expected when sanitypd is absent): " + t);
            }
        }
    }

 /** Properties, each one compared with the vanilla enchanting table it was required it to match. */
    private static void propertiesLine(Minecraft mc) throws Exception
    {
        Block ours = BuiltInRegistries.BLOCK.get(new ResourceLocation(MODID, ID));
        BlockState mine = ours.defaultBlockState();
        BlockState vanilla = Blocks.ENCHANTING_TABLE.defaultBlockState();
        BlockPos pos = mc.player.blockPosition();

        float hardnessMine = mine.getDestroySpeed(mc.level, pos);
        float hardnessVanilla = vanilla.getDestroySpeed(mc.level, pos);
        float resistMine = ours.getExplosionResistance();
        float resistVanilla = Blocks.ENCHANTING_TABLE.getExplosionResistance();
        int lightMine = mine.getLightEmission();
        int lightVanilla = vanilla.getLightEmission();
        boolean toolMine = mine.requiresCorrectToolForDrops();
        boolean toolVanilla = vanilla.requiresCorrectToolForDrops();
        boolean soundSame = mine.getSoundType() == vanilla.getSoundType();
        double topMine = mine.getShape(mc.level, pos).max(Direction.Axis.Y);
        double topVanilla = vanilla.getShape(mc.level, pos).max(Direction.Axis.Y);
        boolean pickaxe = mine.is(BlockTags.MINEABLE_WITH_PICKAXE);

        boolean same = Math.abs(hardnessMine - hardnessVanilla) < 1.0E-4f
                && Math.abs(resistMine - resistVanilla) < 1.0E-4f
                && lightMine == lightVanilla
                && toolMine == toolVanilla
                && soundSame
                && pickaxe;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "properties: hardness %.2f/%.2f resistance %.1f/%.1f light %d/%d tool %s/%s"
                        + " sound %s shapeTop %.4f/%.4f pickaxeTag=%s => %s",
                hardnessMine, hardnessVanilla, resistMine, resistVanilla, lightMine, lightVanilla,
                toolMine, toolVanilla, soundSame ? "same" : "DIFFERENT", topMine, topVanilla, pickaxe,
                same ? "OK" : "CHECK"));
    }

    /** The item form: the block's own name key, and the grey detail line as the client really draws it. */
    private static void itemLine()
    {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(MODID, ID));
        String descId = item.getDescriptionId();
        boolean blockItem = item instanceof BlockItem;
        String name = Component.translatable(DESC).getString();
        String tooltip = Component.translatable(TOOLTIP).getString();
        boolean nameResolves = !name.equals(DESC);
        boolean tooltipResolves = !tooltip.equals(TOOLTIP);

        // The grey is carried by the language value itself (the section-sign prefix), the way every other
        // grey line in this mod carries it, so it is checked on the string.
        boolean grey = tooltip.startsWith("\u00A77");

        // getTooltipLines is the complete path: it runs appendHoverText, which is where this line is added.
        List<String> spans = FragmentProbeSupport.tooltipSpans(item);
        boolean inTooltip = FragmentProbeSupport.anyContains(spans, tooltip);

        boolean ok = blockItem && DESC.equals(descId) && nameResolves && tooltipResolves && grey && inTooltip;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "item: descId=%s blockItem=%s name=\"%s\" grey=%s resolves name/tooltip=%s/%s tooltip=[%s] => %s",
                descId, blockItem, name, grey, nameResolves, tooltipResolves,
                FragmentProbeSupport.join(spans), ok ? "OK" : "CHECK"));
    }

    /** Everything a block needs on disk, read out of the mod's own file system. */
    private static void resourceLine()
    {
        String blockstate = FragmentProbeSupport.resourceText("assets/" + MODID + "/blockstates/" + ID + ".json");
        String model = FragmentProbeSupport.resourceText("assets/" + MODID + "/models/block/" + ID + ".json");
        String itemModel = FragmentProbeSupport.resourceText("assets/" + MODID + "/models/item/" + ID + ".json");
        String recipe = FragmentProbeSupport.resourceText("data/" + MODID + "/recipes/" + ID + ".json");
        String loot = FragmentProbeSupport.resourceText("data/" + MODID + "/loot_tables/blocks/" + ID + ".json");
        String tag = FragmentProbeSupport.resourceText("data/minecraft/tags/blocks/mineable/pickaxe.json");

        boolean stateOk = blockstate != null && blockstate.contains(MODID + ":block/" + ID);
        boolean itemModelOk = itemModel != null && itemModel.contains(MODID + ":block/" + ID);
        boolean lootOk = loot != null && loot.contains(MODID + ":" + ID);
        boolean tagOk = tag != null && tag.contains(MODID + ":" + ID);

        int elements = 0;
        int textures = 0;
        int present = 0;
        int uvFaces = 0;
        int uvBad = 0;
        boolean recipeOk = false;

        try
        {
            JsonObject json = JsonParser.parseString(model).getAsJsonObject();
            elements = json.getAsJsonArray("elements").size();

            List<String> files = new ArrayList<>();

            for (var entry : json.getAsJsonObject("textures").entrySet())
            {
                String ref = entry.getValue().getAsString();
                String rel = ref.contains(":") ? ref.split(":")[1] : ref;

                if (!files.contains(rel))
                    files.add(rel);
            }

            textures = files.size();

            for (String rel : files)
                if (FragmentProbeSupport.resourceExists("assets/" + MODID + "/textures/" + rel + ".png"))
                    present++;

            // ⚠️ The uv space of a block model is 0..16 whatever the texture file's pixel size is, so a box
            // that reaches past 16 samples the neighbouring entries of the block atlas and the block renders
            // as a patchwork of other blocks' textures. That is exactly what shipped once, so it is asserted
            // here as well as in the build-time audit.
            for (var element : json.getAsJsonArray("elements"))
            {
                JsonObject el = element.getAsJsonObject();
                double w = el.getAsJsonArray("to").get(0).getAsDouble() - el.getAsJsonArray("from").get(0).getAsDouble();
                double h = el.getAsJsonArray("to").get(1).getAsDouble() - el.getAsJsonArray("from").get(1).getAsDouble();
                double d = el.getAsJsonArray("to").get(2).getAsDouble() - el.getAsJsonArray("from").get(2).getAsDouble();

                for (var faceEntry : el.getAsJsonObject("faces").entrySet())
                {
                    uvFaces++;

                    String face = faceEntry.getKey();
                    var uv = faceEntry.getValue().getAsJsonObject().getAsJsonArray("uv");
                    double u1 = uv.get(0).getAsDouble();
                    double v1 = uv.get(1).getAsDouble();
                    double u2 = uv.get(2).getAsDouble();
                    double v2 = uv.get(3).getAsDouble();
                    boolean horizontal = face.equals("up") || face.equals("down");
                    double faceW = horizontal ? w : (face.equals("north") || face.equals("south")) ? w : d;
                    double faceH = horizontal ? d : h;

                    if (u2 <= u1 || v2 <= v1 || u1 < 0.0D || v1 < 0.0D || u2 > 16.0D || v2 > 16.0D
                            || Math.abs((u2 - u1) / (v2 - v1) - faceW / faceH) > 0.02D * Math.max(1.0D, faceW / faceH))
                        uvBad++;
                }
            }
        }
        catch (Throwable ignored)
        {
            // Reported through the numbers below.
        }

        try
        {
            JsonObject json = JsonParser.parseString(recipe).getAsJsonObject();
            recipeOk = json.getAsJsonObject("result").get("item").getAsString().equals(MODID + ":" + ID)
                    && json.getAsJsonArray("pattern").size() == 3;
        }
        catch (Throwable ignored)
        {
            // Reported through recipeOk.
        }

        boolean ok = stateOk && model != null && itemModelOk && elements == ELEMENTS && textures == TEXTURES
                && present == TEXTURES && uvBad == 0 && recipeOk && lootOk && tagOk;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "resources: blockstate=%s model=%s(elements=%d textures=%d/%d present uv=%d/%d in 0..16)"
                        + " itemModel=%s recipe=%s loot=%s pickaxeTag=%s => %s",
                stateOk, model != null, elements, present, textures, uvFaces - uvBad, uvFaces, itemModelOk,
                recipeOk, lootOk, tagOk, ok ? "OK" : "CHECK"));
    }
}
