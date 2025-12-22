package org.plugin.theMob.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NormalizedConfig {

    private final FileConfiguration raw;
    private final Map<String, Object> flat = new HashMap<>(256);
    public NormalizedConfig(FileConfiguration raw) {
        this.raw = raw;
        if (raw != null) flatten("", raw);
    }
    private void flatten(String prefix, ConfigurationSection sec) {
        for (String k : sec.getKeys(false)) {
            Object v = sec.get(k);
            String key = normalize(prefix.isEmpty() ? k : prefix + "." + k);
            if (v instanceof ConfigurationSection cs) {
                flatten(key, cs);
            } else {
                flat.put(key, v);
            }
        }
    }
    public boolean has(String path) {
        return flat.containsKey(normalize(path));
    }
    public String getString(String path, String def) {
        Object v = flat.get(normalize(path));
        return v != null ? String.valueOf(v) : def;
    }
    public double getDouble(String path, double def) {
        Object v = flat.get(normalize(path));
        if (v == null) return def;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception ignored) { return def; }
    }
    public int getInt(String path, int def) {
        Object v = flat.get(normalize(path));
        if (v == null) return def;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignored) { return def; }
    }
    public boolean getBool(String path, boolean def) {
        Object v = flat.get(normalize(path));
        if (v == null) return def;
        return Boolean.parseBoolean(String.valueOf(v));
    }
    public FileConfiguration raw() { return raw; }
    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .trim();
    }
}
