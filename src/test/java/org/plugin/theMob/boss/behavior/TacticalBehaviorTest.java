package org.plugin.theMob.boss.behavior;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.core.*;

class TacticalBehaviorTest {
  ServerMock server;
  TheMob plugin;
  World world;
  Mob boss;
  Player player;
  BossPhase phase;
  TacticalBehavior behavior;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    boss = spy((Mob) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.PIG));
    doReturn(true).when(boss).isAware();
    doNothing().when(boss).setAware(anyBoolean());
    doReturn(true).when(boss).hasLineOfSight(any(Entity.class));
    player = server.addPlayer();
    player.teleport(new Location(world, 2, 64, 0));
    plugin.activity().refreshPlayers();
    var config = new YamlConfiguration();
    config.set("behavior.attack-damage", 3);
    config.set("behavior.windup-ticks", 20);
    config.set("behavior.cooldown-ticks", 100);
    phase = new BossPhase("test", 0, 100, "Test", config);
    behavior = new TacticalBehavior(plugin, "bruiser");
    behavior.onEnter(boss, phase);
  }

  @AfterEach
  void close() {
    behavior.onExit(boss, phase);
    MockBukkit.unmock();
  }

  @Test
  void attackWaitsForWindupAndHasCooldown() {
    server.getScheduler().performTicks(40);
    behavior.tick(boss, phase);
    assertEquals(20, player.getHealth());
    server.getScheduler().performTicks(10);
    behavior.tick(boss, phase);
    assertEquals(20, player.getHealth());
    server.getScheduler().performTicks(10);
    behavior.tick(boss, phase);
    assertTrue(player.getHealth() < 20);
    double health = player.getHealth();
    behavior.tick(boss, phase);
    assertEquals(health, player.getHealth());
  }

  @Test
  void attackCanHitPetWhileOnlyPlayerIsCreativeAdmin() {
    player.setGameMode(GameMode.CREATIVE);
    player.setOp(true);
    plugin.activity().refreshPlayers();
    var pet = (LivingEntity) world.spawnEntity(new Location(world, 2, 64, 0), EntityType.COW);
    pet.getPersistentDataContainer()
        .set(
            new NamespacedKey("cosmetics", "pet"),
            org.bukkit.persistence.PersistentDataType.BYTE,
            (byte) 1);
    double health = pet.getHealth();
    server.getScheduler().performTicks(40);
    behavior.tick(boss, phase);
    assertSame(pet, boss.getTarget());
    assertEquals(health, pet.getHealth());
    server.getScheduler().performTicks(20);
    behavior.tick(boss, phase);
    assertTrue(pet.getHealth() < health);
    assertEquals(20, player.getHealth());
  }

  @Test
  void dodgingAndColdSuspendCancelDamageWithoutBurstOnResume() {
    server.getScheduler().performTicks(40);
    behavior.tick(boss, phase);
    player.teleport(new Location(world, 10, 64, 0));
    plugin.activity().refreshPlayers();
    server.getScheduler().performTicks(20);
    behavior.tick(boss, phase);
    assertEquals(20, player.getHealth());
    player.teleport(new Location(world, 2, 64, 0));
    plugin.activity().refreshPlayers();
    server.getScheduler().performTicks(100);
    behavior.tick(boss, phase);
    behavior.onSuspend(boss, phase);
    server.getScheduler().performTicks(20);
    behavior.tick(boss, phase);
    assertEquals(20, player.getHealth());
  }

  @Test
  void noTargetOutsideLeashAndAnchorSurvivesPhaseChange() {
    boss.teleport(new Location(world, 60, 64, 0));
    player.teleport(new Location(world, 62, 64, 0));
    plugin.activity().refreshPlayers();
    behavior.tick(boss, phase);
    assertNull(boss.getTarget());
    behavior.onExit(boss, phase);
    behavior.onEnter(boss, phase);
    behavior.tick(boss, phase);
    assertNull(boss.getTarget());
    assertEquals(
        0,
        boss.getPersistentDataContainer()
            .get(
                new NamespacedKey(plugin, "boss_home_x"),
                org.bukkit.persistence.PersistentDataType.DOUBLE));
  }
}
