package piloser.sanitypd.passive;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.config.ConfigProxy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public class PlayerCompany implements IPassiveSanitySource
{
    /** Expensive: nearby-player scan plus a fresh AABB / TargetingConditions per tick. */
    @Override
    public boolean isExpensive()
    {
        return true;
    }

    @Override
    public float get(@Nonnull ServerPlayer player, @Nonnull ISanity cap, @Nonnull ResourceLocation dim)
    {
        // FIXME: untested

        AtomicReference<Float> result = new AtomicReference<>(0.0f);
        AtomicReference<Boolean> flag = new AtomicReference<>(false);
        Vec3 offset = new Vec3(8.0d, 8.0d, 8.0d);
        float sane = ConfigProxy.getSanePlayerCompany(dim);
        float insane = ConfigProxy.getInsanePlayerCompany(dim);

        for (Player p : player.level().getNearbyPlayers(TargetingConditions.forNonCombat(), player, new AABB(
                player.position().subtract(offset),
                player.position().add(offset)
        )))
        {
            p.getCapability(SanityProvider.CAP).ifPresent(s ->
            {
                if (s.getMadness() >= .5f)
                {
                    result.set(insane);
                    flag.set(true);
                }
                else
                    result.set(sane);
            });

            if (flag.get())
                break;
        }

        return result.get();
    }
}