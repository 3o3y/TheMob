package org.plugin.theMob.hud;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.hud.compass.CompassRenderer;
import org.plugin.theMob.mob.MobManager;

import java.util.*;

public final class NaviHudService {

    private final TheMob plugin;
    private final PlayerBarCoordinator coordinator;
    private final CompassRenderer compass;
    private final MobRadarResolver radar;

    private final boolean navEnabled;
    private final boolean radarEnabled;
    private final boolean hideWhenIdle;
    private final boolean hideCreative;
    private final boolean hideSpectator;

    private final Map<String, BarColor> colors;
    private final Map<UUID, BossBar> hudBars = new HashMap<>();

    private BukkitRunnable task;

    public NaviHudService(
            TheMob plugin,
            PlayerBarCoordinator coordinator,
            CompassRenderer compass,
            MobManager mobs
    ) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.compass = compass;

        var cfg = plugin.getConfig();

        navEnabled = cfg.getBoolean("plugin.navigation-hud.enabled", true);
        radarEnabled = cfg.getBoolean("plugin.mob-radar.enabled", true);

        hideWhenIdle = cfg.getBoolean("plugin.navigation-hud.hide-when-idle", false);
        hideCreative = cfg.getBoolean("plugin.navigation-hud.hide-in-creative", true);
        hideSpectator = cfg.getBoolean("plugin.navigation-hud.hide-in-spectator", true);

        radar = new MobRadarResolver(
                mobs,
                radarEnabled,
                cfg.getDouble("plugin.mob-radar.scan-radius", 20.0),
                cfg.getBoolean("plugin.mob-radar.ignore.bats", true),
                cfg.getBoolean("plugin.mob-radar.ignore.villagers", true),
                cfg.getBoolean("plugin.mob-radar.ignore.named-mobs", false)
        );

        colors = Map.of(
                "PLUGIN", BarColor.valueOf(cfg.getString("plugin.navigation-hud.colors.plugin-mob", "PURPLE")),
                "AGGRESSIVE", BarColor.valueOf(cfg.getString("plugin.navigation-hud.colors.aggressive-mob", "RED")),
                "NEUTRAL", BarColor.valueOf(cfg.getString("plugin.navigation-hud.colors.neutral-mob", "YELLOW")),
                "PASSIVE", BarColor.valueOf(cfg.getString("plugin.navigation-hud.colors.passive-mob", "GREEN")),
                "EMPTY", BarColor.valueOf(cfg.getString("plugin.navigation-hud.colors.empty", "WHITE"))
        );
    }

    // -------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------

    public void start() {
        if ((!navEnabled && !radarEnabled) || task != null) return;

        int interval = plugin.getConfig().getInt(
                "plugin.navigation-hud.update-interval-ticks", 5
        );

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 0L, interval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;

        hudBars.values().forEach(BossBar::removeAll);
        hudBars.clear();
    }

    // -------------------------------------------------------
    // Main Tick
    // -------------------------------------------------------

    private void tick() {

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (hideCreative && p.getGameMode() == GameMode.CREATIVE) continue;
            if (hideSpectator && p.getGameMode() == GameMode.SPECTATOR) continue;

            if (!navEnabled && !radarEnabled) {
                remove(p);
                continue;
            }

            BossBar bar = hudBars.computeIfAbsent(
                    p.getUniqueId(),
                    id -> createBar(p)
            );

            // --- Radar nur EINMAL ---
            MobRadarResolver.RadarTarget target =
                    radarEnabled ? radar.find(p) : null;

            // --- Visibility ---
            bar.setVisible(true);

            // --- TITLE (N-W-S-O) ---
            if (navEnabled) {
                bar.setTitle(compass.render(p.getYaw()));
            } else {
                bar.setTitle(" "); // wichtig: nicht ""
            }

            // --- RADAR AUS ---
            if (!radarEnabled) {
                bar.setProgress(0.0001);
                bar.setColor(colors.get("EMPTY"));
                continue;
            }

            // --- RADAR AN, aber idle ---
            if (hideWhenIdle && target == null) {
                bar.setProgress(0.0001);
                bar.setColor(colors.get("EMPTY"));
                continue;
            }

            if (target == null) {
                bar.setProgress(0.0001);
                bar.setColor(colors.get("EMPTY"));
                continue;
            }

            double progress = Math.max(0.0, Math.min(
                    1.0,
                    1.0 - (target.distance() / radar.getRadius())
            ));

            bar.setProgress(progress);
            bar.setColor(colors.get(target.type().name()));
        }
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private BossBar createBar(Player p) {
        BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
        bar.setProgress(0.0001);
        bar.addPlayer(p);
        coordinator.of(p).setHudBar(bar);
        return bar;
    }

    private void remove(Player p) {
        BossBar bar = hudBars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
        coordinator.remove(p);
    }

    public void onJoin(Player p) {
        if (!navEnabled && !radarEnabled) return;
        hudBars.computeIfAbsent(p.getUniqueId(), id -> createBar(p));
    }

    public void onQuit(Player p) {
        remove(p);
    }
}
