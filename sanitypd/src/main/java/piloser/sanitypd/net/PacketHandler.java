package piloser.sanitypd.net;

import piloser.sanitypd.SanityMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler
{
    private static int packetId = 0;

    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL_INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SanityMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void init()
    {
        CHANNEL_INSTANCE.registerMessage(packetId++, SanityPacket.class, SanityPacket::encode, SanityPacket::decode, SanityPacket::handle);
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                InnerEntityCapImplPacket.class,
                InnerEntityCapImplPacket::encode,
                InnerEntityCapImplPacket::decode,
                InnerEntityCapImplPacket::handle);
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                ThoughtChainPacket.class,
                ThoughtChainPacket::encode,
                ThoughtChainPacket::decode,
                ThoughtChainPacket::handle);
        // Client to server: whether a non-mild inner line is on screen (Command Hallucination).
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                HintStatePacket.class,
                HintStatePacket::encode,
                HintStatePacket::decode,
                HintStatePacket::handle);
        // Client to server: whether the memory tape is being watched (creative-like protection).
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                MemoryTapePacket.class,
                MemoryTapePacket::encode,
                MemoryTapePacket::decode,
                MemoryTapePacket::handle);
        // Client to server: which story fragments this save has watched through (the hidden fragment's gate).
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                MemoryProgressPacket.class,
                MemoryProgressPacket::encode,
                MemoryProgressPacket::decode,
                MemoryProgressPacket::handle);
        // Server to client: whether this save still has the hidden fragment's single allowance.
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                HiddenAllowancePacket.class,
                HiddenAllowancePacket::encode,
                HiddenAllowancePacket::decode,
                HiddenAllowancePacket::handle);
        // Server to client: whether vanilla's record says the end poem has been watched (the tape gate).
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                PoemSeenPacket.class,
                PoemSeenPacket::encode,
                PoemSeenPacket::decode,
                PoemSeenPacket::handle);
        // Server to client: the three moments of the self-injury thought's act (announced / happened /
        // withdrawn). The roll is server-side, the presentation is client-side, so the two halves are joined
        // here.
        CHANNEL_INSTANCE.registerMessage(
                packetId++,
                SelfInjuryPacket.class,
                SelfInjuryPacket::encode,
                SelfInjuryPacket::decode,
                SelfInjuryPacket::handle);
    }

    /**
     * Tells one player which moment of the self-injury act has arrived.
     *
     * <p>Sent three times at most per act ({@code WARN}, {@code ACT}, and {@code CANCEL} when an announced
     * wound is withdrawn), each from the one place that decides it: {@code thought.SelfInjury}.
     */
    public static void sendSelfInjury(net.minecraft.server.level.ServerPlayer player, byte mode)
    {
        // A fake player (the self-checks) has no connection, and the distributor would throw on it - which reads
        // as "the mod broke the server" rather than "there was nobody to tell". The guard belongs here rather
        // than at the three call sites, so no future caller can forget it.
        if (player == null || player.connection == null)
            return;

        CHANNEL_INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new SelfInjuryPacket(mode));
    }

    /**
     * Tells one player whether the memory tapes may be watched.
     *
     * <p>The flag itself is vanilla's private {@code ServerPlayer.seenCredits}: it is never synced by the game,
     * and the refusal has to happen on the client (the charge is driven by the client's item-use state), so
     * the server has to push it. Sent on join, on respawn and whenever the value changes.
     */
    public static void sendPoemSeen(net.minecraft.server.level.ServerPlayer player, boolean seen)
    {
        CHANNEL_INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new PoemSeenPacket(seen));
    }

    /**
     * Tells one player whether the hidden fragment's single allowance is still unspent.
     *
     * <p>Sent on join, on respawn and at the moment it is spent: the client half of the hidden name needs it,
     * because that half is what closes the game and it cannot read the player's saved data.
     */
    public static void sendHiddenAllowance(net.minecraft.server.level.ServerPlayer player, boolean available)
    {
        CHANNEL_INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new HiddenAllowancePacket(available));
    }
}