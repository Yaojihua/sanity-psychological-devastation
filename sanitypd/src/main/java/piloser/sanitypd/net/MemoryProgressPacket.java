package piloser.sanitypd.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import piloser.sanitypd.item.HiddenMemoryGrant;

/**
 * "These are the fragments I have watched through", client to server.
 *
 * <h2>Why the server has to be told</h2>
 * The watch markers are per-save files the client writes (see {@code client.MemoryTapeRecord}); a server
 * cannot read them, and every allowance has to be decided on the server - it hands out an item and must not be
 * talkable into doing so twice. So the client reports its masks whenever they change, and the server keeps the
 * last ones it was told, in that player's own persistent data.
 *
 * <h2>Two masks, one report</h2>
 * {@link #m_mask} answers for the four story fragments ({@code ItemRegistry.MEMORY_FRAGMENT_ORDER}), which is
 * what "the story is finished" means. {@link #m_hiddenMask} answers for the hidden fragments
 * ({@code ItemRegistry.HIDDEN_FRAGMENT_ORDER}), because a word trigger's prerequisite is not always the end
 * poem: the vessel's is "the inner void's fragment was watched through". Both travel in one packet and are
 * written in one place, so a report can never refresh one mask and leave the other stale.
 *
 * <p><b>No client types here.</b> This class is loaded on both sides during setup; it only ever runs on the
 * server half.
 */
public class MemoryProgressPacket
{
    /** Bit i set means {@code ItemRegistry.MEMORY_FRAGMENT_ORDER.get(i)} has been watched through. */
    public int m_mask;

    /** Bit i set means {@code ItemRegistry.HIDDEN_FRAGMENT_ORDER.get(i)} has been watched through. */
    public int m_hiddenMask;

    public MemoryProgressPacket()
    {
    }

    public MemoryProgressPacket(int mask, int hiddenMask)
    {
        m_mask = mask;
        m_hiddenMask = hiddenMask;
    }

    public static void encode(MemoryProgressPacket packet, FriendlyByteBuf buf)
    {
        buf.writeVarInt(packet.m_mask);
        buf.writeVarInt(packet.m_hiddenMask);
    }

    public static MemoryProgressPacket decode(FriendlyByteBuf buf)
    {
        return new MemoryProgressPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MemoryProgressPacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();

        context.enqueueWork(() ->
        {
            if (player != null)
            {
                HiddenMemoryGrant.remember(player, packet.m_mask);
                HiddenMemoryGrant.rememberHidden(player, packet.m_hiddenMask);
            }
        });
        context.setPacketHandled(true);
    }
}
