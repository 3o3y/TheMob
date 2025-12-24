// src/main/java/org/plugin/theMob/hud/NaviHudService.java
package org.plugin.theMob.hud;

import org.bukkit.Bukkit;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.hud.compass.CompassRenderer;
import org.plugin.theMob.mob.MobManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NaviHudService {

    private final TheMob plugin;
    private final PlayerBarCoordinator coordinator;
    private final CompassRenderer compass;
    private final MobRadarResolver radar;

    private final Map<UUID, BossBar> hudBars = new HashMap<>();
    private BukkitRunnable task;

    public NaviHudService(TheMob plugin, PlayerBarCoordinator coordinator, CompassRenderer compass, MobManager mobs) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.compass = compass;
        this.radar = new MobRadarResolver(mobs);
    }

    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        };
        task.runTaskTimer(plugin, 0L, 10L);
    }

    public void shutdown() {
        if (task != null) { task.cancel(); task = null; }
        for (BossBar b : hudBars.values()) b.removeAll();
        hudBars.clear();
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = hudBars.computeIfAbsent(p.getUniqueId(), id -> createBar(p));
            MobRadarResolver.RadarTarget target = radar.find(p);

            bar.setTitle(compass.render(p.getYaw()));

            if (target == null) {
                bar.setProgress(0.0);
                bar.setColor(BarColor.WHITE);
                continue;
            }

            double progress = 1.0 - (target.distance() / MobRadarResolver.RADIUS);
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            bar.setColor(switch (target.type()) {
                case PLUGIN -> BarColor.PURPLE;
                case AGGRESSIVE -> BarColor.RED;
                case NEUTRAL -> BarColor.YELLOW;
                case PASSIVE -> BarColor.GREEN;
            });
        }
    }

    private BossBar createBar(Player p) {
        BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
        bar.addPlayer(p);
        bar.setProgress(0.0);
        coordinator.of(p).setHudBar(bar);
        return bar;
    }
    // src/main/java/org/plugin/theMob/hud/NaviHudService.java

    public void onJoin(Player p) {
        if (p == null) return;
        BossBar bar = hudBars.computeIfAbsent(p.getUniqueId(), id -> createBar(p));
        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
        coordinator.of(p).setHudBar(bar);
    }

    public void onQuit(Player p) {
        if (p == null) return;
        BossBar bar = hudBars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        coordinator.remove(p);
    }

}
