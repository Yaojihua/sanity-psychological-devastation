package piloser.sanitypd.entity;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class InnerEntitySpawner
{
    private static final RandomSource RAND = RandomSource.create();

    public static int spawnRad = 20;
    public static int detectionRad = 40;
    public static int spawnTimeout = 20 * 20;
    /** Returned when no suitable height is found; 0 would collide with the legitimate y=0. */
    private static final int NO_HEIGHT = Integer.MIN_VALUE;

    public static final float SPAWN_THRESHOLD = .75f; // compared against getMadness() (0..1 madness)
    // UUID keys: ServerPlayer instances are lost on death or dimension change, and a stale map would leak.
    public static final Map<UUID, Integer> PLAYER_TO_SPAWN_TIMEOUT = new HashMap<>();

    /** The screaming crawler is 3 blocks tall, so 3 blocks of clearance are needed. */
    private static final double HIGH_MOB_CLEARANCE = 3.0;
    /** How far the spawn height may differ from the player's (keeps mobs off distant roofs and cave floors). */
    private static final int VERTICAL_TOLERANCE = 5;

    /**
     * Resolves the ground height in the column of {@code trialPos} from a {@link Heightmap}, requiring
     * the result to be close to {@code anchorY}.
     *
     * <p>Scanning upward from the player for a solid block with air above placed mobs up to 20 blocks
     * away when the player was underground, so they never appeared. The heightmap is what vanilla uses
     * for its own spawn placement. Two conditions apply:
     * <ul>
     *   <li>the spawn height is within {@link #VERTICAL_TOLERANCE} of the player's height</li>
     *   <li>there is {@link #HIGH_MOB_CLEARANCE} blocks of headroom above the ground</li>
     * </ul>
     */
    private static int getHeightForSpawning(Level level, BlockPos trialPos, int anchorY)
    {
        if (!(level instanceof ServerLevel sl))
            return NO_HEIGHT;

        for (Heightmap.Types type : new Heightmap.Types[]{
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.WORLD_SURFACE})
        {
            int surfaceY = sl.getHeight(type, trialPos.getX(), trialPos.getZ());
            if (Math.abs(surfaceY - anchorY) > VERTICAL_TOLERANCE)
                continue;

            BlockPos feet = new BlockPos(trialPos.getX(), surfaceY, trialPos.getZ());
            // The ground must be solid; water and lava also count as ground in the heightmap, so
            // exclude them via the fluid state.
            var groundState = level.getBlockState(feet.below());
            if (level.getBlockState(feet).getFluidState().isSource()
                    || level.getBlockState(feet.below()).getFluidState().isSource())
                continue;
            if (!groundState.isSolid())
                continue;
            if (!level.getBlockState(feet).isAir())          // the feet block must be air (passable plants/snow are fine)
                continue;
            if (!hasHeadroom(level, feet))
                continue;
            return surfaceY;
        }
        return NO_HEIGHT;
    }

    /**
     * Checks that there is enough clearance above the spawn position. Vanilla
     * {@code checkSpawnObstruction} only looks one block up, which is not enough for the 6 block tall
     * screaming crawler: it would fail against its own bounding box.
     */
    private static boolean hasHeadroom(Level level, BlockPos feetPos)
    {
        for (int i = 0; i < (int) HIGH_MOB_CLEARANCE; i++)
            if (!level.getBlockState(feetPos.above(i)).isAir())
                return false;
        return true;
    }

    public static boolean trySpawnForPlayer(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator() || player.level().getDifficulty().equals(Difficulty.PEACEFUL))
            return false;

        PLAYER_TO_SPAWN_TIMEOUT.putIfAbsent(player.getUUID(), 0);
        int t = PLAYER_TO_SPAWN_TIMEOUT.get(player.getUUID());
        if (t > 0)
        {
            PLAYER_TO_SPAWN_TIMEOUT.put(player.getUUID(), t - 1);
            return false;
        }

        ISanity s = player.getCapability(SanityProvider.CAP).orElse(null);
        if (s == null)
            return false;
        if (s.getMadness() < SPAWN_THRESHOLD || getInnerEntitiesInRadius(player.level(), player.blockPosition(), detectionRad).size() >= 3)
            return false;

        // Try several candidate positions so one failure does not waste the whole spawn cycle.
        for (int attempt = 0; attempt < 8; attempt++)
        {
            int index = RAND.nextInt(EntityRegistry.INNER_ENTITIES.size());
            InnerEntity entity = EntityRegistry.INNER_ENTITIES.get(index).get().create(player.level());
            if (entity == null)
                continue;

            BlockPos trialPos = BlockPos.randomBetweenClosed(RAND, 1,
                    player.blockPosition().getX() - spawnRad,
                    player.blockPosition().getY(),
                    player.blockPosition().getZ() - spawnRad,
                    player.blockPosition().getX() + spawnRad,
                    player.blockPosition().getY(),
                    player.blockPosition().getZ() + spawnRad).iterator().next();

            int h = getHeightForSpawning(player.level(), trialPos, player.blockPosition().getY());
            if (h == NO_HEIGHT)
                continue;

            BlockPos spawnPos = new BlockPos(trialPos.getX(), h, trialPos.getZ());
            entity.setPos(new Vec3(spawnPos.getX() + .5f, spawnPos.getY() + 1.0f, spawnPos.getZ() + .5f));
            if (entity.checkSpawnObstruction(player.level()) &&
                    player.level().noCollision(entity) &&
                    ((ServerLevel)player.level()).tryAddFreshEntityWithPassengers(entity))
            {
                PLAYER_TO_SPAWN_TIMEOUT.put(player.getUUID(), spawnTimeout);
                return true;
            }
            entity.discard();
        }

        return false;
    }

    public static List<InnerEntity> getInnerEntitiesInRadius(Level level, BlockPos blockPos, int radius)
    {
        return level.getEntitiesOfClass(InnerEntity.class, new AABB(blockPos).inflate(radius));
    }
}