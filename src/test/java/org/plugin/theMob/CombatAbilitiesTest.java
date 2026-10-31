package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.boss.*;
import org.plugin.theMob.combat.PlayerHealing;
import org.plugin.theMob.core.CombatAbilities;

class CombatAbilitiesTest {
  ServerMock server;
  TheMob plugin;
  Path file;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    file = plugin.getDataFolder().toPath().resolve("abilities.yml");
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  void startupPolicy(String key, boolean enabled) throws Exception {
    var yaml = new YamlConfiguration();
    yaml.load(file.toFile());
    yaml.set(key, enabled);
    yaml.save(file.toFile());
    var field = TheMob.class.getDeclaredField("abilities");
    field.setAccessible(true);
    field.set(plugin, new CombatAbilities(plugin));
  }

  @Test
  void adminSwitchIsBackedUpAndStagedWithoutResettingLiveApiOrPlayerData() throws Exception {
    var player = server.addPlayer();
    byte[] old = Files.readAllBytes(file);
    var service = plugin.api();
    server.dispatchCommand(player, "mob abilities items.poison aus");
    assertArrayEquals(old, Files.readAllBytes(file));
    player.setOp(true);
    server.dispatchCommand(player, "mob abilities items.poison aus");
    assertArrayEquals(old, Files.readAllBytes(file.resolveSibling("abilities.yml.bak")));
    assertTrue(plugin.abilities().enabled("items.poison"));
    assertFalse(YamlConfiguration.loadConfiguration(file.toFile()).getBoolean("items.poison"));
    plugin.reloadPlugin();
    assertSame(service, plugin.api());
    assertTrue(plugin.abilities().enabled("items.poison"));
    startupPolicy("items.poison", false);
    assertFalse(plugin.abilities().enabled("items.poison"));
    assertTrue(plugin.abilities().enabled("items.ignite"));
  }

  @Test
  void globalGroupAndIndividualSwitchesFilterOnlyOwnedProcs() throws Exception {
    var stats =
        Map.of(
            "damage",
            25.0,
            "poison_chance",
            1.0,
            "lifesteal",
            20.0,
            "crit_chance",
            70.0,
            "movement_speed",
            .1);
    startupPolicy("items.enabled", false);
    var filtered = plugin.abilities().itemEffects(stats);
    assertEquals(Map.of("damage", 25.0, "movement_speed", .1), filtered);
    assertEquals(5, stats.size());
    startupPolicy("items.enabled", true);
    startupPolicy("items.poison", false);
    assertFalse(plugin.abilities().itemEffects(stats).containsKey("poison_chance"));
    assertEquals(20, plugin.abilities().itemEffects(stats).get("lifesteal"));
    startupPolicy("enabled", false);
    assertFalse(plugin.abilities().enabled("bosses.zombie_king"));
    assertFalse(plugin.abilities().enabled("items.execute"));
  }

  @Test
  void malformedSettingsFailClosedAndCannotBeOverwrittenByCommand() throws Exception {
    Files.writeString(file, "items: false\n");
    byte[] original = Files.readAllBytes(file);
    var policy = new CombatAbilities(plugin);
    assertFalse(policy.enabled("items.poison"));
    assertFalse(policy.enabled("bosses.zombie_king"));
    server.dispatchCommand(server.getConsoleSender(), "mob abilities items.poison an");
    assertArrayEquals(original, Files.readAllBytes(file));
  }

  @Test
  void disabledBossSkillsKeepPhaseStateButApplyNoBuffsImmunityOrMinions() throws Exception {
    startupPolicy("bosses.enabled", false);
    Mob boss =
        spy(
            (Mob)
                server
                    .getWorld("world")
                    .spawnEntity(new Location(server.getWorld("world"), 0, 64, 0), EntityType.PIG));
    doNothing().when(boss).setAware(anyBoolean());
    doReturn(true).when(boss).isAware();
    var cfg = new YamlConfiguration();
    cfg.set("behavior.mode", "zombie_king");
    cfg.set("buffs.max-health", 100);
    cfg.set("abilities.invulnerable", true);
    cfg.set("abilities.effects.regeneration", 3);
    cfg.set("actions.summon-minions.enabled", true);
    cfg.set("actions.summon-minions.amount", 3);
    var phase = new BossPhase("phase1", 0, 100, "Boss", cfg);
    var template = new BossTemplate("testboss", 3);
    template.addPhase(phase, cfg);
    double before = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
    plugin.bossPhases().onBossSpawn(boss, template);
    assertEquals("phase1", plugin.bossPhases().currentPhase(boss).id());
    assertFalse(boss.isInvulnerable());
    assertFalse(boss.hasMetadata("themob_zombie_king"));
    assertEquals(before, boss.getAttribute(Attribute.MAX_HEALTH).getValue());
    assertTrue(boss.getActivePotionEffects().isEmpty());
    assertEquals(0, plugin.automation().budgets().aliveMinions());
  }

  @Test
  void disabledPoisonDoesNotApplyWhileOtherProcsRemainAvailable() throws Exception {
    startupPolicy("items.poison", false);
    var player = server.addPlayer();
    var target =
        (LivingEntity)
            player.getWorld().spawnEntity(player.getLocation().add(2, 0, 0), EntityType.PIG);
    var procs = new org.plugin.theMob.item.CustomEnchantSystem(plugin);
    procs.trigger(
        player,
        target,
        Map.of("poison_chance", 1.0, "ignite_chance", 1.0, "ignite_seconds", 999999.0),
        2);
    assertFalse(target.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON));
    assertEquals(600, target.getFireTicks());
  }

  @Test
  void healingRunsThroughProviderModifiersAndCancellationWithoutExceedingMaximum() {
    var player = server.addPlayer();
    player.setHealth(10);
    var listener = new Listener() {};
    server
        .getPluginManager()
        .registerEvent(
            EntityRegainHealthEvent.class,
            listener,
            EventPriority.HIGH,
            (l, e) ->
                ((EntityRegainHealthEvent) e)
                    .setAmount(((EntityRegainHealthEvent) e).getAmount() * 1.5),
            plugin);
    assertEquals(3, PlayerHealing.heal(player, 2));
    assertEquals(13, player.getHealth());
    server
        .getPluginManager()
        .registerEvent(
            EntityRegainHealthEvent.class,
            listener,
            EventPriority.HIGHEST,
            (l, e) -> ((EntityRegainHealthEvent) e).setCancelled(true),
            plugin);
    assertEquals(0, PlayerHealing.heal(player, 2));
    assertEquals(13, player.getHealth());
  }
}
