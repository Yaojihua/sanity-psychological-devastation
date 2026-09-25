package piloser.sanitypd.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.capability.ISanity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Inner monologue hint system: three tiers plus a player-defined pool.
 *
 * <p>Tiers by madness ({@code madness = 1 - sanity/max}): tier 0 "mild" at {@value #T0_MADNESS}
 * (sanity 50%), tier 1 "severe" at {@value #T1_MADNESS} (sanity 25%) and tier 2 "deep" at
 * {@value #T2_MADNESS} (sanity 10%). Defaults come from the lang keys {@code gui.sanitypd.hint0X}
 * (12 lines), {@code hint1X} (9) and {@code hint2X} (4).
 *
 * <p>Players extend any tier with {@code /sanity hint add|remove|clear}. Custom lines are merged
 * with the built-in ones instead of replacing them, and removing them all falls back to the
 * defaults. The pool is stored per client instance in {@code config/sanitypd_mental_hints.json}.
 *
 * <p>The whole class is client-only ({@link OnlyIn}) because hints apply to the current save only,
 * so they are stored and rendered locally with no new network packet. Server code never references
 * it: {@code SanityCommand} selects a tier by string constant and dispatches through
 * {@code DistExecutor}.
 */
@OnlyIn(Dist.CLIENT)
public final class MentalHintManager
{
    // ------------------------------------------------------------------ tier definitions

    /** Number of tiers (mild / severe / deep). */
    public static final int TIER_COUNT = 3;

    /**
     * Index of the extra "immunity is about to expire" warning pool.
     *
     * <p>It is deliberately <b>not</b> one of the madness tiers: no madness value selects it, it never
     * takes part in {@code tierForMadness}, and by default it ships <b>empty</b>, so it can never appear
     * unless a line was added through {@code /sanity hint expiry add <text>}. {@link GuiHandler} shows a
     * line from it only during the last {@code IMMUNITY_EXPIRY_WARNING_TICKS} of the mania-immunity
     * buff (see the expiry warning window there). Keeping it separate is what stops it from ever mixing
     * with the three madness pools.
     */
    public static final int INDEX_EXPIRY = TIER_COUNT;

    public static final float T0_MADNESS = .50f;
    public static final float T1_MADNESS = .75f;
    public static final float T2_MADNESS = .90f;

    /**
     * Madness at or above which the <b>severe</b> tier becomes the single regular line, i.e. the deep tier
     * stops taking part in the regular draw and is reserved for the pre-damage warning window (see
     * {@code GuiHandler#tickDeepWarning}). Equal to {@link #T1_MADNESS} on purpose: past the severe threshold
     * the player gets the severe voice, and the deep voice is only heard once, right before the mania damage
     * starts.
     */
    public static final float SEVERE_ONLY_MADNESS = T1_MADNESS;

    /** Madness threshold per tier; index = tier. */
    public static final float[] TIER_THRESHOLDS = { T0_MADNESS, T1_MADNESS, T2_MADNESS };

    /** Lang key prefix per tier; the index is appended, e.g. {@code hint00}, {@code hint10}, {@code hint20}. */
    private static final String[] TIER_KEY_PREFIXES =
    {
        "gui." + SanityMod.MODID + ".hint0",
        "gui." + SanityMod.MODID + ".hint1",
        "gui." + SanityMod.MODID + ".hint2",
    };

    /** Default line count per tier, matching the keys in the lang file. */
    private static final int[] TIER_DEFAULT_COUNTS = { 12, 9, 4 };

    /**
     * Lang key prefix for the extra expiry warning pool; the index is appended, e.g. {@code hint30}.
     *
     * <p>The matching lang values are real lines. A blanked or removed value can never be drawn: the draw
     * path in {@link GuiHandler} skips empty text, so an emptied pool behaves as "off" instead of leaving
     * an empty line on screen. Owners can add their own with {@code /sanity hint expiry add <text>}.
     */
    private static final String TIER_KEY_PREFIX_EXPIRY = "gui." + SanityMod.MODID + ".hint3";

    /** Number of built-in lines in the expiry warning pool, matching the keys in the lang file. */
    private static final int TIER_DEFAULT_COUNT_EXPIRY = 3;

    /** Lang key holding the display name of the expiry warning pool, used for command feedback. */
    private static final String TIER_NAME_KEY_EXPIRY = "commands.sanity.hint.tier.3";

    /** Lang keys holding the tier names, used for command feedback. */
    private static final String[] TIER_NAME_KEYS =
    {
        "commands.sanity.hint.tier.0",
        "commands.sanity.hint.tier.1",
        "commands.sanity.hint.tier.2",
    };

    /** Display duration in ticks (6 s) for an immediate show via {@code /sanity hint show}. */
    public static final float SHOW_TICKS = 120f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RAND = new Random();

    /** Default pool, built lazily once and then reused. */
    private static MutableComponent[][] s_defaults;

    /** Custom pool: {@code custom[tier]} holds the lines the player added (empty = defaults only). */
    private static final List<List<String>> s_custom = new ArrayList<>();

    private static boolean s_loaded;

    // state for the immediate show
    private static MutableComponent s_immediateText;
    private static float s_immediateTimer;
    private static int s_immediateShakeX;
    private static int s_immediateShakeY;

    static
    {
        // One list per pool: the three madness tiers plus the separate expiry warning pool
        for (int i = 0; i <= INDEX_EXPIRY; i++)
            s_custom.add(new ArrayList<>());
    }

    private MentalHintManager() {}

    /** Called once on client setup: builds the default pools and loads the local custom pool. */
    public static void onClientSetup()
    {
        buildDefaults();
        load();
    }

    private static void buildDefaults()
    {
        if (s_defaults != null)
            return;

        s_defaults = new MutableComponent[INDEX_EXPIRY + 1][];

        for (int tier = 0; tier < TIER_COUNT; tier++)
        {
            int count = TIER_DEFAULT_COUNTS[tier];
            MutableComponent[] pool = new MutableComponent[count];

            for (int i = 0; i < count; i++)
                pool[i] = Component.translatable(TIER_KEY_PREFIXES[tier] + i);

            s_defaults[tier] = pool;
        }

        // The expiry warning pool ships with real lines in the lang file (hint30..hint32). The draw path
        // still skips blank text (see hasText), so an owner who blanks or removes every value leaves the
        // pool behaving as "off" instead of drawing an empty line.
        MutableComponent[] expiryPool = new MutableComponent[TIER_DEFAULT_COUNT_EXPIRY];
        for (int i = 0; i < TIER_DEFAULT_COUNT_EXPIRY; i++)
            expiryPool[i] = Component.translatable(TIER_KEY_PREFIX_EXPIRY + i);
        s_defaults[INDEX_EXPIRY] = expiryPool;
    }

    // ------------------------------------------------------------------ queries

    /**
     * Whether the pool index is valid; command input can be any integer.
     *
     * <p>Accepts the three madness tiers and {@link #INDEX_EXPIRY} (the separate expiry warning pool).
     */
    public static boolean isValidTier(int tier)
    {
        return tier >= 0 && tier <= INDEX_EXPIRY;
    }

    /** Display name of a pool (mild / severe / deep / expiry) for command feedback. */
    public static MutableComponent tierName(int tier)
    {
        if (tier == INDEX_EXPIRY)
            return Component.translatable(TIER_NAME_KEY_EXPIRY);

        return Component.translatable(isValidTier(tier) ? TIER_NAME_KEYS[tier] : "commands.sanity.hint.tier.unknown");
    }

    /** Which tier the given madness falls into; {@code -1} = none yet (sanity above 50%). */
    public static int tierForMadness(float madness)
    {
        for (int tier = TIER_COUNT - 1; tier >= 0; tier--)
        {
            if (madness >= TIER_THRESHOLDS[tier])
                return tier;
        }
        return -1;
    }

    /** Number of default lines for a tier. */
    public static int defaultCount(int tier)
    {
        buildDefaults();
        return isValidTier(tier) ? s_defaults[tier].length : 0;
    }

    /** Custom lines for a tier as a read-only view. */
    public static List<String> customHints(int tier)
    {
        return isValidTier(tier) ? Collections.unmodifiableList(s_custom.get(tier)) : Collections.emptyList();
    }

    public static int customCount(int tier)
    {
        return isValidTier(tier) ? s_custom.get(tier).size() : 0;
    }

    /**
     * The candidate lines a tier actually uses: built-in lines plus custom ones, in that order.
     *
     * <p>Both pools take part in the random draw, so adding custom lines never hides the defaults.
     * An earlier version replaced the default pool whenever custom lines existed, which silently
     * dropped the built-in ones. Returns {@code String} rather than {@code Component} so commands
     * can echo the text as-is.
     */
    public static List<String> effectiveHints(int tier)
    {
        List<Hint> all = effectiveHintEntries(tier);
        List<String> out = new ArrayList<>(all.size());
        for (Hint h : all)
            out.add(h.text());
        return out;
    }

    /**
     * A candidate line: its text and whether it comes from the custom pool.
     *
     * <p>{@code /sanity hint list} uses this flag for its built-in / custom labels, which cannot be
     * derived from the position once both pools are merged.
     */
    public record Hint(String text, boolean custom)
    {
        /**
         * Whether this entry carries drawable text.
         *
         * <p>A lang value may resolve to blank (removed or emptied by the owner), so callers that must not
         * draw a blank line filter on this instead of on the pool size.
         */
        public boolean hasText()
        {
            return text != null && !text.isBlank();
        }
    }

    /** Full candidate list for a tier: built-in lines first, then custom ones. */
    public static List<Hint> effectiveHintEntries(int tier)
    {
        if (!isValidTier(tier))
            return Collections.emptyList();

        buildDefaults();

        List<Hint> out = new ArrayList<>(s_defaults[tier].length + s_custom.get(tier).size());

        for (MutableComponent c : s_defaults[tier])
            out.add(new Hint(c.getString(), false));

        for (String s : s_custom.get(tier))
            out.add(new Hint(s, true));

        return out;
    }

    /** Total candidate lines for a tier (built-in + custom). */
    public static int totalCount(int tier)
    {
        return isValidTier(tier) ? defaultCount(tier) + customCount(tier) : 0;
    }

    /** Picks a random line of a tier for rendering; {@code null} when the tier has no candidate. */
    public static MutableComponent pickHintComponent(int tier)
    {
        Pick pick = pickHint(tier);
        return pick == null ? null : pick.text();
    }

    /**
     * Result of one pick.
     *
     * @param text the line to display
     * @param id   index within the <b>default</b> pool, or {@code -1} for a custom line.
     *             {@link GuiHandler} uses it to reproduce the shake tied to specific default lines.
     */
    public record Pick(MutableComponent text, int id) {}

    /**
     * Picks a random line for a tier and reports its index in the built-in pool.
     *
     * <p>Built-in and custom lines are drawn from the same roll. A built-in pick returns its index,
     * which reproduces the shake tied to specific default lines; a custom pick returns {@code -1}.
     *
     * <p>The returned {@link MutableComponent} is always already-resolved literal text
     * ({@code Component.literal(...)}), because the centre-screen lines are drawn with
     * {@code font.drawInBatch(Component, ...)}, which does not resolve translations - a translatable
     * component would put its raw key (or an unresolved player-name placeholder) on screen.
     */
    public static Pick pickHint(int tier)
    {
        if (!isValidTier(tier))
            return null;

        buildDefaults();

        MutableComponent[] pool = s_defaults[tier];
        List<String> custom = s_custom.get(tier);
        int total = pool.length + custom.size();

        if (total == 0)
            return null;

        int roll = RAND.nextInt(total);

        if (roll < pool.length)
        {
            // Built-in line: resolve to a string and wrap it back into a literal, so what is drawn is
            // guaranteed to be the resolved text.
            return new Pick(Component.literal(resolveHintText(pool[roll])), roll);
        }

        return new Pick(Component.literal(custom.get(roll - pool.length)), -1);
    }

    /**
     * Resolves a built-in line into the string that will actually be drawn.
     *
     * <p>Some lang values contain a {@code %s} player-name placeholder, and calling only
     * {@code getString()} would leave the raw {@code %s} on screen, so the placeholder is replaced
     * explicitly. The replacement is a plain string operation rather than
     * {@code Component.translatable(key, name)}: the latter runs the whole translation through
     * {@code String.format}, where a bare {@code %} in a line would throw
     * {@code UnknownFormatConversionException} and make the line disappear entirely.
     */
    private static String resolveHintText(MutableComponent source)
    {
        try
        {
            String resolved = source.getString();

            // Only a leftover "%s" after resolution means the value is a placeholder template.
            // Deliberately avoid Component.translatable(key, name).getString() here: that runs the
            // whole translation through String.format, so a bare "%" in a line would throw
            // UnknownFormatConversionException and lose the entire line. A plain substring
            // replacement only matches "%s" and cannot fail that way.
            return resolved.indexOf("%s") >= 0 ? resolved.replace("%s", playerName()) : resolved;
        }
        catch (Throwable t)
        {
            // Never let a resolution failure break the hint: fall back to the raw text.
            try
            {
                return source.getString();
            }
            catch (Throwable ignored)
            {
                return "";
            }
        }
    }

    /** Player name, or an empty string when it is not available. */
    private static String playerName()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.getDisplayName().getString() : "";
    }

    /** Whether a tier has any line available (default or custom). */
    public static boolean hasAnyHint(int tier)
    {
        return isValidTier(tier) && (customCount(tier) > 0 || defaultCount(tier) > 0);
    }

    // ------------------------------------------------------------------ custom pool maintenance (commands)

    /** Upper bound on lines per tier, keeping the JSON small and preventing spam. */
    public static final int MAX_CUSTOM_PER_TIER = 64;

    /** Maximum length of a single line, in characters. */
    public static final int MAX_HINT_LENGTH = 120;

    /** Adds a line. Returns {@code null} on success, otherwise an error lang key. */
    public static String addHint(int tier, String text)
    {
        if (!isValidTier(tier))
            return "commands.sanity.hint.invalid_tier";

        if (text == null || text.isBlank())
            return "commands.sanity.hint.empty";

        if (text.length() > MAX_HINT_LENGTH)
            return "commands.sanity.hint.too_long";

        List<String> list = s_custom.get(tier);

        if (list.size() >= MAX_CUSTOM_PER_TIER)
            return "commands.sanity.hint.full";

        if (list.contains(text))
            return "commands.sanity.hint.duplicate";

        list.add(text);
        save();
        return null;
    }

    /**
     * Removes one line.
     *
     * @param index display index, 1-based; 1..defaultCount are the default lines and the custom
     *              lines follow
     * @return the removed text; {@code null} = invalid index or a default line (defaults cannot be
     *         removed)
     */
    public static String removeHint(int tier, int index)
    {
        if (!isValidTier(tier))
            return null;

        int customIndex = index - 1 - defaultCount(tier);
        List<String> list = s_custom.get(tier);

        if (customIndex < 0 || customIndex >= list.size())
            return null;

        String removed = list.remove(customIndex);
        save();
        return removed;
    }

    /** Clears the custom lines of a tier (defaults are untouched); returns how many were removed. */
    public static int clearHints(int tier)
    {
        if (!isValidTier(tier))
            return 0;

        List<String> list = s_custom.get(tier);
        int n = list.size();
        list.clear();
        save();
        return n;
    }

    // ------------------------------------------------------------------ immediate show

    /**
     * {@code /sanity hint show} - displays one line of the given tier in the centre of the screen.
     *
     * <p>The hint pool is client-local (each player has their own), so resolving and rendering it
     * here avoids defining another packet and its dispatch logic. The command is only used in
     * singleplayer or on a local integrated server, so the executor is always that player.
     *
     * @return whether it was displayed (false when the tier has no candidate line)
     */
    public static boolean showNow(int tier)
    {
        Pick picked = pickHint(tier);
        if (picked == null)
            return false;

        s_immediateText = picked.text();
        s_immediateTimer = SHOW_TICKS;
        return true;
    }

    /** Whether an immediate show is currently on screen. */
    public static boolean isImmediateActive()
    {
        return s_immediateText != null && s_immediateTimer > 0f;
    }

    public static MutableComponent immediateText()
    {
        return s_immediateText;
    }

    public static float immediateTimer()
    {
        return s_immediateTimer;
    }

    public static int immediateShakeX()
    {
        return s_immediateShakeX;
    }

    public static int immediateShakeY()
    {
        return s_immediateShakeY;
    }

    /** Called once per tick, driven by {@link GuiHandler#tick}. */
    public static void tick(float dt, int shakeAmplitude)
    {
        if (s_immediateTimer > 0f)
        {
            s_immediateShakeX = RAND.nextInt(shakeAmplitude * 2 + 1) - shakeAmplitude;
            s_immediateShakeY = RAND.nextInt(shakeAmplitude * 2 + 1) - shakeAmplitude;
            s_immediateTimer -= dt;
        }

        if (s_immediateTimer <= 0f)
            s_immediateText = null;
    }

    /** Clears the temporary display when leaving a world; the custom pool is untouched. */
    public static void clearImmediate()
    {
        s_immediateText = null;
        s_immediateTimer = 0f;
    }

    // ------------------------------------------------------------------ local storage

    private static Path storePath()
    {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("sanitypd_mental_hints.json");
    }

    /**
     * JSON field name of a pool: {@code tier0} / {@code tier1} / {@code tier2} for the madness tiers and
     * {@code expiry} for the separate expiry warning pool.
     *
     * <p>The expiry pool gets its own field rather than {@code tier3} so the saved file says what it is and
     * an older client reading the file simply ignores the unknown key.
     */
    private static String storageKey(int tier)
    {
        return tier == INDEX_EXPIRY ? "expiry" : "tier" + tier;
    }

    /** Reads the local file. Exceptions must never escape: one during client init would prevent the game from starting. */
    public static void load()
    {
        s_loaded = true;

        for (List<String> list : s_custom)
            list.clear();

        Path path = storePath();

        if (!Files.exists(path))
            return;

        try
        {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (int tier = 0; tier <= INDEX_EXPIRY; tier++)
            {
                String field = storageKey(tier);
                JsonArray arr = root.has(field) ? root.getAsJsonArray(field) : null;
                if (arr == null)
                    continue;

                List<String> list = s_custom.get(tier);

                for (int i = 0; i < arr.size() && list.size() < MAX_CUSTOM_PER_TIER; i++)
                {
                    String text = arr.get(i).getAsString();
                    if (text != null && !text.isBlank() && text.length() <= MAX_HINT_LENGTH && !list.contains(text))
                        list.add(text);
                }
            }

            SanityMod.LOGGER.info("[MENTAL-HINTS] loaded local custom hints: mild {} / severe {} / deep {} / expiry {}",
                    s_custom.get(0).size(), s_custom.get(1).size(), s_custom.get(2).size(),
                    s_custom.get(INDEX_EXPIRY).size());
        }
        catch (Exception e)
        {
            SanityMod.LOGGER.warn("[MENTAL-HINTS] failed to read custom hint file, ignoring it (using the default pool): {}", e.toString());
        }
    }

    /** Writes the local file, also swallowing exceptions: a failed write only loses this change. */
    public static void save()
    {
        if (!s_loaded)
            return;

        JsonObject root = new JsonObject();

        for (int tier = 0; tier <= INDEX_EXPIRY; tier++)
        {
            JsonArray arr = new JsonArray();
            for (String text : s_custom.get(tier))
                arr.add(text);
            root.add(storageKey(tier), arr);
        }

        Path path = storePath();

        try
        {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            SanityMod.LOGGER.warn("[MENTAL-HINTS] failed to save custom hints: {}", e.toString());
        }
    }
}
