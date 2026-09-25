package piloser.sanitypd.client.render;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.entity.SneakingTerror;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class RendererSneakingTerror extends RendererInnerEntity<SneakingTerror>
{
    public RendererSneakingTerror(EntityRendererProvider.Context renderManager)
    {
        super(renderManager, new DefaultedEntityGeoModel<>(new ResourceLocation(SanityMod.MODID, "sneaking_terror"), true));

        addRenderLayer(new CustomGlowingGeoLayer<>(this));
    }
}
