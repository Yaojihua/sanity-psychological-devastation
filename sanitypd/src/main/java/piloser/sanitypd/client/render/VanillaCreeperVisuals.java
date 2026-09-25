package piloser.sanitypd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;

/**
 * Creeper-style visual parameters for the screaming crawler (formulas copied line by line from
 * vanilla, not approximated).
 *
 * <p>Source: decompiled {@code net.minecraft.client.renderer.entity.CreeperRenderer} and
 * {@code net.minecraft.client.model.CreeperModel}.
 *
 * <h2>Why a separate class</h2>
 * The base model renderer ({@link RendererScreamingCrawler#preRender}) and the white-flash layer
 * ({@link CrawlerFlashLayer}) <b>must use the same swell scale</b>; keeping two copies would drift
 * apart, so the formulas live here only.
 *
 * <pre>
 * Vanilla CreeperRenderer.scale():
 *   s  = swelling
 *   s2 = 1 + sin(swelling * 100) * swelling * 0.01
 *   s  = clamp(swelling, 0, 1)^3
 *   scale(1*s2, 1/(1 + s*s*0.1), 1*s2)
 *
 * Vanilla CreeperRenderer.getWhiteOverlayProgress():
 *   ((int)(swelling * 10) % 2 == 0) ? 0 : clamp(swelling, 0.5, 1)
 * </pre>
 */
public final class VanillaCreeperVisuals
{
    private VanillaCreeperVisuals() {}

    /**
     * Applies the vanilla creeper swell scale to a {@link PoseStack}.
     *
     * @param swelling swell progress (0..1, = {@code Creeper#getSwelling})
     */
    public static void applySwellScale(PoseStack poseStack, float swelling)
    {
        float pulsate = 1.0f + Mth.sin(swelling * 100.0f) * swelling * 0.01f;
        float cubed = Mth.clamp(swelling, 0.0f, 1.0f);
        cubed = cubed * cubed * cubed;
        float lateral = 1.0f + cubed * 0.4f;          // slight lateral bulge
        float vertical = 1.0f + cubed * 0.1f;         // slightly less vertical
        poseStack.scale(lateral * pulsate, 1.0f / vertical, lateral * pulsate);
    }

    /**
     * White-flash strength: vanilla alternates white / not white every 2 ticks and pushes the white
     * to full during the last half.
     *
     * <p><b>Rendering note</b>: GeckoLib vertices carry no color, so the model cannot be whitened
     * per vertex like vanilla. {@link RendererScreamingCrawler} instead multiplies the whole texture
     * brighter with {@code RenderSystem.setShaderColor} for the same look (a return value of 0
     * applies no tint at all).
     *
     * @return 0 = no flash, positive = flashing (higher is whiter)
     */
    public static float whiteOverlayProgress(float swelling)
    {
        if (((int)(swelling * 10.0f)) % 2 == 0)
            return 0.0f;
        return Mth.clamp(swelling, 0.5f, 1.0f);
    }
}
