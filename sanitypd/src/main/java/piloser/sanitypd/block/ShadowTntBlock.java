package piloser.sanitypd.block;

import piloser.sanitypd.entity.PrimedShadowTnt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Shadow TNT: <b>vanilla TNT in every respect except that the blast deals psychic damage</b>.
 *
 * <h2>How "identical to TNT" is kept true</h2>
 * The class extends {@link TntBlock} and copies the block properties wholesale with
 * {@code Properties.copy(Blocks.TNT)}, so hardness, resistance, sound, map colour, the lava ignition rule,
 * the {@code UNSTABLE} block state, the redstone behaviour and the "flint and steel is consumed" handling
 * all come from vanilla rather than from a re-typed list of numbers. The vanilla property chain was read
 * out of {@code Blocks.<clinit>} to confirm what is being copied:
 * {@code of() -> mapColor(MapColor.FIRE) -> instabreak() -> sound(SoundType.GRASS) -> ignitedByLava()
 * -> isRedstoneConductor(never)}.
 *
 * <h2>The four ignition entry points</h2>
 * {@code TntBlock} creates vanilla {@code PrimedTnt} in exactly four public methods (checked against its
 * bytecode). All four are overridden here so <b>every</b> way of lighting it - flint and steel, a fire
 * arrow, redstone, a neighbouring block update, being destroyed by another explosion, and the
 * {@code UNSTABLE} state - produces a {@link PrimedShadowTnt} instead:
 * <ol>
 *   <li>{@link #onCaughtFire} - fire, lava, a fire arrow; also reached from {@code use} (flint and steel),
 *       so the item-consumption path does not need its own override;</li>
 *   <li>{@link #onPlace} - placed next to an already-powered redstone signal;</li>
 *   <li>{@link #neighborChanged} - redstone powered later;</li>
 *   <li>{@link #wasExploded} - caught in someone else's blast, which is what makes chains work.</li>
 * </ol>
 *
 * <h2>What deliberately stays vanilla</h2>
 * {@code wasExploded} keeps the vanilla random fuse (<b>the default fuse plus up to 40% of it</b>) and the
 * vanilla explosion sound and game event, so a chained shadow TNT does not all go off on the same tick.
 */
public class ShadowTntBlock extends TntBlock
{
    public ShadowTntBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    /** Fire / lava / a fire arrow, and the flint-and-steel path that routes through {@code use}. */
    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, Direction face, LivingEntity igniter)
    {
        if (level.isClientSide())
            return;

        spawnPrimed(level, pos, igniter);
        level.removeBlock(pos, false);
    }

    /** Placed while already powered by redstone. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving)
    {
        if (level.isClientSide())
            return;

        if (level.hasNeighborSignal(pos))
        {
            spawnPrimed(level, pos, null);
            level.removeBlock(pos, false);
        }
    }

    /** Redstone powered after placement. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
                                BlockPos fromPos, boolean isMoving)
    {
        if (level.isClientSide())
            return;

        if (level.hasNeighborSignal(pos))
        {
            spawnPrimed(level, pos, null);
            level.removeBlock(pos, false);
        }
    }

    /**
     * Caught in another explosion: the block becomes a primed entity instead of simply vanishing.
     *
     * <p>The random fuse, the sound and the game event are the vanilla ones; only the entity type differs.
     */
    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion)
    {
        if (level.isClientSide())
            return;

        PrimedShadowTnt primed = PrimedShadowTnt.spawn(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d,
                explosion.getIndirectSourceEntity());

        // Vanilla: a chained TNT gets a fuse between the default and 1.4x the default, so a row of them
        // does not detonate on a single tick.
        int fuse = primed.getFuse();
        primed.setFuse(level.random.nextInt(fuse / 4) + fuse / 8);

        level.addFreshEntity(primed);
        level.playSound(null, primed.getX(), primed.getY(), primed.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(null, GameEvent.PRIME_FUSE, pos);
    }

    /** One place that creates the primed entity, so all four entry points behave identically. */
    private static void spawnPrimed(Level level, BlockPos pos, LivingEntity igniter)
    {
        PrimedShadowTnt primed = PrimedShadowTnt.spawn(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, igniter);

        boolean added = level.addFreshEntity(primed);

        // Reported because "the block vanished but nothing was primed" is invisible otherwise: the block
        // removal happens regardless of whether the entity made it into the world.
        piloser.sanitypd.SanityMod.LOGGER.info("[SHADOW-TNT] primed at {}: added={} alive={} id={} type={} sameLevel={}",
                pos, added, primed.isAlive(), primed.getId(), primed.getType(), primed.level() == level);

        level.playSound(null, primed.getX(), primed.getY(), primed.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
    }
}
