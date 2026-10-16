package piloser.sanitypd.block;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import piloser.sanitypd.SanityMod;

/**
 * Menu types of the thought transcription table.
 *
 * <p>This one needs per-opening data, unlike the thought chain's menu: a block menu has to know <b>which</b>
 * block entity it is looking at, and the client only gets the sync id and the player inventory from the
 * open-screen packet. So the factory is Forge's three-argument {@code IForgeMenuType.create}, which also hands
 * over the packet body the server wrote - the block position - and the constructor reads it from there.
 *
 * <p>The thought chain's javadoc already predicted this ("if a future screen ever needs per-opening data, this
 * becomes {@code IForgeMenuType.create(...)} with a three-argument constructor"); that future screen is this
 * one. Note the factory signature is {@code (int, Inventory, FriendlyByteBuf)}, so a two-argument constructor
 * reference will not infer here.
 */
public final class ThoughtTranscriptionTableMenus
{
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SanityMod.MODID);

    /** {@code sanitypd:thought_transcription_table}: the transcription table's screen. */
    public static final RegistryObject<MenuType<ThoughtTranscriptionTableMenu>> TRANSCRIPTION_TABLE =
            MENU_TYPES.register("thought_transcription_table",
                    () -> IForgeMenuType.create(ThoughtTranscriptionTableMenu::new));

    public static void register(IEventBus eventBus)
    {
        MENU_TYPES.register(eventBus);
    }

    private ThoughtTranscriptionTableMenus() {}
}
