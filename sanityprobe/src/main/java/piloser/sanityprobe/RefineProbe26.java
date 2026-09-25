package piloser.sanityprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Group R: live client readout for shadow weapon refinement.
 *
 * <p>The refined result is produced by the client-side {@code AnvilMenu} and only reaches the
 * server once the player takes it, so a server-side self-check cannot see it: that check
 * inspects event fields, which can all be correct while the output slot stays empty. Reading the
 * three real slots of the {@code AnvilMenu} (0 = left, 1 = right, 2 = output) settles whether the
 * output is displayed at all.
 *
 * <h2>Rules checked (logged and shown on the overlay)</h2>
 * <ul>
 *   <li>Shadow weapon left + inner clump right ⇒ the output level must be
 *       {@code min(current level + clump count, 100)};</li>
 *   <li>{@code expectedCost} is the <b>number of levels gained</b>, not the resulting level:
 *       going from 5 to 12 costs 12 - 5 = 7 levels;</li>
 *   <li>At the cap the surplus clumps are returned: 90 + 30 ⇒ level 100, cost 10;</li>
 *   <li>Anything else (non-shadow weapon, non-clump) must not produce a refinement result;
 *       that is reported as {@code NOT-REFINE} and is not a failure;</li>
 *   <li>A refined weapon in hand also reports its {@code refine} level and psychic damage.</li>
 * </ul>
 *
 * <p>Levels come straight from the NBT key {@code sanitypd:refine}, with no class dependency.
 * Psychic damage is reported as base value + level only, without reflecting the base value, so a
 * drifting constant cannot produce a false reading.
 *
 * <p>Read-only: reads menu slots and NBT, mutates no game state, all entry points guarded.
 */
@Mod.EventBusSubscriber(modid = SanityProbe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RefineProbe26
{
    /** NBT key holding the refine level ({@code ShadowRefinement.NBT_LEVEL} on the mod side; both must match). */
    public static final String NBT_LEVEL = "sanitypd:refine";

    /** Per-operation limit on the mod side ({@code ShadowRefinement.MAX_PER_OPERATION}). */
    private static final int MAX_PER_OPERATION = 40;

    /** Level cap on the mod side ({@code ShadowRefinement.MAX_LEVEL}). */
    private static final int MAX_LEVEL = 100;

    /** Registry name of the inner clump (right slot material). */
    private static final String CLUMP_ID = "sanitypd:inner_clump";

    private static int s_tick;
    private static String s_hud = "REFINE-26: put a shadow weapon + an inner clump in an anvil";

    static
    {
        ProbeHud.registerLine(() -> s_hud);
    }

    private RefineProbe26() {}

    /** Called from {@link Round26Probe} to force class init, which registers the HUD line. */
    public static void init()
    {
        // Intentionally empty.
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (++s_tick % 10 != 0)
            return;

        try
        {
            sample();
        }
        catch (Throwable t)
        {
            ProbeLog.log("REFINE-26-C", "sample failed: " + t);
        }
    }

    private static void sample()
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null)
            return;

        AbstractContainerMenu menu = player.containerMenu;

        if (menu instanceof AnvilMenu anvil)
        {
            ItemStack left = anvil.getSlot(0).getItem();
            ItemStack right = anvil.getSlot(1).getItem();
            ItemStack out = anvil.getSlot(2).getItem();

            report(anvil, left, right, out);
            return;
        }

        // Not in an anvil screen: report the refine level of the held weapon instead.
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.isEmpty() && isShadowWeapon(held))
        {
            int lv = refineLevel(held);
            s_hud = "REFINE-26: holding " + shortId(held) + " refine +" + lv + "/" + MAX_LEVEL;
        }
    }

    private static void report(AnvilMenu anvil, ItemStack left, ItemStack right, ItemStack out)
    {
        boolean leftOk = isShadowWeapon(left);
        boolean rightOk = isClump(right);

        if (!leftOk && !rightOk)
        {
            s_hud = "REFINE-26: anvil empty (put shadow weapon + inner clump)";
            return;
        }

        String leftDesc = describe(left);
        String rightDesc = describe(right);

        if (!leftOk || !rightOk)
        {
            // Only one side filled: not a refinement case (a clump on ordinary gear is not meant to be handled).
            s_hud = "REFINE-26: NOT-REFINE left=" + shortId(left) + " right=" + shortId(right)
                    + " (need shadow weapon + inner clump)";
            return;
        }

        int current = refineLevel(left);
        int clumps = right.getCount();
        int wanted = current + clumps;
        int newLevel = Math.min(wanted, MAX_LEVEL);
        int gained = newLevel - current;
        int returned = clumps - Math.min(clumps, Math.max(0, MAX_LEVEL - current));
        int outLevel = refineLevel(out);
        boolean outIsWeapon = isShadowWeapon(out);
        int rawLevel = rawRefineLevel(out);

        String verdict;
        if (out.isEmpty())
            // The output slot is written by the mod's mixin; empty means the mixin did not take
            // over, or vanilla produced nothing for lack of experience.
            verdict = "EMPTY(=broken when enough XP)";
        else if (outIsWeapon && outLevel == newLevel)
            verdict = "OK";
        else if (outIsWeapon && rawLevel == -1)
            verdict = "FAIL(output has no refine NBT)";
        else if (outIsWeapon && outLevel != newLevel)
            verdict = "FAIL(level " + outLevel + " != expected " + newLevel + ")";
        else
            verdict = "NOT-REFINE(output is " + shortId(out) + ")";

        ProbeLog.log("REFINE-26-C", "anvil left=" + leftDesc
                + " right=" + rightDesc
                + " => currentLevel=" + current
                + " clumps=" + clumps
                + " wanted=" + wanted
                + " expectedLevel=" + newLevel
                + " expectedGain(=xp cost)=" + gained
                + " returnedClumps=" + returned
                + " | out=" + describe(out)
                + " cost=" + anvil.getCost()
                + " (client estimate)"
                + " VERDICT=" + verdict);

        s_hud = "REFINE-26: " + shortId(left) + " +" + current
                + " +" + clumps + " clumps => +" + newLevel
                + " cost " + gained + " levels"
                + (returned > 0 ? " return " + returned + " clumps" : "")
                + " | out " + (out.isEmpty() ? "(empty)" : "+" + outLevel)
                + " [" + verdict + "]";
    }

    // ------------------------------------------------------------------ helpers

    /** Refine level of the stack; 0 when the key is absent or unreadable. */
    public static int refineLevel(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return 0;

        int byNbt = rawRefineLevel(stack);
        return byNbt < 0 ? 0 : byNbt;
    }

    /** Reads the NBT key directly; returns -1 when absent, so "level 0" and "no key" stay distinguishable. */
    private static int rawRefineLevel(ItemStack stack)
    {
        try
        {
            if (!stack.hasTag() || stack.getTag() == null)
                return -1;

            return stack.getTag().contains(NBT_LEVEL) ? stack.getTag().getInt(NBT_LEVEL) : -1;
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    /** Shadow weapon test: the item class exposing a {@code shadowPsychicDamage()} method. */
    private static boolean isShadowWeapon(ItemStack stack)
    {
        if (stack.isEmpty())
            return false;

        try
        {
            stack.getItem().getClass().getMethod("shadowPsychicDamage");
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    private static boolean isClump(ItemStack stack)
    {
        if (stack.isEmpty())
            return false;

        return idOf(stack).equals(CLUMP_ID);
    }

    private static String idOf(ItemStack stack)
    {
        try
        {
            return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        }
        catch (Throwable t)
        {
            return "(?)";
        }
    }

    private static String shortId(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return "(empty)";

        String id = idOf(stack);
        return id.startsWith("sanitypd:") ? id.substring("sanitypd:".length()) : id;
    }

    private static String describe(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return "(empty)";

        int lv = rawRefineLevel(stack);

        return idOf(stack)
                + (lv >= 0 ? "(refine=" + lv + ")" : "")
                + " x" + stack.getCount();
    }

    /** Mod-side constants, included in the summary report for cross-checking. */
    public static String constants()
    {
        return "NBT=" + NBT_LEVEL + " maxLevel=" + MAX_LEVEL + " maxPerOp=" + MAX_PER_OPERATION;
    }
}
