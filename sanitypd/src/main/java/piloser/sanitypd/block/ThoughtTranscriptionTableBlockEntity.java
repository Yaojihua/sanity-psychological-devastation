package piloser.sanitypd.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The thought transcription table's inventory: six slots, kept inside the block so closing the screen loses
 * nothing (the design requires a container, not a one-shot exchange).
 *
 * <p>Slot map, in the order the screen draws them. The numbers live here and the menu reads them, so the slots
 * and the recesses on the texture cannot drift apart:
 *
 * <pre>
 *   0 1 2   three material slots, anything may go in
 *       3   the inner core slot, the one above the arrow
 *       4   the thought slot: a thought container, a thought, or a memory fragment
 *       5   the output, which the player may only take from
 * </pre>
 *
 * <p>This is the mod's first block entity, so it also sets the two conventions the next one should copy: the
 * inventory is a plain {@link NonNullList} saved through {@link ContainerHelper}, and nothing here sends a
 * packet - the container protocol already keeps both sides in step, so a screen can never see a stale slot.
 */
public class ThoughtTranscriptionTableBlockEntity extends BlockEntity implements Container
{
    public static final int SLOT_COUNT = 6;
    public static final int MATERIAL_FIRST = 0;
    public static final int MATERIAL_COUNT = 3;
    public static final int CORE_SLOT = 3;
    public static final int THOUGHT_SLOT = 4;
    public static final int OUTPUT_SLOT = 5;

    private final NonNullList<ItemStack> m_items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public ThoughtTranscriptionTableBlockEntity(BlockPos pos, BlockState state)
    {
        super(BlockRegistry.THOUGHT_TRANSCRIPTION_TABLE_ENTITY.get(), pos, state);
    }

    @Override
    public int getContainerSize()
    {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty()
    {
        return m_items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return m_items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack taken = ContainerHelper.removeItem(m_items, slot, amount);

        if (!taken.isEmpty())
            setChanged();

        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(m_items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        m_items.set(slot, stack);

        if (stack.getCount() > getMaxStackSize())
            stack.setCount(getMaxStackSize());

        setChanged();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent()
    {
        m_items.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, m_items);
    }

    /**
     * The same write, reachable from anywhere.
     *
     * <p>Vanilla declares {@code saveAdditional} as {@code protected}, which makes the table's own persistence
     * impossible to assert from outside the package: a self-check either cannot call it or has to widen the
     * method it is testing. Widening it here instead keeps the assertion on the real entry point - and it is the
     * same override, not a second implementation, so nothing can drift.
     */
    public void saveItemsTo(CompoundTag tag)
    {
        saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        m_items.clear();
        ContainerHelper.loadAllItems(tag, m_items);
    }
}
