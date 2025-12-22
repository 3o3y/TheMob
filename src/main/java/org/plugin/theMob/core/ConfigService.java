package org.plugin.theMob.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ConfigService {

    private final JavaPlugin plugin;
    private File mobsFolder;
    private File autoSpawnFile;
    private File statsFile;
    private FileConfiguration autoSpawnCfg;
    private FileConfiguration statsCfg;
    private final Map<String, FileConfiguration> mobConfigs = new HashMap<>(256);
    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }
// INIT
    public void ensureFoldersAndDefaults() {
        mobsFolder = new File(plugin.getDataFolder(), "mobs");
        if (!mobsFolder.exists()) mobsFolder.mkdirs();
        autoSpawnFile = new File(plugin.getDataFolder(), "auto_spawn.yml");
        statsFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!autoSpawnFile.exists()) plugin.saveResource("auto_spawn.yml", false);
        if (!statsFile.exists()) plugin.saveResource("stats.yml", false);
        File z1 = new File(mobsFolder, "zombie_normal.yml");
        File z2 = new File(mobsFolder, "zombie_boss.yml");
        if (!z1.exists()) plugin.saveResource("mobs/zombie_normal.yml", false);
        if (!z2.exists()) plugin.saveResource("mobs/zombie_boss.yml", false);
    }
// RELOAD
    public void reloadAll() {
        reloadAutoSpawn();
        reloadStats();
        reloadMobs();
    }
    public void reloadAutoSpawn() {
        autoSpawnCfg = YamlConfiguration.loadConfiguration(autoSpawnFile);
        if (!autoSpawnCfg.contains("spawns")) autoSpawnCfg.set("spawns", java.util.List.of());
    }
    public void reloadStats() {
        statsCfg = YamlConfiguration.loadConfiguration(statsFile);
    }
    public void reloadMobs() {
        mobConfigs.clear();
        File[] files = mobsFolder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".yml")) continue;
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase();
            mobConfigs.put(id, YamlConfiguration.loadConfiguration(f));
        }
        plugin.getLogger().info("[Config] Loaded mob configs: " + mobConfigs.size());
    }
// SAVE
    public void saveAutoSpawn() {
        try {
            autoSpawnCfg.save(autoSpawnFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Config] Could not save auto_spawn.yml: " + e.getMessage());
        }
    }
// GETTERS
    public Map<String, FileConfiguration> mobConfigs() {
        return Collections.unmodifiableMap(mobConfigs);
    }
    public FileConfiguration autoSpawn() {
        return autoSpawnCfg;
    }
    public FileConfiguration stats() {
        return statsCfg;
    }
}
