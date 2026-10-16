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
 * Shadow TNT's item form.
 *
 * <p>The detail line is always visible (not hidden behind SHIFT), and its colour lives inside the language
 * string, exactly like the transcription table's and the macaron's grey lines - so a translator sees the
 * whole line, prefix included, in one place.
 *
 * <p>The line states the one thing that is <b>not</b> inherited from vanilla TNT and cannot be seen by
 * looking at the block: the blast drains sanity rather than health, and it does so at a <b>fixed</b> amount
 * wherever the target stands inside the radius. That is the property a player would otherwise have to be
 * told out of band, because vanilla's own blast scales its damage with how exposed the target is.
 */
public class ShadowTntItem extends BlockItem
{
    public ShadowTntItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("block.sanitypd.shadow_tnt.tooltip"));
    }
}
