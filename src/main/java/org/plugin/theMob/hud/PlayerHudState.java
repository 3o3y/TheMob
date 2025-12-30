package org.plugin.theMob.hud;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerHudState {

    private static final Map<UUID, Boolean> HUD_ENABLED = new ConcurrentHashMap<>();

    private PlayerHudState() {}

    public static boolean isEnabled(UUID id) {
        return HUD_ENABLED.getOrDefault(id, true);
    }

    public static boolean toggle(UUID id) {
        boolean next = !isEnabled(id);
        HUD_ENABLED.put(id, next);
        return next;
    }

    public static void clear(UUID id) {
        HUD_ENABLED.remove(id);
    }
}
