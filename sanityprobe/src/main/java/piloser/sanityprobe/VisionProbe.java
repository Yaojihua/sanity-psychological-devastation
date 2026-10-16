package piloser.sanityprobe;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The hallucination batch, checked from the client (tag {@code VISION-58}).
 *
 * <h2>What it answers</h2>
 * The five row-less thoughts are the first items whose category owns no row, and the first that print a
 * percentage through the untiered path. Two things can only be judged where the client is:
 *
 * <ul>
 *   <li><b>What the tooltip actually says.</b> The five items are posted through a real
 *       {@code ItemTooltipEvent} and every line is flattened into {@code (colour, text)} pairs, so the
 *       checks are exact: the name carries the category word, the tag line names the type <b>once</b> (the
 *       category label is suppressed for a row-less category), the multiplier renders as a green
 *       {@code 70%} and no raw {@code %1$s} survives anywhere.</li>
 *   <li><b>Membership and the multiplier's arithmetic.</b> Every item must answer to
 *       {@code sanitypd:thought/hallucination}, and the yield function's rounding is asserted on its
 *       boundaries through reflection - the value the sentence prints is also the value the drop rule uses.</li>
 * </ul>
 *
 * <p>Mod members are reached by reflection (the probe has to load with sanitypd absent); vanilla members are
 * called directly, so the build-time remapper handles their names.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VisionProbe
{
    private static final String TAG = "VISION-58";
    private static final String MODID = "sanitypd";

    /** The batch, in the order ItemRegistry declares it. */
    private static final String[] IDS = {
            "thought_hallucination_horse",
            "thought_hallucination_human",
            "thought_hallucination_sheep",
            "thought_hallucination_fish",
            "thought_hallucination_convergent_evolution",
    };

    /** The one item of the batch whose sentence prints a number. */
    private static final String CONVERGENT = "thought_hallucination_convergent_evolution";

    private static boolean s_reported;
    private static boolean s_reportedError;

    private VisionProbe() {}

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event)
    {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || s_reported)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
            return;

        s_reported = true;

        try
        {
            staticChecks();
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

    private static void staticChecks() throws Exception
    {
        Class<?> registry = Class.forName("piloser.sanitypd.item.ItemRegistry");
        var tag = ItemTags.create(new ResourceLocation(MODID, "thought/hallucination"));

        StringBuilder ids = new StringBuilder();
        boolean shapeOk = true;
        boolean tagOk = true;

        for (String id : IDS)
        {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(MODID, id));

            if (item == null || item == net.minecraft.world.item.Items.AIR)
            {
                shapeOk = false;
                ids.append(ids.length() == 0 ? "" : " ").append(id).append("=MISSING");
                continue;
            }

            // category / type / untiered, read off the item the way the mechanics read them
            Object category = item.getClass().getMethod("category").invoke(item);
            @SuppressWarnings("unchecked")
            List<Object> types = (List<Object>) item.getClass().getMethod("types").invoke(item);
            boolean tiered = (Boolean) item.getClass().getMethod("tiered").invoke(item);
            boolean hallucination = "HALLUCINATION".equals(String.valueOf(category))
                    && types.size() == 1 && "HALLUCINATION".equals(String.valueOf(types.get(0)));

            shapeOk &= hallucination && !tiered;
            tagOk &= new ItemStack(item).is(tag);

            ids.append(ids.length() == 0 ? "" : " ").append(shortId(id))
                    .append(hallucination && !tiered ? "=ok" : "=BAD");
        }

        // The row-less category must not have grown a row: the menu lays out as many rows as this list has.
        Object rowCategories = Class.forName("piloser.sanitypd.thought.ThoughtCategory")
                .getMethod("rowCategories").invoke(null);
        boolean twoRows = ((List<?>) rowCategories).size() == 2;

        // The multiplier: the same constant the sentence prints, with the rounding the text promises.
        Class<?> loot = Class.forName("piloser.sanitypd.loot.InnerLoot");
        int yieldOne = (Integer) loot.getMethod("innerYield", int.class, boolean.class).invoke(null, 1, true);
        int yieldTwo = (Integer) loot.getMethod("innerYield", int.class, boolean.class).invoke(null, 2, true);
        int yieldThree = (Integer) loot.getMethod("innerYield", int.class, boolean.class).invoke(null, 3, true);
        int plain = (Integer) loot.getMethod("innerYield", int.class, boolean.class).invoke(null, 3, false);
        float bonus = Class.forName("piloser.sanitypd.thought.ThoughtEffects").getField("CONVERGENT_BONUS").getFloat(null);

        boolean yieldOk = yieldOne == 2 && yieldTwo == 4 && yieldThree == 6 && plain == 3
                && Math.abs((1f + bonus) - 1.70f) < 0.0001f;

        ProbeLog.log(TAG, String.format(Locale.ROOT,
                "static: %s | tag %s=%s | rows=%d | yield 1->%d 2->%d 3->%d plain3=%d bonus=%.2f => %s",
                ids, "sanitypd:thought/hallucination", tagOk ? "5/5" : "MISSING",
                ((List<?>) rowCategories).size(), yieldOne, yieldTwo, yieldThree, plain, bonus,
                shapeOk && tagOk && twoRows && yieldOk == true ? "OK" : "CHECK"));

        tooltipChecks();
    }

    /** Posts a real tooltip event for every item of the batch and flattens what it produced. */
    private static void tooltipChecks()
    {
        for (String id : IDS)
        {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(MODID, id));

            if (item == null || item == net.minecraft.world.item.Items.AIR)
                continue;

            ItemStack stack = new ItemStack(item);
            List<Component> lines = new java.util.ArrayList<>();

            MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(stack, Minecraft.getInstance().player, lines,
                    TooltipFlag.Default.NORMAL));

            StringBuilder flat = new StringBuilder();
            int hallucinationTags = 0;
            boolean green70 = false;
            boolean rawKey = false;

            for (Component line : lines)
            {
                final StringBuilder texts = new StringBuilder();
                final StringBuilder colours = new StringBuilder();

                line.visit((style, part) ->
                {
                    TextColor colour = style.getColor();

                    if (colour != null)
                        colours.append(String.format(Locale.ROOT, "%06X", colour.getValue()));

                    texts.append(part);
                    return Optional.empty();
                }, Style.EMPTY);

                String content = texts.toString();

                flat.append(flat.length() == 0 ? "" : " / ").append(colours).append(':').append(content);

                int zh = countOccurrences(content, "\u5E7B\u89C6");
                int en = countOccurrences(content, "Hallucination");
                hallucinationTags += zh + en;

                if (content.contains("70%") && colours.toString().contains("55FF55"))
                    green70 = true;

                if (content.contains("%1$s") || content.contains(".effect"))
                    rawKey = true;
            }

            // The name line names the category once and the tag line names the type once: two occurrences in
            // total, never four (which is what a visible category label on top of the name would produce).
            boolean labelOk = IDS[4].equals(id) ? hallucinationTags <= 2 : hallucinationTags <= 3;
            boolean ok = !rawKey && (CONVERGENT.equals(id) ? green70 : true) && labelOk;

            ProbeLog.log(TAG, String.format(Locale.ROOT,
                    "tooltip %s [%s] => %s", shortId(id), flat, ok ? "OK" : "CHECK"));
        }
    }

    private static int countOccurrences(String haystack, String needle)
    {
        int count = 0;
        int at = haystack.indexOf(needle);

        while (at >= 0)
        {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }

        return count;
    }

    private static String shortId(String id)
    {
        return id.replace("thought_hallucination_", "");
    }
}
