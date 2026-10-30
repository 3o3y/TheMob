package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

class ApiIntegrationTest {
  ServerMock server;
  TheMob plugin;
  Location location;
  List<Event> seen;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    location = new Location(world, .5, 64, .5);
    plugin = MockBukkit.load(TheMob.class);
    server.getScheduler().performTicks(1);
    seen = new ArrayList<>();
    var listener = new Listener() {};
    for (Class<? extends Event> type :
        List.of(
            TheMobPreSpawnEvent.class,
            TheMobSpawnEvent.class,
            TheMobDeathEvent.class,
            TheMobPhaseChangeEvent.class))
      server
          .getPluginManager()
          .registerEvent(type, listener, EventPriority.MONITOR, (l, e) -> seen.add(e), plugin);
  }

  @AfterEach
  void teardown() {
    MockBukkit.unmock();
  }

  LivingEntity pig() {
    var entity = plugin.api().spawn("pig_lvl_1", location).orElseThrow();
    assertEquals(SpawnReason.API, plugin.api().mob(entity).orElseThrow().spawnReason());
    return entity;
  }

  @Test
  void dungeonQueriesKeepTheServerThreadContract() throws Exception {
    var api = plugin.api();
    CompletableFuture.runAsync(
            () -> {
              assertThrows(IllegalStateException.class, api::dungeons);
              assertThrows(IllegalStateException.class, () -> api.dungeonAt(location));
              assertThrows(IllegalStateException.class, api::supportsDungeonRegions);
            })
        .get(5, TimeUnit.SECONDS);
  }

  EntityDeathEvent death(LivingEntity entity) {
    return new EntityDeathEvent(
        entity,
        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
        new ArrayList<>());
  }

  @Test
  void serviceSurvivesReloadAndReportsMissingProviders() {
    var service = Bukkit.getServicesManager().load(TheMobApi.class);
    assertSame(plugin.api(), service);
    assertEquals(
        8 + org.plugin.theMob.content.MobCatalog.species().size() * 5 + 20,
        service.definitions().size());
    assertEquals(20, service.integrations().size());
    assertTrue(
        service.integrations().stream().allMatch(s -> s.state() == IntegrationStatus.State.ABSENT));
    plugin.reloadPlugin();
    assertSame(service, Bukkit.getServicesManager().load(TheMobApi.class));
    assertEquals(
        8 + org.plugin.theMob.content.MobCatalog.species().size() * 5 + 20,
        service.definitions().size());
  }

  @Test
  void cancelledSpawnsHaveNoEntityOrBudgetForAnySource() {
    server
        .getPluginManager()
        .registerEvent(
            TheMobPreSpawnEvent.class,
            new Listener() {},
            EventPriority.HIGH,
            (l, e) -> ((TheMobPreSpawnEvent) e).setCancelled(true),
            plugin);
    for (String source : Arrays.asList(null, "egg", "spawner", "auto", "api:API"))
      assertNull(plugin.mobs().spawnCustomMob("pig_lvl_1", source, location));
    assertEquals(0, plugin.automation().budgets().aliveTotal());
    assertEquals(5, seen.size());
    assertTrue(seen.stream().allMatch(TheMobPreSpawnEvent.class::isInstance));
  }

  @Test
  void spawnReportsResolvedLocationAndCannotMutateCallersLocation() {
    var original = location.clone();
    var entity = pig();
    assertEquals(original, location);
    var pre = (TheMobPreSpawnEvent) seen.getFirst();
    assertEquals(entity.getLocation(), pre.getLocation());
    pre.getLocation().setY(-100);
    assertEquals(entity.getLocation(), pre.getLocation());
    assertInstanceOf(TheMobSpawnEvent.class, seen.get(1));
    assertEquals(1, plugin.api().activeMobs().size());
  }

  @Test
  void administrativeRemovalAndReloadGiveNoKillProgress() {
    var entity = pig();
    assertTrue(plugin.api().remove(entity.getUniqueId()));
    assertFalse(entity.isValid());
    assertEquals(0, plugin.automation().budgets().aliveTotal());
    assertTrue(plugin.api().activeMobs().isEmpty());
    pig();
    plugin.reloadPlugin();
    assertTrue(plugin.api().activeMobs().isEmpty());
    assertTrue(seen.stream().noneMatch(TheMobDeathEvent.class::isInstance));
  }

  @Test
  void oneNativeDeathProducesExactlyOneApiDeathAndSessionKill() {
    var original = pig();
    LivingEntity entity = spy(original);
    var killer = server.addPlayer();
    doReturn(killer).when(entity).getKiller();
    var event = death(entity);
    server.getPluginManager().callEvent(event);
    server.getPluginManager().callEvent(event);
    assertEquals(1, seen.stream().filter(TheMobDeathEvent.class::isInstance).count());
    assertEquals("1", plugin.integrations().placeholder(killer.getUniqueId(), "kills_session"));
  }

  @Test
  void minionDeathIsVisibleToApiButNotDefaultQuestProgress() {
    var killer = server.addPlayer();
    var entity = spy((LivingEntity) location.getWorld().spawnEntity(location, EntityType.ZOMBIE));
    doReturn(killer).when(entity).getKiller();
    var data = entity.getPersistentDataContainer();
    data.set(new NamespacedKey(plugin, "minion"), PersistentDataType.BYTE, (byte) 1);
    data.set(
        new NamespacedKey(plugin, "api_parent_mob"),
        PersistentDataType.STRING,
        "zombie_boss_template");
    data.set(new NamespacedKey(plugin, "api_source"), PersistentDataType.STRING, "MINION");
    var event = death(entity);
    server.getPluginManager().callEvent(event);
    server.getPluginManager().callEvent(event);
    var snapshot = plugin.api().mob(entity).orElseThrow();
    assertTrue(snapshot.minion());
    assertFalse(snapshot.boss());
    assertEquals(1, seen.stream().filter(TheMobDeathEvent.class::isInstance).count());
    assertEquals("0", plugin.integrations().placeholder(killer.getUniqueId(), "kills_session"));
  }

  @Test
  void npcVictimsDoNotGrantProgressAndAreNotSelectedAsTargets() {
    var entity = pig();
    var npc = server.addPlayer();
    npc.setMetadata("NPC", new FixedMetadataValue(plugin, true));
    var event =
        new EntityTargetLivingEntityEvent(
            entity, npc, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
    server.getPluginManager().callEvent(event);
    assertTrue(event.isCancelled());
    var victim = spy(entity);
    doReturn(npc).when(victim).getKiller();
    server.getPluginManager().callEvent(death(victim));
    assertEquals("0", plugin.integrations().placeholder(npc.getUniqueId(), "kills_session"));
  }

  @Test
  void apiRejectsOffThreadAccessAndInvalidDefinitions() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var failure =
          executor
              .submit(
                  () -> assertThrows(IllegalStateException.class, () -> plugin.api().activeMobs()))
              .get();
      assertTrue(failure.getMessage().contains("server thread"));
    }
    plugin.mobs().mobConfigById("pig_lvl_1").set("base-type", "INVALID_MOB");
    assertTrue(plugin.api().spawn("pig_lvl_1", location).isEmpty());
    assertEquals(
        7 + org.plugin.theMob.content.MobCatalog.species().size() * 5 + 20,
        plugin.api().definitions().size());
    assertTrue(plugin.api().spawn("does_not_exist", location).isEmpty());
  }

  @Test
  void publicApiRejectsNullAndNonFiniteLocationsWithoutSideEffects() {
    assertTrue(plugin.api().spawn(null, location).isEmpty());
    assertTrue(plugin.api().spawn("pig_lvl_1", null).isEmpty());
    assertFalse(plugin.api().remove(null));
    for (double value :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      var invalid = location.clone();
      invalid.setX(value);
      assertTrue(plugin.api().spawn("pig_lvl_1", invalid).isEmpty());
      invalid = location.clone();
      invalid.setY(value);
      assertTrue(plugin.api().spawn("pig_lvl_1", invalid).isEmpty());
      invalid = location.clone();
      invalid.setZ(value);
      assertTrue(plugin.api().spawn("pig_lvl_1", invalid).isEmpty());
    }
    var invalid = location.clone();
    invalid.setYaw(Float.NaN);
    assertTrue(plugin.api().spawn("pig_lvl_1", invalid).isEmpty());
    assertTrue(plugin.api().activeMobs().isEmpty());
    assertTrue(seen.isEmpty());
  }

  @Test
  void incompatibleWorldGuardNeverTurnsIntoAnUnprotectedSpawnBypass() {
    MockBukkit.createMockPlugin("WorldGuard"); // No WorldGuard API in this fixture.
    plugin.reloadPlugin();
    assertTrue(plugin.api().spawn("pig_lvl_1", location).isEmpty());
    assertTrue(plugin.api().spawn("pig_lvl_1", location).isEmpty());
    assertTrue(plugin.api().activeMobs().isEmpty());
    assertEquals(
        IntegrationStatus.State.INCOMPATIBLE,
        plugin.api().integrations().stream()
            .filter(s -> s.plugin().equals("WorldGuard"))
            .findFirst()
            .orElseThrow()
            .state());
  }
}
