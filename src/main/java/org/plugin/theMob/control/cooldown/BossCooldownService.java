package org.plugin.theMob.control.cooldown;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * Central boss cooldown controller.
 * Responsible for reading, writing and exposing boss spawn cooldowns.
 */
public final class BossCooldownService {

    private final Plugin plugin;
    private BossCooldownStore store;

    private boolean enabled = true;

    public BossCooldownService(Plugin plugin) {
        this.plugin = plugin;
    }

    // =========================
    // RELOAD / INIT
    // =========================
    public void reload(FileConfiguration cfg) {
        enabled = cfg.getBoolean("boss-cooldowns.enabled", true);

        String storage = cfg.getString("boss-cooldowns.storage", "file")
                .trim()
                .toLowerCase();

        BossCooldownStore next = switch (storage) {
            case "sqlite" -> new SqliteBossCooldownStore(plugin);
            default -> new FileBossCooldownStore(plugin);
        };

        if (store != null) {
            try { store.close(); } catch (Exception ignored) {}
        }

        store = next;
        store.load();

        // Optional: import predefined cooldowns from config
        if (cfg.isConfigurationSection("boss-cooldowns.data")) {
            for (String bossId : cfg.getConfigurationSection("boss-cooldowns.data").getKeys(false)) {
                long nextSpawn = cfg.getLong(
                        "boss-cooldowns.data." + bossId + ".next-spawn",
                        0L
                );

                if (nextSpawn > 0 && store.getNextSpawnEpochSeconds(bossId) <= 0) {
                    store.setNextSpawnEpochSeconds(bossId, nextSpawn);
                }
            }
        }

        store.save();
    }

    // =========================
    // BASIC STATE
    // =========================
    public boolean enabled() {
        return enabled;
    }

    // =========================
    // SPAWN CHECK
    // =========================
    public boolean canSpawnNow(String bossId) {
        if (!enabled || bossId == null || bossId.isBlank()) return true;

        long now = Instant.now().getEpochSecond();
        long next = store.getNextSpawnEpochSeconds(bossId);

        return next <= 0 || now >= next;
    }

    // =========================
    // TIME LEFT
    // =========================
    public long secondsRemaining(String bossId) {
        if (!enabled || bossId == null || bossId.isBlank()) return 0;

        long now = Instant.now().getEpochSecond();
        long next = store.getNextSpawnEpochSeconds(bossId);

        return Math.max(0, next - now);
    }

    // =========================
    // SET COOLDOWN
    // =========================
    public void setCooldownSeconds(String bossId, long cooldownSeconds) {
        if (!enabled || bossId == null || bossId.isBlank()) return;

        long now = Instant.now().getEpochSecond();
        store.setNextSpawnEpochSeconds(
                bossId,
                now + Math.max(0, cooldownSeconds)
        );
        store.save();
    }

    // =========================
    // DIAGNOSTICS SUPPORT
    // =========================
    public Set<String> allBossIds() {
        if (!enabled || store == null) return Collections.emptySet();
        return store.getAllBossIds();
    }

    // =========================
    // SHUTDOWN
    // =========================
    public void shutdown() {
        if (store != null) {
            try { store.close(); } catch (Exception ignored) {}
        }
        store = null;
    }
}
