package piloser.sanitypd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import piloser.sanitypd.entity.PrimedShadowTnt;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renderer for the primed shadow TNT.
 *
 * <p>It is a copy of vanilla {@link net.minecraft.client.renderer.entity.TntRenderer}'s body rather than a
 * subclass of it, because that class is hard-typed to {@code PrimedTnt} and cannot be registered for a
 * subclass. Every value below - the {@code 0.5} shift, the full-brightness light, the overlay, the flash
 * from {@code getWhiteOverlayProgress} and the block state texture - is the vanilla one, so a primed shadow
 * TNT is indistinguishable from a primed TNT in shape, fuse flash and smoke. Only the texture differs, and
 * that comes from the block.
 *
 * <p>The block state is read from {@link PrimedShadowTnt#getBlockState()}, which vanilla's
 * {@code PrimedTnt#defineSynchedData} keeps in sync with the block, so nothing has to be tracked here.
 */
public class RendererPrimedShadowTnt extends EntityRenderer<PrimedShadowTnt>
{
    private final BlockRenderDispatcher blockRenderer;

    public RendererPrimedShadowTnt(EntityRendererProvider.Context context)
    {
        super(context);
        this.shadowRadius = 0.5f;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PrimedShadowTnt entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
        BlockState state = entity.getShadowBlockState();

        if (state == null || state.isAir())
            return;

        poseStack.pushPose();
        poseStack.translate(-0.5f, 0.0f, -0.5f);

        // Vanilla's white flash: the last 10 ticks of the fuse swell the model up to 1.3x with an eased
        // curve. Copied from TntRenderer#render (its bytecode does exactly these operations).
        int fuse = entity.getFuse();

        if ((float)fuse - partialTicks + 1.0f < 10.0f)
        {
            float f = 1.0f - ((float)fuse - partialTicks + 1.0f) / 10.0f;
            f = net.minecraft.util.Mth.clamp(f, 0.0f, 1.0f);
            f *= f;
            f *= f;
            float scale = 1.0f + f * 0.3f;
            poseStack.scale(scale, scale, scale);
        }

        // Same arguments as vanilla: full brightness, and no hurt overlay - primed TNT does not flash red
        // when it is hit, and the fuse swell above is the only warning visual it has.
        this.blockRenderer.renderSingleBlock(state, poseStack, buffer, 0xF000F0,
                OverlayTexture.pack(0.0f, false));

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Vanilla TNT has no texture of its own - the block model is drawn instead - so this is a placeholder
     * that is never sampled. It is the particle texture vanilla uses.
     */
    @Override
    public ResourceLocation getTextureLocation(PrimedShadowTnt entity)
    {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
