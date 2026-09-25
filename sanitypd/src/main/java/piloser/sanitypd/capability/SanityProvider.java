package piloser.sanitypd.capability;

import piloser.sanitypd.SanityMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

/**
 * Capability provider for sanity.
 *
 * <p>Attached to every living entity except inner entities (see {@code SanityTags.INNER_ENTITIES}):
 * players are capped at a fixed 100 points, other mobs are capped at their max health, so the
 * provider must know its owner entity.
 */
public class SanityProvider implements ICapabilitySerializable<CompoundTag>
{
    public static final ResourceLocation KEY = new ResourceLocation(SanityMod.MODID, "sanity");
    public static final Capability<ISanity> CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private final Sanity m_cap;
    private final LazyOptional<ISanity> m_lazyOpt;

    /** No owner bound; the cap then follows the player rule. */
    public SanityProvider()
    {
        this(null);
    }

    /**
     * @param owner owner entity: player means cap 100, other mobs means cap = max health
     */
    public SanityProvider(LivingEntity owner)
    {
        m_cap = new Sanity();
        m_cap.setOwner(owner);
        m_lazyOpt = LazyOptional.of(() -> m_cap);
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side)
    {
        return CAP.orEmpty(cap, m_lazyOpt);
    }

    @Override
    public CompoundTag serializeNBT()
    {
        CompoundTag nbt = new CompoundTag();
        m_cap.serializeNBT(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        m_cap.deserializeNBT(nbt);
    }
}
