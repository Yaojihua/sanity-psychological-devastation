package piloser.sanityprobe.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import piloser.sanityprobe.PathTrace;

/**
 * Observes whether anything commands a mob's movement control, the last unmeasured link in the
 * "crawler never takes a step" investigation.
 *
 * <p>{@code setWantedPosition} is the only entry point of {@code MoveControl}, and {@code tick()}
 * turns the command into velocity. Both are instrumented, which separates the possible causes:<br>
 * - {@code setWantedPosition} is never called: nothing commands movement, so the problem is in the
 *   AI or navigation layer;<br>
 * - it is called but {@code operation} inside {@code tick()} is not {@code MOVE_TO}: the command is
 *   overwritten, so the problem is in whatever overwrites it;<br>
 * - both run and the mob still does not move: the problem is in the displacement layer of
 *   {@code Mob#travel} or {@code aiStep}.
 */
@Mixin(MoveControl.class)
public abstract class MixinMoveControl
{
    @Shadow @Final protected Mob mob;
    @Shadow protected double wantedX;
    @Shadow protected double wantedY;
    @Shadow protected double wantedZ;
    @Shadow protected double speedModifier;

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

    @Inject(method = "setWantedPosition(DDDD)V", at = @At("RETURN"))
    private void onSetWanted(double x, double y, double z, double speed, CallbackInfo ci)
    {
        try
        {
            if (sanityprobe$isCrawler(this.mob))
                PathTrace.moveSetWanted(this.mob, x, y, z, speed);
        }
        catch (Throwable ignored) {}
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void onTick(CallbackInfo ci)
    {
        try
        {
            if (sanityprobe$isCrawler(this.mob))
                PathTrace.moveTick(this.mob, this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
        }
        catch (Throwable ignored) {}
    }
}
