package piloser.sanitypd.block;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.item.LostMemoryFragmentItem;
import piloser.sanitypd.thought.ThoughtTags;

/**
 * The thought transcription table's menu: six table slots plus the player's thirty-six.
 *
 * <p>Geometry lives here as constants and the screen reads them, so a slot and the recess drawn around it can
 * never end up in different places. Slot origins are the 16x16 item area; the screen draws the 18x18 recess one
 * pixel up and to the left of that origin, which is the convention every vanilla container follows.
 *
 * <p>The player inventory rows are 97 / 115 / 133 and the hotbar 155 - measured off the vanilla sheet this
 * screen stitches from (`generic_54.png`: the three rows sit at 139 / 157 / 175 and the hotbar at 197, and the
 * sheet is pasted at 83), not guessed.
 *
 * <p>Phase 1 is the interface only: no recipe is registered, so {@link #costLine()} returns null and the
 * screen's cost line stays empty. That hook is the seam the recipe layer will fill.
 */
public class ThoughtTranscriptionTableMenu extends AbstractContainerMenu
{
 /** The screen's own title, "upgrade and transcribe thoughts" (the required wording). */
    public static final String TITLE_KEY = "container.sanitypd.thought_transcription_table";

    /**
     * What a filtered slot asks for, shown as a tooltip when the slot is hovered while empty. Split from the
     * title key instead of concatenated at call time, so every language key can be found by searching for it.
     */
    public static final String NEED_CORE_KEY = "container.sanitypd.thought_transcription_table.need_core";
    public static final String NEED_THOUGHT_KEY = "container.sanitypd.thought_transcription_table.need_thought";

    /** The cost line, e.g. "cost: 2"; the number is filled in from the matched rule's own price. */
    public static final String COST_KEY = "container.sanitypd.thought_transcription_table.cost";

    public static final int IMAGE_WIDTH = TranscriptionLayout.WIDTH;
    public static final int IMAGE_HEIGHT = TranscriptionLayout.HEIGHT;

    /**
     * Where the player inventory block is pasted into the GUI, and the label's baseline offset.
     *
     * <p>Re-exported from {@link TranscriptionLayout} rather than written again: the screen, JEI and this menu
     * all have to agree on it, and the first version of this class kept its own copy - which is how the lower
     * block could be moved in one place and not the other.
     */
    public static final int INVENTORY_TOP = TranscriptionLayout.INVENTORY_TOP;
    public static final int INVENTORY_LABEL_Y = TranscriptionLayout.INVENTORY_LABEL_Y;

    private static final int PITCH = TranscriptionLayout.PITCH;
    private static final int MATERIAL_X = TranscriptionLayout.MATERIAL_X;
    private static final int MATERIAL_Y = TranscriptionLayout.MATERIAL_Y;
    private static final int CORE_X = TranscriptionLayout.CORE_X;
    private static final int CORE_Y = TranscriptionLayout.CORE_Y;
    private static final int THOUGHT_X = TranscriptionLayout.THOUGHT_X;
    private static final int LOWER_Y = TranscriptionLayout.LOWER_Y;
    private static final int OUTPUT_X = TranscriptionLayout.OUTPUT_X;
    private static final int PLAYER_X = TranscriptionLayout.PLAYER_X;
    private static final int PLAYER_Y = TranscriptionLayout.PLAYER_Y;
    private static final int HOTBAR_Y = TranscriptionLayout.HOTBAR_Y;
    private static final int PLAYER_SLOTS = 36;

    private final Container m_table;
    private final Player m_player;

    /** The output preview: recomputed from the inputs, never trusted, never meant to be saved. */
    private ItemStack m_preview = ItemStack.EMPTY;

    /**
     * Keeps the output slot showing what the current inputs produce.
     *
     * <p>Done here rather than in {@code slotsChanged} because the table's inventory is a plain block entity:
     * unlike vanilla's crafting grid it has no menu to notify, so the menu has to look for itself. The write
     * happens only when the answer changed, which is what keeps this from looping.
     */
    @Override
    public void broadcastChanges()
    {
        if (!m_player.level().isClientSide())
            refreshPreview();

        super.broadcastChanges();
    }

    private void refreshPreview()
    {
        TranscriptionRules.Result result = currentResult();
        ItemStack wanted = result == null ? ItemStack.EMPTY : result.output();

        if (ItemStack.matches(wanted, m_preview))
            return;

        m_preview = wanted.copy();
        m_table.setItem(ThoughtTranscriptionTableBlockEntity.OUTPUT_SLOT, wanted.copy());
    }

    /** The rule's answer for the five inputs, or null. Reads the inputs only - never the output slot. */
    private TranscriptionRules.Result currentResult()
    {
        return TranscriptionRules.match(
                m_table.getItem(ThoughtTranscriptionTableBlockEntity.MATERIAL_FIRST),
                m_table.getItem(ThoughtTranscriptionTableBlockEntity.MATERIAL_FIRST + 1),
                m_table.getItem(ThoughtTranscriptionTableBlockEntity.MATERIAL_FIRST + 2),
                m_table.getItem(ThoughtTranscriptionTableBlockEntity.THOUGHT_SLOT),
                m_table.getItem(ThoughtTranscriptionTableBlockEntity.CORE_SLOT));
    }

    /**
     * Spends one of each input and clears the output; called when a result is taken.
     *
     * <p>The output slot is emptied here rather than left to the next preview: a real player's take would be
     * cleaned up on the following tick, but "the slot I just emptied is empty" is the invariant, and relying on
     * a later tick makes it unobservable to anything that inspects the menu straight after the take.
     */
    private void consumeInputs()
    {
        for (int slot = 0; slot <= ThoughtTranscriptionTableBlockEntity.THOUGHT_SLOT; slot++)
        {
            ItemStack stack = m_table.getItem(slot);

            if (stack.isEmpty())
                continue;

            stack.shrink(1);
            m_table.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }

        m_table.setItem(ThoughtTranscriptionTableBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        m_preview = ItemStack.EMPTY;
    }

    /**
     * Client side: the constructor {@code IForgeMenuType} calls. The block position is in the packet body,
     * because the client cannot know which table it is opening otherwise.
     */
    public ThoughtTranscriptionTableMenu(int syncId, Inventory playerInventory, FriendlyByteBuf data)
    {
        this(syncId, playerInventory, tableOf(playerInventory.player, data));
    }

    /** Both sides: the server builds this from the block entity it already has. */
    public ThoughtTranscriptionTableMenu(int syncId, Inventory playerInventory, Container table)
    {
        super(ThoughtTranscriptionTableMenus.TRANSCRIPTION_TABLE.get(), syncId);
        m_table = table;
        m_player = playerInventory.player;

        for (int i = 0; i < ThoughtTranscriptionTableBlockEntity.MATERIAL_COUNT; i++)
            addSlot(new Slot(table, ThoughtTranscriptionTableBlockEntity.MATERIAL_FIRST + i,
                    MATERIAL_X + i * PITCH, MATERIAL_Y));

        addSlot(new Slot(table, ThoughtTranscriptionTableBlockEntity.CORE_SLOT, CORE_X, CORE_Y)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return acceptsCore(stack);
            }
        });

        addSlot(new Slot(table, ThoughtTranscriptionTableBlockEntity.THOUGHT_SLOT, THOUGHT_X, LOWER_Y)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return acceptsThought(stack);
            }
        });

        addSlot(new Slot(table, ThoughtTranscriptionTableBlockEntity.OUTPUT_SLOT, OUTPUT_X, LOWER_Y)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return false;
            }

            /**
 * The anvil's rule, which the design requires ("like the anvil"): the result is visible either way,
             * but it can only be taken while the price is actually there. Refusing here - rather than in
             * {@code onTake} - is what keeps the stack in the slot, so nothing has to be put back.
             *
             * <p>The price is the matched rule's own ({@link TranscriptionRules.Result#cost()}), read fresh
             * here: transcription costs two levels and an upgrade costs three, so a single hardcoded constant
             * would charge the upgrade at the transcription price.
             */
            @Override
            public boolean mayPickup(Player player)
            {
                if (player.getAbilities().instabuild)
                    return true;

                TranscriptionRules.Result matched = currentResult();

                return matched != null && player.experienceLevel >= matched.cost();
            }

            @Override
            public void onTake(Player player, ItemStack stack)
            {
                super.onTake(player, stack);

                TranscriptionRules.Result paid = currentResult();

                if (!player.getAbilities().instabuild && paid != null)
                    player.giveExperienceLevels(-paid.cost());

                consumeInputs();
            }
        });

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_X + column * PITCH, PLAYER_Y + row * PITCH));

        for (int column = 0; column < 9; column++)
            addSlot(new Slot(playerInventory, column, PLAYER_X + column * PITCH, HOTBAR_Y));
    }

    /**
 * The thought slot's filter, from the wording: "only a thought container kind, a thought, or a
     * memory fragment".
     *
     * <p>Thoughts are recognised through the three category tags rather than by an id prefix: a prefix is a
     * naming habit, not a type, and tag membership is what the thought chain's own slots accept. A datapack
     * that widens a row therefore widens this slot too.
     *
     * <p>Public and static, and called by the slot itself: the check is then reachable without a live menu, so
     * it can be asserted (and a filter that only exists inside an inner class cannot).
     */
    public static boolean acceptsThought(ItemStack stack)
    {
        for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> container
                : ItemRegistry.THOUGHT_CONTAINERS)
            if (stack.is(container.get()))
                return true;

        return stack.getItem() instanceof LostMemoryFragmentItem
                || stack.is(ThoughtTags.WORLDVIEW)
                || stack.is(ThoughtTags.METHODOLOGY)
                || stack.is(ThoughtTags.HALLUCINATION);
    }

    /** The core slot's filter: an inner core, nothing else. Static for the same reason as above. */
    public static boolean acceptsCore(ItemStack stack)
    {
        return stack.is(ItemRegistry.INNER_CORE.get());
    }

    /**
     * What a table slot asks for, or null for the slots that take anything. The screen draws this as a tooltip
 * while the slot is hovered and empty - the filter is invisible otherwise, and the design requires the hint
     * ("like the box shown when hovering an item").
     */
    public static Component slotHint(int tableSlot)
    {
        if (tableSlot == ThoughtTranscriptionTableBlockEntity.CORE_SLOT)
            return Component.translatable(NEED_CORE_KEY);

        if (tableSlot == ThoughtTranscriptionTableBlockEntity.THOUGHT_SLOT)
            return Component.translatable(NEED_THOUGHT_KEY);

        return null;
    }

    /** The container behind the table slots; the screen uses it to tell table slots from the player's own. */
    public Container tableContainer()
    {
        return m_table;
    }

    /** Whether anything sits in the inputs; this is what decides if the arrow may show its no-result cross. */
    public boolean hasInput()
    {
        for (int slot = 0; slot <= ThoughtTranscriptionTableBlockEntity.THOUGHT_SLOT; slot++)
            if (!m_table.getItem(slot).isEmpty())
                return true;

        return false;
    }

    /**
     * The green cost line the screen draws under the arrow, or null when the inputs match no rule.
     *
     * <p>The number comes from the rule ({@link TranscriptionRules#XP_COST}, "two levels, always"), so the line
     * and the price the player actually pays cannot drift apart.
     */
    public Component costLine()
    {
        TranscriptionRules.Result result = currentResult();

        return result == null ? null : Component.translatable(COST_KEY, result.cost());
    }

    /**
     * Whether the player can pay for the line above. The screen paints the line red when this is false, and the
 * output slot refuses the pickup, so an unaffordable table cannot be crafted from at all - the
     * "without the experience it simply cannot be made".
     *
     * <p>Same source as the take: the matched rule's own cost, so the line, the refusal and the charge are one
     * number (two levels to transcribe, three to upgrade).
     */
    public boolean costAffordable()
    {
        TranscriptionRules.Result result = currentResult();

        return result != null
                && (m_player.getAbilities().instabuild || m_player.experienceLevel >= result.cost());
    }

    /**
 * The vanilla anvil's rule, which the design requires ("a red cross on the arrow like the anvil"): the cross
     * appears when something is in the inputs and nothing can be produced, and disappears as soon as there is a
     * result - so an empty table looks dark, not broken.
     */
    public boolean showsNoResultCross()
    {
        return hasInput() && m_table.getItem(ThoughtTranscriptionTableBlockEntity.OUTPUT_SLOT).isEmpty();
    }

    private static Container tableOf(Player player, FriendlyByteBuf data)
    {
        return player.level().getBlockEntity(data.readBlockPos()) instanceof ThoughtTranscriptionTableBlockEntity table
                ? table
                : new SimpleContainer(ThoughtTranscriptionTableBlockEntity.SLOT_COUNT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);

        if (slot == null || !slot.hasItem())
            return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int tableSlots = ThoughtTranscriptionTableBlockEntity.SLOT_COUNT;

        if (index < tableSlots)
        {
            if (!this.moveItemStackTo(stack, tableSlots, tableSlots + PLAYER_SLOTS, true))
                return ItemStack.EMPTY;
        }
        else if (!this.moveItemStackTo(stack, 0, tableSlots, false))
        {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty())
            slot.set(ItemStack.EMPTY);
        else
            slot.setChanged();

        return original;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return m_table.stillValid(player);
    }
}
