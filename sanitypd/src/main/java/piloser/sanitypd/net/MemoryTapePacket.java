package piloser.sanitypd.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import piloser.sanitypd.event.MemoryTapeGuard;

/**
 * "I am watching the memory tape", client to server.
 *
 * <h2>Why the server has to be told</h2>
 * The tape is the one moment the player cannot react, and the owner asked for it to be damage-free "like
 * creative mode". Damage is settled on the server, and the sequence itself is client side, so there is
 * nothing for the server to derive it from. In single player the game is simply paused instead - but a
 * pause does not exist on a server, which is exactly why this packet does.
 *
 * <p><b>No client types here.</b> This class is loaded during common setup, on both sides, so it must not
 * name {@code Minecraft} or {@code LocalPlayer}; the sending side lives in a client-only class.
 */
public class MemoryTapePacket
{
    /** Whether the tape is being watched right now. */
    public boolean m_playing;

    public MemoryTapePacket()
    {
    }

    public MemoryTapePacket(boolean playing)
    {
        m_playing = playing;
    }

    public static void encode(MemoryTapePacket packet, FriendlyByteBuf buf)
    {
        buf.writeBoolean(packet.m_playing);
    }

    public static MemoryTapePacket decode(FriendlyByteBuf buf)
    {
        return new MemoryTapePacket(buf.readBoolean());
    }

    public static void handle(MemoryTapePacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();

        context.enqueueWork(() ->
        {
            if (player != null)
                MemoryTapeGuard.set(player, packet.m_playing);
        });
        context.setPacketHandled(true);
    }
}
