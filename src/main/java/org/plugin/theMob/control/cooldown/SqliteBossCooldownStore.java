package org.plugin.theMob.control.cooldown;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.plugin.Plugin;

public final class SqliteBossCooldownStore implements BossCooldownStore {
  private final File dbFile;
  private Connection conn;

  public SqliteBossCooldownStore(Plugin plugin) {
    this.dbFile = new File(plugin.getDataFolder(), "boss-cooldowns.db");
  }

  @Override
  public void load() {
    try {
      Class.forName("org.sqlite.JDBC", true, getClass().getClassLoader());
      this.conn = DriverManager.getConnection("jdbc:sqlite:" + this.dbFile.getAbsolutePath());

      try (Statement st = this.conn.createStatement()) {
        st.executeUpdate(
            "    CREATE TABLE IF NOT EXISTS cooldowns (\n"
                + "        boss_id TEXT PRIMARY KEY,\n"
                + "        next_spawn INTEGER\n"
                + "    )\n");
      }
    } catch (SQLException | ClassNotFoundException failure) {
      throw new IllegalStateException("Cannot open boss cooldown database", failure);
    }
  }

  @Override
  public void save() {}

  @Override
  public long getNextSpawnEpochSeconds(String bossId) {
    try (PreparedStatement ps =
        this.conn.prepareStatement("SELECT next_spawn FROM cooldowns WHERE boss_id = ?")) {
      ps.setString(1, bossId);
      ResultSet rs = ps.executeQuery();
      return rs.next() ? rs.getLong(1) : 0L;
    } catch (SQLException failure) {
      throw new IllegalStateException("Boss cooldown database operation failed", failure);
    }
  }

  @Override
  public void setNextSpawnEpochSeconds(String bossId, long epochSeconds) {
    try (PreparedStatement ps =
        this.conn.prepareStatement(
            "INSERT INTO cooldowns (boss_id, next_spawn) VALUES (?, ?) ON CONFLICT(boss_id) DO"
                + " UPDATE SET next_spawn = excluded.next_spawn")) {
      ps.setString(1, bossId);
      ps.setLong(2, epochSeconds);
      ps.executeUpdate();
    } catch (SQLException failure) {
      throw new IllegalStateException("Boss cooldown database operation failed", failure);
    }
  }

  @Override
  public Set<String> getAllBossIds() {
    Set<String> out = new HashSet<>();

    try (Statement st = this.conn.createStatement()) {
      ResultSet rs = st.executeQuery("SELECT boss_id FROM cooldowns");

      while (rs.next()) {
        out.add(rs.getString(1));
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("Boss cooldown database operation failed", failure);
    }

    return out;
  }

  @Override
  public void close() {
    if (this.conn != null) {
      try {
        this.conn.close();
      } catch (SQLException var2) {
      }

      this.conn = null;
    }
  }
}
