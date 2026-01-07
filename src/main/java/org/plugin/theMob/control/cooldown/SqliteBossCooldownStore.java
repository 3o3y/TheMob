package org.plugin.theMob.control.cooldown;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SqliteBossCooldownStore implements BossCooldownStore {

    private final Plugin plugin;
    private final File dbFile;

    private Connection conn;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    public SqliteBossCooldownStore(Plugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "themob.db");
    }

    @Override
    public void load() {
        cache.clear();
        try {
            ensureOpen();
            ensureSchema();

            try (PreparedStatement ps =
                         conn.prepareStatement("SELECT boss_id, next_spawn FROM themob_boss_cooldowns")) {

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cache.put(rs.getString("boss_id"), rs.getLong("next_spawn"));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TheMob] Failed to load boss cooldowns (sqlite): " + e.getMessage());
        }
    }

    @Override
    public void save() {
        // no-op: sqlite writes immediately on set
    }

    @Override
    public long getNextSpawnEpochSeconds(String bossId) {
        return cache.getOrDefault(bossId, 0L);
    }

    @Override
    public void setNextSpawnEpochSeconds(String bossId, long epochSeconds) {
        if (bossId == null || bossId.isBlank()) return;

        try {
            ensureOpen();
            ensureSchema();

            if (epochSeconds <= 0) {
                cache.remove(bossId);
                try (PreparedStatement ps =
                             conn.prepareStatement("DELETE FROM themob_boss_cooldowns WHERE boss_id=?")) {
                    ps.setString(1, bossId);
                    ps.executeUpdate();
                }
                return;
            }

            cache.put(bossId, epochSeconds);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO themob_boss_cooldowns (boss_id, next_spawn) VALUES (?, ?) " +
                            "ON CONFLICT(boss_id) DO UPDATE SET next_spawn=excluded.next_spawn"
            )) {
                ps.setString(1, bossId);
                ps.setLong(2, epochSeconds);
                ps.executeUpdate();
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[TheMob] Failed to set boss cooldown (sqlite): " + e.getMessage());
        }
    }

    @Override
    public Set<String> getAllBossIds() {
        return Set.copyOf(cache.keySet());
    }

    private void ensureOpen() throws SQLException {
        if (conn != null && !conn.isClosed()) return;

        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        conn = DriverManager.getConnection(url);
        conn.setAutoCommit(true);
    }

    private void ensureSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS themob_boss_cooldowns (" +
                            "boss_id TEXT PRIMARY KEY," +
                            "next_spawn INTEGER NOT NULL" +
                            ")"
            );
        }
    }

    @Override
    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (Exception ignored) {}
        conn = null;
        cache.clear();
    }
}
