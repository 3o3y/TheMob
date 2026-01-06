package org.plugin.theMob.mob.ai;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MobAIService {

    private final Set<MobAIController> controllers = ConcurrentHashMap.newKeySet();
    private long tick;

    public void register(MobAIController ai) {
        controllers.add(ai);
    }

    public void unregister(MobAIController ai) {
        controllers.remove(ai);
    }

    public void tick() {
        tick++;
        for (MobAIController ai : controllers) {
            if (tick % ai.tickRate() == 0) {
                ai.tick(tick);
            }
        }
    }
}
