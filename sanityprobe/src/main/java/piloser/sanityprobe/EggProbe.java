package piloser.sanityprobe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Group S (server side): the three inner-mob spawn eggs (probe v1.3.0).
 *
 * <p>A dedicated server can only answer part of the question. Item registration,
 * {@code SpawnEggItem.byId} reverse lookup, dispenser behaviour and lang key presence are
 * assertable; what the icon looks like, whether the tint recolours the texture and whether the
 * name renders as a raw key are not — those need a real client. One client launch therefore
 * leaves a written verdict on whether the three eggs work and are named correctly.
 *
 * <h2>Checks</h2>
 * <pre>
 *   [EGG-25]  models/item/&lt;id&gt;.json + item texture present in the jar (missing -> purple/black)
 *   [EGG-25]  lang keys (zh_cn + en_us) and the rendered name width (a raw key is wider)
 *   [EGG-25]  SpawnEggItem.byId(entityType) reverse lookup, EXT-OK / EXT-MISSING, VERDICT line
 * </pre>
 *
 * <p>Read-only: registers no gameplay content, changes no values, all entry points guarded.
 */
public final class EggProbe
{
    /** The three eggs: item registry name -> entity registry name. */
    private static final Map<String, String> EGGS = new LinkedHashMap<>();

    static
    {
        EGGS.put("rotting_stalker_spawn_egg", "rotting_stalker");
        EGGS.put("sneaking_terror_spawn_egg", "sneaking_terror");
        EGGS.put("screaming_crawler_spawn_egg", "screaming_crawler");
    }

    private static boolean s_serverReported;
    private static boolean s_clientReported;

    private EggProbe() {}

    // ================================================================= server: registration / model / lang / lookup

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (s_serverReported)
            return;

        s_serverReported = true;

        try
        {
            report(event.getServer());
        }
        catch (Throwable t)
        {
            ProbeLog.log("EGG-25", "server side threw: " + t);
        }
    }

    private static void report(MinecraftServer server)
    {
        int ok = 0;
        int bad = 0;

        for (Map.Entry<String, String> e : EGGS.entrySet())
        {
            String itemId = e.getKey();
            String entityId = e.getValue();

            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("sanitypd", itemId));
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("sanitypd", entityId));

            boolean itemOk = item != null;
            boolean typeOk = type != null;
            boolean isEgg = item instanceof SpawnEggItem;

            // Reverse lookup: EntityType -> egg. If this link is broken the egg does nothing.
            SpawnEggItem back = type == null ? null : SpawnEggItem.byId(type);
            boolean byIdOk = back == item;

            // Jar resources: model + texture. Check them through the classloader, not the server-side ResourceManager.
            boolean modelOk = jarHas("assets/sanitypd/models/item/" + itemId + ".json");
            boolean texOk = jarHas("assets/sanitypd/textures/item/" + itemId + ".png");

            // Lang keys inside the jar (one per language).
            boolean langZh = jarLangHas(server, "zh_cn", "item.sanitypd." + itemId);
            boolean langEn = jarLangHas(server, "en_us", "item.sanitypd." + itemId);

            boolean allOk = itemOk && typeOk && isEgg && byIdOk && modelOk && texOk && langZh && langEn;

            if (allOk)
                ok++;
            else
                bad++;

            ProbeLog.log("EGG-25", String.format(java.util.Locale.ROOT,
                    "%s  item=%s type=%s isSpawnEgg=%s byId=%s model=%s texture=%s langZh=%s langEn=%s",
                    itemId, itemOk, typeOk, isEgg, byIdOk, modelOk, texOk, langZh, langEn));
        }

        ProbeLog.log("EGG-25", "VERDICT=" + (bad == 0 ? "OK" : "SUSPECT")
                + "  eggs_ok=" + ok + "/" + EGGS.size() + "  bad=" + bad);
    }

    private static boolean jarHas(String resource)
    {
        try
        {
            return EggProbe.class.getClassLoader().getResource(resource) != null;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    private static boolean jarLangHas(MinecraftServer server, String loc, String key)
    {
        try (InputStreamReader r = new InputStreamReader(
                server.getResourceManager().getResource(new ResourceLocation("sanitypd", "lang/" + loc + ".json"))
                        .orElseThrow().open(), StandardCharsets.UTF_8))
        {
            return com.google.gson.JsonParser.parseReader(r).getAsJsonObject().has(key);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    // ================================================================= client: tint / icon / name

    /** Called from {@link ClientProbe} on the client tick (every 20 ticks, until it reports once). */
    static void clientSample(net.minecraft.client.Minecraft mc)
    {
        if (s_clientReported || mc.player == null || mc.level == null)
            return;

        try
        {
            s_clientReported = true;

            net.minecraft.client.gui.Font font = mc.font;

            for (Map.Entry<String, String> e : EGGS.entrySet())
            {
                String itemId = e.getKey();
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("sanitypd", itemId));

                if (item == null)
                {
                    ProbeLog.log("EGG-25-C", itemId + " EXT-MISSING (egg not in the client registry)");
                    continue;
                }

                ItemStack stack = new ItemStack(item);

                // Tint: Forge's ForgeSpawnEggItem registers two egg colours as item colours.
                // We supply 0xFFFFFF (the multiplicative identity), so both entries must read
                // exactly white. Anything else means the hand-drawn texture gets recoloured,
                // which is the root cause of an egg that "looks the wrong colour".
                int tint0 = itemColor(mc, stack, 0);
                int tint1 = itemColor(mc, stack, 1);
                boolean tintOk = (tint0 & 0xFFFFFF) == 0xFFFFFF && (tint1 & 0xFFFFFF) == 0xFFFFFF;

                // Icon: the sprite the model finally resolves to (missingno when the texture is absent).
                String sprite = "?";
                try
                {
                    net.minecraft.client.resources.model.BakedModel model =
                            mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                    sprite = String.valueOf(model.getParticleIcon().contents().name());
                }
                catch (Throwable t)
                {
                    sprite = "<err:" + t.getClass().getSimpleName() + ">";
                }

                // Name: a raw key and a translated name have different widths, so the width is used as the translated-or-not test.
                String rawKey = "item.sanitypd." + itemId;
                String shown = stack.getHoverName().getString();
                int wShown = font.width(shown);
                int wKey = font.width(rawKey);

                ProbeLog.log("EGG-25-C", String.format(java.util.Locale.ROOT,
                        "%s  name=\"%s\" width=%d (rawKeyWidth=%d) rawKeyShown=%s tint=%06X/%06X tintOk=%s sprite=%s",
                        itemId, shown, wShown, wKey, shown.equals(rawKey),
                        tint0 & 0xFFFFFF, tint1 & 0xFFFFFF, tintOk, sprite));
            }

            ProbeLog.log("EGG-25-C", "VERDICT=REPORTED (criteria: name is not the raw key, tint is all white, sprite is not missingno)");
        }
        catch (Throwable t)
        {
            ProbeLog.log("EGG-25-C", "client side threw: " + t);
        }
    }

    private static int itemColor(net.minecraft.client.Minecraft mc, ItemStack stack, int tintIndex)
    {
        try
        {
            // Read the item colours by reflection: the field name changes under obfuscation, so a
            // failure reports -1 instead of crashing.
            for (java.lang.reflect.Field f : net.minecraft.client.Minecraft.class.getDeclaredFields())
            {
                if (f.getType() == net.minecraft.client.color.item.ItemColors.class)
                {
                    f.setAccessible(true);
                    net.minecraft.client.color.item.ItemColors colors =
                            (net.minecraft.client.color.item.ItemColors) f.get(mc);

                    return colors == null ? -1 : colors.getColor(stack, tintIndex);
                }
            }
        }
        catch (Throwable t)
        {
            return -2;
        }

        return -3;
    }
}
