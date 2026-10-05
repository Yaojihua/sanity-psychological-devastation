package piloser.sanitypd.thought;

import net.minecraft.resources.ResourceLocation;

/**
 * One mindset: what turns on when enough thoughts of a type sit in the chain.
 *
 * <p>A mindset is <b>not an item and not a status effect</b>. It is a derived state: when the count of
 * its type reaches {@link #threshold()}, it appears in the third row of the screen (slot texture and
 * all); when the count falls back below the threshold it disappears again, and the row closes the gap
 * and stays centred. Because it is derived, nothing about "having" a mindset needs saving - only the
 * order in which the currently active ones turned on.
 *
 * @param id        id stem: also the language key suffix and the icon file name, e.g. {@code composure}
 * @param type      the counter that drives it
 * @param threshold how many thoughts of that type are needed; the owner's rule is "at least", so a
 *                  threshold of 5 means the 5th thought turns it on
 * @param icon      the 16x16 texture drawn inside the row-3 slot
 */
public record Mindset(String id, ThoughtType type, int threshold, ResourceLocation icon)
{
    /** Language key of the mindset's display name, e.g. {@code mindset.sanitypd.composure}. */
    public String translationKey()
    {
        return "mindset.sanitypd." + id;
    }
}
