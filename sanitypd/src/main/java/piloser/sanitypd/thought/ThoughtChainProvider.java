package piloser.sanitypd.thought;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

import piloser.sanitypd.SanityMod;

/**
 * Capability provider for a player's thought chain.
 *
 * <p>Attached to players only (see {@code EventHandler#attachEntityCaps}). It mirrors the sanity
 * provider's shape so there is one pattern in the project: a {@code KEY} for
 * {@code AttachCapabilitiesEvent}, a {@code CAP} token for lookups, and {@code ICapabilitySerializable}
 * so Forge writes and reads the contents with the player's own data.
 *
 * <p>The provider holds the owner because the chain has to know which side it is on (see
 * {@link ThoughtChain#authoritative()}), and because pushing counts needs the owning
 * {@code ServerPlayer}.
 */
public class ThoughtChainProvider implements ICapabilitySerializable<CompoundTag>
{
    public static final ResourceLocation KEY = new ResourceLocation(SanityMod.MODID, "thought_chain");
    public static final Capability<IThoughtChain> CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private final ThoughtChain m_cap = new ThoughtChain();
    private final LazyOptional<IThoughtChain> m_lazyOpt = LazyOptional.of(() -> m_cap);

    /**
     * @param owner the owning player; used to tell the server copy from the client copy and to address
     *              count updates
     */
    public ThoughtChainProvider(Player owner)
    {
        m_cap.setOwner(owner);
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
