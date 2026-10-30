package org.plugin.theMob.core.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class NormalizedConfig {
  private final FileConfiguration raw;
  private final Map<String, Object> flat = new HashMap<>(256);

  public NormalizedConfig(FileConfiguration raw) {
    this.raw = raw;
    if (raw != null) {
      this.flatten("", raw);
    }
  }

  private void flatten(String prefix, ConfigurationSection sec) {
    for (String k : sec.getKeys(false)) {
      Object v = sec.get(k);
      String key = normalize(prefix.isEmpty() ? k : prefix + "." + k);
      if (v instanceof ConfigurationSection cs) {
        this.flatten(key, cs);
      } else {
        this.flat.put(key, v);
      }
    }
  }

  public boolean has(String path) {
    return this.flat.containsKey(normalize(path));
  }

  public String getString(String path, String def) {
    Object v = this.flat.get(normalize(path));
    return v != null ? String.valueOf(v) : def;
  }

  public double getDouble(String path, double def) {
    Object v = this.flat.get(normalize(path));
    if (v == null) {
      return def;
    } else {
      try {
        return Double.parseDouble(String.valueOf(v));
      } catch (Exception var6) {
        return def;
      }
    }
  }

  public int getInt(String path, int def) {
    Object v = this.flat.get(normalize(path));
    if (v == null) {
      return def;
    } else {
      try {
        return Integer.parseInt(String.valueOf(v));
      } catch (Exception var5) {
        return def;
      }
    }
  }

  public boolean getBool(String path, boolean def) {
    Object v = this.flat.get(normalize(path));
    return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
  }

  public FileConfiguration raw() {
    return this.raw;
  }

  public static String normalize(String s) {
    return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('-', '_').trim();
  }
}
