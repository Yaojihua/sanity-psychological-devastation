package piloser.sanitypd.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import piloser.sanitypd.item.HiddenMemoryGrant;

/**
 * "This save's single allowance is still unspent", server to client.
 *
 * <h2>Why the client has to know</h2>
 * Speaking the hidden name is answered by <b>two</b> halves of the mod: the server decides the punishment (or
 * the macaron escape, or the hidden fragment), and the client - {@code client.HiddenNameWatcher} - closes the
 * game on the spot. The one fact the server cannot work out for itself is which story fragments this save has
 * watched through: those markers are files on the client. This packet is the announcement that tells the
 * client the server is ready for that answer, and the client replies with {@code net.MemoryProgressPacket}.
 * Until it did, the mask arrived only when a tape happened to be watched to its end, so a save whose four
 * fragments were watched in an earlier session left the server without the mask and never received the
 * hidden fragment.
 *
 * <p>The client also keeps the allowance for its own log line, but it does not decide anything from it: the
 * only thing that may keep the client quiet about the name is the one-shot excuse the server sets when it
 * actually hands the fragment over.
 *
 * <p><b>No client types here.</b> The client half is reached through {@link DistExecutor} into a client-only
 * holder, so a dedicated server never resolves it.
 */
public class HiddenAllowancePacket
{
    /** Whether the hidden fragment may still be earned in this save by this player. */
    public boolean m_available;

    public HiddenAllowancePacket()
    {
    }

    public HiddenAllowancePacket(boolean available)
    {
        m_available = available;
    }

    public static void encode(HiddenAllowancePacket packet, FriendlyByteBuf buf)
    {
        buf.writeBoolean(packet.m_available);
    }

    public static HiddenAllowancePacket decode(FriendlyByteBuf buf)
    {
        return new HiddenAllowancePacket(buf.readBoolean());
    }

    public static void handle(HiddenAllowancePacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();

        // Two things happen on the client when this arrives: the allowance is recorded for the log line, and
        // the client answers with the fragments this save has watched through. The answer is what the server
        // needs, so it is sent on every announcement - joining and respawning - and not only when a tape ends.
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> piloser.sanitypd.client.MemoryTapeGate.onServerAllowance(packet.m_available)));
        context.setPacketHandled(true);
    }
}
