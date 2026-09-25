package piloser.sanitypd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import piloser.sanitypd.capability.InnerEntityCapImplProvider;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.config.ConfigProxy;
import piloser.sanitypd.entity.InnerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

public class RendererInnerEntity<T extends InnerEntity & GeoAnimatable> extends GeoEntityRenderer<T>
{
    private final Minecraft m_mc = Minecraft.getInstance();
    private final AtomicBoolean m_shouldRender = new AtomicBoolean(false);
    private final AtomicBoolean m_isTargetMe = new AtomicBoolean(false);

    public RendererInnerEntity(EntityRendererProvider.Context renderManager, GeoModel<T> model)
    {
        super(renderManager, model);
    }

    public boolean shouldRender(T entity)
    {
        if (m_mc.player == null || entity == null)
            return false;

        if (ConfigProxy.getSaneSeeInnerEntities(m_mc.player.level().dimension().location()) || m_mc.player.isCreative() || m_mc.player.isSpectator())
            return true;

        entity.getCapability(InnerEntityCapImplProvider.CAP).ifPresent(iec ->
        {
            m_isTargetMe.set(iec.getPlayerTargetUUID() != null && iec.getPlayerTargetUUID().equals(m_mc.player.getUUID()));
        });
        if (m_isTargetMe.get())
            return true;

        m_mc.player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            m_shouldRender.set(s.getMadness() >= .6f);
        });

        return m_shouldRender.get();
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight)
    {
        if (shouldRender(entity))
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * Makes "invisible" mean the entity is not rendered at all for that frame.
     *
     * <p>{@code LevelRenderer.renderLevel} asks this method before rendering each entity, and
     * everything an entity draws in a frame (model, shadow, fire overlay, name tag) happens inside
     * {@code EntityRenderDispatcher.render}, so returning false here skips the whole frame.
     *
     * <p>The shadow does not disappear because of this override: the shadow pass inside
     * {@code EntityRenderDispatcher} ignores shouldRender and only looks at
     * {@code options.entityShadows}, {@code shouldRenderShadow}, {@code shadowRadius > 0} and
     * {@code !entity.isInvisible()}. What actually blocks the shadow here is that the outer
     * {@code LevelRenderer} never calls the dispatcher's render at all, while inner mobs have no
     * shadow because their {@code shadowRadius} is left at 0 (see the
     * {@link RendererScreamingCrawler} constructor). The fire overlay on an invisible entity is
     * suppressed for the same reason, since {@code renderFlame} runs in the same dispatcher render.
     */
    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ)
    {
        return super.shouldRender(entity, frustum, camX, camY, camZ) && shouldRender(entity);
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource,
                                    float partialTick)
    {
        return RenderType.entityTranslucent(getTextureLocation(animatable), false);
    }
}
