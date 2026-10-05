package piloser.sanitypd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.thought.ThoughtEffects;

/**
 * Sleep Debt: the "monsters prevent rest" check is skipped while the thought is in the player's chain.
 *
 * <h2>What vanilla does, and why this one call is the whole hook</h2>
 * {@code ServerPlayer#startSleepInBed} decides every reason a sleep can be refused. The monster rule is a
 * single scan inside it, verified with {@code javap} on the mapped jar rather than assumed:
 *
 * <pre>
 *   List&lt;Monster&gt; list = level.getEntitiesOfClass(Monster.class,
 *           new AABB(pos).inflate(8.0, 5.0, 8.0), monster -&gt; monster.isPreventingPlayerRest(this));
 *   if (!list.isEmpty()) return Either.left(BedSleepingProblem.NOT_SAFE);
 * </pre>
 *
 * <p>So replacing that one call is enough, and it is the narrowest hook available: every other refusal -
 * daytime, thunder, the bed being obstructed or too far, a spectator - is untouched. Returning an empty list
 * does not claim "no monsters are there"; it claims "none of them prevent your rest", which is what the
 * thought promises.
 *
 * <h2>Why an instance handler instead of a static one</h2>
 * The redirected call has no parameter that carries the sleeper - the predicate argument is a lambda that
 * <b>captures</b> the player, so it says nothing about who is sleeping and is not the player object. An
 * instance handler receives {@code this}, which is the {@link ServerPlayer} actually trying to sleep, so
 * that is the form used here.
 *
 * <h2>Other players</h2>
 * The receiver of the redirected call is the level, so there is no way to tell which player triggered this
 * scan when it is the sleep of somebody else. Only the player who is actually sleeping reaches this method,
 * which is the case the thought is about.
 */
@Mixin(ServerPlayer.class)
public class MixinServerPlayerSleep
{
    @Redirect(
            method = "startSleepInBed",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;"
                            + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private java.util.List<?> sanitypd$allowSleepNearMonsters(Level level, Class<?> type, AABB area,
            java.util.function.Predicate<?> filter)
    {
        Player sleeper = (Player) (Object) this;

        if (ThoughtEffects.isEquipped(sleeper, ItemRegistry.THOUGHT_SLEEP_DEBT.get()))
            return java.util.List.of();

        // Never reached in practice for this call site: only a Monster list is ever scanned here.
        return level.getEntitiesOfClass((Class) type, area, (java.util.function.Predicate) filter);
    }
}
