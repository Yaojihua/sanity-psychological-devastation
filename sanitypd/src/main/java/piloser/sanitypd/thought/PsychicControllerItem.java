package piloser.sanitypd.thought;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Psychic Controller: the key that opens your own thought chain.
 *
 * <h2>When it opens</h2>
 * Only on a right-click that no other interaction claims. That falls out of the vanilla item pipeline
 * rather than from an event: a chest, a crafting table or a villager consumes the click and this item's
 * {@code use} is never reached, while right-clicking the air - or a block with no interaction of its own -
 * ends up here. So nothing has to fight for priority, and no interaction is stolen.
 *
 * <h2>Either hand</h2>
 * {@code use} runs for whichever hand the click came from, so the controller works from the off hand too.
 * It is the same screen either way, because the chain belongs to the <b>player</b>, not to the item: the
 * controller is only a key, which is also why losing one never loses the thoughts in it.
 *
 * <h2>Server opens, client follows</h2>
 * {@code openMenu} is called on the server only; the client is told which menu to build and where the
 * slots are by the open-screen packet. The counts shown in tooltips travel separately.
 */
public class PsychicControllerItem extends Item
{
    public PsychicControllerItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide)
        {
            // The account name, not the display name: the owner asked for the same reading as the hidden
            // splash-text easter egg, which uses the account name.
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, opener) -> ThoughtChainMenu.forPlayer(id, inventory, opener),
                    Component.translatable(ThoughtChainMenu.TITLE_KEY, player.getGameProfile().getName())));
        }

        // sidedSuccess: the server counts the use as successful while the client plays the swing, which is
        // what makes the click feel like a click. Returning plain success() makes the animation stutter.
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
