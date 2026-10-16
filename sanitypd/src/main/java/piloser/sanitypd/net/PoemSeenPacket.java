package piloser.sanitypd.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * "You have watched the end poem", server to client.
 *
 * <h2>Why this packet has to exist</h2>
 * The memory tapes may only be watched by a player who has seen the end poem, and the authoritative record of
 * that is vanilla's own {@code ServerPlayer.seenCredits} - the flag the third voice already reads, which also
 * covers a save that beat the dragon before this mod was installed.
 *
 * <p>Vanilla never sends that flag to clients, and the tape's refusal has to happen on the <b>client</b>: the
 * charge is driven by the client's own item-use state, so a refusal that only happened on the server would
 * still let the charge play before the player is told no. Reading the integrated server's player directly
 * (the third voice's fallback) works in single player but is impossible on a remote server, where it would
 * make the mod refuse a player who has every right to watch. Hence one boolean, pushed when it changes.
 *
 * <p><b>No client types here.</b> This class is loaded on both sides during setup; the client half is reached
 * through {@link DistExecutor} into a client-only holder, so a dedicated server never resolves it.
 */
public class PoemSeenPacket
{
    /** Whether the server's record says this player has watched the end poem. */
    public boolean m_seen;

    public PoemSeenPacket()
    {
    }

    public PoemSeenPacket(boolean seen)
    {
        m_seen = seen;
    }

    public static void encode(PoemSeenPacket packet, FriendlyByteBuf buf)
    {
        buf.writeBoolean(packet.m_seen);
    }

    public static PoemSeenPacket decode(FriendlyByteBuf buf)
    {
        return new PoemSeenPacket(buf.readBoolean());
    }

    public static void handle(PoemSeenPacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> piloser.sanitypd.client.MemoryTapeGate.setPoemSeen(packet.m_seen)));
        context.setPacketHandled(true);
    }
}
