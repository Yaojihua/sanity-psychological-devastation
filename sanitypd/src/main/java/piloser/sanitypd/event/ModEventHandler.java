package piloser.sanitypd.event;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.client.render.RendererRottingStalker;
import piloser.sanitypd.client.render.RendererScreamingCrawler;
import piloser.sanitypd.client.render.RendererSneakingTerror;
import piloser.sanitypd.config.ConfigManager;
import piloser.sanitypd.entity.EntityRegistry;
import piloser.sanitypd.entity.RottingStalker;
import piloser.sanitypd.entity.ScreamingCrawler;
import piloser.sanitypd.entity.SneakingTerror;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class ModEventHandler
{
    @SubscribeEvent
    public static void addEntityAttributes(final EntityAttributeCreationEvent event)
    {
        event.put(EntityRegistry.ROTTING_STALKER.get(), RottingStalker.buildAttributes());
        event.put(EntityRegistry.SNEAKING_TERROR.get(), SneakingTerror.buildAttributes());
        event.put(EntityRegistry.SCREAMING_CRAWLER.get(), ScreamingCrawler.buildAttributes());
    }

    @SubscribeEvent
    public static void onConfigLoading(final ModConfigEvent.Loading event)
    {
        ConfigManager.onConfigLoading(event);
    }

    @SubscribeEvent
    public static void onConfigReloading(final ModConfigEvent.Reloading event)
    {
        ConfigManager.onConfigReloading(event);
    }

    @SubscribeEvent
    public static void registerOverlaysEvent(final RegisterGuiOverlaysEvent event)
    {
        SanityMod.getInstance().initGui();
        SanityMod.getInstance().getGui().initOverlays(event);
    }

    @SubscribeEvent
    public static void registerEntityRenderersEvent(final EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(EntityRegistry.ROTTING_STALKER.get(), RendererRottingStalker::new);
        event.registerEntityRenderer(EntityRegistry.SNEAKING_TERROR.get(), RendererSneakingTerror::new);
        event.registerEntityRenderer(EntityRegistry.SCREAMING_CRAWLER.get(), RendererScreamingCrawler::new);
    }
}