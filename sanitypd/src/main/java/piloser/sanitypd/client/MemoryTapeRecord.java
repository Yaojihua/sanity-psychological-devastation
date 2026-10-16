package piloser.sanitypd.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import piloser.sanitypd.SanityMod;

/**
 * Remembers which memory fragments this player has already watched through, <b>per save</b>.
 *
 * <h2>What it is for</h2>
 * The first playback of a fragment cannot be skipped - the rule - and every playback after that
 * can be left by pressing any key. It also answers the story-order question: a fragment may only be watched
 * once the one before it has been watched through. Something therefore has to survive between sessions,
 * <b>per fragment</b>: one marker file per item id, so watching the Nether fragment does not unlock skipping
 * the first one.
 *
 * <h2>Where they are kept, and why the scope matters</h2>
 * In the <b>save folder</b>, like the third voice's own unlock marker: the story belongs to a world, and a
 * marker that followed the client instead made a new world start with every fragment already "watched" -
 * which skipped both the order gate and the unskippable first viewing (found on a fresh save).
 * Keeping it in the save also means it travels with a copied save and never leaks into another one.
 *
 * <p>A save folder only exists in single player. On a remote or dedicated server there is none, so the files
 * fall back to the mod's config folder: there the markers are per client, which is the best that can be done
 * without server-side storage - and the same limitation the third voice has.
 *
 * <p>A file's presence is the flag. It is written once the tape has played all the way to the white-out,
 * so quitting halfway still counts as "not yet watched".
 *
 * <p>Failing to read or write only ever costs the player a skippable or unskippable viewing, so every path
 * here swallows its errors and logs.
 */
@OnlyIn(Dist.CLIENT)
public final class MemoryTapeRecord
{
    /** Marker file name; {@code %s} is the fragment's item id. */
    public static final String FILE_NAME = "sanitypd_memory_tape_%s.json";

    /**
     * Cached per (folder + fragment), so a marker is read from disk once per world - and switching worlds
     * re-reads instead of answering with the other world's markers.
     */
    private static final Map<String, Boolean> PLAYED = new HashMap<>();

    private MemoryTapeRecord() {}

    /** Whether this fragment has been watched through at least once in this save. */
    public static boolean hasPlayed(String key)
    {
        Path dir = folder();
        String cacheKey = dir.toAbsolutePath() + "|" + key;

        return PLAYED.computeIfAbsent(cacheKey, k -> Files.exists(dir.resolve(String.format(FILE_NAME, key))));
    }

    /**
     * Records a completed viewing of one fragment. Called when the tape reaches full white; that fragment
     * may then be skipped on later viewings, and it unlocks the next one in story order.
     */
    public static void markPlayed(String key)
    {
        Path dir = folder();
        String cacheKey = dir.toAbsolutePath() + "|" + key;

        if (Boolean.TRUE.equals(PLAYED.get(cacheKey)))
            return;

        PLAYED.put(cacheKey, Boolean.TRUE);

        try
        {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(String.format(FILE_NAME, key)), "{\"played\":true}",
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            // The fragment simply stays unskippable next time; nothing about the story breaks.
            SanityMod.LOGGER.warn("[MEMORY] could not write the watch marker of {}: {}", key, e.toString());
        }
    }

    /**
     * Where this save's markers live, for the probe and the logs: {@code save} for a local world,
     * {@code config} when there is no save folder to write to.
     */
    public static String scope()
    {
        return saveFolder() != null ? "save" : "config";
    }

    private static Path folder()
    {
        Path save = saveFolder();

        return save != null ? save : FMLPaths.CONFIGDIR.get();
    }

    /**
     * The folder this save's markers live in: the world's own folder, or the config fallback where there is
     * none.
     *
     * <p>Public because another per-save marker (whether the void's limit-lift has been announced, see
     * {@link VoidUnbound}) is stored the same way, and "which save does this belong to" must not grow a second
     * definition - a marker written to the wrong scope is exactly the bug the scope comments above describe.
     */
    public static Path markerFolder()
    {
        return folder();
    }

    /**
     * Bitmask of the story fragments watched through in this save: bit {@code i} answers for
     * {@code ItemRegistry.MEMORY_FRAGMENT_ORDER.get(i)}.
     *
     * <p>Sent to the server whenever it changes, because the hidden fragment's allowance is decided there and
     * these markers are client-side files it cannot read.
     */
    public static int watchedMask()
    {
        int mask = 0;

        for (int i = 0; i < piloser.sanitypd.item.ItemRegistry.MEMORY_FRAGMENT_ORDER.size(); i++)
        {
            if (piloser.sanitypd.item.ItemRegistry.MEMORY_FRAGMENT_ORDER.get(i).get() instanceof
                    piloser.sanitypd.item.LostMemoryFragmentItem fragment && hasPlayed(fragment.scriptKey()))
                mask |= 1 << i;
        }

        return mask;
    }

    /**
     * Bitmask of the <b>hidden</b> fragments watched through in this save: bit {@code i} answers for
     * {@code ItemRegistry.HIDDEN_FRAGMENT_ORDER.get(i)}.
     *
     * <p>A second mask, reported in the same packet as {@link #watchedMask()}, because the hidden fragments are
     * not steps of the story order and one of them is another trigger's prerequisite: the vessel fragment may
     * only be handed over once the inner void's fragment has been watched through, and that marker is a
     * client-side file the server cannot read.
     */
    public static int watchedHiddenMask()
    {
        int mask = 0;

        for (int i = 0; i < piloser.sanitypd.item.ItemRegistry.HIDDEN_FRAGMENT_ORDER.size(); i++)
        {
            if (piloser.sanitypd.item.ItemRegistry.HIDDEN_FRAGMENT_ORDER.get(i).get() instanceof
                    piloser.sanitypd.item.LostMemoryFragmentItem fragment && hasPlayed(fragment.scriptKey()))
                mask |= 1 << i;
        }

        return mask;
    }

    /** The local world's folder, or null on a remote or dedicated server. */
    private static Path saveFolder()
    {
        try
        {
            net.minecraft.client.server.IntegratedServer server =
                    net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();

            return server == null ? null : server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        }
        catch (Throwable t)
        {
            // A half-started server must not break the tape: no folder means the config fallback.
            return null;
        }
    }
}
