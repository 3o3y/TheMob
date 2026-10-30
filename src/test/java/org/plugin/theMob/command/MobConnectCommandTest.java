package org.plugin.theMob.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.IntegrationStatus;

class MobConnectCommandTest {
  ServerMock server;
  TheMob plugin;
  Path config;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    server.getScheduler().performTicks(1);
    config = plugin.getDataFolder().toPath().resolve("config.yml");
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  void command(org.bukkit.command.CommandSender sender, String... args) {
    MobConnectCommand.execute(plugin, sender, args);
  }

  List<String> messages(PlayerMock player) {
    var messages = new ArrayList<String>();
    for (String message; (message = player.nextMessage()) != null; ) messages.add(message);
    return messages;
  }

  @Test
  void ordinaryPlayersCannotReadOrChangeConnections() throws Exception {
    var player = server.addPlayer();
    messages(player);
    byte[] before = Files.readAllBytes(config);
    command(player, "connect", "jobs", "disable");
    assertTrue(messages(player).stream().anyMatch(s -> s.contains("themob.integrations.view")));
    assertArrayEquals(before, Files.readAllBytes(config));
    assertFalse(Files.exists(config.resolveSibling("config.yml.connect.bak")));
  }

  @Test
  void viewPermissionAllowsStatusButNotChanges() throws Exception {
    var player = server.addPlayer();
    player.addAttachment(plugin, "themob.integrations.view", true);
    command(player, "connect", "check");
    assertTrue(
        messages(player).stream().anyMatch(s -> s.contains("Mob-API:") && s.contains("bereit")));
    byte[] before = Files.readAllBytes(config);
    command(player, "connect", "jobs", "disable");
    assertTrue(messages(player).stream().anyMatch(s -> s.contains("themob.integrations.manage")));
    assertArrayEquals(before, Files.readAllBytes(config));
  }

  @Test
  void stagedChangePreservesUnknownSettingsBackupAndLiveMobs() throws Exception {
    var disk = YamlConfiguration.loadConfiguration(config.toFile());
    disk.set("some-other-setting.keep-me", "unchanged");
    disk.set("integrations.citizens.spawn-on-click.12", "pig_lvl_1");
    disk.save(config.toFile());
    byte[] before = Files.readAllBytes(config);
    var world = server.getWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    var mob = plugin.api().spawn("pig_lvl_1", new Location(world, .5, 64, .5)).orElseThrow();
    var service = plugin.api();
    int tasks = server.getScheduler().getPendingTasks().size();
    command(server.getConsoleSender(), "connect", "jobs", "disable");
    var after = YamlConfiguration.loadConfiguration(config.toFile());
    assertFalse(after.getBoolean("integrations.jobs.enabled"));
    assertEquals("unchanged", after.getString("some-other-setting.keep-me"));
    assertEquals("pig_lvl_1", after.getString("integrations.citizens.spawn-on-click.12"));
    assertArrayEquals(before, Files.readAllBytes(config.resolveSibling("config.yml.connect.bak")));
    assertSame(service, plugin.api());
    assertTrue(mob.isValid());
    assertEquals(1, plugin.api().activeMobs().size());
    assertEquals(tasks, server.getScheduler().getPendingTasks().size());
    assertEquals(
        IntegrationStatus.State.ABSENT,
        plugin.integrations().statuses().stream()
            .filter(s -> s.plugin().equals("Jobs"))
            .findFirst()
            .orElseThrow()
            .state());
    var player = server.addPlayer();
    player.setOp(true);
    command(player, "connect", "jobs");
    assertTrue(messages(player).stream().anyMatch(s -> s.contains("Neustart ausstehend")));
  }

  @Test
  void aliasesPersistForNextStartupAndCanBeReversed() {
    command(server.getConsoleSender(), "integrations", "JOBS", "aus");
    plugin.reloadPlugin();
    assertEquals(
        IntegrationStatus.State.DISABLED,
        plugin.integrations().statuses().stream()
            .filter(s -> s.plugin().equals("Jobs"))
            .findFirst()
            .orElseThrow()
            .state());
    command(server.getConsoleSender(), "connect", "jobs", "an");
    assertTrue(
        YamlConfiguration.loadConfiguration(config.toFile())
            .getBoolean("integrations.jobs.enabled"));
  }

  @Test
  void malformedYamlIsNeverReplacedByFallbackDefaults() throws Exception {
    String malformed = "integrations: [broken\n";
    Files.writeString(config, malformed);
    command(server.getConsoleSender(), "connect", "jobs", "disable");
    assertEquals(malformed, Files.readString(config));
    assertTrue(plugin.getConfig().getBoolean("integrations.jobs.enabled"));
  }

  @Test
  void wrongParentTypesAndUnknownProvidersCannotOverwriteOtherData() throws Exception {
    String original = "integrations: keep-this-invalid-value\n";
    Files.writeString(config, original);
    command(server.getConsoleSender(), "connect", "quests", "enable");
    command(server.getConsoleSender(), "connect", "../../external", "disable");
    assertEquals(original, Files.readString(config));
    assertFalse(Files.exists(config.resolveSibling("config.yml.connect.bak")));
  }

  @Test
  void completionsRespectViewAndManagePermissions() {
    var player = server.addPlayer();
    var tab = new MobTabCompleter(plugin.mobs(), plugin.configs());
    assertTrue(
        tab.onTabComplete(player, plugin.getCommand("mob"), "mob", new String[] {"connect", ""})
            .isEmpty());
    player.addAttachment(plugin, "themob.integrations.view", true);
    assertTrue(
        tab.onTabComplete(player, plugin.getCommand("mob"), "mob", new String[] {"connect", "j"})
            .contains("jobs"));
    assertEquals(
        List.of("check"),
        tab.onTabComplete(
            player, plugin.getCommand("mob"), "mob", new String[] {"connect", "jobs", ""}));
    player.addAttachment(plugin, "themob.integrations.manage", true);
    assertTrue(
        tab.onTabComplete(
                player, plugin.getCommand("mob"), "mob", new String[] {"connect", "jobs", "d"})
            .contains("disable"));
  }
}
