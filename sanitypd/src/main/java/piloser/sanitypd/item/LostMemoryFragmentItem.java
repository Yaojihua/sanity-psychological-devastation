package piloser.sanitypd.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A memory fragment: a story item and nothing else. It has <b>no gameplay effect at all</b>.
 *
 * <p>Holding right click runs the presentation, which lives entirely on the client (see
 * {@code client.MemorySequence}): the HUD is hidden while the hand and the held item stay visible, the
 * charge sound starts after one second, and when that clip ends the screen goes black and the fragment's
 * tape plays. Releasing early aborts it silently.
 *
 * <h2>The id carries everything</h2>
 * Each fragment is told its own id, which names its two script files
 * ({@code assets/sanitypd/memory/<id>_<language>.txt}), its watch marker and its language keys. That is
 * what makes a second fragment two text files and one registration instead of a second copy of the
 * playback code.
 */
public class LostMemoryFragmentItem extends Item
{
    /**
     * Longest possible hold. Deliberately identical in spirit to {@code ShadowCharge.USE_DURATION}: the
     * item itself must never end the use before the client-side sequence does.
     */
    public static final int USE_DURATION = 72000;

    /**
     * Hold pose: the <b>bow pull</b>, deliberately the very same animation the shadow sword and shadow
     * axe use while charging (owner's request: charge with a drawn-bow pose, like the shadow sword).
     */
    public static final UseAnim USE_ANIM = UseAnim.BOW;

    /** This fragment's id: its scripts, its marker and its language keys all hang off it. */
    private final String m_id;

    public LostMemoryFragmentItem(String id)
    {
        // One per player: a fragment of someone's memory is not a consumable resource.
        super(new Item.Properties().stacksTo(1));
        m_id = id;
    }

    /** The fragment's id, used by the client half to pick the script and the watch marker. */
    public String scriptKey()
    {
        return m_id;
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return USE_ANIM;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        // Both sides start the use; the client half of the sequence keys off exactly this state, so no
        // packet is needed to tell it that the charge began.
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Nothing is settled here.
     *
     * <p>Releasing early is not a gameplay event: the client notices that the use ended and aborts the
     * presentation by itself (the owner: releasing before the black screen cancels it). Releasing after the
     * tape screen has taken over is equally meaningless, because the sequence has already moved on.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft)
    {
    }

    /**
     * The detail line: one word per fragment, shown in grey under the name.
     *
     * <p>The owner's rule: the first fragment reads "Doubt", the Nether one reads "Longing". Kept as one
     * language key per fragment so the wording can change without touching this class.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.sanitypd." + m_id + ".detail").withStyle(ChatFormatting.DARK_GRAY));
    }
}
