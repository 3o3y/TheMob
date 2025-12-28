package org.plugin.theMob.boss.behavior.api;

import org.plugin.theMob.boss.behavior.BossBehavior;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BehaviorRegistry {

    private static final Map<String, BossBehavior> MODES = new ConcurrentHashMap<>();

    private BehaviorRegistry() {}

    public static void register(BossBehavior behavior) {
        MODES.put(behavior.id(), behavior);
    }

    public static BossBehavior get(String id) {
        return MODES.get(id);
    }
}
