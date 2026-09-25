package piloser.sanitypd.capability;

import piloser.sanitypd.ICompoundTagSerializable;
import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * Per-chunk record of blocks placed by the player.
 *
 * <p><b>Must be a Set, not a List</b>: {@code PassiveBlocks} calls {@code contains()} on it in the
 * innermost loop of a block-cube scan, so a List would mean an O(n) linear lookup repeated for every
 * scanned position, which gets worse the longer a world is played. A HashSet lookup is O(1).
 */
public interface ISanityLevelChunk extends ICompoundTagSerializable
{
    Set<BlockPos> getArtificiallyPlacedBlocks();
}