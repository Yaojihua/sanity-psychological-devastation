package piloser.sanitypd.mixin;

import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.client.render.layer.Blackout;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class MixinAnimal
{
    @Inject(method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true)
    private void mobInteract(Player pPlayer, InteractionHand pHand, CallbackInfoReturnable<InteractionResult> ci)
    {
        if (!pPlayer.level().isClientSide())
        {
            pPlayer.getCapability(SanityProvider.CAP).ifPresent(s ->
            {
                if (s.getMadness() >= Blackout.THRESHOLD)
                    ci.setReturnValue(InteractionResult.PASS);
            });
        }
    }
}