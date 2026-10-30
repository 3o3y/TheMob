package org.plugin.theMob.spawn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;
import org.plugin.theMob.spawn.type.SpawnMode;
import org.plugin.theMob.spawn.type.SpawnType;

public final class SpawnController implements Listener {
  private final TheMob plugin;
  private final MobManager mobs;
  private final AutoSpawnManager auto;
  private final ConfigService configs;
  private final Map<String, SpawnPoint> registry = new ConcurrentHashMap<>();

  public SpawnController(TheMob plugin, MobManager mobs, AutoSpawnManager auto) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.auto = auto;
    this.configs = plugin.configs();
  }

  public void start() {
    this.auto.bindController(this);
    this.loadFromConfig();
    this.auto.start();
  }

  public void stop() {
    this.registry.values().forEach(sp -> this.auto.unregister(sp.spawnId()));
    this.registry.clear();
    this.auto.stop();
  }

  public boolean setAutoSpawnFixedPoint(
      String mobId, Location loc, int intervalSeconds, int maxSpawns) {
    return this.upsertFixedOrRadius(
        SpawnType.FIXED_POINT, mobId, loc, intervalSeconds, maxSpawns, 0, 0);
  }

  public boolean setRandomRadius(
      String mobId,
      Location loc,
      int intervalSeconds,
      int maxSpawns,
      int minRadius,
      int maxRadius) {
    return this.upsertFixedOrRadius(
        SpawnType.RANDOM_RADIUS, mobId, loc, intervalSeconds, maxSpawns, minRadius, maxRadius);
  }

  private boolean upsertFixedOrRadius(
      SpawnType type,
      String mobId,
      Location loc,
      int intervalSeconds,
      int maxSpawns,
      int minRadius,
      int maxRadius) {
    if (!this.mobs.mobExists(mobId)) {
      return false;
    } else if (loc != null && loc.getWorld() != null) {
      SpawnPoint sp =
          new SpawnPoint(
              type,
              mobId,
              loc.getWorld().getName(),
              loc.getBlockX(),
              loc.getBlockY(),
              loc.getBlockZ(),
              intervalSeconds,
              maxSpawns,
              true,
              2,
              minRadius,
              maxRadius,
              null,
              SpawnMode.ENDLESS,
              null,
              null,
              "",
              60,
              null,
              null,
              null);
      this.registry.put(sp.spawnId(), sp);
      this.auto.register(sp);
      this.saveToConfig();
      return true;
    } else {
      return false;
    }
  }

  public boolean setFollowPlayer(
      String playerName,
      String mobId,
      int intervalSeconds,
      int maxSpawns,
      SpawnMode mode,
      int minDistance,
      int maxDistance,
      String message) {
    if (!this.mobs.mobExists(mobId)) {
      return false;
    } else {
      SpawnPoint sp =
          new SpawnPoint(
              SpawnType.FOLLOW_PLAYER,
              mobId,
              null,
              0,
              0,
              0,
              intervalSeconds,
              maxSpawns,
              true,
              0,
              0,
              0,
              playerName,
              mode,
              minDistance,
              maxDistance,
              message,
              60,
              null,
              null,
              null);
      this.registry.put(sp.spawnId(), sp);
      this.auto.register(sp);
      if (mode == SpawnMode.ENDLESS) {
        this.saveToConfig();
      }

      return true;
    }
  }

  public boolean setRandomWorld(
      String worldName,
      String mobId,
      int intervalSeconds,
      int maxSpawns,
      SpawnMode mode,
      int messageTimerSeconds,
      String message) {
    if (!this.mobs.mobExists(mobId)) {
      return false;
    } else {
      World w = Bukkit.getWorld(worldName);
      if (w == null) {
        return false;
      } else {
        Location base = w.getSpawnLocation();
        SpawnPoint sp =
            new SpawnPoint(
                SpawnType.RANDOM_WORLD,
                mobId,
                worldName,
                base.getBlockX(),
                base.getBlockY(),
                base.getBlockZ(),
                intervalSeconds,
                maxSpawns,
                true,
                0,
                0,
                0,
                null,
                mode,
                null,
                null,
                message,
                messageTimerSeconds,
                null,
                null,
                null);
        this.registry.put(sp.spawnId(), sp);
        this.auto.register(sp);
        if (mode == SpawnMode.ENDLESS) {
          this.saveToConfig();
        }

        return true;
      }
    }
  }

  public boolean deleteFollowPlayer(String player, String mobId) {
    return this.deleteBySpawnId("follow@" + player + ":" + mobId.toLowerCase());
  }

  public boolean deleteRandomWorld(String world, String mobId) {
    return this.deleteBySpawnId("randomworld@" + world + ":" + mobId.toLowerCase());
  }

  public boolean deleteRandomRadiusAt(String mobId, Location loc) {
    if (loc != null && loc.getWorld() != null) {
      String sid =
          mobId.toLowerCase()
              + "@radius@"
              + loc.getWorld().getName()
              + ":"
              + loc.getBlockX()
              + ","
              + loc.getBlockY()
              + ","
              + loc.getBlockZ();
      return this.deleteBySpawnId(sid);
    } else {
      return false;
    }
  }

  public boolean deleteBySpawnId(String spawnId) {
    SpawnPoint sp = this.registry.remove(spawnId);
    if (sp == null) {
      return false;
    } else {
      this.auto.unregister(sp.spawnId());
      if (sp.mode() == SpawnMode.ENDLESS) {
        this.saveToConfig();
      }

      return true;
    }
  }

  public boolean deleteAutoSpawnByMobId(String mobId) {
    boolean removed = false;
    Iterator<SpawnPoint> it = this.registry.values().iterator();

    while (it.hasNext()) {
      SpawnPoint sp = it.next();
      if (sp.type() == SpawnType.FIXED_POINT && sp.mobId().equalsIgnoreCase(mobId)) {
        this.auto.unregister(sp.spawnId());
        it.remove();
        removed = true;
      }
    }

    if (removed) {
      this.saveToConfig();
    }

    return removed;
  }

  public List<SpawnController.AutoSpawnInfo> listAutoSpawns() {
    List<SpawnController.AutoSpawnInfo> out = new ArrayList<>();

    for (SpawnPoint sp : this.registry.values()) {
      if (sp.type() == SpawnType.FIXED_POINT) {
        Location l = sp.baseLocation();
        if (l != null) {
          out.add(
              new SpawnController.AutoSpawnInfo(
                  sp.mobId(),
                  l.getWorld().getName(),
                  l.getBlockX(),
                  l.getBlockY(),
                  l.getBlockZ(),
                  sp.intervalSeconds(),
                  sp.maxSpawns(),
                  sp.arenaRadiusChunks()));
        }
      }
    }

    return out;
  }

  /** Immutable list of the current immutable definitions, including transient ONETIME points. */
  public List<SpawnPoint> points() {
    return registry.values().stream()
        .sorted(java.util.Comparator.comparing(SpawnPoint::spawnId))
        .toList();
  }

  public List<String> listAllLines() {
    List<String> out = new ArrayList<>();

    for (SpawnPoint sp : this.registry.values()) {
      Location l = sp.lastLocationFallbackBase();
      if (l != null) {
        out.add(
            l.getWorld().getName()
                + " "
                + sp.mobId()
                + " "
                + l.getBlockX()
                + ","
                + l.getBlockY()
                + ","
                + l.getBlockZ()
                + " "
                + sp.type().name());
      }
    }

    return out;
  }

  private void loadFromConfig() {
    this.registry.clear();
    FileConfiguration cfg = this.configs.autoSpawn();
    List<Map<?, ?>> list = cfg.getMapList("spawns");
    if (list != null) {
      for (Map<?, ?> raw : list) {
        try {
          SpawnMode mode = SpawnMode.fromString((String) raw.get("mode"));
          if (mode != SpawnMode.ONETIME) {
            SpawnType type =
                SpawnType.valueOf(String.valueOf(raw.get("type")).toUpperCase(Locale.ROOT));
            String mobId = String.valueOf(raw.get("mobId"));
            if (this.mobs.mobExists(mobId)) {
              SpawnPoint sp =
                  new SpawnPoint(
                      type,
                      mobId,
                      (String) raw.get("world"),
                      type == SpawnType.FOLLOW_PLAYER ? 0 : num(raw.get("x")),
                      type == SpawnType.FOLLOW_PLAYER ? 0 : num(raw.get("y")),
                      type == SpawnType.FOLLOW_PLAYER ? 0 : num(raw.get("z")),
                      num(raw.get("intervalSeconds")),
                      num(raw.get("maxSpawns")),
                      !Boolean.FALSE.equals(raw.get("enabled")),
                      raw.containsKey("arenaRadiusChunks")
                          ? num(raw.get("arenaRadiusChunks"))
                          : null,
                      raw.containsKey("minRadius") ? num(raw.get("minRadius")) : null,
                      raw.containsKey("maxRadius") ? num(raw.get("maxRadius")) : null,
                      (String) raw.get("player"),
                      mode,
                      raw.containsKey("minDistance") ? num(raw.get("minDistance")) : null,
                      raw.containsKey("maxDistance") ? num(raw.get("maxDistance")) : null,
                      (String) (raw.containsKey("message") ? raw.get("message") : ""),
                      raw.containsKey("messageTimerSeconds")
                          ? num(raw.get("messageTimerSeconds"))
                          : null,
                      raw.containsKey("lastX") ? num(raw.get("lastX")) : null,
                      raw.containsKey("lastY") ? num(raw.get("lastY")) : null,
                      raw.containsKey("lastZ") ? num(raw.get("lastZ")) : null);
              this.registry.put(sp.spawnId(), sp);
              this.auto.register(sp);
            }
          }
        } catch (Exception var9) {
        }
      }
    }
  }

  private void saveToConfig() {
    FileConfiguration cfg = this.configs.autoSpawn();
    List<Map<String, Object>> out = new ArrayList<>();

    for (SpawnPoint sp : this.registry.values()) {
      if (sp.mode() != SpawnMode.ONETIME) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", sp.type().name());
        m.put("mobId", sp.mobId());
        m.put("intervalSeconds", sp.intervalSeconds());
        m.put("maxSpawns", sp.maxSpawns());
        m.put("enabled", sp.enabled());
        m.put("arenaRadiusChunks", sp.arenaRadiusChunks());
        m.put("minRadius", sp.minRadius());
        m.put("maxRadius", sp.maxRadius());
        m.put("mode", sp.mode().name());
        if (sp.worldName() != null) {
          m.put("world", sp.worldName());
          m.put("x", sp.x());
          m.put("y", sp.y());
          m.put("z", sp.z());
        }

        if (sp.type() == SpawnType.FOLLOW_PLAYER) {
          m.put("player", sp.playerName());
          m.put("minDistance", sp.minDistance());
          m.put("maxDistance", sp.maxDistance());
          m.put("message", sp.message());
        }

        if (sp.type() == SpawnType.RANDOM_WORLD) {
          m.put("messageTimerSeconds", sp.messageTimerSeconds());
          m.put("message", sp.message());
        }

        if (sp.lastX() != null) {
          m.put("lastX", sp.lastX());
        }

        if (sp.lastY() != null) {
          m.put("lastY", sp.lastY());
        }

        if (sp.lastZ() != null) {
          m.put("lastZ", sp.lastZ());
        }

        out.add(m);
      }
    }

    cfg.set("spawns", out);
    this.configs.saveAutoSpawn();
  }

  private static int num(Object o) {
    return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o));
  }

  public AutoSpawnManager getAutoSpawnManager() {
    return this.auto;
  }

  public void updateRuntimeLocation(String spawnId, int x, int y, int z) {
    SpawnPoint sp = this.registry.get(spawnId);
    if (sp != null) {
      SpawnPoint updated =
          new SpawnPoint(
              sp.type(),
              sp.mobId(),
              sp.worldName(),
              sp.x(),
              sp.y(),
              sp.z(),
              sp.intervalSeconds(),
              sp.maxSpawns(),
              sp.enabled(),
              sp.arenaRadiusChunks(),
              sp.minRadius(),
              sp.maxRadius(),
              sp.playerName(),
              sp.mode(),
              sp.minDistance(),
              sp.maxDistance(),
              sp.message(),
              sp.messageTimerSeconds(),
              x,
              y,
              z);
      this.registry.put(spawnId, updated);
    }
  }

  public void onRuntimeSpawnRemoved(String spawnId) {
    this.registry.remove(spawnId);
  }

  public boolean deleteAllByMobId(String mobId) {
    boolean removed = false;
    Iterator<SpawnPoint> it = this.registry.values().iterator();

    while (it.hasNext()) {
      SpawnPoint sp = it.next();
      if (sp.mobId().equalsIgnoreCase(mobId)) {
        this.auto.unregister(sp.spawnId());
        it.remove();
        removed = true;
      }
    }

    if (removed) {
      this.saveToConfig();
    }

    return removed;
  }

  public record AutoSpawnInfo(
      String mobId,
      String world,
      int x,
      int y,
      int z,
      int intervalSeconds,
      int maxSpawns,
      int arenaRadiusChunks) {}
}
