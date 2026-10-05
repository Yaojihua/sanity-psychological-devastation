package piloser.sanitypd.item;

import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import piloser.sanitypd.SanityMod;

/**
 * The "hold SHIFT for more" tooltip line and its expansion, usable from an item's own
 * {@code appendHoverText}.
 *
 * <h2>Why this is not in {@code ItemTooltipHelper}</h2>
 * That class is a client event subscriber ({@code value = Dist.CLIENT}), and item classes are loaded on a
 * dedicated server too. A common-side item that named it would put a client-only class on the server's
 * loading path - the trap {@code ThoughtChainPacket} fell into once ("Attempted to load class
 * net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER").
 *
 * <p>So the narrowing is explicit instead: this class is loaded on both sides, and only the one method that
 * reads the keyboard is marked {@link OnlyIn}. That annotation is safe here because the class carries no
 * event subscriber and nothing on the server calls the marked method - {@code appendHoverText} only runs
 * when a client draws a tooltip.
 *
 * <p>The expansion walks {@code <id>.tooltip}, {@code <id>.tooltip2}, {@code <id>.tooltip3} … and stops at
 * the first key that is missing, so an item can carry as many detail lines as it likes without any branch
 * here.
 */
public final class ItemTooltips
{
    /** Line shown while SHIFT is not held. */
    public static final String KEY_PRESS_SHIFT = "item." + SanityMod.MODID + ".tooltip.press_shift";

    /** Maximum number of detail lines probed while SHIFT is held. */
    private static final int MAX_TOOLTIP_LINES = 5;

    private ItemTooltips() {}

    /**
     * Adds the "hold SHIFT for more" hint, or the expanded description while SHIFT is held.
     *
     * @param components tooltip line list being built
     * @param itemId     item id inside the language key (for example {@code stabilizer_d})
     */
    @OnlyIn(Dist.CLIENT)
    public static void showTooltipOnShift(List<Component> components, String itemId)
    {
        if (!Screen.hasShiftDown())
        {
            components.add(Component.translatable(KEY_PRESS_SHIFT));
            return;
        }

        for (int i = 1; i <= MAX_TOOLTIP_LINES; i++)
        {
            // The first line uses <id>.tooltip, later ones <id>.tooltip2 / .tooltip3 ...
            String key = "item." + SanityMod.MODID + "." + itemId + (i == 1 ? ".tooltip" : ".tooltip" + i);

            // A key with no language entry resolves to itself, which is how the walk knows where to stop.
            if (!Component.translatable(key).getString().equals(key))
                components.add(Component.translatable(key));
        }
    }
}
