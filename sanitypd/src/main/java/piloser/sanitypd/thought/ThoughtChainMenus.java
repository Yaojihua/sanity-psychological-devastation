package piloser.sanitypd.thought;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import piloser.sanitypd.SanityMod;

/**
 * Menu types of the thought chain.
 *
 * <p>Registered like every other registry in this mod: a {@code DeferredRegister} on the mod event bus. The
 * client side binds a screen to this type in {@code SanityMod#clientSetup}, which is what makes
 * {@code player.openMenu} on the server result in the right window on the client.
 *
 * <p>The factory is a plain {@code MenuType}: the client rebuilds the menu from the sync id and the player
 * inventory alone, because nothing about the chain has to travel with the open-screen packet - the contents
 * come through the container protocol and the counts through {@code ThoughtChainPacket}. If a future screen
 * ever needs per-opening data, this becomes {@code IForgeMenuType.create(...)} with a three-argument
 * constructor; note that Forge's factory signature is {@code (int, Inventory, FriendlyByteBuf)}, so a
 * two-argument constructor reference will not infer and must not be passed to it.
 */
public final class ThoughtChainMenus
{
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SanityMod.MODID);

    /** {@code sanitypd:thought_chain}: the thought chain screen's menu. */
    public static final RegistryObject<MenuType<ThoughtChainMenu>> THOUGHT_CHAIN =
            MENU_TYPES.register("thought_chain", () -> new MenuType<ThoughtChainMenu>(ThoughtChainMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus eventBus)
    {
        MENU_TYPES.register(eventBus);
    }

    private ThoughtChainMenus() {}
}
