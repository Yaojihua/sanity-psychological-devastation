package piloser.sanitypd.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.item.HiddenMemoryGrant;

/**
 * "You feel the void loosening some of the limits on your understanding."
 *
 * <h2>What it answers</h2>
 * Once this save has read the <b>eight fragments other than the vessel's</b> - the four story fragments and
 * the four hidden ones that come before the vessel (the hidden tape, obsession, sweetness, the inner void) -
 * the void stops hiding part of what the memory scripts garble. That is a fact about the watch markers, so
 * it is <b>derived</b> from them rather than stored: a second stored flag could disagree with the markers
 * after a save is copied, and the markers are what "read" means.
 *
 * <p>The vessel's own fragment is deliberately not part of the eight - the wording is "after reading
 * the eight fragments other than the vessel" - so this is not "everything is watched". A save that reaches
 * it has necessarily read the inner void's fragment too (that is the vessel's trigger prerequisite), which
 * is why the condition can be stated over the hidden mask alone once the story mask is complete.
 *
 * <h2>The one thing that is stored: that the line has been shown</h2>
 * The condition stays true forever once reached, so the line itself needs one memory of its own, or it would
 * return on every join. That marker is written the same way the watch markers are - per save, in the world's
 * own folder, falling back to the config folder where there is no save folder - and the scope comes from
 * {@link MemoryTapeRecord#markerFolder()} rather than a second definition of "which save does this belong to".
 *
 * <h2>Where it is checked</h2>
 * In two places, because one of them alone is not enough (the lesson of the hidden fragment's allowance):
 * the moment a tape reaches its end, which is the only moment the eighth fragment can complete, and the
 * first tick of every world, so a save that had already read the eight before this existed is still told the
 * first time it is entered after the update.
 *
 * <p>The join is checked on the client tick rather than only on {@code LoggingIn}: the announcement writes
 * its one-shot marker before it speaks, so a moment where the local player is not usable yet would spend the
 * marker and say nothing - a save that can never be told again. The tick can only run with a player present,
 * which is exactly the condition the line is drawn under.
 *
 * <p>Registered by hand from {@code SanityMod#clientSetup}, like {@link HintStateReporter} - and for the same
 * reason it carries no {@code @OnlyIn}: a class with event subscribers must stay nameable on both sides.
 */
public final class VoidUnbound
{
    /** "You feel the void loosening some of the limits on your understanding......" */
    public static final String NOTICE_KEY = "gui.sanitypd.memory.unbound";

    /** Marker file: this save has already been told. */
    public static final String FILE_NAME = "sanitypd_void_unbound.json";

    /**
     * Cached per folder, so the marker is read from disk once per world - and switching worlds re-reads
     * instead of answering with the other world's marker.
     */
    private static final Map<String, Boolean> ANNOUNCED = new HashMap<>();

    private VoidUnbound() {}

    /**
     * Whether this save has read the eight fragments other than the vessel's.
     *
     * <p>Read live from the watch markers (they are cached themselves), so the answer is never a snapshot
     * that a scene change could leave behind.
     */
    public static boolean unbound()
    {
        if ((MemoryTapeRecord.watchedMask() & HiddenMemoryGrant.ALL) != HiddenMemoryGrant.ALL)
            return false;

        int required = HiddenMemoryGrant.ALL_HIDDEN_EXCEPT_VESSEL;

        return required != 0 && (MemoryTapeRecord.watchedHiddenMask() & required) == required;
    }

    /**
     * Shows the line once per save, when the condition now holds and this save has never been told.
     *
     * <p>The marker is asked first: it is the cheap half (one cached lookup) and it is what keeps this call
     * safe to make every tick, which is how the first moment of a world is covered.
     */
    public static void announceIfDue()
    {
        if (announced())
            return;

        if (!unbound())
            return;

        markAnnounced();

        SanityMod.LOGGER.info("[MEMORY] the void has lifted its limits on this save's understanding; "
                + "the player is told once (marker: {})", FILE_NAME);

        MemoryTapeGate.notice(NOTICE_KEY);
    }

    /** The line in the action bar, the same shape and the same place as the tape's two refusals. */
    @SubscribeEvent
    public static void onLoggedIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        announceIfDue();
    }

    /**
     * The first tick of a world, which is the moment a save that was already past the condition is told.
     *
     * <p>A tick rather than {@code LoggingIn} alone: that event can arrive before the local player is usable,
     * and the line needs a player. With the marker checked first this costs one cached lookup per tick until
     * the save has been told once, and then it returns immediately for the rest of the session.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null)
            return;

        announceIfDue();
    }

    /** Whether this save has already been told. */
    private static boolean announced()
    {
        Path dir = MemoryTapeRecord.markerFolder();
        String cacheKey = dir.toAbsolutePath() + "|unbound";

        return ANNOUNCED.computeIfAbsent(cacheKey, k -> Files.exists(dir.resolve(FILE_NAME)));
    }

    /** Writes the one-shot marker; failing to write only ever costs a repeated line. */
    private static void markAnnounced()
    {
        Path dir = MemoryTapeRecord.markerFolder();
        String cacheKey = dir.toAbsolutePath() + "|unbound";

        if (Boolean.TRUE.equals(ANNOUNCED.get(cacheKey)))
            return;

        ANNOUNCED.put(cacheKey, Boolean.TRUE);

        try
        {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(FILE_NAME), "{\"announced\":true}",
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            SanityMod.LOGGER.warn("[MEMORY] could not write {}: {}", FILE_NAME, e.toString());
        }
    }
}
