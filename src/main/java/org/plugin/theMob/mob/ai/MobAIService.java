package org.plugin.theMob.mob.ai;

import org.bukkit.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobAIService {

    private final Map<UUID, MobAIController> controllers = new ConcurrentHashMap<>();
    private long tick;

    public void register(Mob mob, MobAIProfile profile) {
        if (mob == null || profile == null) return;
        controllers.put(mob.getUniqueId(), new MobAIController(mob, profile));
    }

    public void unregister(Mob mob) {
        if (mob == null) return;
        controllers.remove(mob.getUniqueId());
    }

    public void tick() {
        tick++;

        for (MobAIController ai : controllers.values()) {
            if (tick % ai.tickRate() == 0) {
                ai.tick(tick);
            }
        }
    }

    public void clearAll() {
        controllers.clear();
    }
}
