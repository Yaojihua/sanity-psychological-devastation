package piloser.sanitypd.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.util.List;

/**
 * The yellow splash lines this mod adds to the title screen.
 *
 * <p>Specification:
 * <ul>
 *   <li><b>English only</b>: vanilla splashes live in a single {@code splashes.txt} and do not follow
 *       the language setting (a Chinese client still sees English), so there is no second localized set.</li>
 *   <li><b>Shared pool</b>: these entries are appended to the ~300 vanilla ones and drawn at random,
 *       so the chance of one appearing is our count divided by the total.</li>
 *   <li>{@value #PLAYER_PLACEHOLDER} is replaced with the account name right before display (see
 *       {@code MixinSplashManager}): at title screen time the player is not in a world yet and
 *       {@code Minecraft#player} is usually null, so {@code getUser().getName()} is used instead.</li>
 * </ul>
 *
 * <p>When editing the text: keep one entry per line with no embedded newlines and no leading or
 * trailing spaces (vanilla reads it line by line); use {@value #PLAYER_PLACEHOLDER} for entries that
 * should show the player name; and remember every entry joins the shared pool, so adding too many
 * crowds out the vanilla splashes.
 */
@OnlyIn(Dist.CLIENT)
public final class SplashTexts
{
    /** Placeholder for the player name inside splash text; replaced with the account name before display. */
    public static final String PLAYER_PLACEHOLDER = "{player}";

    /**
     * The splash entries added by this mod.
     *
     * <p>{@code §k} (Obfuscated) works here because {@code SplashRenderer#render} ends with
     * {@code guiGraphics.drawCenteredString(font, this.splash, 0, -8, 0xFFFF00 | i)}, and that
     * String overload parses the text as formatted text (internally via {@code Component.literal}
     * and glyph decomposition), so the glyphs are reshuffled every frame - the flickering end-poem
     * effect. {@code §r} resets it.
     *
     * <p>Do not put colour codes in these entries: splash yellow is hard-coded
     * ({@code 0xFFFF00} OR-ed with the shadow bit), so {@code §f} / {@code §e} would be overwritten.
     *
     * <p>Entries that name the mod's hidden content are kept exactly as written: the title screen is where
     * that content is meant to be discovered. Do not add explanations of them here, and do not expand the
     * set of entries that name it - see {@code HiddenNameDetector}.
     */
    public static final List<String> ALL = List.of(
            "Do not disturb Cacomorth!!!",
            "{player}... master of the world...",
            "Cacomorth... macaron!",
            "Face your inner self",
            "Can you hear my voice, {player}?",
            "Weird Little Thing",
            "B E H I N D  Y O U",
            "Let me go~dsh~",
            "We still need you. Wake up.",
            "Death is not the end",
            // §k = Obfuscated: glyphs reshuffle every frame for the flickering effect. §r resets it.
            // The two 4-character obfuscated blocks are intentional.
            "Caco\u00A7kXXXX\u00A7r morth in a coconut",
            "Do you like this vessel called {player}?"
    );

    private SplashTexts() {}

    /** Account name, available from startup; falls back to {@code you} if it cannot be read, never throws. */
    public static String accountName()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getUser() != null && mc.getUser().getName() != null
                    && !mc.getUser().getName().isBlank())
                return mc.getUser().getName();
        }
        catch (Throwable ignored)
        {
            // fall through to the fallback below
        }

        return "you";
    }

    /**
     * Reads the raw splash text out of a {@link SplashRenderer} so the player name can be substituted.
     *
     * <p>{@code splash} is a {@code private final String} with no getter, so reflection is required.
     * If it cannot be read this returns {@code null} and the caller passes the text through unchanged;
     * when reflection fails it falls back to searching {@code toString()} for the placeholder.
     */
    public static String rawText(SplashRenderer renderer)
    {
        try
        {
            Field f = SplashRenderer.class.getDeclaredField("splash");
            f.setAccessible(true);
            Object value = f.get(renderer);
            if (value instanceof String s)
                return s;
        }
        catch (Throwable ignored)
        {
            // fall through to the fallback below
        }

        try
        {
            String s = String.valueOf(renderer);
            return s.contains(PLAYER_PLACEHOLDER) ? s : null;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }
}
