package piloser.sanitypd.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import piloser.sanitypd.client.SplashTexts;

import java.util.ArrayList;
import java.util.List;

/**
 * Title screen splash texts: mixes this mod's lines into the vanilla pool.
 *
 * <p>Vanilla splashes live in {@code assets/minecraft/texts/splashes.txt}, and
 * {@code ResourceManager#openAsReader} only returns the single highest-priority pack, so replacing
 * the file would mean copying and maintaining all of the vanilla lines. Merging needs two small
 * injections instead: {@link #sanitypd$mergeSplashes} appends our lines to the {@code apply(...)}
 * argument (vanilla then clears and re-adds the merged list), and
 * {@link #sanitypd$resolvePlayerName} replaces {@code {player}} in the returned text with the
 * account name before {@code getSplash()} returns.
 *
 * <p>Three constraints verified against the game:
 * <ul>
 *   <li>This class must live in the {@code client} section of {@code sanitypd.mixins.json}:
 *       {@code SplashManager} is {@code @OnlyIn(CLIENT)}, so a common section would be rejected at
 *       load time by {@code RuntimeDistCleaner}.</li>
 *   <li>The list passed to vanilla {@code apply} is immutable, so calling {@code add} on it throws
 *       {@code UnsupportedOperationException}; the argument reference has to be replaced, which is
 *       why {@link ModifyVariable} is used instead of {@code @Inject} plus {@code add}.</li>
 *   <li>{@code TitleScreen#init} only resolves the splash while {@code this.splash} is null, so it is
 *       resolved once when the title screen first opens. At that point {@code Minecraft#player} is
 *       usually still null, so the account name from {@code mc.getUser().getName()} is used instead.</li>
 * </ul>
 */
@Mixin(SplashManager.class)
public abstract class MixinSplashManager
{
    /**
     * Merges this mod's splash lines into the vanilla pool.
     *
     * <p>Only the argument is modified (nothing is written to a field), so the lines are added
     * exactly once.
     */
    @ModifyVariable(method = "apply(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true)
    private List<String> sanitypd$mergeSplashes(List<String> incoming)
    {
        if (incoming == null || incoming.isEmpty())
            return incoming;

        List<String> merged = new ArrayList<>(incoming);
        merged.addAll(SplashTexts.ALL);
        return merged;
    }

    /**
     * Replaces {@code {player}} in the text with the account name and rebuilds the
     * {@link SplashRenderer}.
     *
     * <p>Rebuilding is the cleanest option because {@code SplashRenderer#splash} is a private final
     * field while the constructor is public, so it avoids mixing into the render path.
     */
    @Inject(method = "getSplash", at = @At("RETURN"), cancellable = true)
    private void sanitypd$resolvePlayerName(CallbackInfoReturnable<SplashRenderer> cir)
    {
        SplashRenderer original = cir.getReturnValue();
        if (original == null)
            return;

        String raw = SplashTexts.rawText(original);
        if (raw == null || !raw.contains(SplashTexts.PLAYER_PLACEHOLDER))
            return;

        cir.setReturnValue(new SplashRenderer(raw.replace(SplashTexts.PLAYER_PLACEHOLDER, SplashTexts.accountName())));
    }
}
