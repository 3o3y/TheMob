package org.plugin.theMob.mob.ai;

import org.bukkit.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobAIService {

    private final Map<UUID, MobAIController> controllers = new ConcurrentHashMap<>();
    private final SmartRepathService repath = new SmartRepathService();
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

        controllers.entrySet().removeIf(entry -> {
            MobAIController ai = entry.getValue();
            Mob mob = ai.mob();

            if (!mob.isValid() || mob.isDead()) return true;

            if (tick % ai.tickRate() == 0) {
                ai.tick(tick);
            }

            repath.tick(mob);
            return false;
        });
    }

    public void clearAll() {
        controllers.clear();
    }
}
