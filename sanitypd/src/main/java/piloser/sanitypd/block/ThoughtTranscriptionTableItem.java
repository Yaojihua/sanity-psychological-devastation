package piloser.sanitypd.block;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The thought transcription table's item form.
 *
 * <p>The detail line is always visible (it is not hidden behind SHIFT), and its colour lives inside the
 * language string the way the macaron's two grey lines do - so a translator sees the whole line, prefix
 * included, in one place.
 *
 * <p>The constant itself is declared in {@link piloser.sanitypd.item.ItemRegistry}, not in a second item
 * registry of its own: every item id in this mod lives in that one list, which is also what the
 * creative-tab gate, the item-hook gate and the binding audit read. A separate item registry would have
 * made this block invisible to all three - the same failure the five madness thoughts had.
 */
public class ThoughtTranscriptionTableItem extends BlockItem
{
    public ThoughtTranscriptionTableItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("block.sanitypd.thought_transcription_table.tooltip"));
    }
}
