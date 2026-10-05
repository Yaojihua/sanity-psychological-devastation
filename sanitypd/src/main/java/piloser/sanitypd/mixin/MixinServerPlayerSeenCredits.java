package piloser.sanitypd.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only view of vanilla's "this player has watched the end poem" flag.
 *
 * <p>{@code ServerPlayer.seenCredits} is {@code private} and has no getter, so the extra inner voice
 * ({@link piloser.sanitypd.client.HiddenVoicePool}) cannot read it through the API. That field is the only
 * <b>mod-independent</b> record of the poem having been seen: vanilla writes it into the player's own data
 * (the {@code seenCredits} tag written by {@code ServerPlayer#addAdditionalSaveData}), so a player who beat
 * the dragon <b>before</b> installing this mod still carries it.
 *
 * <p>An accessor, not a behaviour injection: nothing here writes the flag, nothing here runs on a tick, and
 * the method name is prefixed so it cannot collide with a vanilla or Forge member.
 */
@Mixin(ServerPlayer.class)
public interface MixinServerPlayerSeenCredits
{
    /** @return whether vanilla recorded that this player has seen the end poem and credits */
    @Accessor("seenCredits")
    boolean sanitypd$hasSeenCredits();
}
