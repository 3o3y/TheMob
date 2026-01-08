package org.plugin.theMob.progression;

import java.util.HashMap;
import java.util.Map;

public final class BossDifficultyRegistry {

    private final Map<String, BossDifficultyProfile> profiles = new HashMap<>();

    public void register(BossDifficultyProfile profile) {
        if (profile == null || profile.id() == null) return;
        profiles.put(profile.id(), profile);
    }

    public BossDifficultyProfile get(String id) {
        if (id == null) return null;
        return profiles.get(id);
    }

    public boolean has(String id) {
        return id != null && profiles.containsKey(id);
    }

    public void clear() {
        profiles.clear();
    }
}
