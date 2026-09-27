package piloser.sanitypd.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import piloser.sanitypd.SanityMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The extra inner voice: a small set of lines that joins the severe tier's draw once the current save has
 * reached a certain late-game milestone, plus the sound those lines trigger.
 *
 * <h2>Unlock, per save</h2>
 * The pool starts locked in every save. It unlocks the first time the end poem is put on screen
 * ({@code MixinWinScreen} calls {@link #markPoemSeen()}), and that unlock is written into the save folder
 * ({@value #SAVE_FILE_NAME}). Storing it there means it survives a restart, never leaks into another save,
 * and travels with a copied save. A remote or dedicated server has no local save folder, so the pool stays
 * locked there.
 *
 * <h2>How the lines are used</h2>
 * They are merged into the severe tier by {@link MentalHintManager#pickHintForDraw(int)} and by nothing
 * else: the command list does not show them, {@code add}/{@code remove}/{@code clear} cannot touch them, and
 * the {@code /sanity hint show} preview cannot draw them. A custom line that repeats one of them is refused
 * silently instead (see {@link HiddenVoiceText}). On screen they keep the severe tier's size and duration,
 * are drawn dark red ({@value #COLOR_DARK_RED}) and never shake.
 *
 * <h2>Sound</h2>
 * A line from this pool plays a cave sound at the player (no positional falloff) at most once every
 * {@value #SOUND_COOLDOWN_TICKS} ticks. Like the existing hint sound it honours the per-dimension sound
 * switch, so it stays silent where hint sounds are disabled.
 */
@OnlyIn(Dist.CLIENT)
public final class HiddenVoicePool
{
    /**
     * Language key prefix; the index is appended, giving {@code hint40} .. {@code hint45}.
     *
     * <p>The count is mirrored by {@link #LINE_COUNT} and by the language-key check in
     * {@code audit-bindings.mjs}; all three have to be changed together.
     */
    private static final String KEY_PREFIX = "gui." + SanityMod.MODID + ".hint4";

    /** Built-in line count, matching the {@code hint4X} keys in the language files. */
    public static final int LINE_COUNT = 6;

    /** On-screen colour of these lines (standard dark red). */
    public static final int COLOR_DARK_RED = 0x8B0000;

    /**
     * How long one of these lines stays on screen, in ticks (5 s).
     *
     * <p>Shorter than the regular severe line on purpose: it is a whisper that interrupts, not a thought that
     * lingers. Measured live at 199 ticks (the severe window) the line felt too long, so it has its own
     * window; the value is one constant so it can be tuned without touching anything else.
     */
    public static final float SHOW_TICKS = 100f;

    /** Ticks between two cave sounds: five minutes at twenty ticks per second. */
    public static final int SOUND_COOLDOWN_TICKS = 5 * 60 * 20;

    /** Volume and pitch of the cave sound; the vanilla cave ambience plays its variants at these values. */
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    /** Name of the per-save unlock marker inside the save folder. */
    public static final String SAVE_FILE_NAME = "sanitypd_third_voice.json";

    /** Marker field, spelled out so the file explains itself to whoever opens it. */
    private static final String SAVE_FIELD = "poemSeen";

    /** The pool, built lazily so the language files are definitely loaded. */
    private static MutableComponent[] s_lines;

    /** Whether the current save has unlocked the pool. */
    private static boolean s_unlocked;

    /** Save folder the current state was read from, so a save change is detected by comparison. */
    private static Path s_loadedSaveRoot;

    /** Save folder the current state belongs to; {@code null} while not in a local world. */
    private static Path s_saveRoot;

    /** Ticks left before another cave sound may play. */
    private static float s_soundCooldown;

    /** Whether the "no local save folder" line was already written, to keep the log quiet. */
    private static boolean s_remoteLogged;

    private HiddenVoicePool() {}

    // ------------------------------------------------------------------ state

    /**
     * Client tick: keeps the sound cooldown and re-reads the marker when the played save changes.
     *
     * <p>Called from the client tick rather than from the hint draw so the state is correct even while the
     * draw is gated (paused, creative, HUD hidden).
     */
    public static void onClientTick(Minecraft mc, float dt)
    {
        if (s_soundCooldown > 0f)
            s_soundCooldown = Math.max(0f, s_soundCooldown - dt);

        Path root = currentSaveRoot(mc);

        if (!Objects.equals(root, s_loadedSaveRoot))
        {
            s_loadedSaveRoot = root;
            s_saveRoot = root;
            s_unlocked = readMarker(root);
            SanityMod.LOGGER.info("[THIRD-VOICE] save changed; pool is {} for this save",
                    s_unlocked ? "unlocked" : "locked");
        }
    }

    /** Whether the current save has unlocked the pool. */
    public static boolean isUnlocked()
    {
        return s_unlocked;
    }

    /**
     * Unlocks the pool for the current save and records it in the save folder.
     *
     * <p>Called when the end poem is put on screen. This method never throws: the credits screen must not be
     * able to break because a save folder is read-only, and a failed write only means the pool stays
     * unlocked for this session instead of forever.
     */
    public static void markPoemSeen()
    {
        Minecraft mc = Minecraft.getInstance();
        Path root = currentSaveRoot(mc);

        if (root == null)
        {
            if (!s_remoteLogged)
            {
                s_remoteLogged = true;
                SanityMod.LOGGER.info("[THIRD-VOICE] no local save folder here; the pool stays locked");
            }
            return;
        }

        s_saveRoot = root;
        s_unlocked = true;
        s_loadedSaveRoot = root;

        try
        {
            Files.createDirectories(root);

            JsonObject json = new JsonObject();
            json.addProperty(SAVE_FIELD, true);
            Files.writeString(root.resolve(SAVE_FILE_NAME), json.toString(), StandardCharsets.UTF_8);

            SanityMod.LOGGER.info("[THIRD-VOICE] pool unlocked for this save");
        }
        catch (Exception e)
        {
            SanityMod.LOGGER.warn("[THIRD-VOICE] could not write the save marker, the pool stays unlocked "
                    + "for this session only: {}", e.toString());
        }
    }

    /** Save folder of the local world, or {@code null} on a remote or dedicated server. */
    private static Path currentSaveRoot(Minecraft mc)
    {
        try
        {
            IntegratedServer server = mc.getSingleplayerServer();
            return server == null ? null : server.getWorldPath(LevelResource.ROOT);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** Reads the unlock marker; any failure means "locked", never an exception. */
    private static boolean readMarker(Path root)
    {
        if (root == null)
            return false;

        Path file = root.resolve(SAVE_FILE_NAME);

        if (!Files.exists(file))
            return false;

        try
        {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.has(SAVE_FIELD) && json.get(SAVE_FIELD).getAsBoolean();
        }
        catch (Exception e)
        {
            SanityMod.LOGGER.warn("[THIRD-VOICE] could not read the save marker, treating the pool as locked: {}",
                    e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ lines

    private static void buildLines()
    {
        if (s_lines != null)
            return;

        s_lines = new MutableComponent[LINE_COUNT];

        for (int i = 0; i < LINE_COUNT; i++)
            s_lines[i] = Component.translatable(KEY_PREFIX + i);
    }

    /** Raw language values, in file order. */
    private static List<String> rawLines()
    {
        buildLines();

        List<String> out = new ArrayList<>(s_lines.length);

        for (MutableComponent line : s_lines)
        {
            try
            {
                out.add(line.getString());
            }
            catch (Throwable t)
            {
                // A broken language value must not take the draw down with it; treat it as blank.
                out.add("");
            }
        }

        return out;
    }

    /**
     * Resolved texts that may join the severe draw.
     *
     * <p>Blank values are skipped (a blanked language value means "off", the same rule the three madness
     * tiers use), and so is any line the player already has as a custom line of that tier or that appears
     * twice here: the point of the pool is a new sentence, not the same one twice.
     *
     * @param alreadyVisible raw texts of the lines that are already in the same draw, may be {@code null}
     */
    public static List<String> eligibleLines(List<String> alreadyVisible)
    {
        if (!s_unlocked)
            return Collections.emptyList();

        buildLines();

        String playerName = playerName();
        List<String> out = new ArrayList<>(s_lines.length);

        for (String raw : rawLines())
        {
            if (raw == null || raw.isBlank())
                continue;

            String text = HiddenVoiceText.fill(raw, playerName);

            if (HiddenVoiceText.matchesAny(text, alreadyVisible, playerName))
                continue;

            if (HiddenVoiceText.matchesAny(text, out, playerName))
                continue;

            out.add(text);
        }

        return out;
    }

    /**
     * Whether a text the player wants to add repeats one of these lines.
     *
     * <p>Compared against the values of the language in use, because that is the text that can actually
     * reach the screen; a line in another language cannot put a second copy of the same sentence on it.
     */
    public static boolean matchesForbiddenText(String typed)
    {
        return HiddenVoiceText.matchesAny(typed, rawLines(), playerName());
    }

    /**
     * Number of language values that resolved to drawable text.
     *
     * <p>Exposed for the diagnostic probe: it verifies that every {@code hint4X} key resolves without
     * printing any of the lines.
     */
    public static int resolvedLineCount()
    {
        int n = 0;

        for (String raw : rawLines())
        {
            if (raw != null && !raw.isBlank())
                n++;
        }

        return n;
    }

    /** Player name, or an empty string when it is not available yet. */
    private static String playerName()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.getDisplayName().getString() : "";
    }

    // ------------------------------------------------------------------ sound

    /**
     * Plays the cave sound if the cooldown allows it.
     *
     * @return whether a sound was played
     */
    public static boolean playSoundIfReady(Minecraft mc)
    {
        if (s_soundCooldown > 0f)
            return false;

        try
        {
            // The registry holds this entry as a Holder; during client runtime it is always bound, but the
            // null guard keeps a surprising state from throwing inside the draw path.
            SoundEvent cave = SoundEvents.AMBIENT_CAVE.get();

            if (cave == null)
                return false;

            // Local ambience (no positional falloff) on purpose: the voice is supposed to come from inside
            // the player's head rather than from a point in the world.
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(cave, SOUND_PITCH, SOUND_VOLUME));
            s_soundCooldown = SOUND_COOLDOWN_TICKS;
            return true;
        }
        catch (Throwable t)
        {
            SanityMod.LOGGER.warn("[THIRD-VOICE] cave sound could not be played: {}", t.toString());
            return false;
        }
    }

    /** Ticks left before another cave sound may play; used by the diagnostic probe. */
    public static float soundCooldownTicks()
    {
        return s_soundCooldown;
    }

    /** Save folder the current state belongs to; used by the diagnostic probe. */
    public static Path loadedSaveRoot()
    {
        return s_saveRoot;
    }
}
