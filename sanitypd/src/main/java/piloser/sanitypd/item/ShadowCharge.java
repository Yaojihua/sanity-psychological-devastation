package piloser.sanitypd.item;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import org.joml.Vector3f;

/**
 * Shared charge-to-repair component for the shadow sword and shadow axe.
 *
 * <p>Both weapons need exactly the same mechanic, so the logic lives here once and the item classes
 * only forward to it, which keeps the two from drifting apart through copy and paste.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>Hold right click to charge, using the <b>bow pull animation</b> ({@link #USE_ANIM}), with dark
 *       red particles while charging</li>
 *   <li>Releasing before {@link #CHARGE_TICKS} (4 seconds) aborts and nothing happens</li>
 *   <li>When fully charged, sanity is spent on the <b>durability actually repaired</b>: each point of
 *       sanity restores {@link #DURABILITY_PER_SANITY} durability and the cost is rounded <b>up</b>;
 *       a full durability weapon costs nothing; if sanity is short, only the affordable part is
 *       repaired and sanity drops to 0</li>
 *   <li>Mending cannot be applied (blocked at the enchanting table by {@link #canApplyAtEnchantingTable}
 *       and at the anvil by {@link #blocksAnvil})</li>
 *   <li>With a shield in the offhand the <b>shield takes priority</b>: holding right click is handled
 *       by the offhand shield and the shadow weapon does not charge at all, see
 *       {@link #shouldYieldUseToShield(Player, ItemStack)}</li>
 * </ul>
 */
public final class ShadowCharge
{
    /** Hold time needed to charge fully: 4 seconds = 80 ticks. */
    public static final int CHARGE_TICKS = 80;

    /** Maximum durability. */
    public static final int MAX_DURABILITY = 1500;

    /** Durability restored per point of sanity spent. */
    public static final int DURABILITY_PER_SANITY = 5;

    /** Allows holding indefinitely; the real check is the 4 second test in {@link #finish}. */
    public static final int USE_DURATION = 72000;

    /** Bow pull animation; both first and third person poses come from the vanilla animation. */
    public static final UseAnim USE_ANIM = UseAnim.BOW;

    /** Charge particle colour: dark red. */
    private static final Vector3f CHARGE_COLOR = new Vector3f(.52f, .03f, .03f);

    /** Particle interval in ticks, to keep them from being too dense. */
    private static final int PARTICLE_INTERVAL = 3;

    private ShadowCharge() {}

    /** Emits the dark red particles while charging. */
    public static void emitParticles(Level level, LivingEntity living, int remainingUseTicks)
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        int used = USE_DURATION - remainingUseTicks;
        if (used <= 0 || used % PARTICLE_INTERVAL != 0)
            return;

        Vec3 pos = living.getEyePosition()
                .add(living.getLookAngle().scale(.6d))
                .add(0d, -.35d, 0d);
        serverLevel.sendParticles(new DustParticleOptions(CHARGE_COLOR, 1.0f),
                pos.x, pos.y, pos.z, 2, .18d, .18d, .18d, 0d);
    }

    /**
     * Whether the offhand holds a shield that can be raised.
     *
     * <p>The check uses the official Forge semantics, {@link ToolActions#SHIELD_BLOCK}, the same action
     * vanilla's shield compares {@code ToolActions.DEFAULT_SHIELD_ACTIONS} against, so it holds for the
     * vanilla shield and for any modded shield registering that action.
     *
     * <p><b>The cooldown is deliberately not checked</b>: a shield on cooldown still counts as "having a
     * shield", so the weapon does not charge. Raising the vanilla shield starts a 1 second cooldown
     * ({@code minecraft:shield} in {@code items.json}), and falling back to charging during it would
     * produce an inexplicable behaviour shift right after lowering the shield.
     *
     * <p>Usage: {@link #shouldYieldUseToShield(Player, ItemStack)}.
     */
    private static boolean hasOffhandShield(Player player)
    {
        return player.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /**
     * "Shield priority" check: when this is {@code true} the shadow weapon must cede this right click.
     *
     * <p>Why returning {@code PASS} is the mechanism: the vanilla client
     * {@code Minecraft#startUseItem} walks {@code InteractionHand.values()} and only reaches the offhand
     * when the current hand's {@code use()} does not call {@code consumesAction()}. The shadow weapon
     * returns {@code consume}, so the offhand shield never gets a turn and holding right click only
     * charges the weapon. Returning {@code PASS} from the main hand makes vanilla fall through to the
     * offhand shield. Client and server run the same {@code use()}, so both sides agree and no extra
     * network synchronisation is needed.
     *
     * <p>The shield always wins: while a shield is in the offhand the weapon never charges, and a player
     * who wants to charge must move the shield to the other hand or put it away.
     *
     * @param player the user
     * @param stack  the main hand stack this right click applies to, which must be one of this mod's shadow weapons
     * @return whether this right click should be yielded to the offhand shield
     */
    public static boolean shouldYieldUseToShield(Player player, ItemStack stack)
    {
        // Guard: only shadow weapons can yield; this method may be reused by other entry points.
        return stack.getItem() instanceof IShadowWeapon && hasOffhandShield(player);
    }

    /**
     * Settles the charge when the button is released.
     *
     * <p>Using it for less than {@link #CHARGE_TICKS} returns immediately, which is the abort case and
     * consumes nothing.
     */
    public static void finish(ItemStack stack, Level level, LivingEntity living, int timeLeft)
    {
        if (level.isClientSide() || !(living instanceof ServerPlayer player))
            return;

        int used = USE_DURATION - timeLeft;
        if (used < CHARGE_TICKS)
            return;

        // The amount to repair is the durability already lost, which is getDamageValue() (0 = undamaged).
        // This was once written as getMaxDamage() - getDamageValue(), which is the *remaining* durability,
        // so a full durability weapon looked like it was missing 1500 and demanded 300 sanity: one charge
        // drained all sanity and repaired nothing. Do not change it back.
        int missing = stack.getDamageValue();
        if (missing <= 0)
            return;                 // full durability: no sanity is spent

        ISanity cap = player.getCapability(SanityProvider.CAP).orElse(null);
        if (cap == null)
            return;

        // Sanity needed to close the gap completely, rounded up
        int needed = (int) Math.ceil(missing / (double) DURABILITY_PER_SANITY);
        int available = (int) Math.floor(cap.getSanity());
        // With too little sanity, repair only the affordable part: within the available sanity budget,
        // then charge for what was actually repaired (also rounded up), so payment always matches repair.
        int spend = Math.min(needed, available);
        if (spend <= 0)
            return;

        int repair = Math.min(missing, spend * DURABILITY_PER_SANITY);
        // Derive the real cost from the amount actually repaired, rounded up.
        // repair <= spend * 5 implies cost <= spend <= available, so this can never overspend.
        int cost = (int) Math.ceil(repair / (double) DURABILITY_PER_SANITY);

        cap.setSanity(cap.getSanity() - cost);
        stack.setDamageValue(stack.getDamageValue() - repair);
    }

    /** Whether this enchantment is allowed at the enchanting table (Mending never is). */
    public static boolean canApplyAtEnchantingTable(Enchantment enchantment)
    {
        return enchantment != Enchantments.MENDING;
    }

    /**
     * Whether the anvil combination must be blocked: blocked when the right hand item carries Mending.
     *
     * <p>Read through {@link EnchantmentHelper#getEnchantments}, which special-cases enchanted books, so
     * both an enchanted book and already enchanted gear are recognised.
     */
    public static boolean blocksAnvil(ItemStack right)
    {
        return !right.isEmpty() && EnchantmentHelper.getEnchantments(right).containsKey(Enchantments.MENDING);
    }
}
