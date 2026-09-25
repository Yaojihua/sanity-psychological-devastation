package piloser.sanitypd.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Shadow weapon refinement: strengthens shadow swords and axes with <b>inner clumps</b> at an
 * anvil.
 *
 * <p>One Inner Clump ({@code sanitypd:inner_clump}) gives +1 level, and each level adds <b>+1</b>
 * psychic damage (sword +8 up to +17, axe +10 up to +19). The level is capped at
 * {@value #MAX_LEVEL} and costs a flat <b>1 experience level per level gained</b>, with no
 * experience penalty, so the price never grows with repeated use. The anvil takes no durability
 * damage, the result is a {@code copy()} of the original weapon so enchantments are fully preserved,
 * and holding shift consumes <b>as many clumps as the anvil accepts at once</b> - up to the remaining room
 * below {@value #MAX_LEVEL} and up to the vanilla {@value #MAX_PER_OPERATION} level limit per operation.
 * Only shadow weapons ({@link IShadowWeapon}) can be refined.
 *
 * <p>The level is stored as an int in the weapon's own NBT under {@value #NBT_LEVEL}. The registry is
 * untouched and no attribute modifier is added; values are computed when read, which keeps saves
 * compatible and leaves the vanilla attribute display alone. Every numeric helper lives here as the
 * single source of truth so that {@code SanityCombat} (damage) and {@code ItemTooltipHelper}
 * (tooltip) cannot compute different results.
 */
public final class ShadowRefinement
{
    /** Max refinement level. */
    public static final int MAX_LEVEL = 100;

    /** NBT key holding the refinement level. */
    public static final String NBT_LEVEL = "sanitypd:refine";

    /**
     * Max levels gained in one anvil operation, set by the <b>vanilla</b> anvil hard limit: the anvil refuses
     * to hand out a result whose cost reaches 40 ("Too Expensive!"), so a bulk operation is clamped here to
     * stay takeable. This is vanilla behaviour and is deliberately <b>not</b> overridden (round 28: a forced
     * pickup was tried and reverted as a compatibility risk); more levels need several operations, each
     * costing 1 experience per level with no penalty.
     */
    public static final int MAX_PER_OPERATION = 40;

    private ShadowRefinement() {}

    // ------------------------------------------------------------------ read / write

    /** Refinement level of the weapon (0 when there is no NBT or it is not a shadow weapon). */
    public static int level(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IShadowWeapon))
            return 0;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_LEVEL))
            return 0;

        int level = tag.getInt(NBT_LEVEL);
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** Sets the refinement level, clamped to [0, {@value #MAX_LEVEL}]. */
    public static void setLevel(ItemStack stack, int level)
    {
        if (stack == null || stack.isEmpty())
            return;

        int clamped = Math.max(0, Math.min(MAX_LEVEL, level));

        if (clamped <= 0)
        {
            // At level 0 drop the key entirely so saves are not littered with meaningless zeroes
            CompoundTag tag = stack.getTag();
            if (tag != null)
            {
                tag.remove(NBT_LEVEL);
                if (tag.isEmpty())
                    stack.setTag(null);
            }
            return;
        }

        stack.getOrCreateTag().putInt(NBT_LEVEL, clamped);
    }

    /** Levels still available (0 when maxed). */
    public static int remaining(ItemStack stack)
    {
        return MAX_LEVEL - level(stack);
    }

    public static boolean isMaxed(ItemStack stack)
    {
        return level(stack) >= MAX_LEVEL;
    }

    // ------------------------------------------------------------------ values

    /**
     * Extra psychic damage this weapon actually deals = base value + refinement level.
     *
     * <p>Example: a shadow sword with base 8 and +9 refinement deals <b>17</b>.
     */
    public static float psychicDamage(ItemStack stack, IShadowWeapon weapon)
    {
        if (weapon == null)
            return 0f;

        return weapon.shadowPsychicDamage() + level(stack);
    }
}
