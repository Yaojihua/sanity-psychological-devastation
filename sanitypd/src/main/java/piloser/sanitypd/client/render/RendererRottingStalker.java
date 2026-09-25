package piloser.sanitypd.client.render;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.entity.RottingStalker;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class RendererRottingStalker extends RendererInnerEntity<RottingStalker>
{
    public RendererRottingStalker(EntityRendererProvider.Context renderManager)
    {
        super(renderManager, new DefaultedEntityGeoModel<>(new ResourceLocation(SanityMod.MODID, "rotting_stalker"), true));

        addRenderLayer(new CustomGlowingGeoLayer<>(this));
    }
}