package org.plugin.theMob.control;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether an arena is still "hot"
 * after boss death (cleanup, waves, grace time).
 */
public final class ArenaHeatService {

    private final Map<String, Long> hotUntil = new ConcurrentHashMap<>();

    /** Mark arena as hot for N seconds */
    public void markHot(String arenaId, long seconds) {
        hotUntil.put(arenaId, System.currentTimeMillis() + seconds * 1000);
    }

    /** True if arena is still hot */
    public boolean isHot(String arenaId) {
        Long until = hotUntil.get(arenaId);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            hotUntil.remove(arenaId);
            return false;
        }
        return true;
    }
}
