package org.plugin.theMob.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ConfigService {

    private final JavaPlugin plugin;

    private File mobsFolder;
    private File installedZipFolder;
    private File autoSpawnFile;
    private File statsFile;

    private volatile FileConfiguration autoSpawnCfg;
    private volatile FileConfiguration statsCfg;
    private volatile Map<String, FileConfiguration> mobConfigs = Map.of();

    private static final List<String> DEFAULT_MOBS = List.of(
            "mob_template.yml",
            "boss_template.yml",
            "skeleton_normal.yml",
            "spider_normal.yml",
            "warden_normal.yml",
            "pig_normal.yml"
    );

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureFoldersAndDefaults() {
        plugin.getDataFolder().mkdirs();

        mobsFolder = new File(plugin.getDataFolder(), "mobs");
        installedZipFolder = new File(mobsFolder, ".installed");

        mobsFolder.mkdirs();
        installedZipFolder.mkdirs();

        autoSpawnFile = new File(plugin.getDataFolder(), "auto_spawn.yml");
        statsFile = new File(plugin.getDataFolder(), "stats.yml");

        if (!autoSpawnFile.exists()) plugin.saveResource("auto_spawn.yml", false);
        if (!statsFile.exists()) plugin.saveResource("stats.yml", false);

        copyDefaultMobConfigs();
    }

    private void copyDefaultMobConfigs() {
        for (String fileName : DEFAULT_MOBS) {
            File out = new File(mobsFolder, fileName);
            if (out.exists()) continue;
            try {
                plugin.saveResource("mobs/" + fileName, false);
                plugin.getLogger().info("[Config] Installed default mob: " + fileName);
            } catch (Exception ignored) {}
        }
    }

    public void reloadAll() {
        installZipPacks();   // 🔥 AUTO ZIP LOADER
        reloadAutoSpawn();
        reloadStats();
        reloadMobsValidated();
    }

    private void installZipPacks() {
        File[] zips = mobsFolder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (zips == null || zips.length == 0) return;

        for (File zip : zips) {
            plugin.getLogger().info("[Packs] Installing " + zip.getName());
            int installed = 0;

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;

                    String name = e.getName().replace("\\", "/");
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".yml")) continue;

                    String rel;

                    int idx = name.indexOf("mobs/");
                    if (idx != -1) {
                        rel = name.substring(idx + 5);
                    }
                    else {
                        rel = name;
                    }

                    File out = new File(mobsFolder, rel);

                    if (out.exists()) {
                        plugin.getLogger().warning("[Packs] Skipped existing mob: " + rel);
                        continue;
                    }

                    out.getParentFile().mkdirs();
                    try (OutputStream os = Files.newOutputStream(out.toPath())) {
                        zis.transferTo(os);
                    }
                    installed++;
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("[Packs] Failed to install " + zip.getName() + ": " + ex.getMessage());
                continue;
            }

            File target = new File(installedZipFolder, zip.getName());
            if (!zip.renameTo(target)) {
                plugin.getLogger().warning(
                        "[Packs] Could not move pack to .installed/: " + zip.getName()
                );
            }

            if (installed == 0) {
                plugin.getLogger().warning(
                        "[Packs] No YAML files installed from " + zip.getName()
                );
            } else {
                plugin.getLogger().info(
                        "[Packs] Installed " + installed + " mob configs from " + zip.getName()
                );
            }
        }
    }

    public void reloadAutoSpawn() {
        autoSpawnCfg = YamlConfiguration.loadConfiguration(autoSpawnFile);
        if (!autoSpawnCfg.isList("spawns")) autoSpawnCfg.set("spawns", List.of());
    }

    public void reloadStats() {
        statsCfg = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void reloadMobsValidated() {
        List<File> files = new ArrayList<>(256);
        collectYmlFilesRecursive(mobsFolder, files);

        Map<String, FileConfiguration> next = new HashMap<>(files.size() * 2);
        int ok = 0, bad = 0;

        for (File f : files) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

            String mobId = cfg.getString("mob-id");
            if (mobId == null || mobId.isBlank()) {
                mobId = defaultMobIdFromPath(mobsFolder, f);
            }
            mobId = mobId.toLowerCase(Locale.ROOT);

            String type = cfg.getString("type");
            if (type == null || type.isBlank()) {
                type = cfg.getString("base-type");
            }
            if (type == null || type.isBlank()) {
                bad++;
                continue;
            }

            double hp = cfg.getDouble("stats.health.max", -1);
            if (hp <= 0) {
                bad++;
                continue;
            }

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

            if (next.containsKey(mobId)) {
                plugin.getLogger().warning("[Config] Duplicate mob-id '" + mobId + "' skipped");
                bad++;
                continue;
            }

            next.put(mobId, cfg);
            ok++;
        }

        mobConfigs = Collections.unmodifiableMap(next);
        plugin.getLogger().info("[Config] Mob configs loaded: ok=" + ok + " bad=" + bad);
    }

    private void collectYmlFilesRecursive(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (f.isDirectory()) {
                if (!f.getName().equals(".installed")) {
                    collectYmlFilesRecursive(f, out);
                }
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                out.add(f);
            }
        }
    }

    private String defaultMobIdFromPath(File root, File file) {
        String rp = file.getAbsolutePath().substring(root.getAbsolutePath().length());
        rp = rp.replace(File.separatorChar, '_');
        if (rp.endsWith(".yml")) rp = rp.substring(0, rp.length() - 4);
        return rp.toLowerCase(Locale.ROOT);
    }

    public void saveAutoSpawn() {
        if (autoSpawnCfg == null) return;
        try {
            autoSpawnCfg.save(autoSpawnFile);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "[Config] Could not save auto_spawn.yml: " + e.getMessage()
            );
        }
    }

    public Map<String, FileConfiguration> mobConfigs() { return mobConfigs; }
    public FileConfiguration autoSpawn() { return autoSpawnCfg; }
    public FileConfiguration stats() { return statsCfg; }
}
