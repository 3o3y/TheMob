// src/main/java/org/plugin/theMob/core/context/PlayerBarCoordinator.java
package org.plugin.theMob.core.context;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerBarCoordinator {

    public static final class Ctx {
        private BossBar bossBar;
        private BossBar hudBar;

        public BossBar bossBar() { return bossBar; }
        public BossBar hudBar() { return hudBar; }

        public void setBossBar(BossBar b) { this.bossBar = b; }
        public void setHudBar(BossBar b) { this.hudBar = b; }

        public void hideBoss() {
            if (bossBar != null) bossBar.removeAll();
        }

        public void hideHud() {
            if (hudBar != null) hudBar.removeAll();
        }
    }

    private final Map<UUID, Ctx> map = new HashMap<>();

    public Ctx of(Player p) {
        return map.computeIfAbsent(p.getUniqueId(), k -> new Ctx());
    }

    public void remove(Player p) {
        Ctx ctx = map.remove(p.getUniqueId());
        if (ctx != null) {
            ctx.hideBoss();
            ctx.hideHud();
        }
    }

    public void clearAll() {
        for (Ctx ctx : map.values()) {
            ctx.hideBoss();
            ctx.hideHud();
        }
        map.clear();
    }
}
