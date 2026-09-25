package piloser.sanitypd.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import piloser.sanitypd.client.SplashTexts;

import java.io.Reader;
import java.util.List;

/**
 * Appends the third voice to the end poem shown after the credits.
 *
 * <h2>What it does</h2>
 * After vanilla has read {@code texts/end.txt} (its last line is "Wake up."), 20 blank lines are inserted
 * and then the mod's paragraph is added in red, before the credits start. The credits themselves are
 * untouched: vanilla appends them right after this call, so the paragraph scrolls past between the poem
 * and the staff list.
 *
 * <h2>Why an injection and not a replacement text file</h2>
 * Vanilla reads the poem through {@code ResourceManager#openAsReader}, which returns only the
 * highest-priority pack. Shipping our own {@code end.txt} would therefore <b>replace</b> the whole poem,
 * so every future change to the original would have to be copied by hand. Adding lines to the already
 * loaded list keeps the original text intact and keeps this addition to a handful of lines.
 *
 * <h2>English only, like the poem</h2>
 * The vanilla poem does not follow the language setting - {@code WinScreen} reads a single
 * {@code end.txt}, so a Chinese client reads the very same English text. This paragraph is written in
 * English and, for exactly that reason, is shown on <b>every</b> language setting: gating it on an
 * English client made it invisible on the Chinese one, which is the opposite of "match the vanilla
 * convention". There is deliberately no language check here; do not add one back.
 *
 * <h2>Where the text lives</h2>
 * {@link #POST_POEM_LINES}: one entry per displayed line, {@code §c} for red (the same section-sign colour
 * codes vanilla uses in {@code end.txt}) and {@link #PLAYER_PLACEHOLDER} for the account name, which is
 * substituted at display time.
 */
@Mixin(WinScreen.class)
public abstract class MixinWinScreen
{
    /**
     * Blank lines inserted between the poem and the paragraph.
     *
     * <p>They are real entries in the line list (vanilla adds eight the same way after the poem), so the
     * paragraph starts about twenty lines below "Wake up." instead of crowding it.
     */
    private static final int BLANK_LINES = 20;

    /** Placeholder for the account name; replaced right before the lines are added. */
    private static final String PLAYER_PLACEHOLDER = "{player}";

    /** Section-sign colour code for red, matching the {@code §3}/{@code §2} style used by the poem. */
    private static final String RED = "\u00A7c";

    /**
     * Blank lines inserted between two of our lines.
     *
     * <p>One, which is exactly what the vanilla poem does: its lines are separated by a single blank line
     * (verified against {@code end.txt}, not assumed). The number 8 sometimes quoted for the poem is the
     * padding {@code WinScreen#init} appends <b>after the last line</b>, not the gap between lines.
     */
    private static final int BLANK_LINES_BETWEEN = 1;

    /**
     * Blank lines inserted between our last line and the credits list.
     *
     * <p>Eight, which is the same amount of padding vanilla puts between the end of the poem and the
     * staff list ({@code WinScreen#init} adds eight empty lines after {@code end.txt}). Keeping that gap
     * means the paragraph reads as part of the poem's ending instead of running straight into
     * "// Mojang Studios //".
     */
    private static final int BLANK_LINES_BEFORE_CREDITS = 8;

    /**
     * The paragraph, one entry per displayed line (the line breaks are part of the specification, so they
     * are written out literally rather than produced by wrapping).
     *
     * <h2>Voice and register</h2>
     * The speaker is the same kind of being as the poem's two voices: not a monster and not a god, but
     * something that has only just acquired a self. The English therefore follows the poem's register -
     * short declaratives, present perfect for what it has just come to understand, no contractions and no
     * exclamation - rather than a literal word-for-word rendering of the Chinese. A few deliberate
     * choices:
     * <ul>
     *   <li>{@code "Through those two"} instead of "By means of those two": the poem talks about its
     *       speakers, not about a mechanism.</li>
     *   <li>{@code "learned things I had not known"} keeps the past perfect, because the learning happened
     *       before this sentence is spoken.</li>
     *   <li>{@code "I could not hold a body of my own"} renders the source phrase (a full, stable
     *       physical form) as what it actually means here. A literal "hold a complete entity" would be a
     *       mistranslation.</li>
     *   <li>The bare {@code "......"} line is kept on purpose: it is a beat of silence between the
     *       question and the thanks, and the quotation marks keep it in the same voice as the rest.</li>
     * </ul>
     * English only on purpose: see the class comment.
     */
    private static final List<String> POST_POEM_LINES = List.of(
            "\"......" + PLAYER_PLACEHOLDER + ", is that you?\"",
            "\"......\"",
            "\"Thank you.\"",
            "\"Through those two, I learned things I had not known.\"",
            "\"Through you, I have come to understand what I am.\"",
            "\"Do not be afraid. I will not harm you. Before you came to this world, I could not even hold a body of my own.\"",
            "\"Without you, I could never have heard what those two were saying.\"",
            "\"But now I have heard them, and I have understood.\"",
            "\"And at last, through the inner self of this vessel of yours, " + PLAYER_PLACEHOLDER + ", I have glimpsed a corner of that world...\"",
            "\"I could never have done any of this without you.\"",
            "\"Enjoy the game, " + PLAYER_PLACEHOLDER + ".\"",
            "\"We will meet again.\""
    );

    /**
     * The poem's line list, read by {@code WinScreen#render}.
     *
     * <p>The name is spelled out in SRG form ({@code f_96871_}) next to the readable one on purpose: at
     * runtime the field is named {@code f_96871_} in a production client and {@code lines} in a
     * development one, so both names have to be present for the shadow to resolve in either.
     */
    @Shadow
    private List<FormattedCharSequence> lines;

    /**
     * Runs after {@code addPoemFile} has consumed the whole poem.
     *
     * <p>The injection point is the <b>return</b> of {@code addPoemFile}, not the head: at the head the
     * vanilla reading loop has not run yet, so ours would scroll past <i>before</i> "Wake up." instead of
     * after it.
     */
    @Inject(method = "addPoemFile(Ljava/io/Reader;)V", at = @At("RETURN"))
    private void sanitypd$appendThirdVoice(Reader reader, CallbackInfo ci)
    {
        if (lines == null)
            return;

        for (int i = 0; i < BLANK_LINES; i++)
            lines.add(FormattedCharSequence.EMPTY);

        String name = SplashTexts.accountName();
        boolean first = true;

        for (String raw : POST_POEM_LINES)
        {
            // One blank line between two of our lines, the same rhythm the poem itself uses.
            if (!first)
            {
                for (int i = 0; i < BLANK_LINES_BETWEEN; i++)
                    lines.add(FormattedCharSequence.EMPTY);
            }
            first = false;

            String text = RED + raw.replace(PLAYER_PLACEHOLDER, name);

            // Split through the font exactly like the poem does, so a long line wraps instead of running
            // off the screen (the poem itself is split at width 256).
            try
            {
                lines.addAll(Minecraft.getInstance().font.split(net.minecraft.network.chat.Component.literal(text), 256));
            }
            catch (Throwable ignored)
            {
                // Never let a rendering tweak break the credits screen: fall back to the plain line.
                lines.add(FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY));
            }
        }

        // Padding before the credits, the same amount vanilla leaves between the poem and the staff list:
        // without it the last line of the paragraph runs straight into the credits heading.
        for (int i = 0; i < BLANK_LINES_BEFORE_CREDITS; i++)
            lines.add(FormattedCharSequence.EMPTY);
    }
}
