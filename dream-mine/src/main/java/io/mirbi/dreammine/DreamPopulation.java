package io.mirbi.dreammine;

/** Pure-Java rules for maintaining a bounded population across generations.
 *  Keeping these rules independent of Minecraft lets us test the important
 *  invariants before touching a live save.
 */
public final class DreamPopulation {
    public static final int AUTO_LIMIT = 1; // manually spawned Dreams are not capped
    public static final long SCAN_INTERVAL_TICKS = 100; // 5s at normal TPS
    public static final long REPLACEMENT_DELAY_TICKS = 600; // 30s after death
    public static final long RETRY_DELAY_TICKS = 200; // 10s after failure / birth

    private DreamPopulation() {}

    public static boolean shouldAttempt(long now,long nextAttempt,boolean enabled,
                                        boolean automaticExists,int eligiblePlayers) {
        return enabled && now >= nextAttempt && eligiblePlayers > 0 && !automaticExists;
    }

    /** Handles overflow safely if the world has been running for a very long time. */
    public static long delayUntil(long now, long delay) {
        if (delay < 0) throw new IllegalArgumentException("delay cannot be negative");
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }
}