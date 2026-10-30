package org.plugin.theMob.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigService {
  private final JavaPlugin plugin;
  private File mobsFolder;
  private File installedZipFolder;
  private File autoSpawnFile;
  private File statsFile;
  private File spawnEggsFile;
  private volatile FileConfiguration spawnEggsCfg;
  private volatile FileConfiguration autoSpawnCfg;
  private volatile FileConfiguration statsCfg;
  private volatile Map<String, FileConfiguration> mobConfigs = Map.of();
  private static final List<String> DEFAULT_MOBS =
      List.of(
          "mob_template.yml",
          "boss_template.yml",
          "skeleton_normal.yml",
          "spider_normal.yml",
          "warden_normal.yml",
          "pig_baby.yml",
          "chicken_baby.yml",
          "pig_normal.yml");

  public ConfigService(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public void ensureFoldersAndDefaults() {
    this.plugin.getDataFolder().mkdirs();
    this.mobsFolder = new File(this.plugin.getDataFolder(), "mobs");
    this.installedZipFolder = new File(this.mobsFolder, ".installed");
    this.mobsFolder.mkdirs();
    this.installedZipFolder.mkdirs();
    this.autoSpawnFile = new File(this.plugin.getDataFolder(), "auto_spawn.yml");
    this.statsFile = new File(this.plugin.getDataFolder(), "stats.yml");
    this.spawnEggsFile = new File(this.plugin.getDataFolder(), "spawneggs.yml");
    if (!this.spawnEggsFile.exists()) {
      this.plugin.saveResource("spawneggs.yml", false);
    }

    if (!this.autoSpawnFile.exists()) {
      this.plugin.saveResource("auto_spawn.yml", false);
    }

    if (!this.statsFile.exists()) {
      this.plugin.saveResource("stats.yml", false);
    }

    File mainConfig = new File(this.plugin.getDataFolder(), "config.yml");
    if (!mainConfig.exists()) {
      this.plugin.saveResource("config.yml", false);
    }

    this.copyDefaultMobConfigs();
  }

  private void copyDefaultMobConfigs() {
    for (String fileName : DEFAULT_MOBS) {
      File out = new File(this.mobsFolder, fileName);
      if (!out.exists()) {
        try {
          this.plugin.saveResource("mobs/" + fileName, false);
          this.plugin.getLogger().info("[Config] Installed default mob: " + fileName);
        } catch (Exception var5) {
        }
      }
    }
  }

  public void reloadAll() {
    this.installZipPacks();
    this.reloadAutoSpawn();
    this.reloadStats();
    this.reloadMobsValidated();
    this.reloadSpawnEggs();
  }

  private void installZipPacks() {
    File[] archives =
        this.mobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".zip"));
    if (archives == null) return;
    for (File archive : archives) {
      try {
        int count = MobPackInstaller.install(archive.toPath(), this.mobsFolder.toPath());
        Files.move(
            archive.toPath(),
            this.installedZipFolder.toPath().resolve(archive.getName()),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        this.plugin
            .getLogger()
            .info("[Packs] Installed " + count + " files from " + archive.getName());
      } catch (IOException failure) {
        this.plugin
            .getLogger()
            .warning("[Packs] Rejected " + archive.getName() + ": " + failure.getMessage());
      }
    }
  }

  public void reloadAutoSpawn() {
    this.autoSpawnCfg = YamlConfiguration.loadConfiguration(this.autoSpawnFile);
    if (!this.autoSpawnCfg.isList("spawns")) {
      this.autoSpawnCfg.set("spawns", List.of());
    }
  }

  public void reloadStats() {
    this.statsCfg = YamlConfiguration.loadConfiguration(this.statsFile);
  }

  public void reloadMobsValidated() {
    List<File> files = new ArrayList<>(256);
    this.collectYmlFilesRecursive(this.mobsFolder, files);
    Map<String, FileConfiguration> next = new HashMap<>(files.size() * 2);
    int ok = 0;
    int bad = 0;

    for (File f : files) {
      FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
      String mobId = cfg.getString("mob-id");
      if (mobId == null || mobId.isBlank()) {
        mobId = this.defaultMobIdFromPath(this.mobsFolder, f);
      }

      mobId = mobId.toLowerCase(Locale.ROOT);
      String type = cfg.getString("type");
      if (type == null || type.isBlank()) {
        type = cfg.getString("base-type");
      }

      if (type != null && !type.isBlank()) {
        try {
          org.bukkit.entity.EntityType entityType =
              org.bukkit.entity.EntityType.valueOf(type.toUpperCase(Locale.ROOT));
          if (!entityType.isAlive() || !entityType.isSpawnable())
            throw new IllegalArgumentException("not a living spawnable type");
        } catch (IllegalArgumentException invalid) {
          this.plugin
              .getLogger()
              .warning("[Config] Invalid entity type in " + f.getName() + ": " + type);
          bad++;
          continue;
        }
        boolean invalidNumber =
            cfg.getValues(true).values().stream()
                .anyMatch(
                    value ->
                        value instanceof Number number && !Double.isFinite(number.doubleValue()));
        if (invalidNumber) {
          this.plugin.getLogger().warning("[Config] Non-finite value in " + f.getName());
          bad++;
          continue;
        }
        double hp = cfg.getDouble("stats.health.max", -1.0);
        if (hp <= 0.0 || hp > 1_000_000) {
          bad++;
        } else {
          ConfigurationSection ps = cfg.getConfigurationSection("phases");
          if (ps != null) {
            for (String pid : ps.getKeys(false)) {
              String range = ps.getString(pid + ".hp-range");
              if (range == null || !range.contains("-")) {
                this.plugin
                    .getLogger()
                    .warning("[Config] " + mobId + " phase '" + pid + "' invalid hp-range");
              }
            }
          }

          if (next.containsKey(mobId)) {
            this.plugin.getLogger().warning("[Config] Duplicate mob-id '" + mobId + "' skipped");
            bad++;
          } else {
            next.put(mobId, cfg);
            ok++;
          }
        }
      } else {
        bad++;
      }
    }

    var generated = org.plugin.theMob.content.MobCatalog.generate(this.plugin);
    generated.forEach(next::putIfAbsent);
    org.plugin.theMob.boss.behavior.ZombieBossProfiles.apply(this.plugin, next);
    this.mobConfigs = Collections.unmodifiableMap(next);
    this.plugin
        .getLogger()
        .info(
            "[Config] Mob configs loaded: custom="
                + ok
                + " catalog="
                + generated.size()
                + " bad="
                + bad);
  }

  private void collectYmlFilesRecursive(File dir, List<File> out) {
    File[] children = dir.listFiles();
    if (children != null) {
      for (File f : children) {
        if (f.isDirectory()) {
          if (!f.getName().startsWith(".") && !Files.isSymbolicLink(f.toPath())) {
            this.collectYmlFilesRecursive(f, out);
          }
        } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
          out.add(f);
        }
      }
    }
  }

  private String defaultMobIdFromPath(File root, File file) {
    String rp = file.getAbsolutePath().substring(root.getAbsolutePath().length());
    rp = rp.replace(File.separatorChar, '_');
    while (rp.startsWith("_")) rp = rp.substring(1);
    if (rp.endsWith(".yml")) {
      rp = rp.substring(0, rp.length() - 4);
    }

    return rp.toLowerCase(Locale.ROOT);
  }

  public void saveAutoSpawn() {
    if (this.autoSpawnCfg != null) {
      try {
        var temporary =
            Files.createTempFile(this.autoSpawnFile.toPath().getParent(), "auto-spawn-", ".tmp");
        try {
          this.autoSpawnCfg.save(temporary.toFile());
          try {
            Files.move(
                temporary,
                this.autoSpawnFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(
                temporary,
                this.autoSpawnFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          }
        } finally {
          Files.deleteIfExists(temporary);
        }
      } catch (IOException var2) {
        this.plugin
            .getLogger()
            .severe("[Config] Could not save auto_spawn.yml: " + var2.getMessage());
      }
    }
  }

  public void reloadSpawnEggs() {
    this.spawnEggsCfg = YamlConfiguration.loadConfiguration(this.spawnEggsFile);
    if (!this.spawnEggsCfg.isConfigurationSection("spawn-eggs")) {
      this.spawnEggsCfg.set("spawn-eggs", Map.of());
    }
    // Add runtime mappings without rewriting the user's configured egg aliases.
    for (String id : mobConfigs.keySet())
      if (!spawnEggsCfg.contains("spawn-eggs." + id)) spawnEggsCfg.set("spawn-eggs." + id, id);
  }

  public FileConfiguration spawnEggs() {
    return this.spawnEggsCfg;
  }

  public Map<String, FileConfiguration> mobConfigs() {
    return this.mobConfigs;
  }

  public FileConfiguration autoSpawn() {
    return this.autoSpawnCfg;
  }

  public FileConfiguration stats() {
    return this.statsCfg;
  }
}
