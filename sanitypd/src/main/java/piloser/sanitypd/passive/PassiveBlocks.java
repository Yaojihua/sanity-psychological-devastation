package piloser.sanitypd.passive;

import piloser.sanitypd.block.BlockStateHelper;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityLevelChunkProvider;
import piloser.sanitypd.config.ConfigPassiveBlock;
import piloser.sanitypd.config.ConfigProxy;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class PassiveBlocks implements IPassiveSanitySource
{
    /** Expensive: scans every block in a cube (radius 4 gives 512 getBlockState calls per tick). */
    @Override
    public boolean isExpensive()
    {
        return true;
    }

    @Override
    public float get(@Nonnull ServerPlayer player, @Nonnull ISanity cap, @Nonnull ResourceLocation dim)
    {
        float result = 0;

        for (ConfigPassiveBlock block : ConfigProxy.getPassiveBlocks(dim))
        {
            if (block.m_sanity == 0.0f)
                continue;

            Block regBlock = null;
            if (!block.m_isTag && ((regBlock = ForgeRegistries.BLOCKS.getValue(block.m_name)) == null || regBlock.defaultBlockState().isAir()))
                continue;

            boolean flag = false;
            // Hoist the player position out of the loops: the three loop conditions would otherwise
            // call player.position() six times per iteration
            final double px = player.position().x;
            final double py = player.position().y;
            final double pz = player.position().z;
            for (float x = (float)px - block.m_rad; x < px + block.m_rad; ++x)
            {
                if (flag) break;
                for (float y = (float)py - block.m_rad; y < py + block.m_rad; ++y)
                {
                    if (flag) break;
                    for (float z = (float)pz - block.m_rad; z < pz + block.m_rad; ++z)
                    {
                        BlockPos posAt = new BlockPos((int)x, (int)y, (int)z);
                        BlockState stateAt = player.level().getBlockState(posAt);

                        if (block.m_isTag && stateAt.getTags().anyMatch(tag -> tag.location().equals(block.m_name)) || regBlock == stateAt.getBlock())
                        {
                            if (block.m_naturallyGend)
                            {
                                AtomicBoolean placedArtificially = new AtomicBoolean(false);
                                player.level().getChunkAt(posAt).getCapability(SanityLevelChunkProvider.CAP).ifPresent(sl ->
                                {
                                    if (sl.getArtificiallyPlacedBlocks().contains(posAt))
                                        placedArtificially.set(true);
                                });
                                if (placedArtificially.get())
                                    continue;
                            }

                            boolean flag1 = false;
                            for (Map.Entry<String, Boolean> entry : block.m_props.entrySet())
                            {
                                BooleanProperty prop = BlockStateHelper.getBooleanProperty(stateAt, entry.getKey());
                                if (prop != null && stateAt.getValue(prop) != entry.getValue())
                                {
                                    flag1 = true;
                                    break;
                                }
                            }
                            if (flag1)
                                continue;

                            HitResult hit = player.level().clip(new ClipContext(
                                    player.getEyePosition(),
                                    posAt.getCenter(),
                                    ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE,
                                    player));
                            if (hit.getType() == HitResult.Type.MISS)
                            {
                                result += block.m_sanity;
                                flag = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        return result;
    }
}