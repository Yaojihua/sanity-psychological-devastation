package piloser.sanitypd.item;

import piloser.sanitypd.SanityProcessor;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.effect.EffectRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Sanity stabilizer: a food item that restores neither hunger nor saturation.
 *
 * <p>The three variants are named A/B/C with plain ASCII letters.
 *
 * <table border="1">
 *   <tr><th>Item</th><th>Effect</th><th>Cooldown</th></tr>
 *   <tr><td>{@link Kind#ALPHA} A</td><td>restores {@value #ALPHA_SANITY} sanity immediately</td><td>15 s</td></tr>
 *   <tr><td>{@link Kind#BETA} B</td><td>grants 3 minutes of mania immunity</td><td>5 min</td></tr>
 *   <tr><td>{@link Kind#GAMMA} C</td><td>grants 1 minute of inner immunity</td><td>2 min</td></tr>
 * </table>
 *
 * <h2>Cooldown</h2>
 * Cooldowns use the vanilla item cooldown (the grey radial overlay used by chorus fruit and
 * ender pearls) through {@link Player#getCooldowns()}{@code .addCooldown(this, ticks)}.
 * <ul>
 *   <li>It applies per {@link Item}, so every stack and slot of the same item shares one cooldown,
 *       and the three stabilizers are independent of each other;</li>
 *   <li>Vanilla {@code ItemCooldowns} only draws the overlay and does not block use, which is why
 *       chorus fruit and ender pearls can be spammed, so {@link #use} blocks explicitly on top of it.</li>
 * </ul>
 *
 * <p>Food properties use {@code nutrition(0) / saturationMod(0)} and {@code alwaysEat()}, so the
 * item can be consumed even on a full hunger bar.
 */
public class StabilizerItem extends Item
{
    /** The three stabilizer variants. Cooldowns are in ticks (20 ticks = 1 second). */
    public enum Kind
    {
        /** A: restores 35 sanity immediately, 15 s cooldown. */
        ALPHA(15 * 20, "item.sanitypd.stabilizer_a.tooltip"),
        /** B: 3 minutes of mania immunity, 5 min cooldown. */
        BETA(5 * 60 * 20, "item.sanitypd.stabilizer_b.tooltip"),
        /** C: 1 minute of inner immunity, 2 min cooldown. */
        GAMMA(2 * 60 * 20, "item.sanitypd.stabilizer_c.tooltip");

        private final int m_cooldownTicks;
        private final String m_tooltipKey;

        Kind(int cooldownTicks, String tooltipKey)
        {
            m_cooldownTicks = cooldownTicks;
            m_tooltipKey = tooltipKey;
        }

        public int cooldownTicks()
        {
            return m_cooldownTicks;
        }

        public String tooltipKey()
        {
            return m_tooltipKey;
        }
    }

    /** Sanity restored immediately by A. */
    public static final float ALPHA_SANITY = 35.0f;
    /** Mania immunity duration granted by B (ticks) = 3 minutes. */
    public static final int BETA_IMMUNITY_TICKS = 3 * 60 * 20;
    /** Inner immunity duration granted by C (ticks) = 1 minute, matching {@code InnerImmunityEffect.DURATION_TICKS}. */
    public static final int GAMMA_INNER_IMMUNITY_TICKS = 60 * 20;

    /** Shared food properties: no hunger or saturation restored, and edible on a full hunger bar. */
    public static FoodProperties stabilizerFood()
    {
        return new FoodProperties.Builder()
                .nutrition(0)
                .saturationMod(0.0f)
                .alwaysEat()
                .build();
    }

    private final Kind m_kind;

    public StabilizerItem(Kind kind, Properties properties)
    {
        super(properties);
        m_kind = kind;
    }

    public Kind kind()
    {
        return m_kind;
    }

    /**
     * Adds a single grey line describing both the effect and the cooldown.
     *
     * <p>The item restores no hunger and has a cooldown, so a line is shown directly rather than
     * through the shared "hold SHIFT for more" tooltips, which are meant for longer descriptions.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(m_kind.tooltipKey()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Blocks use while the cooldown is running.
     *
     * <p>Vanilla {@code ItemCooldowns} only draws the grey overlay and does not block use, so this
     * check is what makes the cooldown real. It also runs on the client, so the eating animation
     * does not start during the cooldown.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);

        return super.use(level, player, hand);
    }

    /** Applies the effect and starts the cooldown after the item is consumed. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    {
        // Superclass runs the eating flow; nutrition and saturation are both 0, so hunger is untouched.
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide && entity instanceof ServerPlayer player)
            applyEffect(player);

        return result;
    }

    private void applyEffect(ServerPlayer player)
    {
        switch (m_kind)
        {
            case ALPHA -> player.getCapability(SanityProvider.CAP)
                    .ifPresent(cap -> SanityProcessor.addSanity(cap, ALPHA_SANITY, player));

            case BETA -> player.addEffect(new MobEffectInstance(
                    EffectRegistry.MANIA_IMMUNITY.get(), BETA_IMMUNITY_TICKS, 0,
                    false /* ambient */, false /* no particles */, true /* show icon */));

            case GAMMA -> player.addEffect(new MobEffectInstance(
                    EffectRegistry.INNER_IMMUNITY.get(), GAMMA_INNER_IMMUNITY_TICKS, 0,
                    false /* ambient */, false /* no particles */, true /* show icon */));
        }

        // Cooldown is keyed by Item, so all stacks and slots of that item share it; the three
        // stabilizers are independent of each other.
        player.getCooldowns().addCooldown(this, m_kind.cooldownTicks());
    }
}
