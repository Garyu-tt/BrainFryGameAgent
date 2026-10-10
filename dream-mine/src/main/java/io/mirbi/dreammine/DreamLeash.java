package io.mirbi.dreammine;

/** Pure deterministic world-space leash policy, safe for isolated tests. */
public final class DreamLeash {
    public static final double MAX_RADIUS = 100.0;
    public static final double START_RETURN_RADIUS = 72.0;
    public static final double FINISH_RETURN_RADIUS = 52.0;
    public static final double ROAM_TARGET_RADIUS = 68.0;

    public enum Decision { IDLE, ROAM, RETURN, RELOCATE }
    private DreamLeash() {}

    public static boolean withinLimit(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0
            && distanceSquared <= MAX_RADIUS * MAX_RADIUS;
    }

    public static boolean allowWander(double candidateDistanceSquared) {
        return Double.isFinite(candidateDistanceSquared) && candidateDistanceSquared >= 0
            && candidateDistanceSquared <= ROAM_TARGET_RADIUS * ROAM_TARGET_RADIUS;
    }

    public static Decision decide(double distanceSquared, boolean hasPlayer, boolean returning) {
        if (!hasPlayer) return Decision.IDLE;
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return Decision.IDLE;
        if (distanceSquared > MAX_RADIUS * MAX_RADIUS) return Decision.RELOCATE;
        if (returning && distanceSquared > FINISH_RETURN_RADIUS * FINISH_RETURN_RADIUS)
            return Decision.RETURN;
        if (distanceSquared > START_RETURN_RADIUS * START_RETURN_RADIUS)
            return Decision.RETURN;
        return Decision.ROAM;
    }
}