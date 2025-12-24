// src/main/java/org/plugin/theMob/core/ConfigService.java
package org.plugin.theMob.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ConfigService {

    private final JavaPlugin plugin;

    private File mobsFolder;
    private File autoSpawnFile;
    private File statsFile;

    // atomic swap targets (reload-safe)
    private volatile FileConfiguration autoSpawnCfg;
    private volatile FileConfiguration statsCfg;
    private volatile Map<String, FileConfiguration> mobConfigs = Map.of();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =====================================================
    // INIT
    // =====================================================
    public void ensureFoldersAndDefaults() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        mobsFolder = new File(plugin.getDataFolder(), "mobs");
        if (!mobsFolder.exists()) mobsFolder.mkdirs();

        autoSpawnFile = new File(plugin.getDataFolder(), "auto_spawn.yml");
        statsFile = new File(plugin.getDataFolder(), "stats.yml");

        if (!autoSpawnFile.exists()) plugin.saveResource("auto_spawn.yml", false);
        if (!statsFile.exists()) plugin.saveResource("stats.yml", false);

        // example defaults (only if missing)
        File z1 = new File(mobsFolder, "zombie_normal.yml");
        File z2 = new File(mobsFolder, "zombie_boss.yml");
        if (!z1.exists()) plugin.saveResource("mobs/zombie_normal.yml", false);
        if (!z2.exists()) plugin.saveResource("mobs/zombie_boss.yml", false);
    }

    // =====================================================
    // RELOAD (v1.1 hardened)
    // =====================================================
    public void reloadAll() {
        reloadAutoSpawn();
        reloadStats();
        reloadMobsValidated();
    }

    public void reloadAutoSpawn() {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(autoSpawnFile);
        if (!cfg.isList("spawns")) {
            cfg.set("spawns", List.of());
        }
        autoSpawnCfg = cfg;
    }

    public void reloadStats() {
        statsCfg = YamlConfiguration.loadConfiguration(statsFile);
    }

    /**
     * YAML validation + atomic swap
     */
    public void reloadMobsValidated() {
        Map<String, FileConfiguration> next = new HashMap<>(256);

        File[] files = mobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            mobConfigs = Map.of();
            return;
        }

        int ok = 0;
        int bad = 0;

        for (File f : files) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

            // -------------------------
            // mob-id (required)
            // -------------------------
            String mobId = cfg.getString("mob-id");
            if (mobId == null || mobId.isBlank()) {
                mobId = f.getName().substring(0, f.getName().length() - 4);
            }
            mobId = mobId.toLowerCase(Locale.ROOT);

            // -------------------------
            // type / base-type (required)
            // -------------------------
            String type = cfg.getString("type");
            if (type == null || type.isBlank()) {
                type = cfg.getString("base-type");
            }
            if (type == null || type.isBlank()) {
                plugin.getLogger().warning("[Config] " + mobId + " skipped: missing type/base-type");
                bad++;
                continue;
            }

            // -------------------------
            // health (required)
            // -------------------------
            double hp;
            if (cfg.isDouble("stats.health")) {
                hp = cfg.getDouble("stats.health");
            } else {
                hp = cfg.getDouble("stats.health.max", -1);
            }

            if (hp <= 0) {
                plugin.getLogger().warning("[Config] " + mobId + " skipped: stats.health <= 0");
                bad++;
                continue;
            }

            // -------------------------
            // phase validation (safe)
            // -------------------------
            ConfigurationSection ps = cfg.getConfigurationSection("phases");
            if (ps != null) {
                for (String pid : ps.getKeys(false)) {
                    String range = ps.getString(pid + ".hp-range");
                    if (range == null || !range.contains("-")) {
                        plugin.getLogger().warning(
                                "[Config] " + mobId + " phase '" + pid + "' invalid hp-range"
                        );
                    }
                }
            }

            next.put(mobId, cfg);
            ok++;
        }

        mobConfigs = Collections.unmodifiableMap(next);
        plugin.getLogger().info("[Config] Mob configs loaded: ok=" + ok + " bad=" + bad);
    }


    // =====================================================
    // SAVE
    // =====================================================
    public void saveAutoSpawn() {
        try {
            autoSpawnCfg.save(autoSpawnFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Config] Could not save auto_spawn.yml: " + e.getMessage());
        }
    }

    // =====================================================
    // GETTERS (READ-ONLY)
    // =====================================================
    public Map<String, FileConfiguration> mobConfigs() {
        return mobConfigs;
    }

    public FileConfiguration autoSpawn() {
        return autoSpawnCfg;
    }

    public FileConfiguration stats() {
        return statsCfg;
    }
}
