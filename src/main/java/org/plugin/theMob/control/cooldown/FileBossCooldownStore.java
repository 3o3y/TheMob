package org.plugin.theMob.control.cooldown;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FileBossCooldownStore implements BossCooldownStore {

    private final Plugin plugin;
    private final File file;

    // bossId -> nextSpawnEpochSeconds
    private final Map<String, Long> next = new ConcurrentHashMap<>();

    public FileBossCooldownStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss_cooldowns.yml");
    }

    @Override
    public void load() {
        next.clear();

        try {
            if (!file.exists()) return;

            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            if (!yml.isConfigurationSection("data")) return;

            for (String bossId : yml.getConfigurationSection("data").getKeys(false)) {
                long v = yml.getLong("data." + bossId + ".next-spawn", 0L);
                if (v > 0) {
                    next.put(bossId, v);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TheMob] Failed to load boss cooldowns (file): " + e.getMessage());
        }
    }

    @Override
    public void save() {
        try {
            YamlConfiguration yml = new YamlConfiguration();

            for (Map.Entry<String, Long> en : next.entrySet()) {
                yml.set("data." + en.getKey() + ".next-spawn", en.getValue());
            }

            yml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[TheMob] Failed to save boss cooldowns (file): " + e.getMessage());
        }
    }

    @Override
    public long getNextSpawnEpochSeconds(String bossId) {
        return next.getOrDefault(bossId, 0L);
    }

    @Override
    public void setNextSpawnEpochSeconds(String bossId, long epochSeconds) {
        if (bossId == null || bossId.isBlank()) return;

        if (epochSeconds <= 0) {
            next.remove(bossId);
        } else {
            next.put(bossId, epochSeconds);
        }
    }

    @Override
    public Set<String> getAllBossIds() {
        return Set.copyOf(next.keySet());
    }

    @Override
    public void close() {
        save();
        next.clear();
    }
}
