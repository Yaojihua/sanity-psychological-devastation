package piloser.sanitypd.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import piloser.sanitypd.thought.IThoughtChain;

/**
 * The six per-type counts of a player's thought chain, server to client.
 *
 * <p>Only the counts are sent, never the items: the client can see the <b>contents</b> through the
 * container protocol while the screen is open, but a tooltip needs the counts wherever the item happens to
 * be (inventory, a chest, JEI), which is when no container is open. Six var-ints per change is the price
 * of that, and it is paid only when something actually changes.
 *
 * <p><b>No client types here, deliberately.</b> This class is loaded during common setup (packet
 * registration runs on both sides), so naming {@code Minecraft} or {@code LocalPlayer} - even inside a
 * lazily-run lambda - is what makes the dedicated server refuse to start with
 * {@code Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist
 * DEDICATED_SERVER}. The client work lives in {@code ThoughtChainClientHandler}, behind
 * {@link DistExecutor}.
 */
public class ThoughtChainPacket
{
    /** Server side: the counts to send. Null on the receiving side. */
    public int[] m_counts;

    /** Server side: the active mindset ids, oldest first. Null on the receiving side. */
    public java.util.List<String> m_activeIds = java.util.List.of();

    /** Server side: registry names of the equipped items, so the tooltip can tell "this one" from "its type". */
    public java.util.List<String> m_equippedIds = java.util.List.of();

    /** Receiving side: the raw buffer, read once the handler runs on the client thread. */
    public FriendlyByteBuf m_buf;

    public ThoughtChainPacket()
    {
    }

    public ThoughtChainPacket(int[] counts, java.util.List<String> activeIds, java.util.List<String> equippedIds)
    {
        this.m_counts = counts;
        this.m_activeIds = java.util.List.copyOf(activeIds);
        this.m_equippedIds = java.util.List.copyOf(equippedIds);
    }

    public static void encode(ThoughtChainPacket packet, FriendlyByteBuf buf)
    {
        for (int count : packet.m_counts)
            buf.writeVarInt(count);

        buf.writeVarInt(packet.m_activeIds.size());

        for (String id : packet.m_activeIds)
            buf.writeUtf(id);

        buf.writeVarInt(packet.m_equippedIds.size());

        for (String id : packet.m_equippedIds)
            buf.writeUtf(id);
    }

    public static ThoughtChainPacket decode(FriendlyByteBuf buf)
    {
        ThoughtChainPacket packet = new ThoughtChainPacket();
        packet.m_buf = buf;
        return packet;
    }

    public static void handle(ThoughtChainPacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        if (packet.m_buf == null)
            return;

        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> piloser.sanitypd.client.ThoughtChainClientHandler.applyCounts(packet.m_buf)));
        ctx.get().setPacketHandled(true);
    }

    /** Pushes the current counts of a chain to its owner. */
    public static void send(ServerPlayer player, IThoughtChain chain)
    {
        // A server player is placed in the world - and its capabilities deserialized - before the server
        // attaches the connection, so an early push would dereference a null connection and take the whole
        // join down with it ("Loading entity NBT", 2026-10-02). Fake players never have one either.
        if (player.connection == null)
            return;

        PacketHandler.CHANNEL_INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new ThoughtChainPacket(chain.counts(), chain.activeMindsetIds(), chain.equippedItemIds()));
    }
}
