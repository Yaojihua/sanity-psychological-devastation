package piloser.sanityprobe.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import piloser.sanityprobe.PathTrace;

import java.lang.reflect.Method;

/**
 * Observes what {@code PathNavigation} actually does at runtime instead of inferring it from
 * disassembly. Static analysis of this class produced several plausible explanations for the
 * stuck-crawler problem that in-game data later contradicted, so each suspicious method is now
 * instrumented at its entry point:
 * <ul>
 *   <li>onTick() - whether {@code tick()} is called at all; if it is not, the whole AI chain is
 *       broken before navigation;</li>
 *   <li>onMoveTo() - who starts pathfinding (top frames of the call stack), the target coordinates
 *       and the returned value;</li>
 *   <li>onStop() - who clears the path, the prime suspect for a constant nextIdx of 0 and
 *       moveWanted=false.</li>
 * </ul>
 *
 * <p>All probes only fire for the {@code screaming_crawler} mob (matched by class name); every other
 * mob is left alone.
 */
@Mixin(PathNavigation.class)
public abstract class MixinPathNavigation
{
    @Shadow @Final protected Mob mob;

    private static boolean sanityprobe$isCrawler(Mob m)
    {
        try
        {
            String n = m.getClass().getName();
            return n.contains("screaming_crawler") || n.contains("ScreamingCrawler");
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void onTick(CallbackInfo ci)
    {
        try
        {
            if (sanityprobe$isCrawler(this.mob))
                PathTrace.navTick(this.mob);
        }
        catch (Throwable ignored) {}
    }

    @Inject(method = "moveTo(DDDD)Z", at = @At("RETURN"))
    private void onMoveTo(double x, double y, double z, double speed,
                          CallbackInfoReturnable<Boolean> cir)
    {
        try
        {
            if (sanityprobe$isCrawler(this.mob))
                PathTrace.navMoveTo(this.mob, x, y, z, speed, cir.getReturnValueZ());
        }
        catch (Throwable ignored) {}
    }

    @Inject(method = "stop()V", at = @At("HEAD"))
    private void onStop(CallbackInfo ci)
    {
        try
        {
            if (sanityprobe$isCrawler(this.mob))
                PathTrace.navStop(this.mob);
        }
        catch (Throwable ignored) {}
    }

    // Exposes the path field for reflective reads by PathTrace. Field names are obfuscated at
    // runtime, so the field can only be reached directly from inside the mixin.
    @Shadow protected net.minecraft.world.level.pathfinder.Path path;
}
