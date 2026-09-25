package piloser.sanityprobe.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code Mob#goalSelector} / {@code targetSelector} are protected fields and cannot be read
 * from outside the class.
 *
 * <p>Listing the goals that are currently running is the key evidence for diagnosing mobs that
 * refuse to move. If a goal holding the {@code Goal.Flag.MOVE} flag keeps running, lower-priority
 * goals can never claim the flag, so movement goals never fire.
 *
 * <p>An {@code @Accessor} is used instead of reflection: reflection needs the literal field name,
 * but field names are obfuscated in production (for example {@code goalSelector} becomes an SRG
 * name), and only the mixin refmap can map them correctly at runtime.
 */
@Mixin(Mob.class)
public interface MobAccessor
{
    @Accessor("goalSelector")
    GoalSelector sanityprobe$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector sanityprobe$getTargetSelector();
}
