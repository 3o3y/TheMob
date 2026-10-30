package org.plugin.theMob.hud;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.hud.compass.CompassRenderer;
import org.plugin.theMob.hud.compass.CompassService;
import org.plugin.theMob.mob.MobManager;

public final class NaviHudService {
  private final TheMob plugin;
  private final CompassRenderer compass;
  private CompassService resourceCompass;
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
  public static final NamespacedKey HUD_KEY = new NamespacedKey("themob", "navihud");
  private BukkitRunnable task;
  private final boolean hideInCombat;
  private final long combatHideTicks;
  private final java.util.Map<java.util.UUID, Long> lastCombat = new java.util.HashMap<>();

  public void markCombat(Player player) {
    lastCombat.put(player.getUniqueId(), plugin.automation().tps().currentTick());
  }

  public NaviHudService(TheMob plugin, MobManager mobs) {
    this.plugin = plugin;
    this.compass = new CompassRenderer();
    FileConfiguration cfg = plugin.getConfig();
    // In-memory migration only; original admin configuration is not rewritten.
    for (String key : new java.util.ArrayList<>(cfg.getKeys(true))) {
      if (key.startsWith("plugin.navigation-hud.") || key.startsWith("plugin.mob-radar.")) {
        String canonical = key.substring(7);
        if (!cfg.isSet(canonical)) cfg.set(canonical, cfg.get(key));
      }
    }

    this.enabled = cfg.getBoolean("navigation-hud.enabled", true);
    this.hideInCombat = cfg.getBoolean("navigation-hud.hide-in-combat", false);
    this.combatHideTicks =
        org.plugin.theMob.compat.Numbers.clamp(
                cfg.getLong("navigation-hud.combat-hide-seconds", 10), 1, 300)
            * 20;
    this.radarEnabled = cfg.getBoolean("mob-radar.enabled", true);
    this.hideWhenIdle = cfg.getBoolean("navigation-hud.hide-when-idle", false);
    this.hideCreative = cfg.getBoolean("navigation-hud.hide-in-creative", false);
    this.hideSpectator = cfg.getBoolean("navigation-hud.hide-in-spectator", true);
    this.maxVisibleDistance = cfg.getDouble("navigation-hud.max-visible-distance", 32.0);
    this.updateInterval =
        org.plugin.theMob.compat.Numbers.clamp(
            cfg.getInt("navigation-hud.update-interval-ticks", 5), 1, 200);
    this.radar =
        new MobRadarResolver(
            mobs,
            this.radarEnabled,
            org.plugin.theMob.compat.Numbers.clamp(
                cfg.getDouble("mob-radar.scan-radius", 20.0), 1, 128),
            cfg.getBoolean("mob-radar.ignore.bats", true),
            cfg.getBoolean("mob-radar.ignore.villagers", true),
            cfg.getBoolean("mob-radar.ignore.named-mobs", false));
    this.radar.configure(cfg.getConfigurationSection("mob-radar"));
    this.colors =
        Map.of(
            "PLUGIN",
            safeColor(cfg.getString("mob-radar.colors.plugin-mob", "PURPLE")),
            "AGGRESSIVE",
            safeColor(cfg.getString("mob-radar.colors.aggressive-mob", "RED")),
            "NEUTRAL",
            safeColor(cfg.getString("mob-radar.colors.neutral-mob", "YELLOW")),
            "PASSIVE",
            safeColor(cfg.getString("mob-radar.colors.passive-mob", "GREEN")),
            "EMPTY",
            safeColor(cfg.getString("mob-radar.colors.empty", "WHITE")));
    try {
      this.resourceCompass = new CompassService(plugin);
    } catch (java.io.IOException | RuntimeException | LinkageError failure) {
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.WARNING,
              "CoinOverlay-Kompass konnte nicht aktiviert werden; Textkompass bleibt aktiv.",
              failure);
    }
  }

  private static BarColor safeColor(String name) {
    try {
      return BarColor.valueOf(name.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      return BarColor.WHITE;
    }
  }

  public void start() {
    if (this.enabled && this.task == null) {
      this.task =
          new BukkitRunnable() {
            public void run() {
              NaviHudService.this.tick();
            }
          };
      this.task.runTaskTimer(this.plugin, 0L, this.updateInterval);
    }
  }

  public void shutdown() {
    if (resourceCompass != null) resourceCompass.close();
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }

    Iterator<KeyedBossBar> it = Bukkit.getBossBars();

    while (it.hasNext()) {
      KeyedBossBar bar = it.next();
      if (HUD_KEY.equals(bar.getKey())) {
        bar.removeAll();
      }
    }

    for (BossBar bar : this.hudBars.values()) bar.removeAll();
    this.hudBars.clear();
    this.lastCombat.clear();
  }

  private void tick() {
    radar.beginFrame();
    try {
      for (Player p : Bukkit.getOnlinePlayers()) {
        boolean blocked =
            hideInCombat
                && plugin.automation().tps().currentTick()
                        - lastCombat.getOrDefault(p.getUniqueId(), -100000L)
                    < combatHideTicks;
        blocked |=
            !PlayerHudState.isEnabled(p.getUniqueId())
                || hideCreative && p.getGameMode() == GameMode.CREATIVE
                || hideSpectator && p.getGameMode() == GameMode.SPECTATOR;
        if (blocked) {
          if (resourceCompass != null) resourceCompass.hudVisible(p.getUniqueId(), false);
          removeHud(p);
          continue;
        }
        MobRadarResolver.RadarTarget target = this.radarEnabled ? this.radar.find(p) : null;
        blocked |=
            !PlayerHudState.isEnabled(p.getUniqueId())
                || hideCreative && p.getGameMode() == GameMode.CREATIVE
                || hideSpectator && p.getGameMode() == GameMode.SPECTATOR
                || hideWhenIdle && target == null
                || target != null && target.distance() > maxVisibleDistance;
        if (resourceCompass != null) resourceCompass.hudVisible(p.getUniqueId(), !blocked);
        boolean graphical =
            resourceCompass != null && resourceCompass.resourceCompass(p.getUniqueId());
        if (blocked
            || graphical && target == null
            || !graphical
                && resourceCompass != null
                && !resourceCompass.personalVisible(p.getUniqueId())) {
          this.removeHud(p);
        } else {
          BossBar bar = this.hudBars.computeIfAbsent(p.getUniqueId(), id -> this.createHudBar(p));
          if (graphical) {
            String name = target.entity().getCustomName();
            if (name == null) name = target.entity().getType().name().replace('_', ' ');
            bar.setTitle(name + " §7• " + Math.round(target.distance()) + " m");
          } else bar.setTitle(this.compass.render(p.getLocation().getYaw()));
          bar.setVisible(true);
          if (this.radarEnabled && target != null) {
            if (target.distance() > this.maxVisibleDistance) {
              this.removeHud(p);
            } else {
              double progress = Math.max(1.0E-4, 1.0 - target.distance() / this.radar.getRadius());
              bar.setProgress(progress);
              bar.setColor(this.colors.get(target.type().name()));
            }
          } else if (this.hideWhenIdle) {
            this.removeHud(p);
          } else {
            bar.setProgress(1.0E-4);
            bar.setColor(this.colors.get("EMPTY"));
          }
        }
      }
    } finally {
      radar.endFrame();
    }
  }

  private BossBar createHudBar(Player p) {
    BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID, new BarFlag[0]);
    bar.setProgress(1.0E-4);
    bar.addPlayer(p);
    return bar;
  }

  private void removeHud(Player p) {
    BossBar bar = this.hudBars.remove(p.getUniqueId());
    if (bar != null) {
      bar.removeAll();
    }
  }

  public void onJoin(Player p) {
    if ((resourceCompass == null || !resourceCompass.resourceCompass(p.getUniqueId()))
        && PlayerHudState.isEnabled(p.getUniqueId())) {
      this.hudBars.computeIfAbsent(p.getUniqueId(), id -> this.createHudBar(p));
    }
  }

  public void onQuit(Player p) {
    this.lastCombat.remove(p.getUniqueId());
    PlayerHudState.clear(p.getUniqueId());
    this.removeHud(p);
  }
}
