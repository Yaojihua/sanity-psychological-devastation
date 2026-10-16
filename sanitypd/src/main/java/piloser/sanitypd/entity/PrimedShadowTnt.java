package piloser.sanitypd.entity;

import piloser.sanitypd.event.ShadowTntExplosionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The primed shadow TNT entity: <b>every vanilla primed-TNT behaviour, with the blast turned into psychic damage</b>.
 *
 * <h2>Only two methods differ from vanilla</h2>
 * The class extends {@link PrimedTnt}, so the fuse countdown, the fuse/block-state synchronisation, the NBT
 * round trip, the eye height, the smoke, the initial launch spread and the default 80-tick fuse all stay
 * vanilla. What changes is the blast (see below) and how the <b>igniter</b> is remembered (see
 * {@link #getOwner()}).
 *
 * <h2>The blast</h2>
 * {@link PrimedTnt#explode()} calls {@code level.explode(this, x, y, z, 4.0f, ExplosionInteraction.TNT)},
 * whose damage loop calls {@code hurt(explosion damage source, ...)}. The damage source is built inside
 * {@code Level#explode}, so it cannot be swapped from here: the same explosion is created - same radius,
 * same block interaction, same geometry - and {@link ShadowTntExplosionHandler} replaces the physical
 * damage afterwards. That also allows the damage to be <b>psychic</b>, which no vanilla damage source is.
 *
 * <p>⚠️ The amount is <b>not</b> vanilla's {@code (1 - exposure) * 7}. That formula gives a fully exposed
 * entity <b>zero</b> damage - true in vanilla, but with psychic damage it reads as "the bomb does nothing",
 * which is exactly what play-testing reported. Fully exposed now means full damage.
 *
 * <h2>Why the type is the registered one, and the igniter is not</h2>
 * {@link PrimedTnt}'s {@code owner} field is private and has no setter, and the only constructor that writes
 * it hard-codes {@code EntityType.TNT} ({@code getstatic EntityType.TNT} in its bytecode). An earlier
 * version therefore used that constructor and overrode {@link #getType()} to report this mod's own type -
 * which made the entity's type disagree with the one it was built from, and the client then could not match
 * it to this mod's renderer: it drew a vanilla TNT (wrong look) and never applied the fuse flash.
 *
 * <p>So the entity is now built from its <b>own registered type</b>, and the igniter is remembered as a
 * <b>position</b> instead: the block stores the igniter's block position, and {@link #getOwner()} resolves it
 * back to a living entity on the server. That is all the three death messages need, and it keeps the author
 * information in this class rather than in a field nothing can write.
 */
public class PrimedShadowTnt extends PrimedTnt implements ShadowTntSource
{
    /** NBT key for the igniter's UUID; a mod-private key, next to vanilla's own "Fuse". */
    private static final String IGNITER_TAG = "SanitypdIgniter";

    /** Who lit it, or {@code null} when nobody did (redstone, fire, a chain reaction). */
    private java.util.UUID igniterId;

    public PrimedShadowTnt(EntityType<? extends PrimedTnt> entityType, Level level)
    {
        super(entityType, level);
    }

    /**
     * The entity that lit this one, or {@code null}.
     *
 * <p>Vanilla's own {@code owner} field cannot be used here: it is private, has no setter, and the only
     * constructor that writes it hard-codes {@code EntityType.TNT} - and building this entity from
     * {@code EntityType.TNT} is exactly what stopped the client from matching it to this mod's renderer (it
     * drew a vanilla TNT and never flashed). The igniter is therefore kept as a UUID in this class.
     *
     * <p>Only meaningful on the server: the death messages are produced there, and that is the only thing the
     * igniter is used for.
     */
    @Override
    public LivingEntity getOwner()
    {
        if (igniterId == null || level().isClientSide())
            return null;

        if (level() instanceof ServerLevel serverLevel)
        {
            for (Player candidate : serverLevel.players())
            {
                if (candidate.getUUID().equals(igniterId))
                    return candidate;
            }
        }

        return null;
    }

    /** Records who lit this entity. Pass {@code null} for redstone/fire/chain ignitions. */
    public void rememberIgniter(LivingEntity igniter)
    {
        this.igniterId = igniter == null ? null : igniter.getUUID();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);

        if (igniterId != null)
            tag.putUUID(IGNITER_TAG, igniterId);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);

        if (tag.hasUUID(IGNITER_TAG))
            igniterId = tag.getUUID(IGNITER_TAG);
    }

    /**
     * The block this primed entity draws itself as.
     *
     * <p>Vanilla {@code PrimedTnt} keeps its block state in a synced data field with no public getter, so the
     * renderer cannot read it. This entity is only ever the shadow block, so holding that one state is both
     * simpler and always correct.
     */
    public net.minecraft.world.level.block.state.BlockState getShadowBlockState()
    {
        return piloser.sanitypd.block.BlockRegistry.SHADOW_TNT.get().defaultBlockState();
    }

    /** Spawns a primed shadow TNT with the vanilla fuse and launch spread, remembering the igniter. */
    public static PrimedShadowTnt spawn(Level level, double x, double y, double z, LivingEntity owner)
    {
        PrimedShadowTnt primed = new PrimedShadowTnt(EntityRegistry.PRIMED_SHADOW_TNT.get(), level);
        primed.setPos(x, y, z);

        // The fuse comes from this entity's own default (80) rather than a hard-coded number, matching
        // vanilla. The launch spread and the previous-position fields are vanilla's own values too
        // (bytecode: sin(random * 2pi) * -0.02, 0.2, cos(random * 2pi) * -0.02, then xo/yo/zo).
        primed.setFuse(primed.getFuse());

        double angle = level.random.nextDouble() * (Math.PI * 2.0d);
        primed.setDeltaMovement(-Math.sin(angle) * 0.02d, 0.2d, -Math.cos(angle) * 0.02d);

        primed.xo = x;
        primed.yo = y;
        primed.zo = z;

        primed.rememberIgniter(owner);
        return primed;
    }

    /**
     * The blast: identical to vanilla primed TNT except that the damage is psychic.
     *
     * <p>Delegating the geometry to {@link Level#explode} is deliberate - the block interaction, the
     * resistance checks, the destroyed-block accounting and the smoke all stay vanilla. The handler is
     * told about this entity <b>before</b> the explosion runs, because {@code ExplosionEvent.Detonate}
     * fires from inside that call.
     */
    @Override
    protected void explode()
    {
        if (level().isClientSide())
            return;

        ShadowTntExplosionHandler.register(this);

        // Same numbers as PrimedTnt#explode: radius 4.0, +0.0625 on Y, ExplosionInteraction.TNT.
        level().explode(this, getX(), getY(0.0625d), getZ(), 4.0f, Level.ExplosionInteraction.TNT);
    }

    /** Vanilla primed TNT does not emit movement (it is not a walking entity). */
    @Override
    protected net.minecraft.world.entity.Entity.MovementEmission getMovementEmission()
    {
        return net.minecraft.world.entity.Entity.MovementEmission.NONE;
    }
}
