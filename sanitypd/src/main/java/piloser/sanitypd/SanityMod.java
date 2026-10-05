package piloser.sanitypd;

import com.mojang.logging.LogUtils;
import piloser.sanitypd.client.GuiHandler;
import piloser.sanitypd.config.ConfigManager;
import piloser.sanitypd.effect.EffectRegistry;
import piloser.sanitypd.enchantment.SanityEnchantments;
import piloser.sanitypd.entity.EntityRegistry;
import piloser.sanitypd.event.EventHandler;
import piloser.sanitypd.event.ModEventHandler;
import piloser.sanitypd.item.CreativeTabRegistry;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.loot.InnerLoot;
import piloser.sanitypd.net.PacketHandler;
import piloser.sanitypd.sound.SoundRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import net.minecraft.client.gui.screens.MenuScreens;
import piloser.sanitypd.client.ThoughtChainScreen;
import piloser.sanitypd.thought.ThoughtChainMenus;
import piloser.sanitypd.client.ThoughtTooltips;

@Mod(SanityMod.MODID)
public class SanityMod
{
    @OnlyIn(Dist.CLIENT)
    private GuiHandler m_gui;

    private static SanityMod m_inst;
    public static final String MODID = "sanitypd";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SanityMod()
    {
        m_inst = this;

        ConfigManager.register();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(ModEventHandler::addEntityAttributes);
        modEventBus.addListener(ModEventHandler::onConfigLoading);
        modEventBus.addListener(ModEventHandler::registerOverlaysEvent);
        modEventBus.addListener(ModEventHandler::registerEntityRenderersEvent);
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        MinecraftForge.EVENT_BUS.register(new InnerLoot());
        EntityRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);
        CreativeTabRegistry.register(modEventBus);
        SoundRegistry.register(modEventBus);
        ThoughtChainMenus.register(modEventBus);
        // Damage types are not registered here: damage_type is a pure datapack registry supplied by
        // data/sanitypd/damage_type/*.json
        EffectRegistry.EFFECTS.register(modEventBus);
        SanityEnchantments.ENCHANTMENTS.register(modEventBus);
        // The two potion recipes need an ingredient that matches "any variant of this potion"; see
        // PotionIngredient for why no built-in ingredient can express that, and for why an ingredient
        // serializer is registered from a RegisterEvent rather than through a DeferredRegister.
        modEventBus.addListener(piloser.sanitypd.recipe.PotionIngredient::register);
    }

    static
    {
        ConfigManager.init();
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        PacketHandler.init();
    }

    private void clientSetup(final FMLClientSetupEvent event)
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Mental hints (three tiers plus a player-defined pool): build the default pool and load the
        // local custom pool
        piloser.sanitypd.client.MentalHintManager.onClientSetup();

        // The screen for the thought chain menu. Registered here rather than in a lambda-free static
        // block because MenuScreens only exists on a physical client, and this method only runs there.
        MenuScreens.register(ThoughtChainMenus.THOUGHT_CHAIN.get(), ThoughtChainScreen::new);

        // Thought tooltips: an event handler rather than Item#appendHoverText, because item classes are loaded
        // on a dedicated server too and may not name client types (see ThoughtTooltips).
        MinecraftForge.EVENT_BUS.register(new ThoughtTooltips());

        // Reports the on-screen inner line to the server, which is what lets the Command Hallucination
        // thought grant its attack bonus there (see HintStateReporter).
        MinecraftForge.EVENT_BUS.register(piloser.sanitypd.client.HintStateReporter.class);

        initGui();
        //EntityRenderers.register(EntityRegistry.SHADE_CHOMPER.get(), RendererShadeChomper::new);
    }

    @OnlyIn(Dist.CLIENT)
    public void initGui()
    {
        if (m_gui == null) m_gui = new GuiHandler();
    }

    @OnlyIn(Dist.CLIENT)
    public GuiHandler getGui()
    {
        return m_gui;
    }

    public static SanityMod getInstance()
    {
        return m_inst;
    }
}