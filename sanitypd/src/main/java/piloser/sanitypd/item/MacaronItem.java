package piloser.sanitypd.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import piloser.sanitypd.effect.EffectRegistry;
import piloser.sanitypd.effect.SanityRegenEffect;

/**
 * Macaron: a small cake in eight colours.
 *
 * <p>Eating one:
 * <ul>
 *   <li>restores 3 hunger points and 3 saturation (the {@code 6 / 6} the food properties are built
 *       from, since both values are counted in half-units here);</li>
 *   <li>grants Regeneration II for {@value #REGEN_TICKS} ticks (15 seconds);</li>
 *   <li>grants slow sanity recovery for {@link SanityRegenEffect#DURATION_TICKS} ticks (25 seconds,
 *       {@link SanityRegenEffect#PER_SECOND} point per second);</li>
 *   <li>starts a {@value #COOLDOWN_TICKS} tick (5 second) cooldown.</li>
 * </ul>
 *
 * <h2>The cooldown is shared by all eight colours</h2>
 * Vanilla {@code ItemCooldowns} is keyed by {@link Item}, so each colour would otherwise have a
 * cooldown of its own and the player could chain eight macarons in a row. To make the eight share
 * one, every macaron is put on cooldown when any of them is eaten ({@link #startSharedCooldown}).
 * That is also the honest reading of the rule: while the five seconds run, no macaron of any colour
 * can be eaten, and every colour shows the same grey sweep.
 *
 * <h2>Eating again replaces the effect</h2>
 * The cooldown is 10 seconds while the effect lasts 25, so a player can still eat again before it
 * ends. The duration is not extended: the fresh instance simply replaces the old one, which is the
 * vanilla behaviour for {@code addEffect}. The same is true of the Regeneration effect.
 *
 * <p>Food properties do <b>not</b> use {@code alwaysEat()}: a macaron cannot be eaten on a full hunger
 * bar. The sanity and Regeneration parts therefore no longer bypass the hunger gate - a player who wants
 * them has to make room first. That is the owner's ruling: eating is eating, and a full stomach refuses
 * the food whatever else the food would have done.
 */
public class MacaronItem extends Item
{
    /** Regeneration duration, in ticks: 15 seconds. */
    public static final int REGEN_TICKS = 15 * 20;
    /** Regeneration amplifier: 1 means level II. */
    public static final int REGEN_AMPLIFIER = 1;
    /** Cooldown shared by every macaron colour, in ticks: 10 seconds. */
    public static final int COOLDOWN_TICKS = 10 * 20;

    /** Hunger restored, in the half-units the food properties use (3 hunger points). */
    public static final int NUTRITION = 6;

    /**
     * Saturation modifier. The saturation a food actually adds is
     * {@code nutrition * saturationMod * 2}, and it is then capped at the hunger level, so this value is
     * <b>not</b> a saturation amount and has to be derived rather than written down.
     *
     * <p><b>Do not set this to 6.</b> The first version did, meaning to say "3 saturation points", and the
     * game read it as {@code 6 * 6 * 2 = 72} saturation - caught by AppleSkin, which showed the macaron as
     * restoring 36 drumsticks. {@value #SATURATION_MOD} is what yields exactly {@value #SATURATION_GAIN}
     * saturation from {@value #NUTRITION} hunger, which is the intended 3 saturation points.
     */
    public static final float SATURATION_MOD = 0.5f;

    /**
     * Saturation actually added by one macaron, as the game computes it ({@code nutrition * mod * 2}).
     *
     * <p>A derived constant rather than a literal, so the intent stays visible next to the modifier.
     *
     * <p><b>Note for anyone checking this value:</b> eating with a full hunger bar is no longer a way to read
     * it. That relied on {@code alwaysEat()}; the item does not have it any more, so a full bar refuses the
     * food and the assertion would pass for the wrong reason. A check has to leave room in the bar before
     * eating, or read the food properties directly. (No check asserts this value today - the earlier
     * reference to a self-check here pointed at code that no longer existed.)
     */
    public static final float SATURATION_GAIN = NUTRITION * SATURATION_MOD * 2.0f;

    /**
     * The eight colours, in the order the creative tab lists them.
     *
     * <p>The enum constant name is also the item id suffix ({@code macaron_red} and so on) and the
     * language key stem, so adding a colour means adding: an enum constant here, a registry entry, a
     * texture, a model, a recipe and four language entries (name and tooltip, both languages).
     *
     * <p>The fifth one is {@code light_blue}, not {@code cyan}: the texture file is named after the
     * art's cyan, but the dye that crafts it and the name the player reads are both light blue, and
     * those are what the item id follows.
     */
    public enum Colour
    {
        RED("red"),
        ORANGE("orange"),
        YELLOW("yellow"),
        GREEN("green"),
        LIGHT_BLUE("light_blue"),
        BLUE("blue"),
        PURPLE("purple"),
        PINK("pink");

        private final String m_id;

        Colour(String id)
        {
            m_id = id;
        }

        /** Item id suffix, e.g. {@code red} for {@code sanitypd:macaron_red}. */
        public String id()
        {
            return m_id;
        }
    }

    /**
     * Shared food properties: restores a little hunger and saturation, and is <b>not</b> edible when full.
     *
     * <p>{@code alwaysEat()} is deliberately absent, so vanilla's own hunger gate applies and this item
     * behaves like every other food. Removing it is a behaviour change, not a tidy-up: the sanity and
     * Regeneration parts are granted by eating, so they are now gated by hunger too.
     */
    public static FoodProperties macaronFood()
    {
        return new FoodProperties.Builder()
                .nutrition(NUTRITION)
                .saturationMod(SATURATION_MOD)
                .build();
    }

    private final Colour m_colour;

    public MacaronItem(Colour colour, Properties properties)
    {
        super(properties);
        m_colour = colour;
    }

    public Colour colour()
    {
        return m_colour;
    }

    /**
     * Two grey lines: what the macaron is, and what eating one does.
     *
     * <p>The colour is part of each language string (the {@code §7} prefix) rather than applied here, so
     * both lines carry exactly the wording and formatting the brief asked for and a translator can see
     * the whole line in one place.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.sanitypd.macaron.flavour"));
        tooltip.add(Component.translatable("item.sanitypd.macaron.tooltip"));
    }

    /**
     * Blocks use while the shared cooldown is running.
     *
     * <p>Vanilla {@code ItemCooldowns} only draws the grey overlay and does not block use (chorus fruit
     * and ender pearls can be spammed), so this check is what makes the cooldown real. It also runs on
     * the client, so the eating animation does not start during the cooldown.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);

        return super.use(level, player, hand);
    }

    /** Applies the effects and starts the shared cooldown once the macaron is eaten. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide && entity instanceof Player player)
        {
            // replaceCurrent: eating a second macaron before the first one ends replaces it instead of
            // extending it, which is what the brief asks for.
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, REGEN_AMPLIFIER,
                    false /* ambient */, false /* no particles */, true /* show icon */));

            player.addEffect(new MobEffectInstance(EffectRegistry.SANITY_REGEN.get(),
                    SanityRegenEffect.DURATION_TICKS, 0,
                    false /* ambient */, false /* no particles */, false /* hidden icon */));

            startSharedCooldown(player);
        }

        return result;
    }

    /**
     * Puts every macaron colour on cooldown, so the eight share a single five-second window.
     *
     * <p>Called on both sides (see {@link #finishUsingItem}); the vanilla cooldown map exists on the
     * client too, and the client copy is what draws the grey sweep over the hotbar.
     */
    public static void startSharedCooldown(Player player)
    {
        for (RegistryObject<Item> macaron : ItemRegistry.MACARONS())
        {
            if (macaron.isPresent())
                player.getCooldowns().addCooldown(macaron.get(), COOLDOWN_TICKS);
        }
    }
}
