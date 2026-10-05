package piloser.sanitypd.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import piloser.sanitypd.thought.ThoughtChainProvider;
import piloser.sanitypd.thought.ThoughtType;

/**
 * Client-only half of {@code ThoughtChainPacket}.
 *
 * <p>It exists so the packet class itself can stay free of client types. Forge's
 * {@code RuntimeDistCleaner} refuses to load a client-specific class in a dedicated server, and packet
 * registration happens during common setup on <b>both</b> sides - so anything the packet class touches at
 * class-load time has to exist on the server too. Keeping the client work behind this {@code @OnlyIn}
 * class means the packet never names {@code Minecraft} or {@code LocalPlayer}, and the client code is
 * only ever loaded when the handler actually runs on a client.
 */
@OnlyIn(Dist.CLIENT)
public final class ThoughtChainClientHandler
{
    /**
     * Adopts counts sent by the server into the local player's copy of the chain.
     *
     * <p>Reads the buffer here rather than in {@code decode}: the numbers are only needed once the work
     * runs on the client thread, and reading them on the network thread would touch the player too early.
     */
    public static void applyCounts(FriendlyByteBuf buf)
    {
        Player player = Minecraft.getInstance().player;

        if (player == null)
            return;

        int[] counts = new int[ThoughtType.values().length];

        for (int i = 0; i < counts.length; i++)
            counts[i] = buf.readVarInt();

        int active = buf.readVarInt();
        java.util.List<String> ids = new java.util.ArrayList<>(active);

        for (int i = 0; i < active; i++)
            ids.add(buf.readUtf());

        int equipped = buf.readVarInt();
        java.util.List<String> equippedIds = new java.util.ArrayList<>(equipped);

        for (int i = 0; i < equipped; i++)
            equippedIds.add(buf.readUtf());

        player.getCapability(ThoughtChainProvider.CAP).ifPresent(chain ->
        {
            chain.applyCounts(counts);
            chain.applyActiveMindsetIds(ids);
            chain.applyEquippedItemIds(equippedIds);
        });
    }

    private ThoughtChainClientHandler() {}
}
