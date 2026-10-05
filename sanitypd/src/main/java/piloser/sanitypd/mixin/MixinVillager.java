package piloser.sanitypd.mixin;

import piloser.sanitypd.capability.SanityProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.thought.ThoughtEffects;

@Mixin(Villager.class)
public abstract class MixinVillager
{
    @Shadow protected abstract void setUnhappy();

    @Inject(method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true)
    private void mobInteract(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> ci)
    {
        if (!player.level().isClientSide())
        {
            player.getCapability(SanityProvider.CAP).ifPresent(s ->
            {
                // Duplicity: the villager no longer turns the player away at low sanity. Both halves are
                // skipped on purpose - refusing to trade and going unhappy are the same symptom, and leaving
                // the mood change behind would keep the villager visibly upset over a trade that just happened.
                if (s.getMadness() >= .6f
                        && !ThoughtEffects.isEquipped(player, ItemRegistry.THOUGHT_DUPLICITY.get()))
                {
                    this.setUnhappy();
                    ci.setReturnValue(InteractionResult.PASS);
                }
            });
        }
    }

    /**
     * Socialization: villagers ask less while the thought is in the chain and sanity is above half.
     *
     * <p>Injected at the <b>tail</b> of vanilla's own price pass, which is the only place the numbers are
     * already known: reputation and Hero of the Village are folded in there through
     * {@code addToSpecialPriceDiff}, so this only has to add its own share.
     *
     * <p>The share is a percentage of the <b>base</b> price rather than of the already-reduced one, which is
     * what "settled first" means: the three sources then come off the same base and stack additively,
     * instead of compounding - a percentage of a percentage would make Hero of the Village quietly weaker
     * for a player holding this thought.
     *
     * <p>Nothing is persisted and nothing leaks between players. The offer's price difference is per trade
     * session: vanilla recomputes it in {@code startTrading} (its one and only caller) and clears it in
     * {@code stopTrading}, so this runs once per session and the next player to open the villager gets a
     * clean slate.
     */
    @Inject(method = "updateSpecialPrices(Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void sanitypd$applySocialization(Player player, CallbackInfo ci)
    {
        float discount = ThoughtEffects.tierValue(player, ItemRegistry.THOUGHT_SOCIALIZATION.get());

        if (discount <= 0f)
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            // Madness, not sanity: the effect needs sanity ABOVE half, so at or above half madness it is off.
            // sanity > 50% is the same statement as madness < 50%; the threshold itself comes from the one
            // place that also prints it, so the price and the sentence cannot disagree.
            if (s.getMadness() >= 1f - ThoughtEffects.sanityThreshold(ItemRegistry.THOUGHT_SOCIALIZATION.get()))
                return;

            for (MerchantOffer offer : ((Villager) (Object) this).getOffers())
            {
                int cut = Math.round(offer.getBaseCostA().getCount() * discount);

                // Rounded down to nothing on a one-item offer at the low tiers, which is the honest answer:
                // "15% off" of a single emerald is not a whole emerald.
                if (cut > 0)
                    offer.addToSpecialPriceDiff(-cut);
            }
        });
    }
}