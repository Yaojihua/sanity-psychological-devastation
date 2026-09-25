package piloser.sanitypd.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.HashSet;
import java.util.Set;

public class SanityLevelChunk implements ISanityLevelChunk
{
    /**
     * Blocks the player has placed in this chunk.
     *
     * <p>HashSet rather than ArrayList: {@code PassiveBlocks} calls {@code contains()} on it in an
     * innermost loop, where the O(n) lookup of an ArrayList would degrade as more blocks are placed.
     */
    private final Set<BlockPos> m_blocksPlacedByPlayer = new HashSet<>();

    @Override
    public Set<BlockPos> getArtificiallyPlacedBlocks()
    {
        return m_blocksPlacedByPlayer;
    }

    @Override
    public void serializeNBT(CompoundTag tag)
    {
        int size = m_blocksPlacedByPlayer.size();
        long[] arr = new long[size];
        int i = 0;
        for (BlockPos pos : m_blocksPlacedByPlayer)
        {
            arr[i++] = pos.asLong();
        }
        tag.putLongArray("sanity.blocks_placed_by_player", arr);
    }

    @Override
    public void deserializeNBT(CompoundTag tag)
    {
        long[] arr = tag.getLongArray("sanity.blocks_placed_by_player");
        m_blocksPlacedByPlayer.clear();
        for (int i = 0; i < arr.length; ++i) // each position is one long; dividing by 3 would drop 2/3 of them
        {
            m_blocksPlacedByPlayer.add(BlockPos.of(arr[i]));
        }
    }
}