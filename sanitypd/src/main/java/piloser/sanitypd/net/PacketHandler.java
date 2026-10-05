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
    }
}