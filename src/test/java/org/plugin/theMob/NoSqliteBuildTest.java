package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.plugin.theMob.control.cooldown.BossCooldownService;

/** Run against the distributed NoSQLite JAR, with main output and SQLite removed. */
class NoSqliteBuildTest {
  @Test
  void distributedJarHasNoSqliteDriverAndUsesJava17Core() throws Exception {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("org.sqlite.JDBC"));
    try (var jar = new ZipFile(System.getProperty("themob.nosqlite.jar"))) {
      assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/sqlite/")));
      assertNull(jar.getEntry("META-INF/services/java.sql.Driver"));
      assertNull(jar.getEntry("stats.yml"));
      assertNull(jar.getEntry("skills.yml"));
      assertNotNull(jar.getEntry("abilities.yml"));
      assertFalse(
          jar.stream()
              .anyMatch(
                  entry ->
                      entry.getName().startsWith("org/plugin/theMob/player/stats/")
                          || entry.getName().startsWith("org/plugin/theMob/progression/")));
      Properties profile = new Properties();
      try (var input = jar.getInputStream(jar.getEntry("themob-build.properties"))) {
        profile.load(input);
      }
      assertEquals("false", profile.getProperty("sqlite.enabled"));
      var classes =
          jar.stream()
              .filter(
                  e ->
                      e.getName().startsWith("org/plugin/theMob/")
                          && !e.getName().startsWith("org/plugin/theMob/libs/")
                          && e.getName().endsWith(".class"))
              .toList();
      assertTrue(classes.size() > 100);
      for (var entry : classes) {
        try (var input = jar.getInputStream(entry)) {
          byte[] header = input.readNBytes(8);
          assertEquals(61, ((header[6] & 255) << 8) | (header[7] & 255), entry.getName());
        }
      }
    }
  }

  @Test
  void legacySqliteSettingUsesPersistentFileAndLeavesDatabaseUntouched() throws Exception {
    MockBukkit.mock();
    try {
      TheMob plugin = MockBukkit.load(TheMob.class);
      assertTrue(plugin.isEnabled());
      try (var input = plugin.getResource("themob-build.properties")) {
        assertNotNull(input, "Plugin must actually load the packaged NoSQLite profile");
        Properties profile = new Properties();
        profile.load(input);
        assertEquals("false", profile.getProperty("sqlite.enabled"));
      }
      Path database = plugin.getDataFolder().toPath().resolve("boss-cooldowns.db");
      byte[] original = new byte[] {83, 81, 76, 105, 116, 101, 0, 1, 2};
      Files.write(database, original);
      var config = new YamlConfiguration();
      config.set("boss-cooldowns.storage", "sqlite");
      var first = new BossCooldownService(plugin);
      first.reload(config);
      first.setCooldownSeconds("nosqlite_test_boss", 600);
      first.shutdown();
      Path file = plugin.getDataFolder().toPath().resolve("boss-cooldowns.yml");
      assertTrue(Files.exists(file));
      assertTrue(
          YamlConfiguration.loadConfiguration(file.toFile()).getLong("nosqlite_test_boss") > 0);
      var second = new BossCooldownService(plugin);
      second.reload(config);
      assertFalse(second.canSpawnNow("nosqlite_test_boss"));
      assertTrue(second.secondsRemaining("nosqlite_test_boss") > 0);
      second.shutdown();
      assertArrayEquals(original, Files.readAllBytes(database));
    } finally {
      MockBukkit.unmock();
    }
  }
}
