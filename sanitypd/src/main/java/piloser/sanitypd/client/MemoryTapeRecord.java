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
 * Remembers which memory fragments this player has already watched through.
 *
 * <h2>What it is for</h2>
 * The first playback of a fragment cannot be skipped - the owner's rule - and every playback after that
 * can be left by pressing any key. Something therefore has to survive between sessions, <b>per fragment</b>:
 * one marker file per item id, so watching the Nether fragment does not unlock skipping the first one.
 *
 * <h2>Where they are kept, and why not in the save</h2>
 * Small files in the mod's config folder, one per client. The save folder would make it per-world, which
 * reads better for a story beat, but a save folder only exists in single player: on a server the file could
 * never be written, so a fragment would be unskippable forever for anyone playing on one. Per client is
 * the scope that behaves the same everywhere.
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
    /** Marker file name pattern inside the config folder; {@code %s} is the fragment's item id. */
    public static final String FILE_NAME = "sanitypd_memory_tape_%s.json";

    /** Cached per fragment, so a marker is read from disk once. */
    private static final Map<String, Boolean> PLAYED = new HashMap<>();

    private MemoryTapeRecord() {}

    /** Whether this fragment has been watched through at least once on this client. */
    public static boolean hasPlayed(String key)
    {
        return PLAYED.computeIfAbsent(key, k -> Files.exists(file(k)));
    }

    /**
     * Records a completed viewing of one fragment. Called when the tape reaches full white; that fragment
     * may then be skipped on later viewings.
     */
    public static void markPlayed(String key)
    {
        if (Boolean.TRUE.equals(PLAYED.get(key)))
            return;

        PLAYED.put(key, Boolean.TRUE);

        try
        {
            Path file = file(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\"played\":true}", java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            // The fragment simply stays unskippable next time; nothing about the story breaks.
            SanityMod.LOGGER.warn("[MEMORY] could not write the watch marker of {}: {}", key, e.toString());
        }
    }

    private static Path file(String key)
    {
        return FMLPaths.CONFIGDIR.get().resolve(String.format(FILE_NAME, key));
    }
}
