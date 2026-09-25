package piloser.sanitypd.item;

import piloser.sanitypd.client.ItemTooltipHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Shadow Sword.
 *
 * <p>Mechanics are identical to the Shadow Axe (both share {@link ShadowCharge}); only the values
 * differ:
 * <ul>
 *   <li>Attack damage <b>8</b>: constructor value {@value #ATTACK_DAMAGE_MODIFIER} plus 3 for the
 *       diamond tier gives an item modifier of 7, plus the player base of 1, for 8 total attack
 *       damage (shown as "8 Attack Damage" in the tooltip)</li>
 *   <li>Extra <b>{@value #PSYCHIC_DAMAGE}</b> psychic damage on hit</li>
 *   <li>Max durability {@value ShadowCharge#MAX_DURABILITY}; charge duration, sanity cost ratio and
 *       interruption rules are the same as the axe</li>
 * </ul>
 */
public class ShadowSwordItem extends SwordItem implements IShadowWeapon
{
    /** Extra psychic damage on hit. */
    public static final float PSYCHIC_DAMAGE = 8f;

    /** Attack damage constructor value: 4 + 3 for the diamond tier = item modifier 7, so total 1 + 7 = 8. */
    public static final int ATTACK_DAMAGE_MODIFIER = 4;

    public ShadowSwordItem()
    {
        super(Tiers.DIAMOND, ATTACK_DAMAGE_MODIFIER, -2.4f,
                new Properties().durability(ShadowCharge.MAX_DURABILITY));
    }

    @Override
    public float shadowPsychicDamage()
    {
        return PSYCHIC_DAMAGE;
    }

    /** ID used for the tooltip translation key. */
    @Override
    public String shadowTooltipId()
    {
        return "shadow_sword";
    }

    // ------------------------------------------------------------------ charging (delegated to the shared component)

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return ShadowCharge.USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return ShadowCharge.USE_ANIM;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        // Shield priority: with a shield in the offhand, give up this right click (return PASS so
        // vanilla uses the offhand shield). startUsingItem must not be called here, otherwise the
        // sword would charge while the shield is raised.
        if (ShadowCharge.shouldYieldUseToShield(player, stack))
            return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseTicks)
    {
        ShadowCharge.emitParticles(level, living, remainingUseTicks);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft)
    {
        ShadowCharge.finish(stack, level, living, timeLeft);
    }

    // ------------------------------------------------------------------ tooltip and enchantment limits

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        // Attack damage and attack speed lines come from the vanilla attribute system. The "psychic
        // damage" line is inserted by ItemTooltipHelper below the attribute lines after the tooltip
        // is assembled (reusing the attribute line blue), so it is not added here.
        ItemTooltipHelper.showTooltipOnShift(tooltip, "shadow_sword");
    }

    /** No Mending (this covers the enchanting table path; the anvil path is blocked by EventHandler#onAnvilUpdate). */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
    {
        return ShadowCharge.canApplyAtEnchantingTable(enchantment) && super.canApplyAtEnchantingTable(stack, enchantment);
    }
}
