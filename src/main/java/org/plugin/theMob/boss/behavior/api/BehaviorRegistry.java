package org.plugin.theMob.boss.behavior.api;

import org.plugin.theMob.boss.behavior.BossBehavior;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BehaviorRegistry {

    // MAIN THREAD + SAFE FOR RELOAD
    private static final Map<String, BossBehavior> MODES = new ConcurrentHashMap<>();

    private BehaviorRegistry() {}

    public static void register(BossBehavior behavior) {
        if (behavior == null) return;
        String id = behavior.id();
        if (id == null || id.isBlank()) return;

        MODES.put(id.toLowerCase(), behavior);
    }

    public static BossBehavior get(String id) {
        if (id == null) return null;

        return MODES.get(id.toLowerCase());
    }

    public static boolean contains(String id) {
        if (id == null) return false;
        return MODES.containsKey(id.toLowerCase());
    }

    public static void clear() {
        MODES.clear();
    }
}
