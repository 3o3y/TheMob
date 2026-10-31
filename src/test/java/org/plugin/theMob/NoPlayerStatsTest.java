package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.plugin.theMob.core.CombatAbilities;

class NoPlayerStatsTest {
  private ServerMock server;
  private TheMob plugin;

  @BeforeEach
  void start() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
  }

  @AfterEach
  void stop() {
    MockBukkit.unmock();
  }

  @Test
  void playerStatsResourcesCommandsAndPermissionsAreGone() throws Exception {
    assertNull(plugin.getCommand("stats"));
    assertNull(plugin.getResource("stats.yml"));
    assertNull(plugin.getResource("skills.yml"));
    assertNotNull(plugin.getResource("abilities.yml"));
    assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve("stats.yml")));
    assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve("skills.yml")));
    assertFalse(plugin.getDescription().getSoftDepend().contains("SkilltreePlugin"));
    assertTrue(
        plugin.getDescription().getPermissions().stream()
            .noneMatch(
                p ->
                    p.getName().equals("themob.stats")
                        || p.getName().equals("themob.skills.manage")));
    for (String removed :
        new String[] {
          "org.plugin.theMob.command.StatsCommand",
          "org.plugin.theMob.player.stats.menu.StatsMenuService",
          "org.plugin.theMob.player.stats.PlayerStatCache",
          "org.plugin.theMob.progression.PlayerProgressionManager",
          "org.plugin.theMob.core.TheMobSkills"
        }) assertThrows(ClassNotFoundException.class, () -> Class.forName(removed));
    var player = server.addPlayer();
    player.setOp(true);
    var completions = plugin.getCommand("mob").tabComplete(player, "mob", new String[] {""});
    assertFalse(completions.contains("skills"));
    assertTrue(completions.contains("abilities"));
  }

  @Test
  void otherPluginsCanOwnStatsAndSkillsCommandsAcrossReload() {
    for (String name : new String[] {"stats", "skills"}) {
      server
          .getCommandMap()
          .register(
              "external",
              new Command(name) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                  sender.sendMessage("external-" + name);
                  return true;
                }
              });
    }
    plugin.reloadPlugin();
    var player = server.addPlayer();
    assertTrue(server.dispatchCommand(player, "stats"));
    assertEquals("external-stats", player.nextMessage());
    assertTrue(server.dispatchCommand(player, "skills"));
    assertEquals("external-skills", player.nextMessage());
  }

  @Test
  void retiredStatsFileAndProgressionSettingsAreNotReadOrChanged() throws Exception {
    Path file = plugin.getDataFolder().toPath().resolve("stats.yml");
    byte[] old =
        "menu: [ deliberately invalid legacy YAML\n"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(file, old);
    plugin.getConfig().set("progression.enabled", true);
    plugin.saveConfig();
    plugin.reloadPlugin();
    assertTrue(plugin.isEnabled());
    assertArrayEquals(old, Files.readAllBytes(file));
    assertNull(plugin.getCommand("stats"));
    assertTrue(plugin.abilities().enabled("items.lifesteal"));
    assertTrue(plugin.abilities().enabled("bosses.zombie_king"));
  }

  @Test
  void oldCombatSwitchesMigrateWithoutResettingBossesOrItemEffects() throws Exception {
    Path current = plugin.getDataFolder().toPath().resolve("abilities.yml");
    Path legacy = current.resolveSibling("skills.yml");
    byte[] old =
        "# Existing server choices\nbosses:\n  zombie-stomp: false\nitems:\n  poison: false\n  lifesteal: true\n"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(legacy, old);
    Files.delete(current);
    var policy = new CombatAbilities(plugin);
    assertArrayEquals(old, Files.readAllBytes(legacy));
    assertArrayEquals(old, Files.readAllBytes(current));
    assertFalse(policy.enabled("bosses.zombie-stomp"));
    assertFalse(policy.enabled("items.poison"));
    assertTrue(policy.enabled("bosses.zombie-cleave"));
    assertTrue(policy.enabled("items.lifesteal"));
    assertEquals(
        Map.of("damage", 40.0, "lifesteal", 12.0),
        policy.itemEffects(Map.of("damage", 40.0, "lifesteal", 12.0, "poison_chance", 1.0)));
  }

  @Test
  void invalidLegacySwitchesFailClosedAndAreNeverOverwritten() throws Exception {
    Path current = plugin.getDataFolder().toPath().resolve("abilities.yml");
    Path legacy = current.resolveSibling("skills.yml");
    byte[] old = "items: false\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(legacy, old);
    Files.delete(current);
    var policy = new CombatAbilities(plugin);
    assertFalse(policy.enabled("bosses.zombie_king"));
    assertFalse(policy.enabled("items.poison"));
    assertFalse(Files.exists(current));
    assertArrayEquals(old, Files.readAllBytes(legacy));
  }

  @Test
  void currentAbilitySettingsTakePrecedenceOverTheRetiredFile() throws Exception {
    Path current = plugin.getDataFolder().toPath().resolve("abilities.yml");
    byte[] original = Files.readAllBytes(current);
    Path legacy = current.resolveSibling("skills.yml");
    Files.writeString(legacy, "enabled: false\n");
    var policy = new CombatAbilities(plugin);
    assertTrue(policy.enabled("items.lifesteal"));
    assertTrue(policy.enabled("bosses.zombie_king"));
    assertArrayEquals(original, Files.readAllBytes(current));
    assertEquals("enabled: false\n", Files.readString(legacy));
  }
}
