package piloser.sanitypd.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import piloser.sanitypd.item.IShadowWeapon;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.item.ShadowRefinement;

/**
 * Iron anvil takeover point for shadow weapon refinement.
 *
 * <h2>Why a mixin instead of {@code AnvilUpdateEvent}</h2>
 * The first implementation used {@code AnvilUpdateEvent} with {@code setOutput(refined sword)} plus
 * {@code setCost(n)}, {@code setMaterialCost(n)} and {@code setCanceled(true)}. Every event field
 * looked correct ({@code TAKEOVER levels=3 outLevel=3 canceled=true}) but the anvil never showed a
 * result, and tracing the chain with a real {@code AnvilMenu} showed
 * {@code ret=false container=#0=EMPTY menuCost=1} after the hook. Cancelling
 * {@code AnvilUpdateEvent} therefore does not write the result into the anvil. Refinement instead
 * decides for itself at the very start of {@code createResult()} and writes the result straight into
 * the result slot ({@code AnvilMenu.getSlot(2).set(...)}; {@code Slot#set} is public API and needs no
 * accessor), then calls {@code ci.cancel()} to suppress the vanilla logic.
 *
 * <p>It hooks HEAD rather than TAIL because only HEAD can claim the operation before vanilla runs. At
 * TAIL vanilla has already computed an empty or unrelated result slot that would have to be
 * overwritten, and its experience penalty and anvil damage rolls would have run for nothing.
 *
 * <p>All values come from {@link ShadowRefinement}, the single source of truth; this class only
 * decides whether to take over and what to write.
 */
@Mixin(AnvilMenu.class)
public abstract class MixinAnvilMenu
{
    /**
     * Refinement takeover: shadow weapon in the left slot plus inner clump in the right slot writes
     * the result into the result slot and suppresses the vanilla logic.
     *
     * <p>The result is a {@code copy()} of the original weapon (enchantments, durability and name all
     * kept) with a raised refinement level. Experience cost and material consumption both equal the
     * number of levels gained (1 point each, no vanilla experience penalty), and the gain is clamped to the
     * vanilla 40 level anvil limit so the result stays takeable (see {@code sanitypd$refine}). At max level or
     * with no clumps the gain is 0, so the mixin does not take over and vanilla runs instead; the anvil then
     * produces nothing and no clumps are consumed.
     */
    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void sanitypd$refineAtAnvil(CallbackInfo ci)
    {
        AnvilMenu self = (AnvilMenu) (Object) this;

        ItemStack left = self.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        ItemStack right = self.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();

        // Only the "shadow weapon + inner clump" combination is handled; everything else falls
        // through to vanilla
        if (!(left.getItem() instanceof IShadowWeapon))
            return;
        if (!right.is(ItemRegistry.INNER_CLUMP.get()))
            return;

        int current = ShadowRefinement.level(left);
        int levels = sanitypd$refine(right.getCount(), current);

        if (levels <= 0)
            return;

        ItemStack out = left.copy();
        ShadowRefinement.setLevel(out, current + levels);

        // Writing the result slot directly (Slot#set is public) is the step that cancelling the
        // event could not accomplish
        self.getSlot(AnvilMenu.RESULT_SLOT).set(out);

        // Both the experience cost and the consumed amount are reported as the number of levels
        // gained, and vanilla onTake charges accordingly.
        // Do not report 0 here: onTake uses this cost to charge experience.
        // "No anvil damage" is handled by EventHandler#onAnvilRepairRefined, which zeroes breakChance.
        self.setMaximumCost(levels);
        self.repairItemCountCost = levels;


        ci.cancel();
    }

    /**
     * Levels gained this operation = min(clump count, remaining room below the level cap, vanilla 40 limit).
     *
     * <p>The 40 level ceiling is <b>vanilla</b>, not this mod's invention: the anvil refuses to hand out a
     * result whose cost reaches 40 (the "Too Expensive!" state in {@code ItemCombinerMenu#mayPickup}, which
     * {@code AnvilMenu} implements). Clamping here is deliberate:
     * <ul>
     *   <li>the operation stays takeable, instead of producing an untakeable result (the round-28 report
     *       "over 40 shows Too Expensive" was exactly that);</li>
     *   <li>nothing in the vanilla anvil is overridden, so other mods and vanilla behaviour are untouched
     *       (a forced pickup would be a compatibility risk).</li>
     * </ul>
     * More levels simply need more operations, each costing 1 experience per level with no penalty.
     *
     * @param clumpCount inner clumps in the right slot (all of them when shift-clicking in bulk)
     * @param current current refinement level
     * @return 0 when refinement is not possible (max level or no clumps)
     */
    private static int sanitypd$refine(int clumpCount, int current)
    {
        int room = ShadowRefinement.MAX_LEVEL - current;
        if (room <= 0 || clumpCount <= 0)
            return 0;

        return Math.min(Math.min(clumpCount, room), ShadowRefinement.MAX_PER_OPERATION);
    }
}
