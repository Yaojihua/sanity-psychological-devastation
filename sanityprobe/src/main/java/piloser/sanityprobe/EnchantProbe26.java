package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.TreeMap;

/**
 * Client-side readout for the psychic protection enchantment.
 *
 * <h2>Why it exists</h2>
 * The damage reduction is computed inside the sanitypd combat code while psychic damage is
 * being resolved, so nothing about it is visible in the UI. This probe puts the effective
 * level and the resulting factor on screen, e.g.
 * <pre>
 * [ENCH-26-C] levels=8 points=12.0 factor=0.52
 * </pre>
 *
 * <h2>Verdict</h2>
 * <ul>
 *   <li>With {@code levels > 0} the printed {@code factor} must equal
 *       {@code 1 - min(points / 25, CAP)}.</li>
 *   <li>{@code POINTS_PER_LEVEL} is read reflectively from the sanitypd constant, so if the
 *       constant is ever changed this line changes with it instead of silently agreeing.</li>
 *   <li>The per-level value reported by the real enchantment object is printed next to it as
 *       a cross-check -- the two disagreeing means the constant drifted.</li>
 * </ul>
 *
 * <p>Read-only: reads the player's armor and the constant, mutates no game state.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EnchantProbe26
{
    /** Cap on the reduction fraction used when the probe recomputes the factor. */
    private static final float CAP = 0.80f;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static int s_tick;
    private static String s_hud;
    private static boolean s_announced;

    static
    {
        ProbeHud.registerLine(() -> s_hud);
    }

    private EnchantProbe26() {}

    /** Called by {@link ClientProbe} on the first tick to force initialization, which registers the overlay line. */
    public static void init()
    {
        // Intentionally empty: initialization happens in the static block.
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 20 != 0)
            return;

        try
        {
            sample();
        }
        catch (Throwable t)
        {
            ProbeLog.log("ENCH-26-C", "sample failed: " + t);
        }
    }

    private static void sample()
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null)
            return;

        int levels = 0;
        Map<String, Integer> perSlot = new TreeMap<>();

        for (EquipmentSlot slot : ARMOR)
        {
            ItemStack stack = player.getItemBySlot(slot);
            int lv = enchLevelOf(stack);
            if (lv > 0)
            {
                perSlot.put(slot.getName(), lv);
                levels += lv;
            }
        }

        float pointsPerLevel = reflectPointsPerLevel();
        float points = levels * pointsPerLevel;
        float factor = 1f - Math.min(points / 25f, CAP);

        // Authoritative value: what the enchantment itself reports for level 1.
        int jsonPointsLv1 = queryEnchantmentPoints();

        if (!s_announced || levels > 0)
        {
            s_announced = true;
            ProbeLog.log("ENCH-26-C", "levels=" + levels
                    + " perSlot=" + (perSlot.isEmpty() ? "-" : perSlot)
                    + " points=" + ProbeLog.fmt(points) + " (per level " + ProbeLog.fmt(pointsPerLevel) + ")"
                    + " factor=" + ProbeLog.fmt(factor) + " (effective " + ProbeLog.fmt(factor * 100f) + "%, i.e. -"
                    + ProbeLog.fmt((1f - factor) * 100f) + "%)"
                    + " cap=" + ProbeLog.fmt(CAP)
                    + " | items.json pointsPerLevel=" + jsonPointsLv1
                    + (jsonPointsLv1 == (int) pointsPerLevel ? " (matches constant ✓)" : " ⚠️constant mismatch (drift)"));
        }

        s_hud = "ENCH-26: psychic protection Lv" + levels + " -> -" + ProbeLog.fmt((1f - factor) * 100f) + "%"
                + (levels > 0 ? " " + perSlot : "(not enchanted)");
    }

    /** Enchantment level on the stack, looked up by registry name (no compile-time dependency on sanitypd). */
    private static int enchLevelOf(ItemStack stack)
    {
        if (stack.isEmpty())
            return 0;

        try
        {
            Enchantment ench = stack.getAllEnchantments().keySet().stream()
                    .filter(e -> {
                        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(e);
                        return id != null && id.toString().equals("sanitypd:psychic_protection");
                    })
                    .findFirst()
                    .orElse(null);

            return ench == null ? 0 : stack.getEnchantmentLevel(ench);
        }
        catch (Throwable t)
        {
            return 0;
        }
    }

    /** Reflectively reads {@code PsychicProtectionEnchantment.POINTS_PER_LEVEL} so this readout follows the constant. */
    private static float reflectPointsPerLevel()
    {
        try
        {
            Class<?> c = Class.forName("piloser.sanitypd.enchantment.PsychicProtectionEnchantment");
            return c.getField("POINTS_PER_LEVEL").getFloat(null);
        }
        catch (Throwable t)
        {
            return -1f;     // mod absent or field gone -> -1 makes the anomaly obvious in the log
        }
    }

    /**
     * Asks the real enchantment object how many points level 1 grants, as a cross-check on the constant.
     *
     * <p>WARNING: the damage source must be an actual psychic damage source. This enchantment is
     * designed to return 0 for ordinary damage, so querying it with {@code damageSources().generic()}
     * would always yield 0 and prove nothing. There is no compile-time dependency on sanitypd, so
     * {@code SanityDamageTypes.psychic(Level, Entity)} is invoked reflectively.
     */
    private static int queryEnchantmentPoints()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            Enchantment ench = mc.level.registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .get(new ResourceLocation("sanitypd", "psychic_protection"));

            if (ench == null)
                return -1;

            Class<?> types = Class.forName("piloser.sanitypd.damage.SanityDamageTypes");
            Object psychic = types.getMethod("psychic", net.minecraft.world.level.Level.class,
                    net.minecraft.world.entity.Entity.class).invoke(null, mc.level, null);

            return ench.getDamageProtection(1, (net.minecraft.world.damagesource.DamageSource) psychic);
        }
        catch (Throwable t)
        {
            return -2;
        }
    }
}
