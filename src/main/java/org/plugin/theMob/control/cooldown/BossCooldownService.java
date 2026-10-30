package org.plugin.theMob.control.cooldown;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class BossCooldownService {
  private final Plugin plugin;
  private BossCooldownStore store;
  private boolean enabled = true;

  public BossCooldownService(Plugin plugin) {
    this.plugin = plugin;
  }

  public void reload(FileConfiguration cfg) {
    this.enabled = cfg.getBoolean("boss-cooldowns.enabled", true);
    String storage = cfg.getString("boss-cooldowns.storage", "file");
    storage = storage == null ? "file" : storage.trim().toLowerCase();
    if (storage.equals("sqlite") && !sqliteEnabledInBuild()) {
      this.plugin
          .getLogger()
          .warning(
              "This NoSQLite build uses boss-cooldowns.yml instead of SQLite. "
                  + "An existing boss-cooldowns.db is left unchanged and is not imported.");
      storage = "file";
    }
    byte bossId = -1;
    switch (storage.hashCode()) {
      case -894935028:
        if (storage.equals("sqlite")) {
          bossId = 0;
        }
      default:
        BossCooldownStore next =
            (BossCooldownStore)
                (switch (bossId) {
                  case 0 -> new SqliteBossCooldownStore(this.plugin);
                  default -> new FileBossCooldownStore(this.plugin);
                });
        if (this.store != null) {
          try {
            this.store.close();
          } catch (Exception var8) {
          }
        }

        this.store = next;
        try {
          this.store.load();
        } catch (RuntimeException failure) {
          this.plugin
              .getLogger()
              .severe(
                  "Cooldown storage failed: "
                      + failure.getMessage()
                      + "; falling back to file storage.");
          this.store.close();
          this.store = new FileBossCooldownStore(this.plugin);
          this.store.load();
        }
        if (cfg.isConfigurationSection("boss-cooldowns.data")) {
          for (String bossIdx : cfg.getConfigurationSection("boss-cooldowns.data").getKeys(false)) {
            long nextSpawn = cfg.getLong("boss-cooldowns.data." + bossIdx + ".next-spawn", 0L);
            if (nextSpawn > 0L && this.store.getNextSpawnEpochSeconds(bossIdx) <= 0L) {
              this.store.setNextSpawnEpochSeconds(bossIdx, nextSpawn);
            }
          }
        }

        this.store.save();
    }
  }

  private boolean sqliteEnabledInBuild() {
    try (InputStream input = this.plugin.getResource("themob-build.properties")) {
      if (input == null) return true;
      Properties properties = new Properties();
      properties.load(input);
      return !"false".equalsIgnoreCase(properties.getProperty("sqlite.enabled"));
    } catch (IOException failure) {
      this.plugin.getLogger().warning("Cannot read build profile; using file cooldown storage.");
      return false;
    }
  }

  public boolean enabled() {
    return this.enabled;
  }

  public boolean canSpawnNow(String bossId) {
    if (!this.enabled) {
      return true;
    } else if (this.store == null) {
      return true;
    } else if (bossId != null && !bossId.isBlank()) {
      long now = Instant.now().getEpochSecond();
      long next = this.store.getNextSpawnEpochSeconds(bossId);
      return next <= 0L || now >= next;
    } else {
      return true;
    }
  }

  public long secondsRemaining(String bossId) {
    if (!this.enabled) {
      return 0L;
    } else if (this.store == null) {
      return 0L;
    } else if (bossId != null && !bossId.isBlank()) {
      long now = Instant.now().getEpochSecond();
      long next = this.store.getNextSpawnEpochSeconds(bossId);
      return Math.max(0L, next - now);
    } else {
      return 0L;
    }
  }

  public void setCooldownSeconds(String bossId, long cooldownSeconds) {
    if (this.enabled) {
      if (this.store != null) {
        if (bossId != null && !bossId.isBlank()) {
          long now = Instant.now().getEpochSecond();
          this.store.setNextSpawnEpochSeconds(bossId, now + Math.max(0L, cooldownSeconds));
          this.store.save();
        }
      }
    }
  }

  public Set<String> allBossIds() {
    if (!this.enabled) {
      return Collections.emptySet();
    } else {
      return this.store == null ? Collections.emptySet() : this.store.getAllBossIds();
    }
  }

  public void shutdown() {
    if (this.store != null) {
      try {
        this.store.close();
      } catch (Exception var2) {
      }
    }

    this.store = null;
  }
}
