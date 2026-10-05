package piloser.sanitypd.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.config.ConfigProxy;
import piloser.sanitypd.sound.SoundRegistry;

/**
 * The black "tape" screen of the lost memory fragment: the noise of a worn tape, and the script.
 *
 * <h2>The owner's spec (2026-10-05)</h2>
 * <ul>
 *   <li>The tape hiss loops from the moment the screen appears <b>until playback ends</b> (owned by
 *       {@code MemorySequence})</li>
 *   <li><b>Two seconds</b> of black, then the first line</li>
 *   <li>Lines play <b>one at a time</b>, the next only after the previous one is done, each still
 *       wrapped in its double quotes</li>
 *   <li>A line <b>appears instantly with no fade-in</b>, but <b>heavily blurred</b>, and recovers to
 *       sharp text within a few frames</li>
 *   <li>White text, <b>smaller than the inner hint lines</b>, at about the place the owner's reference
 *       frame had it</li>
 *   <li>Shake at the inner lines' amplitude, re-rolled <b>every 0.1 s</b></li>
 *   <li>Each line shows for <b>3 s</b>, fades out, and the next starts <b>0.1 s</b> later</li>
 *   <li>The <b>last</b> line does not fade: the screen bleaches to white, and then the game comes back</li>
 *   <li>The <b>first</b> viewing <b>cannot be skipped</b>; later viewings end on <b>any key</b></li>
 *   <li>In single player the game is <b>paused</b> while the tape runs; on a server it never is</li>
 *   <li>A black square in the script is <b>garbled text</b>, see {@link MemoryScript}</li>
 *   <li>The classic tape noise - the bottom band and the white dropout lines - stays on screen the
 *       whole time</li>
 * </ul>
 *
 * <h2>Why the timeline is a wall clock, not ticks</h2>
 * This screen makes the game pause in single player, so anything driven by a tick hook would be at the
 * mercy of "does that hook still run while paused". {@code System#nanoTime} is not: the presentation
 * advances in real seconds whether the world is ticking or not, which is also what the owner's durations
 * ("2 seconds", "3 seconds", "0.1 s") literally mean.
 *
 * <p>Two consequences are handled here on purpose: the sound engine is nudged awake every frame (vanilla
 * pauses it along with the game, which would silence the tape), and the final close happens from
 * {@code render} rather than a tick hook, for the same reason.
 */
@OnlyIn(Dist.CLIENT)
public class MemoryTapeScreen extends Screen
{
    // ------------------------------------------------------------------ tape noise (part 1)

    /** Height of the head-switching noise band as a fraction of the screen (measured off the owner's reference frame: 7.16%). */
    private static final float BAND_FRACTION = 0.072f;

    /** Chance that the band is missing from a given frame: it must flicker in and out, never sit there. */
    private static final float BAND_OFF_CHANCE = 0.25f;

    /** Fully opaque black, so none of the world shows through. */
    private static final int BLACK = 0xFF000000;

    // ------------------------------------------------------------------ script playback (part 2)

    /** Black for this long before the first line: the owner's "black screen, then 2 s". */
    private static final int FIRST_LINE_DELAY_TICKS = 40;

    /** How long a line stays fully readable: the owner's "3 s, then it fades". */
    private static final int LINE_HOLD_TICKS = 60;

    /** The fade itself. Not specified beyond "fades out"; this is the readable-not-abrupt value. */
    private static final int LINE_FADE_TICKS = 8;

    /** Silence between the end of a fade and the next line: the owner's "after the fade, 0.1 s". */
    private static final int LINE_GAP_TICKS = 2;

    /** One line's whole cycle. */
    private static final int LINE_PERIOD_TICKS = LINE_HOLD_TICKS + LINE_FADE_TICKS + LINE_GAP_TICKS;

    /** A line enters blurred and is sharp again within this many ticks. */
    private static final int BLUR_TICKS = 8;

    /** Blur radius at the moment a line appears, in unscaled font pixels. */
    private static final float BLUR_MAX_PIXELS = 6f;

    /** Copies drawn for the blur; they average out into a smear. */
    private static final int BLUR_COPIES = 8;

    /** Shake amplitude, in the same units the inner hint line uses. */
    private static final int SHAKE_AMPLITUDE = 1;

    /** Shake is re-rolled every 0.1 s (2 ticks) - the owner's frequency, slower than the inner lines'. */
    private static final int SHAKE_INTERVAL_TICKS = 2;

    /** After the last line the screen bleaches to white over this long, then the sequence ends. */
    private static final int WHITE_OUT_TICKS = 40;

    /** Text size, as a multiple of the vanilla font: deliberately smaller than the 2x inner hint. */
    private static final float TEXT_SCALE = 1.5f;

    /**
     * Where the text block sits, as a fraction of the screen height.
     *
     * <p>0.5 = the middle of the screen, which is where the owner wants it ("播放时文字在屏幕中心"). The
     * block is centred on this line rather than hung from it, so one line and a wrapped pair both sit
     * symmetric about it.
     */
    private static final float TEXT_Y_FRACTION = 0.5f;

    /** Clear space kept at each side of the screen: a line wider than this is wrapped onto more lines. */
    private static final float TEXT_MARGIN = 24f;

    /** White text, as in the owner's reference frame. */
    private static final int TEXT_COLOUR = 0xFFFFFF;

    /** The story clock's unit: one tick is 50 ms, so the tick constants above read as the owner's seconds. */
    private static final double NANOS_PER_TICK = 50_000_000.0;

    // ------------------------------------------------------------------ state

    /** When the screen opened; every duration is measured from here. */
    private final long m_startNanos = System.nanoTime();

    /** Whether the player has already watched this fragment and may therefore leave early. */
    private final boolean m_skippable;

    /** The fragment being played: names its script files and its watch marker. */
    private final String m_scriptKey;

    private int m_shakeX;
    private int m_shakeY;

    /** Which shake interval the offsets were rolled for, so they are rolled exactly once per interval. */
    private int m_shakeRolledFor = -1;

    /** Line whose announcement click has already been played. */
    private int m_cuedLine = -1;

    /** Guards the close, since finishing can be reached more than once per frame. */
    private boolean m_finished;

    public MemoryTapeScreen(String scriptKey)
    {
        super(Component.empty());
        m_scriptKey = scriptKey;
        // Read the script now, so a resource reload is picked up and a stale script can never survive.
        MemoryScript.reload(scriptKey);
        m_skippable = MemoryTapeRecord.hasPlayed(scriptKey);
    }

    /**
     * The game pauses while the tape runs in single player - the owner's rule. On a server this has no
     * effect at all: vanilla only pauses a local game, which is exactly the split the owner described.
     */
    @Override
    public boolean isPauseScreen()
    {
        return true;
    }

    // ------------------------------------------------------------------ input

    /** Escape only leaves a re-watch; the first viewing must be watched. */
    @Override
    public boolean shouldCloseOnEsc()
    {
        return m_skippable;
    }

    /** Any key ends a re-watch. The first viewing swallows the key instead, Escape included. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (m_skippable)
            onClose();

        return true;
    }

    /** A click ends a re-watch; during the first viewing it does nothing. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (m_skippable)
            onClose();

        return true;
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();

        // Vanilla pauses the sound engine together with the game, which would silence the tape in single
        // player - the one place the owner wants the hiss. Nudging it awake every frame keeps the tape
        // audible and costs nothing when the game was not paused to begin with.
        mc.getSoundManager().resume();

        int ticks = ticksElapsed();

        if (ticks / SHAKE_INTERVAL_TICKS != m_shakeRolledFor)
        {
            m_shakeRolledFor = ticks / SHAKE_INTERVAL_TICKS;
            rollShake(ticks);
        }

        graphics.fill(0, 0, this.width, this.height, BLACK);

        // Seeded from the clock rather than advanced per draw call, so the noise belongs to the moment.
        RandomSource random = RandomSource.create(ticks * 31L + 7L);
        drawHeadSwitchingBand(graphics, random);
        drawDropouts(graphics, random);

        if (whiteOutTicks(ticks) >= 0)
        {
            if (whiteOutTicks(ticks) >= WHITE_OUT_TICKS)
            {
                finish();
                return;
            }

            drawWhiteOut(graphics, whiteOutTicks(ticks));
            return;
        }

        int line = currentLine(ticks);
        if (line != m_cuedLine)
        {
            m_cuedLine = line;
            if (line >= 0)
            {
                SanityMod.LOGGER.info("[MEMORY] line {}/{} on screen: {}",
                        line + 1, MemoryScript.count(), MemoryScript.line(line, playerName()));
                playCue();
            }
        }

        drawCurrentLine(graphics, ticks);
    }

    /** Ticks since the tape started, as real time. */
    private int ticksElapsed()
    {
        return (int)((System.nanoTime() - m_startNanos) / NANOS_PER_TICK);
    }

    /**
     * Ends the tape and records the viewing, so the next one can be skipped.
     *
     * <p>Called from {@code render} rather than a tick hook: single player pauses the game here, and a tick
     * hook is the one thing that might not run in that state.
     */
    private void finish()
    {
        if (m_finished)
            return;

        m_finished = true;
        MemoryTapeRecord.markPlayed(m_scriptKey);
        SanityMod.LOGGER.info("[MEMORY] tape of {} finished; returning to the game", m_scriptKey);
        onClose();
    }

    // ------------------------------------------------------------------ script timing

    /** Tick at which the last line's hold ends and the bleach to white begins. */
    private int whiteOutStartTick()
    {
        return FIRST_LINE_DELAY_TICKS + (MemoryScript.count() - 1) * LINE_PERIOD_TICKS + LINE_HOLD_TICKS;
    }

    /** Ticks into the bleach-to-white, or {@code -1} while the script is still playing. */
    private int whiteOutTicks(int ticks)
    {
        int start = whiteOutStartTick();
        return ticks < start ? -1 : ticks - start;
    }

    /** The line that should be on screen now, or {@code -1} while there is none (black, or a gap). */
    private int currentLine(int ticks)
    {
        int elapsed = ticks - FIRST_LINE_DELAY_TICKS;
        if (elapsed < 0)
            return -1;

        int index = elapsed / LINE_PERIOD_TICKS;
        if (index >= MemoryScript.count())
            return -1;

        int phase = elapsed % LINE_PERIOD_TICKS;
        if (phase >= LINE_HOLD_TICKS + LINE_FADE_TICKS)
            return -1;                      // the gap between two lines

        return index;
    }

    private void drawCurrentLine(GuiGraphics graphics, int ticks)
    {
        int index = currentLine(ticks);
        if (index < 0)
            return;

        int phase = (ticks - FIRST_LINE_DELAY_TICKS) % LINE_PERIOD_TICKS;

        // Full strength the instant it appears; only the very end of the cycle fades.
        float alpha = phase < LINE_HOLD_TICKS
                ? 1f
                : 1f - (phase - LINE_HOLD_TICKS) / (float) LINE_FADE_TICKS;
        alpha = Mth.clamp(alpha, 0f, 1f);
        if (alpha <= 0.01f)
            return;

        // Heavy blur on entry, gone within BLUR_TICKS: the line resolves the way a tape's picture does.
        float blur = phase < BLUR_TICKS ? BLUR_MAX_PIXELS * (1f - phase / (float) BLUR_TICKS) : 0f;

        drawLine(graphics, MemoryScript.component(index, playerName()), alpha, blur, MemoryScript.isDarkRed(index));
    }

    /**
     * Draws one line, blurred if asked, wrapped if it cannot fit.
     *
     * <p>Wrapping uses vanilla's own splitter ({@code Font#split}), so Latin text breaks between words and
     * Chinese text between characters, exactly as the rest of the game does. A wrapped block stays centred
     * on {@link #TEXT_Y_FRACTION}, which means a line that fits looks no different from before, and a long
     * line - English especially, as the owner pointed out - is never clipped at the screen edge. Nothing is
     * truncated: the block simply grows upwards and downwards from the centre.
     *
     * <p>The blur is a handful of low-alpha copies scattered inside the blur radius; at 60 frames a second
     * they average into a smear, and the radius shrinks to zero so the last frames are a single crisp copy.
     * A real gaussian blur is not available here: the mod's post-processing passes run before the GUI, so
     * nothing drawn in a screen can be filtered by them.
     */
    private void drawLine(GuiGraphics graphics, Component text, float alpha, float blur, boolean darkRed)
    {
        Font font = Minecraft.getInstance().font;
        int baseAlpha = Math.round(alpha * 255f);
        // Dark red for the lines the script marks (the finale's last line), white otherwise. The value is
        // the third voice's own constant, so the two can never drift apart.
        int textColour = darkRed ? HiddenVoicePool.COLOR_DARK_RED : TEXT_COLOUR;
        RandomSource random = RandomSource.create();
        int copies = blur > 0.5f ? BLUR_COPIES : 1;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        RenderSystem.enableBlend();

        int maxWidth = (int)((this.width - TEXT_MARGIN * 2f) / TEXT_SCALE);
        List<FormattedCharSequence> lines = maxWidth > 0 ? font.split(text, maxWidth) : Collections.emptyList();
        if (lines.isEmpty())
            lines = Collections.singletonList(text.getVisualOrderText());

        float centreX = this.width / TEXT_SCALE / 2f;
        float lineStep = font.lineHeight + 1f;
        // Centre the whole block on the line, so a single line and a wrapped pair are both symmetric
        // about the middle of the screen.
        float blockHeight = (lines.size() - 1) * lineStep + font.lineHeight;
        float blockTop = this.height * TEXT_Y_FRACTION / TEXT_SCALE - blockHeight / 2f;

        for (int index = 0; index < lines.size(); index++)
        {
            FormattedCharSequence line = lines.get(index);
            float lineCentre = centreX - font.width(line) / 2f;
            float lineTop = blockTop + index * lineStep;

            for (int i = 0; i < copies; i++)
            {
                float offsetX = i == 0 ? 0f : (random.nextFloat() * 2f - 1f) * blur;
                float offsetY = i == 0 ? 0f : (random.nextFloat() * 2f - 1f) * blur;

                // Spread the alpha over the copies so the smear does not read as a bright blob.
                float share = copies == 1 ? 1f : (i == 0 ? .45f : .18f);
                int colour = (Math.round(baseAlpha * share) << 24) | textColour;

                int x = Math.round(lineCentre + offsetX) + m_shakeX;
                int y = Math.round(lineTop + offsetY) + m_shakeY;
                graphics.drawString(font, line, x, y, colour, false);
            }
        }

        RenderSystem.disableBlend();
        pose.popPose();
    }

    private void drawWhiteOut(GuiGraphics graphics, int whiteTicks)
    {
        int alpha = Mth.clamp(Math.round(255f * whiteTicks / (float) WHITE_OUT_TICKS), 0, 255);
        graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0xFFFFFF);
    }

    private void rollShake(int ticks)
    {
        RandomSource random = RandomSource.create(ticks * 104729L + 17L);
        m_shakeX = random.nextInt(SHAKE_AMPLITUDE * 2 + 1) - SHAKE_AMPLITUDE;
        m_shakeY = random.nextInt(SHAKE_AMPLITUDE * 2 + 1) - SHAKE_AMPLITUDE;
    }

    private String playerName()
    {
        return SplashTexts.accountName();
    }

    /** The tape-eject click that announces every line. */
    private void playCue()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        if (!ConfigProxy.getPlaySounds(mc.player.level().dimension().location()))
            return;

        mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                SoundRegistry.MEMORY_TAPE_EJECT.get(), SoundSource.PLAYERS, 1f, 1f, false);
    }

    // ------------------------------------------------------------------ tape noise

    /**
     * The bottom noise band: the area of a tape frame where the heads switch and no picture is recorded.
     *
     * <p>One thin fill per row with a random grey level and alpha, so it reads as noise rather than a solid
     * strip, plus a few hard bright streaks and sometimes a ragged bright top edge. The row count is capped
     * so the per-frame fill count stays bounded on any screen size.
     */
    private void drawHeadSwitchingBand(GuiGraphics graphics, RandomSource random)
    {
        if (random.nextFloat() < BAND_OFF_CHANCE)
            return;

        int bandHeight = Math.max(3, (int)(this.height * BAND_FRACTION * (0.75f + 0.5f * random.nextFloat())));
        int top = this.height - bandHeight;

        int rows = Math.min(bandHeight, 96);
        for (int i = 0; i < rows; i++)
        {
            int y = top + (int)((float)i / (float)rows * bandHeight);
            int level = 60 + random.nextInt(150);
            int alpha = 40 + random.nextInt(90);
            graphics.fill(0, y, this.width, y + 1, (alpha << 24) | (level << 16) | (level << 8) | level);
        }

        for (int i = 0, streaks = random.nextInt(3); i < streaks; i++)
        {
            int y = top + random.nextInt(bandHeight);
            graphics.fill(0, y, this.width, y + 1, 0xFFE8E8E8);
        }

        if (random.nextFloat() < 0.5f)
            graphics.fill(0, top, this.width, top + 1, 0xFFB4B4B4);
    }

    /**
     * The white dropout lines: the flickering broken lines the owner opened the whole request with.
     *
     * <p>Zero to three new lines per frame, mostly near the bottom, with random length, thickness and
     * brightness; occasionally a long one spans most of the screen. Nothing is remembered between frames,
     * which is exactly what makes them flicker rather than persist.
     */
    private void drawDropouts(GuiGraphics graphics, RandomSource random)
    {
        int count = random.nextInt(4);
        for (int i = 0; i < count; i++)
        {
            int y = random.nextFloat() < 0.75f
                    ? this.height - 1 - (int)(this.height * 0.35f * random.nextFloat())
                    : random.nextInt(this.height);

            int length = (int)(this.width * (0.02f + 0.12f * random.nextFloat()));
            if (random.nextFloat() < 0.15f)
                length = (int)(this.width * (0.5f + 0.45f * random.nextFloat()));

            int x = random.nextInt(Math.max(1, this.width - length));
            int bright = 200 + random.nextInt(56);
            int color = 0xFF000000 | (bright << 16) | (bright << 8) | bright;

            int thickness = 1 + random.nextInt(2);
            for (int t = 0; t < thickness && y + t < this.height; t++)
                graphics.fill(x, y + t, x + length, y + t + 1, color);
        }
    }

    /**
     * Escape, the white-out finishing, or any other path that closes the screen ends the tape.
     *
     * <p>The cleanup lives in {@link MemorySequence} and is idempotent, so closing twice is harmless.
     */
    @Override
    public void onClose()
    {
        MemorySequence.onTapeScreenClosed(Minecraft.getInstance());
    }
}
