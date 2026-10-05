package piloser.sanitypd.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Whether a non-mild inner line is on screen right now, client to server.
 *
 * <h2>Why the server has to be told</h2>
 * Inner lines are drawn entirely on the client ({@code GuiHandler}), but the "Command Hallucination"
 * thought grants a <b>server-side attribute</b> while such a line is showing. The server has no way to
 * derive that state - it is not a function of sanity, because the deep and expiry windows show lines at
 * sanities that also show mild ones - so the client reports it.
 *
 * <h2>Only the interesting half is reported</h2>
 * The flag is true for the severe, deep, expiry and extra inner-voice lines and <b>false for the mild
 * tier</b>, which is the owner's rule: the bonus does not apply to mild lines. That also keeps the mild
 * tier - by far the most frequent - from producing any traffic at all.
 *
 * <p><b>No client types here.</b> This class is loaded during common setup, on both sides, so it must not
 * name {@code Minecraft} or {@code LocalPlayer}.
 */
public class HintStatePacket
{
    /** Receiving side: whether a non-mild inner line is on screen. */
    public boolean m_active;

    public HintStatePacket()
    {
    }

    public HintStatePacket(boolean active)
    {
        m_active = active;
    }

    public static void encode(HintStatePacket packet, FriendlyByteBuf buf)
    {
        buf.writeBoolean(packet.m_active);
    }

    public static HintStatePacket decode(FriendlyByteBuf buf)
    {
        return new HintStatePacket(buf.readBoolean());
    }

    public static void handle(HintStatePacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();

        context.enqueueWork(() ->
        {
            if (player != null)
                piloser.sanitypd.thought.HintState.set(player, packet.m_active);
        });
        context.setPacketHandled(true);
    }
}
