package org.plugin.theMob.hud;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.hud.compass.CompassRenderer;
import org.plugin.theMob.mob.MobManager;

import java.util.*;

public final class NaviHudService {

    private final TheMob plugin;
    private final CompassRenderer compass;
    private final MobRadarResolver radar;

    private final boolean enabled;
    private final boolean radarEnabled;
    private final boolean hideWhenIdle;
    private final boolean hideCreative;
    private final boolean hideSpectator;

    private final double maxVisibleDistance;
    private final int updateInterval;

    private final Map<String, BarColor> colors;
    private final Map<UUID, BossBar> hudBars = new HashMap<>();

    private BukkitRunnable task;

    public NaviHudService(
            TheMob plugin,
            MobManager mobs
    ) {
        this.plugin = plugin;
        this.compass = new CompassRenderer();

        var cfg = plugin.getConfig();

        enabled = cfg.getBoolean("plugin.navigation-hud.enabled", true);
        radarEnabled = cfg.getBoolean("plugin.mob-radar.enabled", true);

        hideWhenIdle = cfg.getBoolean("plugin.navigation-hud.hide-when-idle", false);
        hideCreative = cfg.getBoolean("plugin.navigation-hud.hide-in-creative", false);
        hideSpectator = cfg.getBoolean("plugin.navigation-hud.hide-in-spectator", true);

        maxVisibleDistance = cfg.getDouble(
                "plugin.navigation-hud.max-visible-distance",
                32.0
        );

        updateInterval = cfg.getInt(
                "plugin.navigation-hud.update-interval-ticks",
                5
        );

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
        if (!enabled || task != null) return;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 0L, updateInterval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;

        for (BossBar bar : hudBars.values()) {
            bar.removeAll();
        }
        hudBars.clear();
    }

    // -------------------------------------------------------
    // Main Tick
    // -------------------------------------------------------

    private void tick() {

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (!PlayerHudState.isEnabled(p.getUniqueId())) {
                removeHud(p);
                continue;
            }

            if (hideCreative && p.getGameMode() == GameMode.CREATIVE) {
                removeHud(p);
                continue;
            }

            if (hideSpectator && p.getGameMode() == GameMode.SPECTATOR) {
                removeHud(p);
                continue;
            }

            BossBar bar = hudBars.computeIfAbsent(
                    p.getUniqueId(),
                    id -> createHudBar(p)
            );

            MobRadarResolver.RadarTarget target =
                    radarEnabled ? radar.find(p) : null;

            bar.setTitle(compass.render(p.getYaw()));
            bar.setVisible(true);

            if (!radarEnabled || target == null) {
                if (hideWhenIdle) {
                    removeHud(p);
                } else {
                    bar.setProgress(0.0001);
                    bar.setColor(colors.get("EMPTY"));
                }
                continue;
            }

            if (target.distance() > maxVisibleDistance) {
                removeHud(p);
                continue;
            }

            double progress = Math.max(
                    0.0001,
                    1.0 - (target.distance() / radar.getRadius())
            );

            bar.setProgress(progress);
            bar.setColor(colors.get(target.type().name()));
        }
    }

    // -------------------------------------------------------
    // HUD ONLY (❗ DOES NOT TOUCH BOSS BARS)
    // -------------------------------------------------------

    private BossBar createHudBar(Player p) {
        BossBar bar = Bukkit.createBossBar(
                "",
                BarColor.WHITE,
                BarStyle.SOLID
        );
        bar.setProgress(0.0001);
        bar.addPlayer(p);
        return bar;
    }

    private void removeHud(Player p) {
        BossBar bar = hudBars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void onJoin(Player p) {
        if (!PlayerHudState.isEnabled(p.getUniqueId())) return;
        hudBars.computeIfAbsent(p.getUniqueId(), id -> createHudBar(p));
    }

    public void onQuit(Player p) {
        PlayerHudState.clear(p.getUniqueId());
        removeHud(p);
    }
}
