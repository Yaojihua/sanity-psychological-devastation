package piloser.sanitypd.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.capability.IPassiveSanity;
import piloser.sanitypd.capability.Sanity;
import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.combat.SanityCombat;
import piloser.sanitypd.config.ConfigProxy;
import piloser.sanitypd.config.SanityIndicatorLocation;
import piloser.sanitypd.effect.EffectRegistry;
import piloser.sanitypd.sound.SwishSoundInstance;
import piloser.sanitypd.thought.ThoughtEffects;
import piloser.sanitypd.util.MathHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class GuiHandler
{
    /**
     * The active handler, so the static render hook below can reach the current state.
     *
     * <p>There is one handler per client, installed when the client setup runs.
     */
    private static GuiHandler s_instance;

    /**
     * Draws the centre line from {@link RenderGuiEvent.Post}.
     *
     * <p>Why this event instead of the mod's own GUI overlay: the line was drawn from a registered overlay and
     * never reached the screen, while the diagnostic probe's HUD - which draws from this very event - is
     * visible on the same setup. Using the same event therefore removes the overlay layer as a variable.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event)
    {
        GuiHandler handler = s_instance;
        if (handler == null)
            return;

        handler.drawCentreHint(event.getGuiGraphics());
    }

    private static final float PASSIVE_THRESHOLD = Sanity.MAX_SANITY * .0002f; // 0.02 points/tick (= 0.02% of the cap)
    private static final float BT_DELAY = 5f * 20;

    /**
     * Tier threshold for the mental hints. The three tiers themselves live in {@link MentalHintManager}:
     * <ul>
     *   <li>sanity &le; 50% (madness &ge; {@code T0_MADNESS}) &rarr; mild tier {@code hint0X}</li>
     *   <li>sanity &le; 25% (madness &ge; {@code T1_MADNESS}) &rarr; severe tier {@code hint1X}</li>
     *   <li>sanity &le; 10% (madness &ge; {@code T2_MADNESS}) &rarr; deep tier {@code hint2X} (the inner monologue before a breakdown)</li>
     * </ul>
     * Every tier can be replaced by the player with {@code /sanity hint add} (see {@link MentalHintManager}).
     */
    private static final float HINT_STAGE0_MADNESS = MentalHintManager.T0_MADNESS;

    /**
     * Tier whose line is shown to the centre while a warning window is open:
     * {@code 1} = severe (the tier every madness at or above the threshold falls back to) and
     * {@code 2} = deep (talked once, in the window right before the mania damage starts).
     *
     * <p>The severe index comes from {@link MentalHintManager#TIER_SEVERE} because that is also the tier the
     * extra inner-voice pool shares its draw with; one constant keeps the two from drifting apart.
     */
    private static final int HINT_STAGE_SEVERE = MentalHintManager.TIER_SEVERE;
    private static final int HINT_STAGE_DEEP = 2;

    /**
     * Madness at or above which the regular draw uses the severe tier only, so the deep tier stays reserved
     * for the pre-damage warning window.
     */
    private static final float HINT_SEVERE_MADNESS = MentalHintManager.SEVERE_ONLY_MADNESS;

    /** Shake amplitude of the regular inner hints (render-space pixels; the outer 2x scale makes it 2 px on screen). */
    private static final int HINT_SHAKE = 1;

    /**
     * How long a regular inner line stays on screen, in ticks (about 10 s).
     *
     * <p>The extra inner-voice pool uses its own, shorter window ({@link HiddenVoicePool#SHOW_TICKS}).
     * Either way the line is <b>taken off the screen</b> when its window ends (see {@link #tickHint}): it used
     * to stay visible at the alpha floor for the whole gap, which measured 30-60 s of the same sentence and
     * made the next line look like it was pasted on top of the previous one.
     */
    private static final float HINT_SHOW_TICKS = 199f;

    /**
     * How many ticks a shake offset stays in place before a new one is rolled.
     *
     * <p>The offset used to be re-rolled in the draw call, i.e. once per <b>frame</b> (60-100 Hz on a
     * normal client), which reads as electric noise rather than a tremble. Rolling it once every
     * {@value} tick ties it to the 20 Hz tick instead of the frame rate.
     */
    private static final int HINT_SHAKE_INTERVAL_TICKS = 1;

    /**
     * Shake amplitude for a warning line (deep tier / expiry pool), in render-space pixels.
     *
     * <p>Kept from the mania whisper this replaced: mania is supposed to tremble harder than an ordinary
     * inner line, so the warning lines use this instead of {@link #HINT_SHAKE}. The value is applied per
     * displayed line (see {@link #shakeAmplitude()}), not per tick.
     */
    private static final int WARNING_LINE_SHAKE = 2;


    /**
     * Timing of a warning line (the deep tier before the mania damage starts, the expiry pool before the
     * immunity runs out). These are the numbers the mania whisper this replaced used:
     * {@link #WARNING_LINE_SHOW_TICKS} on screen, {@link #WARNING_LINE_LEAD_TICKS} of quiet before it so the
     * previous line retires instead of being cut off, and {@link #WARNING_LINE_TAIL_TICKS} of quiet after it
     * so the next line does not follow immediately.
     */
    private static final float WARNING_LINE_SHOW_TICKS = 100f;
    private static final float WARNING_LINE_LEAD_TICKS = 60f;
    private static final float WARNING_LINE_TAIL_TICKS = 100f;

    /**
     * How long before the mania-immunity buff expires its warning window opens (100 ticks = 5 seconds),
     * mirroring the last 5 seconds of the mania grace period.
     */
    private static final int IMMUNITY_EXPIRY_WARNING_TICKS = 100;

    /**
     * Turns on the on-screen debug markers for the centre line (see {@link #drawHintLine}).
     *
     * <p>Off by default, and only ever switched on through {@code /sanity hint debug <on|off>}. It exists
     * because "the line was drawn but nothing is visible" cannot be told apart from "the line never
     * reached the draw call" by reading the log alone: the markers are drawn with the same pose and the
     * same event as the line, in colours that cannot be confused with the fade, so one screenshot
     * separates "wrong place / wrong event" from "present but invisible".
     */
    public static boolean DEBUG_CENTRE_MARKERS;

    /**
     * Frames per second above which the warning windows are considered "too fast to be readable".
     *
     * <p>The display duration is measured in ticks but decremented once per <b>tick</b>, so it is only
     * frame-rate independent as long as the game ticks normally. When the measured tick delta is this
     * large the logger says so explicitly instead of leaving "the text was drawn but nobody saw it"
     * unexplained.
     */
    private static final float SUSPICIOUS_TICK_DELTA = 4f;

    /** How many diagnostic lines a warning window may write (keeps the log readable). */
    private static final int WARNING_DIAG_LINES = 4;

    /**
     * Tier stored for the centre-screen line while it comes from the separate expiry warning pool
     * ({@link MentalHintManager#INDEX_EXPIRY}).
     */
    private static final int HINT_STAGE_EXPIRY = MentalHintManager.INDEX_EXPIRY;

    public static final ResourceLocation SANITY_INDICATOR = new ResourceLocation(SanityMod.MODID, "textures/sanity_indicator.png");
    public static final ResourceLocation BLOOD_TENDRILS_OVERLAY = new ResourceLocation(SanityMod.MODID, "textures/overlay/blood_tendrils.png");

    private final Minecraft m_mc;
    private ISanity m_cap;
    private PostProcessor m_post;
    private final Random m_random = new Random();
    private int m_indicatorOffset;
    private int m_hintOffsetX;
    private int m_hintOffsetY;
    /** Shake amplitude used for the current offset, chosen per displayed line (see {@link #shakeAmplitude()}). */
    private int m_hintShakeAmplitude = HINT_SHAKE;
    /** Ticks left before the shake offset is rolled again (see {@link #HINT_SHAKE_INTERVAL_TICKS}). */
    private int m_hintShakeCooldown;
    private float m_dt;
    private float m_prevSanity;
    private float m_sanityGain;
    private float m_flashTimer;
    private float m_flashSanityGain;
    private float m_arrowTimer;
    private float m_hintTimer;
    private float m_showingHintTimer;
    private float m_maxShowingHintTimer;

    private float m_btGainedAlpha;
    private float m_btDelay;
    private float m_btAlpha;
    private double m_btTimer;

    private MutableComponent m_hint;
    /**
     * Tier of the currently shown hint: -1 = none, 0 = mild (sanity &lt;50%), 1 = severe (shown for every
     * madness at or above the severe threshold), 2 = deep (only during the pre-damage warning window) and
     * {@link #HINT_STAGE_EXPIRY} for the separate immunity-expiry pool.
     */
    private int m_hintStage = -1;

    /**
     * Whether the line on screen came from the extra inner-voice pool ({@link HiddenVoicePool}).
     *
     * <p>Such a line shares the severe tier's size, position and duration but is drawn dark red and never
     * shakes, so both the colour and the shake need to know where the current line came from.
     */
    private boolean m_hintHiddenVoice;
    /** Colour of the line on screen; only the extra inner-voice pool changes it. */
    private int m_hintColor = 0xFFFFFF;
    /** Whether "the extra inner-voice line reached the screen" was already written for this line. */
    private boolean m_hiddenVoiceDrawLogged;

    /**
     * Text of the "immunity is about to expire" warning currently on screen, or {@code null}.
     *
     * <p>Drawn through the regular hint path (same centre position, 2x scale and fade) but picked from
     * {@link MentalHintManager#INDEX_EXPIRY}, which is a pool entirely separate from the three madness tiers.
     */
    private MutableComponent m_expiryWarning;

    /** Ticks left in the expiry warning display (the pick itself is made once per window). */
    private float m_expiryWarningTimer;
    /**
     * Whether the currently open expiry window has already been announced.
     *
     * <p>A single flag is enough: the window is announced on the frame it opens, and stays announced for as
     * long as it stays open. It is cleared only once the window has been <b>closed</b> for longer than
     * {@link #IMMUNITY_EXPIRY_WARNING_TICKS} (see {@link #m_expiryWindowClosedTicks}), which keeps the Beta
     * and Gamma immunity running out within five seconds of each other inside ONE window, while a genuinely
     * later expiry opens a fresh window and is announced again.
     */
    private boolean m_expiryWindowAnnounced;
    /** Ticks the expiry window was closed for, used to decide when a new window has started. */
    private float m_expiryWindowClosedTicks;
    /** Whether this mania already showed its one deep-tier line, so a single mania shows exactly one. */
    private boolean m_deepTextDone;
    /** The deep-tier line to draw for the current mania; {@code null} = nothing to draw. */
    private MutableComponent m_deepText;
    /** Ticks left in the deep-tier line display. */
    private float m_deepTextTimer;
    /** Whether the "the deep line reached the screen" line was already logged for this mania. */
    private boolean m_deepTextDrawLogged;
    /** Whether the "the expiry line reached the screen" line was already logged for this window. */
    private boolean m_expiryDrawLogged;
    /** Number of diagnostic lines still allowed for the warning windows (see {@link #logWarningDiag}). */
    private int m_warningDiagBudget = WARNING_DIAG_LINES;
    /** Ticks left before the per-second centre-line status line is written again. */
    private int m_centreStatusCountdown;
    /** Set while a warning line is on screen: forces one unconditional status line per second. */
    private boolean m_centreStatusForced;
    /** Alpha (0..255) used by the last centre-line text call, reported by {@link #logCentreStatus}. */
    private int m_lastOpacity = -1;

    /** Last colour the sneak HUD printed for the sanity value, so a state change is logged exactly once. */
    private int m_lastSanityValueColour = 0xFFFFFF;
    /**
     * Quiet timer shared by the two warning windows (ticks): the previous line retires
     * {@link #WARNING_LINE_LEAD_TICKS} before the warning, and nothing else is drawn for
     * {@link #WARNING_LINE_TAIL_TICKS} after it. This is the transition the mania whisper used.
     */
    private float m_maniaHintQuiet;

    public GuiHandler()
    {
        m_mc = Minecraft.getInstance();
        s_instance = this;
    }

    /**
     * Draws the centre line for the current GUI pass.
     *
     * <p>Called from {@link #onRenderGui}; the text is skipped while no line is on screen, the player is not in
     * a world, the HUD is hidden or the sanity rules say no line should be shown.
     */
    private void drawCentreHint(GuiGraphics guiGraphics)
    {
        if (m_hint == null || m_cap == null || m_mc.player == null)
        {
            logCentreStatus("no line");
            return;
        }

        if (m_mc.options.hideGui || m_mc.player.isCreative() || m_mc.player.isSpectator())
        {
            logCentreStatus("skipped (hideGui/creative/spectator)");
            return;
        }

        if (m_cap.getMadness() < HINT_STAGE0_MADNESS)
        {
            logCentreStatus("skipped (madness " + m_cap.getMadness() + " below " + HINT_STAGE0_MADNESS + ")");
            return;
        }

        // Same switch the overlay draw used: the centre line can be turned off per dimension.
        if (!ConfigProxy.getRenderHint(m_mc.player.level().dimension().location()))
        {
            logCentreStatus("skipped (hints disabled for this dimension)");
            return;
        }

        drawHintLine(guiGraphics, guiGraphics.guiWidth(), guiGraphics.guiHeight());
        m_centreStatusForced = m_hintStage == HINT_STAGE_DEEP || m_hintStage == HINT_STAGE_EXPIRY;
        logCentreStatus("drew stage " + m_hintStage);
    }

    /**
     * Writes one centre-line status line per second while the player is insane enough for a line to be
     * eligible.
     *
     * <p>It exists because "the warning text did not appear" has three very different causes - the state
     * never had a line, the render gate rejected it, or it was drawn but not visible - and this line says
     * which one it was without a debugger. It is rate limited to one line per second and stays silent
     * while no line is pending, so it cannot flood the log.
     */
    private void logCentreStatus(String outcome)
    {
        if (m_cap == null || (m_cap.getMadness() < HINT_STAGE0_MADNESS && !m_centreStatusForced))
        {
            m_centreStatusCountdown = 20;
            return;
        }

        if (--m_centreStatusCountdown > 0)
            return;

        m_centreStatusCountdown = 20;

        // Diagnostic: the alpha that actually reaches the text call, so "drawn but invisible" is
        // distinguishable from "not drawn" in a log. "quiet" is the warning-window quiet stretch: while it is
        // above zero the regular draw yields, so a frozen value there is exactly what "no line ever appears"
        // looks like from the outside (it stayed invisible in this line until it was added).
        SanityMod.LOGGER.info("[CENTRE] {} | hint=[{}] stage={} hidden={} quiet={} timer={} max={} alpha={} madness={} maniaTicks={} dt={}",
                outcome,
                m_hint == null ? "null" : m_hint.getString(),
                m_hintStage, m_hintHiddenVoice, m_maniaHintQuiet, m_showingHintTimer, m_maxShowingHintTimer,
                m_lastOpacity, m_cap.getMadness(), m_cap.getManiaTicks(), m_dt);
    }

    private void initSanityPostProcess()
    {
        Minecraft mc = Minecraft.getInstance();
        m_post.addSinglePassEntry("insanity", pass ->
        {
            return processPlayer(mc.player, cap ->
            {
                if (cap.getMadness() < .4f)
                    return false;
                pass.getEffect().safeGetUniform("DesaturateFactor").set(MathHelper.clampNorm(Mth.inverseLerp(cap.getMadness(), .4f, .8f)) * .69f);
                pass.getEffect().safeGetUniform("SpreadFactor").set(MathHelper.clampNorm(Mth.inverseLerp(cap.getMadness(), .4f, .8f)) * 1.43f);
                return true;
            });
        });
        m_post.addSinglePassEntry("chromatical", pass ->
        {
            return processPlayer(mc.player, cap ->
            {
                if (cap.getMadness() < .4f)
                    return false;
                pass.getEffect().safeGetUniform("Factor").set(MathHelper.clampNorm(Mth.inverseLerp(cap.getMadness(), .4f, .8f)) * .1f);
                pass.getEffect().safeGetUniform("TimeTotal").set(m_post.getTime() / 20.0f);
                return true;
            });
        });
    }

    private boolean processPlayer(LocalPlayer player, Function<ISanity, Boolean> action)
    {
        ISanity cap;
        return player != null &&
                (!player.isCreative() && !player.isSpectator()) &&
                (cap = player.getCapability(SanityProvider.CAP).orElse(null)) != null &&
                cap.getMadness() > 0 &&
                action.apply(cap);
    }

    /**
     * Whether the player is currently under a macaron's slow sanity recovery.
     *
     * <p>That recovery is applied by the server once per second, so the capability's passive value is 0
     * between those steps and the HUD brain arrow would never light up on its own. Reading the effect
     * here is what makes the arrow appear for the whole 25 seconds.
     *
     * <p>The effect is looked up by name rather than through {@code EffectRegistry}: this class is client
     * only, and a name lookup cannot pin a registry object at class-load time.
     */
    private boolean sanityRecovering()
    {
        return m_mc.player != null && m_mc.player.hasEffect(
                net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                        .getValue(new ResourceLocation(SanityMod.MODID, "sanity_regen")));
    }

    private void renderSanityIndicator(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int scw, int sch)
    {
        if (m_mc.player == null ||
                m_mc.player.isCreative() ||
                m_mc.player.isSpectator() ||
                m_cap == null ||
                !ConfigProxy.getRenderIndicator(m_mc.player.level().dimension().location()))
            return;

        ResourceLocation dim = m_mc.player.level().dimension().location();
        float scale = ConfigProxy.getIndicatorScale(dim);
        if (scale <= 0f)
            return;

        SanityIndicatorLocation loc = ConfigProxy.getIndicatorLocation(dim);
        PoseStack poseStack = guiGraphics.pose();

        poseStack.pushPose();

        if (loc == SanityIndicatorLocation.HOTBAR_LEFT)
            poseStack.translate(scw / 2f - 97f - (!m_mc.player.getOffhandItem().isEmpty() ? 29f : 0f), sch - 5f, 0f);
        else if (loc == SanityIndicatorLocation.HOTBAR_RIGHT)
            poseStack.translate(97f, 0f, 0f);
        else if (loc == SanityIndicatorLocation.TOP_LEFT)
            poseStack.translate(5f, 5f, 0f);
        else if (loc == SanityIndicatorLocation.TOP_RIGHT)
            poseStack.translate(scw - 5f, 5f, 0f);
        else if (loc == SanityIndicatorLocation.BOTTOM_LEFT)
            poseStack.translate(5f, sch - 5f, 0f);
        else if (loc == SanityIndicatorLocation.BOTTOM_RIGHT)
            poseStack.translate(scw - 5f, sch - 5f, 0f);

        poseStack.scale(scale, scale, 1f);

        int texw = 256;
        int texh = 128;
        int spritew = 33;
        int spriteh = 24;
        int x = 0;
        int y = 0;

        if (loc == SanityIndicatorLocation.HOTBAR_LEFT || loc == SanityIndicatorLocation.BOTTOM_RIGHT)
        {
            x = -spritew;
            y = -spriteh;
        }
        else if (loc == SanityIndicatorLocation.HOTBAR_RIGHT || loc == SanityIndicatorLocation.BOTTOM_LEFT)
        {
            y = -spriteh;
        }
        else if (loc == SanityIndicatorLocation.TOP_RIGHT)
        {
            x = -spritew;
        }

        if (ConfigProxy.getTwitchIndicator(m_mc.player.level().dimension().location()))
            y += m_indicatorOffset;
        int vOffset = Math.round(m_cap.getMadness() * (spriteh - 2)) + 1;

        RenderSystem.setShaderTexture(0, SANITY_INDICATOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // bg
        guiGraphics.blit(SANITY_INDICATOR, x, y, 0, 0, 0, spritew, spriteh, texw, texh);
        if (m_flashTimer > 0 && ((int) m_flashTimer / 3) % 2 == 0)
        {
            // bg flash
            guiGraphics.blit(SANITY_INDICATOR, x, y, 0, spritew, 0, spritew, spriteh, texw, texh);
            if (m_flashSanityGain > 0)
            {
                int flashOffset = Math.round((m_cap.getMadness() - m_flashSanityGain) * (spriteh - 2)) + 1;
                // brain flash
                guiGraphics.blit(SANITY_INDICATOR, x, y + flashOffset, 0, spritew * 3, flashOffset, spritew, spriteh - flashOffset, texw, texh);
            }
        }
        // brain
        guiGraphics.blit(SANITY_INDICATOR, x, y + vOffset, 0, spritew * 2, vOffset, spritew, spriteh - vOffset, texw, texh);
        if (m_cap instanceof IPassiveSanity)
        {
            float p = ((IPassiveSanity)m_cap).getPassiveIncrease();
            float absp;
            int os;
            // A macaron's slow recovery arrives one whole point per second, which is a single jump for the
            // arrow; without the second half of this test the arrow would only appear while some other
            // passive source happened to be moving sanity at the same time.
            boolean recovering = sanityRecovering();
            if (p != 0 || recovering)
            {
                if ((absp = Math.abs(p)) >= PASSIVE_THRESHOLD)
                {
                    float maxArrowTimer = 23.99f;
                    m_arrowTimer = Mth.clamp(m_arrowTimer, 0f, maxArrowTimer);
                    os = (m_arrowTimer >= 12f && m_arrowTimer <= 15f) || (m_arrowTimer >= 0f && m_arrowTimer <= 3f) ?
                            0 : (((int) m_arrowTimer / 3) % 2 == 0 ? 2 : 1);
                    os *= m_arrowTimer > 12f ? 1 : -1;
                }
                else
                {
                    float maxArrowTimerSmall = 15.99f;
                    m_arrowTimer = Mth.clamp(m_arrowTimer, 0f, maxArrowTimerSmall);
                    os = ((int) m_arrowTimer / 4) % 2;
                    os *= m_arrowTimer > 8f ? 1 : -1;
                }

                if (p < 0) // point scale: p < 0 means sanity is dropping (older builds used p > 0 for rising madness); the arrow sprites stay the same
                {
                    if (absp < PASSIVE_THRESHOLD)
                    {
                        guiGraphics.blit(SANITY_INDICATOR, x, y + os, 0, 0, spriteh, spritew, spriteh, texw, texh);
                        guiGraphics.blit(SANITY_INDICATOR, x, y + vOffset, 0, spritew, spriteh + vOffset - os, spritew, spriteh - vOffset + os, texw, texh);
                    }
                    else
                    {
                        guiGraphics.blit(SANITY_INDICATOR, x, y + os, 0, spritew * 2, spriteh, spritew, spriteh, texw, texh);
                        guiGraphics.blit(SANITY_INDICATOR, x, y + vOffset, 0, spritew * 3, spriteh + vOffset - os, spritew, spriteh - vOffset + os, texw, texh);
                    }
                }
                else
                {
                    if (absp < PASSIVE_THRESHOLD)
                    {
                        guiGraphics.blit(SANITY_INDICATOR, x, y + os, 0, 0, spriteh * 2, spritew, spriteh, texw, texh);
                        guiGraphics.blit(SANITY_INDICATOR, x, y + vOffset, 0, spritew, spriteh * 2 + vOffset - os, spritew, spriteh - vOffset + os, texw, texh);
                    }
                    else
                    {
                        guiGraphics.blit(SANITY_INDICATOR, x, y + os, 0, spritew * 2, spriteh * 2, spritew, spriteh, texw, texh);
                        guiGraphics.blit(SANITY_INDICATOR, x, y + vOffset, 0, spritew * 3, spriteh * 2 + vOffset - os, spritew, spriteh - vOffset + os, texw, texh);
                    }
                }
            }
        }

        // Show the sanity value above the gauge while sneaking
        renderSanityValueWhileSneaking(gui, guiGraphics, x, y, spritew, spriteh);

        poseStack.popPose();
    }

    /**
     * Sanity value text shown while sneaking, e.g. {@code Sanity 12/20}.
     *
     * <p>It is <b>public static</b> so the diagnostic probe can call it directly to check that the number
     * on screen matches the real sanity value.
     *
     * <p><b>The maximum comes from {@link ISanity#getMaxSanity()} instead of a hardcoded 100</b>:
     * a future mechanic that raises the sanity cap then needs no change here.
     */
    public static MutableComponent sanityValueText(ISanity cap)
    {
        // Round the current value; round the maximum up so a full bar never reads 19/20
        int cur = Math.round(cap.getSanity());
        int max = Math.max(1, (int) Math.ceil(cap.getMaxSanity()));
        return Component.translatable("gui." + SanityMod.MODID + ".sanity", cur, max);
    }

    /**
     * Colour of the sanity value above the gauge: grey-red while the value sits at the recovery ceiling, plain
     * white otherwise.
     *
     * <p>A grey red rather than a bright one on purpose - bright red already means "your sanity is low" in
     * this HUD family, while this state says something different: you cannot recover any further, however long
     * you wait. Kept as one constant so the owner can tune the shade without touching the logic.
     */
    private static final int SANITY_CEILING_COLOUR = 0xB06060;

    /** {@link #SANITY_CEILING_COLOUR} once the value has reached the ceiling; white in every other case. */
    private int sanityValueColour()
    {
        if (m_cap == null || m_mc.player == null)
            return 0xFFFFFF;

        float max = m_cap.getMaxSanity();
        float ceiling = max * ThoughtEffects.recoveryCeilingFraction(m_mc.player);

        // No ceiling (the thought is not in the chain) means the ceiling equals the maximum: keep it white.
        if (ceiling >= max - 0.01f)
            return 0xFFFFFF;

        // A hundredth of a point of slack, so a value resting exactly on the ceiling still counts as "at" it.
        return m_cap.getSanity() >= ceiling - 0.01f ? SANITY_CEILING_COLOUR : 0xFFFFFF;
    }

    /**
     * Draws the current sanity value <b>above</b> the brain gauge sprite while sneaking.
     *
     * <p>Placement note: the gauge may be drawn above or below the position origin
     * ({@code HOTBAR_LEFT}, for example, draws upwards), so the text position is derived from the sprite's
     * actual bounding box instead of a hardcoded offset for one direction.
     *
     * <p>Client-side rendering only; the value comes from the synced sanity capability, so the server
     * does not need to know about it.
     */
    private void renderSanityValueWhileSneaking(ForgeGui gui, GuiGraphics guiGraphics, int x, int y, int spritew, int spriteh)
    {
        if (m_mc.player == null || m_cap == null)
            return;
        if (!m_mc.player.isShiftKeyDown())
            return;

        // This text is a translatable component too, and drawInBatch(Component) does not resolve
        // translations, so it is converted to a string first.
        // sanityValueText(ISanity) itself stays untouched: the probe calls it directly to verify values.
        MutableComponent text = Component.literal(sanityValueText(m_cap).getString());

        Font font = gui.getFont();
        int tw = font.width(text);

        // Sprite bounding box: x may be -spritew (drawn leftwards), y may be -spriteh (drawn upwards)
        int left = Math.min(x, x + spritew);
        int top = Math.min(y, y + spriteh);

        float tx = left + (spritew - tw) / 2f;          // horizontally centred on the gauge
        float ty = top - font.lineHeight - 2f;          // vertically: hugging the top edge of the gauge

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Grey-red while the value sits at (or above) the recovery ceiling Depersonalization imposes; white
        // otherwise. The ceiling is NOT the sanity maximum, so this is a state of its own: the owner asked for
        // it to be readable at a glance without the maximum itself ever moving. Logged on change, because the
        // colour is the only part of this that a probe cannot see.
        int colour = sanityValueColour();

        if (colour != m_lastSanityValueColour)
        {
            m_lastSanityValueColour = colour;
            SanityMod.LOGGER.info("[SANITY-CEILING] value={} max={} ceiling={} colour={}",
                    String.format(java.util.Locale.ROOT, "%.2f", m_cap.getSanity()),
                    String.format(java.util.Locale.ROOT, "%.2f", m_cap.getMaxSanity()),
                    String.format(java.util.Locale.ROOT, "%.2f",
                            m_cap.getMaxSanity() * ThoughtEffects.recoveryCeilingFraction(m_mc.player)),
                    String.format(java.util.Locale.ROOT, "%06X", colour & 0xFFFFFF));
        }

        font.drawInBatch(text, tx, ty, colour, true,
                guiGraphics.pose().last().pose(), guiGraphics.bufferSource(),
                Font.DisplayMode.NORMAL, 0, 15728880);
        // drawInBatch needs an explicit flush (see drawHintLine); this line was empty for the same reason.
        guiGraphics.flush();
        RenderSystem.disableBlend();
    }

    private void renderHint(ForgeGui gui, GuiGraphics guiGraphics, float partialTicks, int scw, int sch)
    {
        // The centre line is drawn from RenderGuiEvent.Post (see onRenderGui). The overlay registration is kept
        // so the line still has exactly one owner, but it no longer draws anything itself.
    }

    /**
     * Shake amplitude for the line that is currently on screen: a warning line trembles harder than an
     * ordinary inner line, matching what the mania whisper used to do.
     */
    private int shakeAmplitude()
    {
        return m_hintStage == HINT_STAGE_DEEP || m_hintStage == HINT_STAGE_EXPIRY
                ? WARNING_LINE_SHAKE
                : HINT_SHAKE;
    }

    /**
     * Whether a non-mild inner line is on screen right now, read by {@code HintStateReporter}.
     *
     * <p>One rule for every path that draws a line: the mild tier is index 0, the severe tier is 1 and the
     * deep and expiry windows have their own stages, so "stage is at least severe" covers the regular
     * severe draw, both warning windows and the extra inner-voice line (which shares the severe draw) in a
     * single comparison. The immediate {@code /sanity hint show} preview goes through the same field, so it
     * counts too - which is right: it is the same line on the same screen.
     *
     * <p>The owner's rule is that the Command Hallucination bonus does <b>not</b> apply to the mild tier,
     * which is exactly the case this returns false for.
     */
    public static boolean isNonMildHintOnScreen()
    {
        GuiHandler handler = s_instance;

        return handler != null && handler.m_hint != null && handler.m_hintStage >= HINT_STAGE_SEVERE;
    }

    /** Draws {@link #m_hint} in the centre of the screen (shared by the regular line and the warning lines). */
    private void drawHintLine(GuiGraphics guiGraphics, int scw, int sch)
    {
        // The pick-time log only says a line was selected, not that it reached the screen, so the first
        // frame of each warning window is logged here too: one line per window, useful for reports.
        if (m_hintStage == HINT_STAGE_DEEP && !m_deepTextDrawLogged)
        {
            m_deepTextDrawLogged = true;
            SanityMod.LOGGER.info("[HINT-WINDOW] deep tier shown on screen: {}", m_hint.getString());
        }
        else if (m_hintStage == HINT_STAGE_EXPIRY && !m_expiryDrawLogged)
        {
            m_expiryDrawLogged = true;
            SanityMod.LOGGER.info("[HINT-WINDOW] immunity expiry shown on screen: {}", m_hint.getString());
        }
        else if (m_hintHiddenVoice && !m_hiddenVoiceDrawLogged)
        {
            // One line per extra inner-voice line: proves that a picked line really reaches the screen, which
            // is the failure the two earlier display bugs hid behind.
            m_hiddenVoiceDrawLogged = true;
            SanityMod.LOGGER.info("[THIRD-VOICE] line shown on screen: {}", m_hint.getString());
        }

        PoseStack poseStack = guiGraphics.pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.pushPose();

        // On-screen markers for the "drawn but invisible" hunt (see DEBUG_CENTRE_MARKERS). They are drawn
        // with the same event and the same pose as the line, so their visibility answers "did this code
        // reach the screen at all" without a debugger. Top-left corner, unscaled coordinates.
        if (DEBUG_CENTRE_MARKERS)
        {
            guiGraphics.fill(2, 2, 10, 10, 0xFFFF00FF);
            guiGraphics.drawString(m_mc.font, "CENTRE#"
                    + (m_hintStage == HINT_STAGE_DEEP ? "DEEP" : m_hintStage == HINT_STAGE_EXPIRY ? "EXPIRY" : "stage" + m_hintStage),
                    12, 2, 0xFFFF00FF, true);
        }

        poseStack.translate(scw / 2d, sch / 2d, 0d);
        poseStack.scale(2f, 2f, 1f);

        // Fade in over the first 9 ticks and out over the last 10, with a slow breathing ripple in
        // between.
        //
        // Two traps used to make the line effectively invisible:
        //   * Math.floorMod, not %, for the ripple phase: (int) of a negative timer made "% 10" return a
        //     negative value, so the ripple degenerated to a constant 0.
        //   * The alpha floor is 0.60, not 0x10: a 6% floor is indistinguishable from "nothing is drawn"
        //     on a real screen, which is exactly how "the text is drawn but invisible" was reported.
        //
        // The extra inner-voice line fades from nothing instead. Its colour is dark red, so a fade that runs
        // 60% -> 100% is not readable as a fade at all on a dark screen: the line appeared to pop in and out
        // at full strength. Its steady brightness is unchanged - only the two fade windows start lower, and
        // the floor is lifted out of their way so the clamp cannot flatten them again.
        float timer = Math.max(0f, m_showingHintTimer);
        float o = Math.floorMod((int) timer, 10) / 10f;
        o = ((int) timer / 10) % 2 == 0 ? o : 1 - o;
        boolean fading = timer >= m_maxShowingHintTimer - 9f || timer < 10f;
        float fadeFrom = fading && m_hintHiddenVoice ? 0f : fading ? .6f : .85f;
        float fade = Mth.lerp(o, fadeFrom, 1f);
        int opacity = Mth.clamp((int)(fade * 0xFF), fading && m_hintHiddenVoice ? 0x00 : 0x99, 0xEF) << 24;
        if (DEBUG_CENTRE_MARKERS)
            opacity = 0xFF000000;

        // The centre line is drawn with drawString, the immediate call that flushes internally.
        // The batched variant (drawInBatch) silently produced nothing in this overlay no matter how it was
        // flushed, while drawString is the call the probe HUD and the sanity value above the gauge use and
        // is proven to reach the screen; that is why the centre line goes through it.
        //
        // The picked component is drawn as it is. It used to be re-wrapped with
        // Component.literal(m_hint.getString()), which flattens the component and throws away its styles -
        // harmless while every line was plain text, but the chaos-restraint line 4 needs its garbled run to
        // stay an OBFUSCATED style span (see TypeHintSpec.styled), and getString() would drop exactly that.
        final MutableComponent shown = m_hint;

        Font font = m_mc.font;
        float pX = -font.width(shown) / 2f;
        float pY = -font.lineHeight / 2f;
        if (ConfigProxy.getTwitchHint(m_mc.player.level().dimension().location()) && !m_hintHiddenVoice)
        {
            // The offset is rolled once per HINT_SHAKE_INTERVAL_TICKS in tick(); this call only consumes it.
            // Rolling it here instead would make the shake change on every frame, which is the "too high a
            // frequency" report this replaced.
            //
            // The extra inner-voice pool is excluded here as well as in tick(): that line is meant to be
            // perfectly steady, and the guard has to hold on the very frame it is picked, before tick() has
            // had a chance to zero the offsets.
            pX += m_hintOffsetX;
            pY += m_hintOffsetY;
        }

        // Marker 2: a magenta box exactly where the line should start, and a red copy of the line itself.
        // Red is used because the normal colour goes through a fade that can land near-invisible.
        if (DEBUG_CENTRE_MARKERS)
        {
            guiGraphics.fill(Math.round(pX) - 2, Math.round(pY) - 2, Math.round(pX) + 2, Math.round(pY) + 2, 0xFFFF00FF);
            guiGraphics.drawString(font, shown, Math.round(pX), Math.round(pY + 12), 0xFFFF2020, true);
        }

        // Same immediate call as the centre line (see drawHintLine): the batched variant produced nothing here.
        // m_hintColor is white for every line except the extra inner-voice pool, which is drawn dark red.
        m_lastOpacity = (opacity >>> 24) & 0xFF;
        guiGraphics.drawString(font, shown, Math.round(pX), Math.round(pY), m_hintColor | opacity, true);

        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    /**
     * Immediate display for {@code /sanity hint show <tier>}.
     *
     * <p>The style deliberately matches the regular inner hints (screen centre, 2x scale, drop shadow,
     * shake) so the preview looks exactly like the real thing; the only difference is that it disappears
     * on a timer ({@link MentalHintManager#SHOW_TICKS} = 6 seconds, with a fade in and out).
     *
     * <p>It deliberately ignores the "sanity must be below 50%" rule: this line was explicitly requested
     * by the command, and without it there would be no way to preview an edited line while sane.
     */
    private void renderImmediateHint(ForgeGui gui, GuiGraphics guiGraphics, float partialTicks, int scw, int sch)
    {
        if (m_mc.player == null || m_mc.player.isCreative() || m_mc.player.isSpectator())
            return;

        MutableComponent text = MentalHintManager.immediateText();
        if (text == null || !MentalHintManager.isImmediateActive())
            return;

        // drawInBatch(Component) does not resolve translations, so the string is taken explicitly here;
        // otherwise built-in lines would be drawn as raw keys / raw format strings.
        final MutableComponent shown = Component.literal(text.getString());

        // Fade in over 10 ticks, fade out over 20 ticks. The floor is 0.60 rather than 0x10: see
        // drawHintLine - a 6% floor reads as "nothing was drawn".
        float timer = MentalHintManager.immediateTimer();
        float fade = Mth.clamp((MentalHintManager.SHOW_TICKS - timer) / 10f, 0f, 1f)
                * Mth.clamp(timer / 20f, 0f, 1f);
        int opacity = Mth.clamp((int)(fade * 0xFF), 0x99, 0xEF) << 24;

        PoseStack poseStack = guiGraphics.pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.pushPose();
        poseStack.translate(scw / 2d, sch / 2d, 0d);
        poseStack.scale(2f, 2f, 1f);

        Font font = m_mc.font;
        float pX = -font.width(shown) / 2f;
        float pY = -font.lineHeight / 2f;

        if (ConfigProxy.getTwitchHint(m_mc.player.level().dimension().location()))
        {
            pX += MentalHintManager.immediateShakeX();
            pY += MentalHintManager.immediateShakeY();
        }

        // Same immediate call as the centre line (see drawHintLine): the batched variant produced nothing here.
        guiGraphics.drawString(font, shown, Math.round(pX), Math.round(pY), 0xFFFFFF | opacity, true);

        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    /**
     * Red screen-edge filter warning that mania is about to start dealing damage.
     *
     * <p>The mania rhythm is: {@link SanityCombat#MANIA_GRACE_TICKS} (40 seconds) of grace with no damage,
     * then 1 point of true damage per second. A status icon alone gives no clue when that starts, so:
     * <ul>
     *   <li>last 5 seconds of the grace period: the red edge fades in from nothing (warning)</li>
     *   <li>once damage starts: it pulses with the "1 point per second" beat, brightest on the hit</li>
     * </ul>
     *
     * <p>Implementation: reuses the tendril texture used around the screen when sanity drops
     * ({@link #BLOOD_TENDRILS_OVERLAY}, the same texture as {@link #renderBloodTendrilsOverlay}) and
     * controls intensity through alpha. Registered above {@code VIGNETTE}, so it covers the world but
     * stays below the hotbar and other HUD elements.
     */
    private void renderManiaWarning(ForgeGui gui, GuiGraphics guiGraphics, float partialTicks, int scw, int sch)
    {
        if (m_mc.player == null || m_mc.player.isCreative() || m_mc.player.isSpectator() || m_cap == null)
            return;

        int mania = m_cap.getManiaTicks();
        if (mania <= 0)
            return;

        // Warnings are for incoming damage, and the immunity blocks the mania damage, so neither the fade-in
        // of the grace period nor the damage pulse is drawn while the buff is held. The expiring shield is
        // announced by the separate expiry warning pool during the buff's final 5 seconds.
        if (hasManiaImmunity())
            return;

        float alpha;
        if (mania < SanityCombat.MANIA_GRACE_TICKS)
        {
            // Fade in over the last 5 seconds of the grace period (100 ticks)
            float ramp = Mth.clamp((mania - (SanityCombat.MANIA_GRACE_TICKS - 100)) / 100f, 0f, 1f);
            alpha = ramp * .45f;
        }
        else
        {
            // Damage already running: pulse on a 20 tick cycle, brightest on the hit
            int phase = (mania - SanityCombat.MANIA_GRACE_TICKS) % 20;
            alpha = .45f + (1f - phase / 20f) * .35f;
        }

        if (alpha <= 0f)
            return;

        // Do not draw a plain red gradient instead: a flat colour rectangle has no texture detail and
        // just looks like a red block pasted over the edge.
        RenderSystem.setShaderTexture(0, BLOOD_TENDRILS_OVERLAY);
        renderFullscreen(guiGraphics.pose(), scw, sch, 100, 58, 0, 0, 100, 58, Mth.clamp(alpha, 0f, 1f));
    }

    private void renderBloodTendrilsOverlay(ForgeGui gui, GuiGraphics guiGraphics, float partialTicks, int scw, int sch)
    {
        if (m_mc.player == null || m_mc.player.isCreative() || m_mc.player.isSpectator())
            return;

        ResourceLocation dim = m_mc.player.level().dimension().location();

        if (!ConfigProxy.getRenderBtOverlay(dim) || !(ConfigProxy.getFlashBtOnShortBurst(dim) || ConfigProxy.getRenderBtPassive(dim)))
            return;

        RenderSystem.setShaderTexture(0, BLOOD_TENDRILS_OVERLAY);
//        ForgeGui.blit(poseStack, 0, 0, scw, sch, 0, 0, 64, 36, 64, 36);

        if (m_btAlpha > 0f)
            renderFullscreen(guiGraphics.pose(), scw, sch, 100, 58, 0, 0, 100, 58, m_btAlpha);
    }

    public void tick(float dt)
    {
        // The extra inner-voice pool carries its own per-save unlock state and sound cooldown. Updated before
        // the gates below, which return early while the draw is not allowed to run (paused, creative, HUD
        // hidden): the cooldown and the save marker must keep tracking regardless of what is on screen.
        HiddenVoicePool.onClientTick(m_mc, dt);

        if (m_mc.player == null || m_mc.isPaused() || m_mc.player.isCreative() || m_mc.player.isSpectator())
            return;

        m_cap = m_mc.player.getCapability(SanityProvider.CAP).orElse(null);
        if (m_cap == null)
            return;

        m_dt = dt;

        // The warning windows are measured in ticks but drained once per tick, so a delta far above one
        // tick means a window can open and close between two frames. Say so instead of leaving a missing
        // warning unexplained.
        if (m_dt >= SUSPICIOUS_TICK_DELTA)
            logWarningDiag("tick delta is " + m_dt + " ticks (warning windows are measured in ticks but drained once per tick)");

        if (m_flashTimer > 0)
            m_flashTimer -= dt;

        m_sanityGain = m_cap.getMadness() - m_prevSanity;
        if (Math.abs(m_sanityGain) >= 0.01f)
            m_flashTimer = 20;
        m_flashSanityGain = m_flashTimer <= 0 ? 0 : m_flashSanityGain + m_sanityGain;

        if (m_cap instanceof IPassiveSanity)
        {
            // Keep the arrow's window alive while a macaron is restoring sanity, even though that
            // recovery is applied on the server once per second and leaves the passive value at 0.
            boolean recovering = sanityRecovering();
            if (m_arrowTimer <= 0)
                m_arrowTimer = 23.99f;
            float p = ((IPassiveSanity)m_cap).getPassiveIncrease();
            if (p != 0 || recovering)
                m_arrowTimer -= dt;
        }

        if (m_cap.getMadness() >= .7f)
        {
            m_indicatorOffset = m_random.nextInt(3) - 1;

            // Only hints of the severe tier (1) and deeper (2) shake; the mild tier (0) stays still,
            // and so does m_hintStage == -1 (no regular hint on screen).
            // A line from the extra inner-voice pool shares the severe tier but is deliberately steady.
            // m_indicatorOffset (the shake of the HUD brain gauge itself) is unaffected.
            boolean hintShakes = m_hintStage >= 1 && !m_hintHiddenVoice;
            m_hintShakeAmplitude = shakeAmplitude();

            // The offset is held for HINT_SHAKE_INTERVAL_TICKS ticks and only re-rolled after that, so the
            // tremble has a frequency the eye can follow instead of changing on every rendered frame. The
            // draw call only reads these fields, which is what makes the interval effective.
            if (--m_hintShakeCooldown <= 0)
            {
                m_hintShakeCooldown = HINT_SHAKE_INTERVAL_TICKS;
                m_hintOffsetX = hintShakes ? m_random.nextInt(m_hintShakeAmplitude * 2 + 1) - m_hintShakeAmplitude : 0;
                m_hintOffsetY = hintShakes ? m_random.nextInt(m_hintShakeAmplitude * 2 + 1) - m_hintShakeAmplitude : 0;
            }
            else if (!hintShakes)
            {
                m_hintOffsetX = 0;
                m_hintOffsetY = 0;
            }
        }
        else
        {
            m_indicatorOffset = 0;
            m_hintOffsetX = 0;
            m_hintOffsetY = 0;
            m_hintShakeCooldown = 0;
        }

        // Advance the warning-window quiet stretch HERE, unconditionally, exactly once per tick.
        //
        // INVARIANT (a shipped bug, twice over): a per-tick countdown must never be advanced inside a branch
        // that can be skipped. This one used to be decremented at the end of tickDeepWarning, which returns
        // early while mania immunity is held - so the counter froze at its last positive value, tickHint then
        // dropped the centre line on every single tick, and NO inner-voice line appeared again until the buff
        // ran out (reported as "not a single line shows"). Decrementing here makes the countdown unable to
        // stall, whatever the window state does.
        if (m_maniaHintQuiet > 0f)
            m_maniaHintQuiet -= 1f;

        tickDeepWarning();    // run first: its quiet stretch and display state must be updated before tickHint
        MentalHintManager.tick(dt, HINT_SHAKE);   // countdown of the immediate "/sanity hint show" display
        tickHint(dt);
        tickBt(dt);

        m_prevSanity = m_cap.getMadness();
    }

    /** Writes one diagnostic line about a warning window, up to {@link #WARNING_DIAG_LINES} per session. */
    private void logWarningDiag(String message)
    {
        if (m_warningDiagBudget <= 0)
            return;

        m_warningDiagBudget--;
        SanityMod.LOGGER.info("[HINT-WINDOW] {}", message);
    }

    private void tickHint(float dt)
    {
        ResourceLocation dim = m_mc.player.level().dimension().location();
        float madness = m_cap.getMadness();

        // Immunity expiry warning owns the centre while its 5 second window is open (see
        // tickExpiryWarning). It takes priority over the madness tiers: when the immunity runs out while the
        // mania grace period is already over, damage starts immediately, so "the shield is going away" is
        // the useful line to show.
        if (tickExpiryWarning(dim, madness))
            return;

        // Pre-damage warning window: this mania talks in its own voice exactly once (see tickDeepWarning)
        if (m_deepText != null)
        {
            logWarningDiag("deep line on screen: [" + m_deepText.getString() + "] timer=" + m_deepTextTimer
                    + " dt=" + m_dt + " madness=" + madness);
            drawWarningLine(m_deepText, HINT_STAGE_DEEP);
            m_deepTextTimer -= m_dt;
            if (m_deepTextTimer <= 0f)
            {
                m_deepText = null;
                dropHint();
            }
            return;
        }
        // Quiet stretch of a warning window: the previous line retires and nothing replaces it yet
        if (m_maniaHintQuiet > 0f)
        {
            dropHint();
            return;
        }

        // While the immediate "/sanity hint show" line is on screen the regular hint yields,
        // so the centre never shows two monologues at once
        if (MentalHintManager.isImmediateActive())
        {
            dropHint();
            return;
        }

        // Sanity back above 50%: show no hint at all
        if (madness < HINT_STAGE0_MADNESS || !ConfigProxy.getRenderHint(dim))
        {
            dropHint();
            return;
        }

        // Once madness reaches the severe threshold the deeper pool no longer takes part in the regular
        // draw: it is reserved for the pre-damage warning window, so the same lines are not spent twice.
        int stage = madness >= HINT_SEVERE_MADNESS ? HINT_STAGE_SEVERE : MentalHintManager.tierForMadness(madness);

        if (m_hint != null && stage != m_hintStage)
        {
            m_hint = null;
            m_hintTimer = 0f;
            m_showingHintTimer = 0f;
            m_hintHiddenVoice = false;
            m_hintColor = 0xFFFFFF;
        }

        if (m_hintTimer <= 0f && m_showingHintTimer <= 0f)
        {
            // The draw may include a line from the extra inner-voice pool when the severe tier is drawn and the
            // current save has unlocked it (see HiddenVoicePool). The command preview deliberately does not.
            MentalHintManager.Pick picked = MentalHintManager.pickHintForDraw(stage);

            // Type-pool diagnostics: report the synced counts whenever they change (and on the first pick of a
            // session). The type pools are invisible to /sanity hint list by design, so "the new lines never
            // appeared" has to be told apart from "the counts never reached this client" - and the only place
            // that can answer it is this line.
            String held = TypeHintPools.heldSummaryIfChanged();

            if (!held.isEmpty())
                SanityMod.LOGGER.info("[HINT-TYPE] held={} active mild={} severe={}",
                        held, TypeHintPools.activeLineCount(0), TypeHintPools.activeLineCount(1));

            if (picked == null)
            {
                // This tier has no candidate line at all (should not happen: the default pool is never empty)
                dropHint();
                return;
            }

            m_hint = picked.text();
            m_hintHiddenVoice = picked.hidden();
            // The colour travels with the pick: white for the normal pools, dark red for the extra inner voice
            // and the type colour for the chaos-restraint type pool (see TypeHintSpec.POOLS). Reading it from
            // the pick instead of re-deriving it here keeps that table the single source of the colours.
            m_hintColor = picked.color();
            m_hiddenVoiceDrawLogged = false;
            // Worse tiers keep their line on screen for a shorter time; the extra inner-voice line is a short
            // whisper rather than a thought that lingers, so it has its own window (measured live: the severe
            // window felt too long for it).
            m_hintTimer = stage == 0 ? 2000 : 600;
            float showTicks = m_hintHiddenVoice ? HiddenVoicePool.SHOW_TICKS : HINT_SHOW_TICKS;

            // Existing behaviour: the 3rd mild line and the 1st severe line play a swish sound
            int soundAt = stage == 0 ? 2 : 0;
            if (ConfigProxy.getPlaySounds(dim) && picked.id() >= 0 && picked.id() == soundAt)
                m_mc.getSoundManager().play(new SwishSoundInstance());

            if (m_hintHiddenVoice)
            {
                // One line per pick: written separately from the draw so "picked but never drawn" stays
                // distinguishable from "never picked", which is how the two earlier display bugs were told apart.
                SanityMod.LOGGER.info("[THIRD-VOICE] line picked: {}", m_hint.getString());

                if (ConfigProxy.getPlaySounds(dim) && HiddenVoicePool.playSoundIfReady(m_mc))
                    SanityMod.LOGGER.info("[THIRD-VOICE] cave sound played");
            }
            else if (picked.fromTypePool())
            {
                // One line per pick, exactly like the extra voice above: without it a real-machine session
                // cannot tell "the type pool was never dealt" from "it was dealt and drawn" - and the type
                // pools are invisible to /sanity hint list by design, so the log is the only evidence.
                SanityMod.LOGGER.info("[HINT-TYPE] type={} tier={} held={} line={}",
                        picked.typeId(), stage, TypeHintPools.heldSummary(), m_hint.getString());
            }

            m_hintStage = stage;
            m_showingHintTimer = (m_maxShowingHintTimer = showTicks);
        }

        if (m_showingHintTimer > 0f)
        {
            m_showingHintTimer -= dt;
        }
        else
        {
            // The line has had its time: take it off the screen and let the centre stay empty until the next
            // pick. Removing it here is the difference between "a line appears, lives, and leaves" and what
            // was measured live: the text stayed visible at the alpha floor for the rest of the gap (30-60 s
            // of the same sentence) and the next line then replaced it with no pause at all.
            //
            // m_hintTimer is deliberately left alone: it is the pause before the next line, and zeroing it
            // here would make the next line appear on the very next tick.
            if (m_hint != null)
            {
                m_hint = null;
                m_hintStage = -1;
                m_hintColor = 0xFFFFFF;
                m_hintHiddenVoice = false;
            }

            m_hintTimer = MathHelper.clamp(m_hintTimer - dt, 0, Float.MAX_VALUE);
        }
    }

    /** Whether the player currently holds the mania-immunity effect, which blocks the mania true damage only. */
    private boolean hasManiaImmunity()
    {
        return m_mc.player != null && m_mc.player.hasEffect(EffectRegistry.MANIA_IMMUNITY.get());
    }

    /**
     * The immunity expiry warning window: the last {@link #IMMUNITY_EXPIRY_WARNING_TICKS} of an immunity
     * buff.
     *
     * <p>Two buffs are watched, and whichever runs out <b>first</b> is the one announced:
     * <ul>
     *   <li>{@code mania_immunity} (the Beta stabilizer) - blocks the mania true damage;</li>
     *   <li>{@code inner_immunity} (the Gamma stabilizer, "inner entities ignore you") - stops inner
     *       entities from attacking and stops a crawler from self-destructing.</li>
     * </ul>
     * Both buffers protect against something the player wants to know is about to end, so both use the
     * same separate expiry pool ({@link MentalHintManager#INDEX_EXPIRY}). They are never mixed with the
     * three madness tiers.
     *
     * <p>Warnings are only meaningful while something is actually coming. The buffs block their damage, so
     * no warning is shown anywhere else in the buff, and the expiry warning is shown <b>instead of</b> the
     * madness-tier lines (which stay for the rest of the buff). Both end at the same instant, so no label
     * is ever left on screen.
     *
     * <p>A breakdown has to be running for the buff to matter at all: if sanity recovered above 50% (which
     * clears {@code maniaTicks}) and the buff is still ticking, its expiry warns about nothing and the
     * window stays closed.
     *
     * @return {@code true} when the caller must stop and leave the centre to this warning
     */
    private boolean tickExpiryWarning(ResourceLocation dim, float madness)
    {
        boolean enabled = madness >= HINT_STAGE0_MADNESS
                && ConfigProxy.getRenderHint(dim)
                && m_cap.getManiaTicks() > 0;

        MobEffectInstance expiring = enabled ? expiringImmunity() : null;

        if (expiring == null)
        {
            // Window closed (no immunity buff inside its last five seconds): drop the line at once and arm
            // the next window. The window only counts as "new" once it has been closed for longer than the
            // window itself - so the Beta and Gamma immunity running out within five seconds of each other
            // stays ONE window and is announced once, while a genuinely later expiry is announced again.
            m_expiryWindowClosedTicks += m_dt;
            m_expiryWarning = null;

            if (m_expiryWindowClosedTicks > IMMUNITY_EXPIRY_WARNING_TICKS)
                m_expiryWindowAnnounced = false;

            return false;
        }

        // A window is currently open: whatever was announced belongs to this same window.
        m_expiryWindowClosedTicks = 0f;

        // ---- 1) A picked line is still on screen: keep drawing it and keep the centre ---------------
        // INVARIANTS this block exists to protect (two separate bugs have already shipped from breaking
        // them, so do not merge it back into the pick block below):
        //   a) It must come BEFORE the "already announced" guard. With the guard first, every frame after
        //      the picking one returned early: the timer was armed to 100 but never decremented again, so
        //      the line was drawn exactly once and then frozen - the "only shows for one frame" report.
        //   b) The timer is advanced HERE and only here, exactly once per frame. drawWarningLine()
        //      re-arms m_showingHintTimer every frame, so drawing and decrementing must stay together.
        // The deep pre-damage warning keeps the same shape a different way (tickDeepWarning only PICKS,
        // tickHint draws and decrements); this block is the expiry warning's equivalent of that split.
        if (m_expiryWarningTimer > 0f && m_expiryWarning != null)
        {
            // Diagnostics only: this runs while the expiry line is on screen, and the budget in
            // logWarningDiag keeps it from flooding the log.
            logWarningDiag("expiry line on screen: [" + m_expiryWarning.getString() + "] timer="
                    + m_expiryWarningTimer + " dt=" + m_dt + " madness=" + madness);
            drawWarningLine(m_expiryWarning, HINT_STAGE_EXPIRY);
            m_expiryWarningTimer -= m_dt;
            if (m_expiryWarningTimer <= 0f)
                m_expiryWarning = null;
            return true;
        }

        // ---- 2) Nothing on screen: announce this window once ---------------------------------------
        // Already announced in this window: keep the centre free instead of repeating the line.
        //
        // NOTE: this check must not be "absorbed" into the state above. An earlier version pushed the
        // window generation into the warned generation on every open frame, which made the two counters
        // equal on the very first frame and left this branch - and the pick below it - permanently
        // unreachable, so the expiry line never appeared for a whole session at all.
        if (m_expiryWindowAnnounced)
            return false;

        m_expiryWarning = pickExpiryWarning();
        m_expiryWarningTimer = WARNING_LINE_SHOW_TICKS;
        m_expiryWindowAnnounced = true;
        // One line per window: makes "the expiry pool never appeared" verifiable from the log alone
        SanityMod.LOGGER.info("[HINT-WINDOW] {} expiring ({} ticks left): {}",
                effectName(expiring), expiring.getDuration(),
                m_expiryWarning == null ? "<none available>" : m_expiryWarning.getString());

        // Nothing configured for this pool: leave the window unannounced so the tier lines keep the
        // centre, and re-check on the next frame (without spamming the log) in case the pool gains a
        // line while this window is still open.
        if (m_expiryWarning == null)
            m_expiryWindowAnnounced = false;

        // Defensive: an empty or blank pool means "nothing configured" (the player may have removed every
        // line, or the lang value may be blank), so yield to the tier lines instead of drawing nothing
        if (m_expiryWarning == null)
            return false;

        // ---- 3) First frame of the window: draw it once, from the next frame on block 1 owns it -----
        logWarningDiag("expiry line on screen: [" + m_expiryWarning.getString() + "] timer="
                + m_expiryWarningTimer + " dt=" + m_dt + " madness=" + madness);
        drawWarningLine(m_expiryWarning, HINT_STAGE_EXPIRY);
        m_expiryWarningTimer -= m_dt;
        return true;
    }

    /**
     * The immunity buff that is inside its last {@link #IMMUNITY_EXPIRY_WARNING_TICKS} ticks, or
     * {@code null} when neither is.
     *
     * <p>When both are about to run out, the one that expires <b>sooner</b> is announced: that is the one
     * whose protection the player is about to lose first. The second one is then suppressed for as long as
     * the two windows can still overlap (see {@code tickExpiryWarning}), so an overlap never produces two
     * warnings.
     */
    private MobEffectInstance expiringImmunity()
    {
        MobEffectInstance mania = m_mc.player.getEffect(EffectRegistry.MANIA_IMMUNITY.get());
        MobEffectInstance inner = m_mc.player.getEffect(EffectRegistry.INNER_IMMUNITY.get());

        boolean maniaSoon = mania != null && mania.getDuration() <= IMMUNITY_EXPIRY_WARNING_TICKS;
        boolean innerSoon = inner != null && inner.getDuration() <= IMMUNITY_EXPIRY_WARNING_TICKS;

        if (maniaSoon && innerSoon)
            return mania.getDuration() <= inner.getDuration() ? mania : inner;

        return maniaSoon ? mania : innerSoon ? inner : null;
    }

    /** Readable name of an immunity buff, for the diagnostic log. */
    private static String effectName(MobEffectInstance instance)
    {
        return instance.getEffect() == EffectRegistry.INNER_IMMUNITY.get()
                ? "inner immunity (ignore inner)"
                : "mania immunity";
    }

    /**
     * Shows a warning line through the regular hint path and refreshes its quiet stretch.
     *
     * <p>The display duration and the quiet stretch are the transition the mania whisper used: the line is
     * shown for {@link #WARNING_LINE_SHOW_TICKS} while nothing else is drawn for
     * {@link #WARNING_LINE_TAIL_TICKS} afterwards.
     */
    private void drawWarningLine(MutableComponent line, int stage)
    {
        if (m_hintStage != stage)
            dropHint();

        m_hint = line;
        m_hintStage = stage;
        // The warning windows never draw a line from the extra inner-voice pool, so the style stays the
        // regular white and the shake below stays active.
        m_hintHiddenVoice = false;
        m_hintColor = 0xFFFFFF;
        m_showingHintTimer = (m_maxShowingHintTimer = WARNING_LINE_SHOW_TICKS);
        m_showingHintTimer -= m_dt;
        m_maniaHintQuiet = WARNING_LINE_TAIL_TICKS;
    }

    /**
     * The pre-damage warning window of a mania: the deep tier talks <b>once</b>, in the last 5 seconds of the
     * grace period, instead of the regular draw.
     *
     * <p>Why the deep tier is reserved for this: the deep pool and the old mania whisper carry the same four
     * lines, so letting the regular draw use them would spend the same words twice and at the wrong moment.
     * The regular draw falls back to the severe tier from the severe threshold upwards, so this window is the
     * only place the deep lines appear.
     *
     * <p>An immunity taken before the window opens keeps the whole window closed, and that only changes what
     * is drawn: the mania timer (and therefore when the true damage starts) keeps running untouched.
     */
    private void tickDeepWarning()
    {
        int mania = m_cap.getManiaTicks();

        if (mania <= 0)
        {
            // Mania over: clear the display and arm the next mania
            m_deepTextDone = false;
            m_deepText = null;
            m_deepTextTimer = 0f;
            m_maniaHintQuiet = 0f;
            return;
        }

        // The immunity blocks the damage this window would warn about, so the window never opens while it is
        // held; the immunity expiry warning speaks instead.
        //
        // The centre must not stay reserved for a line that will never come: the leading stretch above may
        // already have armed the quiet stretch, and holding the centre for it is what silenced every
        // inner-voice line for the whole duration of the buff. Only an unspoken window is released; a quiet
        // stretch that belongs to a deep line already on screen is left to drain on its own.
        if (hasManiaImmunity())
        {
            if (!m_deepTextDone)
                m_maniaHintQuiet = 0f;

            return;
        }

        final int triggerAt = SanityCombat.MANIA_GRACE_TICKS - 100;   // last 5 seconds of the grace period

        if (!m_deepTextDone && mania >= triggerAt - WARNING_LINE_LEAD_TICKS)
        {
            // Leading stretch: retire whatever is on screen, then talk
            m_maniaHintQuiet = Math.max(m_maniaHintQuiet,
                    (triggerAt - mania) + WARNING_LINE_SHOW_TICKS + WARNING_LINE_TAIL_TICKS);
            m_hint = null;
            m_hintStage = -1;
            m_hintTimer = 0f;
            m_showingHintTimer = 0f;
        }

        if (!m_deepTextDone && mania >= triggerAt)
        {
            m_deepTextDone = true;
            m_deepText = pickDeepWarning();
            m_deepTextTimer = WARNING_LINE_SHOW_TICKS;
            // One line per mania: makes "the deep tier never appeared" verifiable from the log alone
            SanityMod.LOGGER.info("[HINT-WINDOW] deep tier speaks (maniaTicks {}): {}",
                    mania, m_deepText == null ? "<none available>" : m_deepText.getString());
        }

        // No countdown here on purpose: the quiet stretch is advanced in tick() so it cannot be skipped
        // (see the invariant there).
    }

    /**
     * Picks one line of the deep tier for the pre-damage warning.
     *
     * <p>Resolved to a literal here because the centre is drawn with {@code drawInBatch(Component, ...)}, which
     * does not resolve translations; the player-name placeholder of the last line is substituted explicitly by
     * the pool.
     */
    private MutableComponent pickDeepWarning()
    {
        MentalHintManager.Pick picked = MentalHintManager.pickHint(HINT_STAGE_DEEP);
        return picked == null ? null : picked.text();
    }

    /**
     * Picks one line from the expiry warning pool ({@link MentalHintManager#INDEX_EXPIRY}).
     *
     * <p>Built-in and custom lines are drawn from the same roll, so candidates are filtered for actual text:
     * a pool whose lines were all removed behaves exactly like an empty pool.
     */
    private MutableComponent pickExpiryWarning()
    {
        List<MentalHintManager.Hint> candidates = new ArrayList<>();
        for (MentalHintManager.Hint h : MentalHintManager.effectiveHintEntries(HINT_STAGE_EXPIRY))
        {
            if (h.hasText())
                candidates.add(h);
        }

        if (candidates.isEmpty())
            return null;

        return Component.literal(candidates.get(m_random.nextInt(candidates.size())).text());
    }

    /** Drops the current regular hint (the single path used for tier changes, quiet periods and yielding). */
    private void dropHint()
    {
        m_hint = null;
        m_hintStage = -1;
        m_hintTimer = 0f;
        m_showingHintTimer = 0f;
        m_hintHiddenVoice = false;
        m_hintColor = 0xFFFFFF;
    }

    private void tickBt(float dt)
    {
        ResourceLocation dim = m_mc.player.level().dimension().location();
        boolean flash = ConfigProxy.getFlashBtOnShortBurst(dim);
        boolean passive = ConfigProxy.getRenderBtPassive(dim);

        if (!ConfigProxy.getRenderBtOverlay(dim) || !(flash || passive))
            return;

        if (m_sanityGain >= .002f && flash)
            m_btGainedAlpha = Mth.lerp(MathHelper.clampNorm(Mth.inverseLerp(m_sanityGain, .002f, .02f)), .4f, .75f);

        if (passive)
            m_btDelay = Mth.clamp(m_btDelay + (m_cap instanceof IPassiveSanity ps && ps.getPassiveIncrease() < 0f ? dt : -dt), 0, BT_DELAY);

        if (m_btGainedAlpha > 0f && flash)
        {
            if (m_btAlpha < m_btGainedAlpha)
                m_btAlpha = Mth.clamp(m_btAlpha + .5f, 0f, m_btGainedAlpha);
            else
                m_btGainedAlpha = 0f;
        }
        else if (m_btDelay >= BT_DELAY && passive)
        {
            if (m_btAlpha < .15f)
            {
                m_btTimer = 0;
                m_btAlpha = Mth.clamp(m_btAlpha + .1f, m_btAlpha, .15f);
            }
            else if (m_btAlpha > .3f)
            {
                m_btTimer = Mth.PI / .2f;
                m_btAlpha = Mth.clamp(m_btAlpha - .1f, .3f, m_btAlpha);
            }
            else
            {
                m_btAlpha = Mth.lerp((-Mth.cos((float)m_btTimer * .2f) + 1f) * .5f, .15f, .3f);
                m_btTimer += m_dt;
            }
        }
        else
            m_btAlpha = Mth.clamp(m_btAlpha - .1f, 0f, m_btAlpha);
    }

    public void initOverlays(final RegisterGuiOverlaysEvent event)
    {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), SanityMod.MODID.concat(".sanity_indicator"), this::renderSanityIndicator);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), SanityMod.MODID.concat(".hint"), this::renderHint);
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), SanityMod.MODID.concat(".blood_tendrils_overlay"), this::renderBloodTendrilsOverlay);
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), SanityMod.MODID.concat(".mania_warning"), this::renderManiaWarning);
        // The deep-tier and expiry warning lines share the regular hint overlay (renderHint draws m_hint),
        // so no separate overlay is registered for them.
        // Immediate "/sanity hint show" display: draw it on top so no other HUD element hides the preview
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), SanityMod.MODID.concat(".immediate_hint"), this::renderImmediateHint);
    }

    public void initPostProcessor()
    {
        if (m_post != null) return;

        m_post = new PostProcessor();
        initSanityPostProcess();
    }

    public PostProcessor getPostProcessor()
    {
        return m_post;
    }

    public void renderPostProcess(float partialTicks)
    {
        if (m_post == null) return;

        m_post.render(partialTicks);
    }

    public void resize(int w, int h)
    {
        if (m_post == null) return;

        m_post.resize(w, h);
    }

    private static void renderFullscreen(PoseStack poseStack, int scw, int sch, int texw, int texh, int uoffset, int voffset, int spritew, int spriteh, float alpha)
    {
        Matrix4f mat = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.enableBlend();
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        bufferbuilder.vertex(mat, 0f, 0f, 0f).color(1f, 1f, 1f, alpha).uv((float)uoffset / texw, (float)voffset / texh).endVertex();
        bufferbuilder.vertex(mat, 0f, (float)sch, 0f).color(1f, 1f, 1f, alpha).uv((float)uoffset / texw, (float)(voffset + spriteh) / texh).endVertex();
        bufferbuilder.vertex(mat, (float)scw, (float)sch, 0f).color(1f, 1f, 1f, alpha).uv((float)(uoffset + spritew) / texw, (float)(voffset + spriteh) / texh).endVertex();
        bufferbuilder.vertex(mat, (float)scw, 0f, 0f).color(1f, 1f, 1f, alpha).uv((float)(uoffset + spritew) / texw, (float)voffset / texh).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
        RenderSystem.disableBlend();
    }
}