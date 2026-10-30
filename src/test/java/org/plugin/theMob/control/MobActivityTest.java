package org.plugin.theMob.control;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;

class MobActivityTest {
  ServerMock server;
  TheMob plugin;
  World world;
  MobActivityService activity;

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    plugin.activity().close();
    plugin.getConfig().set("performance.activity.hot-radius", 16);
    plugin.getConfig().set("performance.activity.cold-radius", 32);
    plugin.getConfig().set("performance.activity.cold-grace-seconds", 0);
    activity = new MobActivityService(plugin);
    var field = TheMob.class.getDeclaredField("activity");
    field.setAccessible(true);
    field.set(plugin, activity);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  Mob mob(double x, boolean ai) {
    var mob = spy((Mob) world.spawnEntity(new Location(world, x, 64, 0), EntityType.PIG));
    var enabled = new AtomicBoolean(ai);
    var aware = new AtomicBoolean(ai);
    doAnswer(call -> enabled.get()).when(mob).hasAI();
    doAnswer(
            call -> {
              enabled.set(call.getArgument(0));
              return null;
            })
        .when(mob)
        .setAI(anyBoolean());
    doAnswer(call -> aware.get()).when(mob).isAware();
    doAnswer(
            call -> {
              aware.set(call.getArgument(0));
              return null;
            })
        .when(mob)
        .setAware(anyBoolean());
    mob.getPersistentDataContainer()
        .set(plugin.keys().MOB_ID, PersistentDataType.STRING, "pig_lvl_1");
    plugin.mobs().track(mob);
    return mob;
  }

  @Test
  void coldAiSleepsAndHotBoundaryUsesHysteresis() {
    var mob = mob(0, true);
    activity.tick();
    assertFalse(mob.hasAI());
    assertEquals(1, activity.coldCount());
    var player = server.addPlayer();
    player.teleport(new Location(world, 24, 64, 0));
    activity.refreshPlayers();
    activity.tick();
    assertFalse(mob.hasAI());
    player.teleport(new Location(world, 15, 64, 0));
    activity.refreshPlayers();
    activity.tick();
    assertTrue(mob.hasAI());
    player.teleport(new Location(world, 24, 64, 0));
    activity.refreshPlayers();
    activity.tick();
    assertTrue(mob.hasAI());
    player.teleport(new Location(world, 33, 64, 0));
    activity.refreshPlayers();
    activity.tick();
    assertFalse(mob.hasAI());
  }

  @Test
  void coldPreservesOriginalNoAiAndCombatWakesImmediately() {
    var originallyDisabled = mob(0, false);
    var active = mob(4, true);
    activity.tick();
    assertFalse(active.hasAI());
    activity.engage(active);
    assertTrue(active.hasAI());
    assertTrue(activity.active(active));
    var player = server.addPlayer();
    player.teleport(new Location(world, 0, 64, 0));
    activity.refreshPlayers();
    activity.tick();
    assertFalse(originallyDisabled.hasAI());
    assertFalse(
        originallyDisabled
            .getPersistentDataContainer()
            .has(new NamespacedKey(plugin, "activity_original_ai"), PersistentDataType.BYTE));
  }

  @Test
  void processesOnlyBudgetedEntitiesAndRepeatedColdUpdatesDoNotWriteAi() {
    var mobs = new ArrayList<Mob>();
    for (int i = 0; i < 70; i++) mobs.add(mob(i, true));
    activity.tick();
    assertEquals(64, activity.coldCount());
    activity.tick();
    assertEquals(70, activity.coldCount());
    activity.tick();
    for (var mob : mobs) verify(mob, times(1)).setAI(false);
  }

  @Test
  void npcSpectatorAndCreativeAreNeverBossTargets() {
    var mob = mob(0, true);
    var player = server.addPlayer();
    player.teleport(new Location(world, 1, 64, 0));
    for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
      player.setGameMode(mode);
      activity.refreshPlayers();
      assertNull(activity.nearest(mob, 20));
    }
    player.setGameMode(GameMode.SURVIVAL);
    player.setMetadata("NPC", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    activity.refreshPlayers();
    assertNull(activity.nearest(mob, 20));
    player.removeMetadata("NPC", plugin);
    activity.refreshPlayers();
    assertSame(player, activity.nearest(mob, 20));
  }

  @Test
  void detachedPetWakesMobEvenWithoutEligiblePlayerAndIsBossTarget() {
    var mob = mob(0, true);
    activity.tick();
    assertFalse(mob.hasAI());
    var owner = server.addPlayer();
    owner.setOp(true);
    owner.setGameMode(GameMode.SPECTATOR);
    owner.teleport(new Location(world, 1000, 64, 0));
    activity.refreshPlayers();
    var pet = (LivingEntity) world.spawnEntity(new Location(world, 2, 64, 0), EntityType.COW);
    pet.getPersistentDataContainer()
        .set(new NamespacedKey("cosmetics", "pet"), PersistentDataType.BYTE, (byte) 1);
    pet.getPersistentDataContainer()
        .set(new NamespacedKey("cosmetics", "visual"), PersistentDataType.BYTE, (byte) 1);
    activity.tick();
    assertTrue(mob.hasAI());
    assertTrue(activity.active(mob));
    assertNull(activity.nearest(mob, 20));
    assertSame(pet, org.plugin.theMob.boss.behavior.BossTargets.combatNearest(mob, 20));
    pet.remove();
    activity.tick();
    assertFalse(mob.hasAI());
  }

  @Test
  void unloadRetrackDiscardsOldQueueGenerationAndApiUsesTrackedEntities() {
    var mob = mob(0, true);
    plugin.mobs().untrack(mob.getUniqueId());
    plugin.mobs().track(mob);
    activity.tick();
    activity.tick();
    assertEquals(1, activity.trackedCount());
    verify(mob, times(1)).setAI(false);
    assertEquals(1, plugin.api().activeMobs().size());
    server
        .getPluginManager()
        .callEvent(new EntitiesUnloadEvent(world.getChunkAt(0, 0), List.of(mob)));
    assertTrue(plugin.api().activeMobs().isEmpty());
  }

  @Test
  void apiQueriesNeverEnumerateWorldEntities() {
    mob(0, true);
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit
          .when(Bukkit::getWorlds)
          .thenThrow(new AssertionError("Global entity scan in hot path"));
      assertEquals(1, plugin.mobs().getAllLivingMobs().size());
      assertEquals(1, plugin.api().activeMobs().size());
      activity.tick();
    }
  }

  @Test
  void coldArenaEpochRemovesStaleLoadedEntitiesWithoutAwardingKills() {
    var auto = plugin.mobs().getAutoSpawnManager();
    var sp =
        new org.plugin.theMob.spawn.SpawnPoint(
            org.plugin.theMob.spawn.type.SpawnType.FIXED_POINT,
            "pig_lvl_1",
            "world",
            0,
            64,
            0,
            10,
            2,
            true,
            2,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    auto.register(sp);
    var mob = mob(0, true);
    mob.getPersistentDataContainer()
        .set(plugin.keys().AUTO_SPAWN_ID, PersistentDataType.STRING, sp.spawnId());
    mob.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "arena_epoch"), PersistentDataType.LONG, 0L);
    mob.getPersistentDataContainer()
        .set(
            new NamespacedKey(plugin, "spawn_session"),
            PersistentDataType.STRING,
            plugin.runtimeSession());
    plugin.mobs().untrack(mob.getUniqueId()); // Represents a mob in an unloaded chunk.
    auto.unregister(sp.spawnId());
    assertFalse(auto.currentEpoch(mob));
    server
        .getPluginManager()
        .callEvent(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.of(mob)));
    assertFalse(mob.isValid());
    assertTrue(plugin.api().activeMobs().isEmpty());
  }

  @Test
  void arenaTicketsDoNotLoadAnyMissingChunks() throws Exception {
    var auto = plugin.mobs().getAutoSpawnManager();
    var adapter = spy(world);
    doAnswer(call -> (int) call.getArgument(0) == 0 && (int) call.getArgument(1) == 0)
        .when(adapter)
        .isChunkLoaded(anyInt(), anyInt());
    var tickets = mock(org.plugin.theMob.world.ChunkTicketTracker.class);
    var field = auto.getClass().getDeclaredField("chunkTickets");
    field.setAccessible(true);
    field.set(auto, tickets);
    var sp =
        new org.plugin.theMob.spawn.SpawnPoint(
            org.plugin.theMob.spawn.type.SpawnType.FIXED_POINT,
            "pig_lvl_1",
            "world",
            0,
            64,
            0,
            10,
            2,
            true,
            20,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(adapter);
      var method =
          auto.getClass()
              .getDeclaredMethod("forceLoadArenaChunks", org.plugin.theMob.spawn.SpawnPoint.class);
      method.setAccessible(true);
      method.invoke(auto, sp);
    }
    verify(adapter, times(1)).getChunkAt(anyInt(), anyInt());
    verify(adapter).getChunkAt(0, 0);
    verify(tickets, times(1)).add(any(Chunk.class));
  }
}
