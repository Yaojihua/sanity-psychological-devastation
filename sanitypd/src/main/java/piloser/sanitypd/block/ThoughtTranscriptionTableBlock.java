package piloser.sanitypd.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * The thought transcription table's block behaviour.
 *
 * <p>The shape is the one thing a plain {@code Block} cannot get right on its own, so it is what this class
 * carried first; the interface came later, in the order the design requires ("do the block body first").
 *
 * <p>The shape follows the same rule the vanilla enchanting table follows - it is the model's own
 * silhouette, not a full cube. The enchanting table's model is 12 pixels tall and its shape is 12 pixels
 * tall; this model is a 12-pixel base carrying a rim up to pixel 15, so the shape stops at 15. A full
 * 16-high box would leave a pixel of collision above the art, and the enchanting table's 12 would let
 * players walk through the rim.
 *
 * <p>{@code useShapeForLightOcclusion} is true for the enchanting table's reason as well: light should
 * follow the visible silhouette instead of the full cube.
 *
 * <p><b>The screen is opened on the server only</b>, and the block position travels with the open-screen
 * packet ({@code openMenu(provider, pos)}): that is how the client's menu finds the same block entity, since
 * the sync id and the player inventory alone cannot say which table was clicked.
 */
public class ThoughtTranscriptionTableBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public ThoughtTranscriptionTableBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ThoughtTranscriptionTableBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit)
    {
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ThoughtTranscriptionTableBlockEntity table)
        {
            MenuProvider provider = new SimpleMenuProvider(
                    (syncId, inventory, opener) -> new ThoughtTranscriptionTableMenu(syncId, inventory, table),
                    Component.translatable(ThoughtTranscriptionTableMenu.TITLE_KEY));
            // NetworkHooks rather than ServerPlayer#openMenu: vanilla's overload takes no position, and the
            // position is exactly what the client needs to find this table (the menu reads it back).
            NetworkHooks.openScreen(serverPlayer, provider, buffer -> buffer.writeBlockPos(pos));
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state)
    {
        return true;
    }
}
