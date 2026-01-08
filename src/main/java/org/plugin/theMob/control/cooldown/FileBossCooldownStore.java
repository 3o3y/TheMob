package org.plugin.theMob.control.cooldown;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class FileBossCooldownStore implements BossCooldownStore {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Long> data = new HashMap<>();

    public FileBossCooldownStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss-cooldowns.yml");
    }

    @Override
    public void load() {
        data.clear();

        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            data.put(key, cfg.getLong(key, 0L));
        }
    }

    @Override
    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (var e : data.entrySet()) {
            cfg.set(e.getKey(), e.getValue());
        }

        try {
            cfg.save(file);
        } catch (IOException ignored) {}
    }

    @Override
    public long getNextSpawnEpochSeconds(String bossId) {
        return data.getOrDefault(bossId, 0L);
    }

    @Override
    public void setNextSpawnEpochSeconds(String bossId, long epochSeconds) {
        data.put(bossId, epochSeconds);
    }

    @Override
    public Set<String> getAllBossIds() {
        return data.keySet();
    }

    @Override
    public void close() {
        save();
        data.clear();
    }
}
