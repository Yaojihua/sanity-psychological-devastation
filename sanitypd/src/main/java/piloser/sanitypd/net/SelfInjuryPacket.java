package piloser.sanitypd.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The self-injury thought's three announcements, server to client.
 *
 * <h2>Why the client cannot work them out itself</h2>
 * The whole mechanic is a server-side roll: how long sanity has been low, whether this period's chance passed,
 * and when the wound lands are all decided on the server (see {@code thought.SelfInjury}). The client owns the
 * presentation - fading the inner lines out, pulling the view out of focus, ducking the sound, the blood
 * tendrils and the three clips - and each of those has to start at a different moment, so it needs to be told
 * which of the three moments has arrived rather than poll a state.
 *
 * <p><b>No client types here.</b> This class is loaded on both sides during setup; the client half is reached
 * through {@link DistExecutor} into a client-only holder, so a dedicated server never resolves it.
 */
public class SelfInjuryPacket
{
    /** The act is {@code SELF_INJURY_WARN_TICKS} away: start the five-second wind-down. */
    public static final byte WARN = 0;
    /** The act just happened: flash, blur, ghost, duck and play the clips. */
    public static final byte ACT = 1;
    /** A wound that was announced is not coming after all: drop the wind-down. */
    public static final byte CANCEL = 2;

    /** One of {@link #WARN}, {@link #ACT} or {@link #CANCEL}. */
    public byte m_mode;

    public SelfInjuryPacket()
    {
    }

    public SelfInjuryPacket(byte mode)
    {
        m_mode = mode;
    }

    public static void encode(SelfInjuryPacket packet, FriendlyByteBuf buf)
    {
        buf.writeByte(packet.m_mode);
    }

    public static SelfInjuryPacket decode(FriendlyByteBuf buf)
    {
        return new SelfInjuryPacket(buf.readByte());
    }

    public static void handle(SelfInjuryPacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> piloser.sanitypd.client.SelfInjuryPresentation.onPacket(packet.m_mode)));
        context.setPacketHandled(true);
    }
}
