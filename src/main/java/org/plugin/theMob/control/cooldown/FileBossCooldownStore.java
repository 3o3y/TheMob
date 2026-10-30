package org.plugin.theMob.control.cooldown;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

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
    this.data.clear();
    if (this.file.exists()) {
      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);

      for (String key : cfg.getKeys(true)) {
        if (cfg.isLong(key) || cfg.isInt(key)) this.data.put(key, cfg.getLong(key, 0L));
      }
    }
  }

  @Override
  public void save() {
    YamlConfiguration cfg = new YamlConfiguration();

    for (Entry<String, Long> e : this.data.entrySet()) {
      cfg.set(e.getKey(), e.getValue());
    }

    try {
      java.nio.file.Files.createDirectories(this.file.toPath().getParent());
      java.nio.file.Path temporary =
          java.nio.file.Files.createTempFile(this.file.toPath().getParent(), "cooldowns-", ".tmp");
      try {
        cfg.save(temporary.toFile());
        try {
          java.nio.file.Files.move(
              temporary,
              this.file.toPath(),
              java.nio.file.StandardCopyOption.ATOMIC_MOVE,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
          java.nio.file.Files.move(
              temporary, this.file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        java.nio.file.Files.deleteIfExists(temporary);
      }
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot save boss cooldowns", failure);
    }
  }

  @Override
  public long getNextSpawnEpochSeconds(String bossId) {
    return this.data.getOrDefault(bossId, 0L);
  }

  @Override
  public void setNextSpawnEpochSeconds(String bossId, long epochSeconds) {
    this.data.put(bossId, epochSeconds);
  }

  @Override
  public Set<String> getAllBossIds() {
    return this.data.keySet();
  }

  @Override
  public void close() {
    this.save();
    this.data.clear();
  }
}
