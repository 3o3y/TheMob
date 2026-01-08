package org.plugin.theMob.progression;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProgressionManager {

    private final Map<UUID, PlayerProgressionState> players = new ConcurrentHashMap<>();

    public PlayerProgressionState get(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerProgressionState::new);
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }
}
