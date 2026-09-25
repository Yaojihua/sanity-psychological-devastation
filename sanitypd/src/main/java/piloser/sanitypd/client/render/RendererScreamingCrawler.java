package piloser.sanitypd.client.render;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.entity.ScreamingCrawler;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Renderer for the screaming crawler.
 *
 * <p>{@link DefaultedEntityGeoModel} resolves its assets from the entity name
 * ({@code screaming_crawler}) in three fixed locations:
 * <ul>
 *   <li>model {@code geo/entity/screaming_crawler.geo.json}</li>
 *   <li>animation {@code animations/entity/screaming_crawler.animation.json}</li>
 *   <li>texture {@code textures/entity/screaming_crawler.png}</li>
 * </ul>
 * The third constructor argument {@code true} lets the head follow the observer, matching vanilla
 * creeper head tracking.
 *
 * <p>The superclass {@link RendererInnerEntity} renders with {@code RenderType.entityTranslucent(...)},
 * so the low alpha value (100) in the skin is actually honoured, giving the ghostly look.
 *
 * <p>Two creeper-style visual details: the swell scale from {@link VanillaCreeperVisuals#applySwellScale}
 * in {@link #preRender}, and the white flash from {@link CrawlerFlashLayer}.
 *
 * <p>Emissive eyes use {@link CustomGlowingGeoLayer} with
 * {@code textures/entity/screaming_crawler_glowmask.png} (256x128), matching the other inner mobs.
 * The colouring is static, exactly like {@code rotting_stalker_glowmask.png} and
 * {@code sneaking_terror_glowmask.png}; there is no sanity-driven recolouring or texture swap.
 *
 * <p>Glow works because {@code AutoGlowingGeoLayer#render} pins {@code packedLight} to {@code 0xF00000}
 * (full brightness) and re-renders the same model, so the eyes stay lit in the dark. That re-render
 * does call {@link #preRender} but with {@code isReRender=true}, which keeps the swell scale behaving
 * exactly as before the layer was added.
 */
public class RendererScreamingCrawler extends RendererInnerEntity<ScreamingCrawler>
{
    public RendererScreamingCrawler(EntityRendererProvider.Context renderManager)
    {
        super(renderManager, new DefaultedEntityGeoModel<>(new ResourceLocation(SanityMod.MODID, "screaming_crawler"), true));

        // Inner mobs must never cast a shadow.
        //
        // Reason 1: they are black-skinned and translucent (alpha=100), so a solid shadow looks wrong.
        // Reason 2: it caused a real bug - when the player cannot see the crawler (not insane enough
        // and not its target), RendererInnerEntity hides the model but the shadow blob stayed on the ground.
        //
        // Disassembling EntityRenderDispatcher.render shows the shadow pass requires all four of:
        //   127~133 options.entityShadows -> 137~140 shouldRenderShadow ->
        //   143~150 renderer.shadowRadius > 0.0F -> 153~157 !entity.isInvisible()
        // => keeping GeckoLib's defaults (shadowRadius = 0) is what disables shadows. Do not set
        //    shadowRadius or shadowStrength here.

        // White flash on explosion (same as vanilla creeper)
        addRenderLayer(new CrawlerFlashLayer(this));

        // Emissive eyes: identical setup to the rotting stalker and sneaking terror
        // (CustomGlowingGeoLayer -> AutoGlowingGeoLayer, reading textures/entity/screaming_crawler_glowmask.png).
        // Layer order does not matter: both layers redraw the whole model with their own texture and
        // blend mode, so neither covers the other.
        addRenderLayer(new CustomGlowingGeoLayer<>(this));
    }

    /**
     * Swells up while charging, the same way a vanilla creeper does.
     *
     * <p>GeckoLib's {@code preRender} receives {@code partialTick}, so the swell progress can be
     * interpolated smoothly. Note that {@link software.bernie.geckolib.renderer.layer.GeoRenderLayer}
     * does not inherit this scale, so {@link CrawlerFlashLayer} has to apply the same formula itself.
     */
    @Override
    public void preRender(PoseStack poseStack, ScreamingCrawler animatable,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        // Only apply on the base model pass; each layer's own re-render applies it separately,
        // otherwise the scale would be applied twice.
        if (!isReRender)
            VanillaCreeperVisuals.applySwellScale(poseStack, animatable.getSwelling(partialTick));

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * Disables the vanilla red damage overlay.
     *
     * <p>Inner mobs use a translucent skin (alpha=100), and the red overlay tints them into a red
     * haze that hides the ghostly look. The other two inner mobs have no overlay either, so this
     * stays consistent.
     */
    @Override
    public int getPackedOverlay(ScreamingCrawler animatable, float u)
    {
        return net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
    }
}
