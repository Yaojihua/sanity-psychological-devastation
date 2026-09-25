package piloser.sanityprobe;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Heartbeat counters for the probe: incremented by mixins, read by the self-test.
 *
 * <p>Why this exists: a failed mixin injection is silent by default (the injections use
 * {@code require = 0} so a mismatch can never crash the game). A silent failure would silently
 * invalidate every measurement taken afterwards, so the counter turns "did the injection actually
 * run?" into an explicit PASS/FAIL log line.
 */
public final class ProbeStats
{
    /** Number of calls to {@code MinecraftServer#sendSystemMessage}, incremented by {@code MixinMinecraftServer}. */
    public static final AtomicInteger SEND_SYSTEM_MESSAGE_HITS = new AtomicInteger();

    private ProbeStats() {}
}
