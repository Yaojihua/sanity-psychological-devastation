package piloser.sanitypd.thought;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The "empowered" state of a thought: the mark the transcription table's upgrade puts on a stack.
 *
 * <p>The rule is that an upgraded thought "ignores the stacking ladder and simply acts at its top tier",
 * and that this must be a change of <b>state</b>, not a new item ("do not add an item, change the item's
 * state"). A new item could not have worked anyway: the tier is derived from how many copies the chain holds
 * ({@code TIER_THRESHOLDS = {1,3,5}}), and a brand-new item would arrive with exactly one copy - still tier one.
 *
 * <p>The mark is a plain NBT boolean, because this is 1.20.1 and data components do not exist yet. Two stacks
 * that differ in NBT never merge in vanilla, so an empowered thought cannot silently blend back into a pile of
 * ordinary ones.
 */
public final class EmpoweredThought
{
    /** The NBT key. Namespaced so it cannot collide with another mod's tag. */
    public static final String TAG = "sanitypd:empowered";

    private EmpoweredThought() {}

    public static boolean is(ItemStack stack)
    {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(TAG);
    }

    /** Marks a stack in place and returns it, so callers can write {@code return empower(stack);}. */
    public static ItemStack empower(ItemStack stack)
    {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG, true);

        return stack;
    }
}
