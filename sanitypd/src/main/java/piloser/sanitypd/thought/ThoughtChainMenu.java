package piloser.sanitypd.thought;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The thought chain menu.
 *
 * <h2>Two rows, no third row</h2>
 * Only the two player-editable rows are slots: worldview (0..8) and methodology (9..17). The third row -
 * the mindsets - is <b>not</b> part of this menu at all. It cannot be taken from, cannot be put into, and
 * must not be reachable by shift-click, hoppers or anything else that walks a menu's slots, so the honest
 * way to model it is to leave it out and let the screen draw it.
 *
 * <h2>Screen coordinates come from the vanilla double chest</h2>
 * The background is composed at runtime from {@code minecraft:textures/gui/container/generic_54.png}
 * (scheme B: no Mojang art is shipped in this jar). That texture's own hole rows sit at y=18/36/54/72/90/108
 * for a double chest, but this screen replaces four of them with subtitle text, so the two rows it keeps
 * are placed at y=30 and y=60 and those two strips are blitted at the matching offsets. The player
 * inventory is left exactly where vanilla put it, which is why its rows are at 140/158/176 and the hotbar at
 * 198. Changing a number here without changing the same number in the screen puts slots and holes out of
 * step, so both classes state them as constants with this comment attached.
 */
public class ThoughtChainMenu extends AbstractContainerMenu
{
    /** Language key of the window title; {@code %1$s} is the player's account name. */
    public static final String TITLE_KEY = "gui.sanitypd.thought_chain.title";

    /** Screen y of the first slot of each row, in {@link ThoughtCategory} order. */
    public static final int[] ROW_Y = { 30, 60 };

    /** Screen y of the first player inventory row and of the hotbar (vanilla double chest values). */
    public static final int PLAYER_ROW_Y = 140;
    public static final int HOTBAR_Y = 198;

    /** Leftmost slot x of every row; the standard 8-pixel margin. */
    public static final int SLOT_X = 8;

    private final Container m_chain;

    /**
     * Client side: the mirror container the container protocol fills.
     *
     * <p>Called through the menu type's factory, which is what the client runs when the open-screen packet
     * arrives. The contents arrive afterwards, slot by slot, through the normal menu sync.
     */
    public ThoughtChainMenu(int syncId, Inventory playerInventory)
    {
        this(syncId, playerInventory, new SimpleContainer(IThoughtChain.SIZE));
    }

    /** Server side: the opening player's own chain. */
    public static ThoughtChainMenu forPlayer(int syncId, Inventory playerInventory, Player player)
    {
        return new ThoughtChainMenu(syncId, playerInventory, chainOf(player));
    }

    /**
     * The chain of a player, or an empty stand-in if the capability is somehow absent.
     *
     * <p>The fallback is not decoration: a menu that cannot be built leaves the client with a screen it
     * cannot open, whereas an empty temporary chain still shows the layout and simply has nothing in it.
     */
    public static Container chainOf(Player player)
    {
        return player.getCapability(ThoughtChainProvider.CAP)
                .map(IThoughtChain::container)
                .orElseGet(() -> new SimpleContainer(IThoughtChain.SIZE));
    }

    public ThoughtChainMenu(int syncId, Inventory playerInventory, Container chain)
    {
        super(ThoughtChainMenus.THOUGHT_CHAIN.get(), syncId);
        m_chain = chain;
        chain.startOpen(playerInventory.player);

        for (ThoughtCategory category : ThoughtCategory.values())
        {
            int first = IThoughtChain.rowStart(category);
            int y = ROW_Y[category.ordinal()];

            for (int column = 0; column < IThoughtChain.ROW_SIZE; column++)
                addSlot(new ThoughtSlot(chain, first + column, SLOT_X + column * 18, y, category));
        }

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(playerInventory, column + row * 9 + 9, SLOT_X + column * 18, PLAYER_ROW_Y + row * 18));

        for (int column = 0; column < 9; column++)
            addSlot(new Slot(playerInventory, column, SLOT_X + column * 18, HOTBAR_Y));
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        m_chain.stopOpen(player);
    }

    /**
     * Shift-click transfer.
     *
     * <p>Nothing special is needed for the two rules: {@code moveItemStackTo} asks every candidate slot's
     * {@code mayPlace}, so a thought can only land in its own row and never twice. When the chain has no
     * room the stack stays where it was, which is the behaviour a player expects.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = slots.get(index);

        if (slot == null || !slot.hasItem())
            return ItemStack.EMPTY;

        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        int chainSlots = IThoughtChain.SIZE;

        if (index < chainSlots)
        {
            if (!moveItemStackTo(inSlot, chainSlots, slots.size(), true))
                return ItemStack.EMPTY;
        }
        else if (!moveItemStackTo(inSlot, 0, chainSlots, false))
        {
            return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty())
            slot.set(ItemStack.EMPTY);
        else
            slot.setChanged();

        return original;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return m_chain.stillValid(player);
    }
}
