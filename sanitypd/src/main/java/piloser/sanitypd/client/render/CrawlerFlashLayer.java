package piloser.sanitypd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.entity.ScreamingCrawler;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Flash layer: makes the screaming crawler blink white while it is swelling, matching the vanilla
 * creeper effect.
 *
 * <p>Vanilla {@code MobRenderer} folds {@code getWhiteOverlayProgress()} into vertex colors, and the
 * vanilla creeper texture is already a pure white silhouette, so the vertex color alpha decides how
 * white that frame looks (0 = no flash, 1 = fully white).
 *
 * <p>GeckoLib cube vertices carry no color attribute ({@code renderCube} writes only
 * position/normal/uv/light/overlay), so per-vertex tinting is not possible. Instead a matching white
 * silhouette texture ({@code screaming_crawler_white.png}) is drawn over the same model a second
 * time, which keeps pose and swell scaling identical, and the per-frame whiteness is driven through
 * the layer alpha from {@code getWhiteOverlayProgress}. That maps one to one onto the vanilla vertex
 * color semantics.
 *
 * <p>Scaling the base texture with {@code RenderSystem.setShaderColor(1.5, ...)} was tried first and
 * abandoned: the crawler skin is near black ({@code #0E0E17}), and black times 1.5 is still black,
 * so only the white pixels of the face would brighten. The vanilla creeper can flash that way
 * because its texture is mid-tone green.
 */
public class CrawlerFlashLayer extends GeoRenderLayer<ScreamingCrawler>
{
    /** White silhouette texture: same name as the base texture plus {@code _white}. */
    public static final ResourceLocation WHITE_TEXTURE =
            new ResourceLocation(SanityMod.MODID, "textures/entity/screaming_crawler_white.png");

    public CrawlerFlashLayer(software.bernie.geckolib.renderer.GeoRenderer<ScreamingCrawler> renderer)
    {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, ScreamingCrawler crawler, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay)
    {
        float progress = VanillaCreeperVisuals.whiteOverlayProgress(crawler.getSwelling(partialTick));
        if (progress <= 0.0f)
            return;   // not a flashing frame: skip the extra full-model redraw

        // White silhouette with a translucent render type; whiteness comes from the reRender alpha
        RenderType flashType = RenderType.entityTranslucent(WHITE_TEXTURE, false);

        poseStack.pushPose();
        // Render layers do not inherit the swell scaling applied in the base model's preRender
        VanillaCreeperVisuals.applySwellScale(poseStack, crawler.getSwelling(partialTick));
        try
        {
            VertexConsumer flashBuffer = bufferSource.getBuffer(flashType);
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, crawler, flashType,
                    flashBuffer, partialTick, packedLight, OverlayTexture.NO_OVERLAY,
                    1.0f, 1.0f, 1.0f, progress);   // alpha = flash progress, like the vanilla vertex color alpha
        }
        finally
        {
            poseStack.popPose();
        }
    }
}
