package org.plugin.theMob.control.cooldown;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public final class SqliteBossCooldownStore implements BossCooldownStore {

    private final File dbFile;
    private Connection conn;

    public SqliteBossCooldownStore(Plugin plugin) {
        this.dbFile = new File(plugin.getDataFolder(), "boss-cooldowns.db");
    }

    @Override
    public void load() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cooldowns (
                        boss_id TEXT PRIMARY KEY,
                        next_spawn INTEGER
                    )
                """);
            }
        } catch (SQLException ignored) {}
    }

    @Override
    public void save() {
        // sqlite auto-commit, nothing required
    }

    @Override
    public long getNextSpawnEpochSeconds(String bossId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT next_spawn FROM cooldowns WHERE boss_id = ?"
        )) {
            ps.setString(1, bossId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException ignored) {}
        return 0L;
    }

    @Override
    public void setNextSpawnEpochSeconds(String bossId, long epochSeconds) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO cooldowns (boss_id, next_spawn) VALUES (?, ?) " +
                        "ON CONFLICT(boss_id) DO UPDATE SET next_spawn = excluded.next_spawn"
        )) {
            ps.setString(1, bossId);
            ps.setLong(2, epochSeconds);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public Set<String> getAllBossIds() {
        Set<String> out = new HashSet<>();
        try (Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT boss_id FROM cooldowns");
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException ignored) {}
        return out;
    }

    @Override
    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
    }
}
