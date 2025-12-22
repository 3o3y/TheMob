package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class AutoSpawnManager {

    private final Plugin plugin;
    private File stateFile;
    private final Map<String, Integer> alive = new HashMap<>();
    private final Map<String, Long> lastSpawn = new HashMap<>();
    private long lastValidation = 0L;
    public AutoSpawnManager(Plugin plugin) {
        this.plugin = plugin;
    }
// LOAD / SAVE
    public void load() {
        loadState();
    }
    private void loadState() {
        stateFile = new File(plugin.getDataFolder(), "auto_spawn_state.yml");
        if (!stateFile.exists()) return;
        FileConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection sec = state.getConfigurationSection("spawners");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            alive.put(id, sec.getInt(id + ".alive", 0));
            lastSpawn.put(id, sec.getLong(id + ".last_spawn", 0L));
        }
    }
    public void saveState() {
        if (stateFile == null) return;
        YamlConfiguration out = new YamlConfiguration();
        for (String id : alive.keySet()) {
            out.set("spawners." + id + ".alive", alive.get(id));
            out.set("spawners." + id + ".last_spawn", lastSpawn.getOrDefault(id, 0L));
        }
        try {
            out.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save auto_spawn_state.yml");
        }
    }
// COUNTERS
    public int alive(String id) {
        return alive.getOrDefault(id, 0);
    }
    public void incrementAlive(String id) {
        alive.put(id, alive(id) + 1);
    }
    public void decrementAlive(String id) {
        alive.put(id, Math.max(0, alive(id) - 1));
    }
    public void updateLastSpawn(String id) {
        lastSpawn.put(id, System.currentTimeMillis());
    }
// VALIDATION (ANTI-DESYNC)
    public void validateAliveCounts(MobManager mobs, KeyRegistry keys) {
        long now = System.currentTimeMillis();
        if (now - lastValidation < 30_000L) return;
        lastValidation = now;
        Map<String, Integer> real = new HashMap<>();
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity le : w.getLivingEntities()) {
                if (!mobs.isCustomMob(le)) continue;
                if (!le.getPersistentDataContainer()
                        .has(keys.AUTO_SPAWNED, PersistentDataType.INTEGER)) continue;
                String id = mobs.mobIdOf(le);
                if (id == null) continue;
                real.merge(id, 1, Integer::sum);
            }
        }
        alive.clear();
        alive.putAll(real);
    }
}
