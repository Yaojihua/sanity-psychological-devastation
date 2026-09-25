package piloser.sanitypd.passive;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.config.ConfigProxy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nonnull;

public class Hungry implements IPassiveSanitySource
{
    @Override
    public float get(@Nonnull ServerPlayer player, @Nonnull ISanity cap, @Nonnull ResourceLocation dim)
    {
        if (player.getFoodData().getFoodLevel() <= ConfigProxy.getHungerThreshold(dim))
            return ConfigProxy.getHungry(dim);

        return 0;
    }
}
