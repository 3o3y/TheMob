package org.plugin.theMob.dungeon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.dungeon.*;
import org.plugin.theMob.api.event.TheMobDeathEvent;

class DungeonCompletionTest {
  ServerMock server;
  TheMob plugin;
  World world;
  DungeonRecord record;
  Player first, second, outside, spectator, npc;
  LivingEntity boss;
  List<DungeonCompletedEvent> completed = new ArrayList<>();

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    var store = new DungeonStore(plugin.getDataFolder().toPath().resolve("dungeons.yml"));
    record = store.reserve(DungeonStyle.GOD_TOWER, 5, world, 100, 64, 100);
    record.state = DungeonRecord.State.READY;
    record.levels = 10;
    record.completionBossId = "zombie_boss_template";
    store.save();
    plugin.reloadPlugin();
    first = player(130, 130);
    second = player(180, 180);
    outside = player(0, 0);
    spectator = player(150, 150);
    spectator.setGameMode(GameMode.SPECTATOR);
    npc = player(150, 150);
    npc.setMetadata("NPC", new FixedMetadataValue(plugin, true));
    boss = world.spawn(new Location(world, 150, 73, 150), Zombie.class);
    server
        .getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void finish(DungeonCompletedEvent e) {
                completed.add(e);
              }
            },
            plugin);
  }

  Player player(int x, int z) {
    var p = server.addPlayer();
    p.teleport(new Location(world, x + .5, 73, z + .5));
    return p;
  }

  TheMobDeathEvent death(String id, boolean isBoss, boolean minion, Player killer) {
    return new TheMobDeathEvent(
        boss,
        new MobSnapshot(boss.getUniqueId(), id, "Boss", isBoss, minion, "rage", SpawnReason.MANUAL),
        killer);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  @Test
  void assignedEndbossCompletesForAllEligiblePresentPlayersExactlyOnce() {
    var event = death(record.completionBossId, true, false, first);
    server.getPluginManager().callEvent(event);
    assertEquals(1, completed.size());
    assertEquals(Set.of(first, second), Set.copyOf(completed.getFirst().getParticipants()));
    assertEquals("gottesturm", completed.getFirst().getStyle());
    assertEquals(10, completed.getFirst().getLevels());
    assertEquals(boss.getUniqueId(), completed.getFirst().getRunId());
    server.getPluginManager().callEvent(event);
    assertEquals(1, completed.size());
    assertTrue(plugin.dungeons().get(record.id).completedRuns.contains(boss.getUniqueId()));
  }

  @Test
  void otherMobsMinionsEnvironmentAndOutsideKillsDoNotCompleteADungeon() {
    for (var event :
        List.of(
            death("other", true, false, first),
            death(record.completionBossId, false, false, first),
            death(record.completionBossId, true, true, first),
            death(record.completionBossId, true, false, null),
            death(record.completionBossId, true, false, outside),
            death(record.completionBossId, true, false, npc)))
      server.getPluginManager().callEvent(event);
    boss.teleport(new Location(world, 0, 73, 0));
    server.getPluginManager().callEvent(death(record.completionBossId, true, false, first));
    assertTrue(completed.isEmpty());
  }

  @Test
  void completionApiIsStableAcrossReloadAndPersistentlyRejectsReplayedRunIds() throws Exception {
    var api = server.getServicesManager().load(TheMobDungeonApi.class);
    assertNotNull(api);
    UUID run = UUID.randomUUID();
    assertTrue(
        api.complete(
            record.id,
            run,
            List.of(first.getUniqueId(), first.getUniqueId(), outside.getUniqueId())));
    assertEquals(List.of(first), completed.getFirst().getParticipants());
    plugin.reloadPlugin();
    assertSame(api, server.getServicesManager().load(TheMobDungeonApi.class));
    assertFalse(api.complete(record.id, run, List.of(first.getUniqueId())));
    assertThrows(
        java.util.concurrent.CompletionException.class,
        () ->
            java.util.concurrent.CompletableFuture.runAsync(() -> api.at(first.getLocation()))
                .join());
  }

  @Test
  void bossAssignmentIsAdminOnlyValidatedAndSurvivesReload() {
    plugin.dungeons().setBoss(first, record.id, "off");
    assertEquals(record.completionBossId, plugin.dungeons().get(record.id).completionBossId);
    first.setOp(true);
    plugin.dungeons().setBoss(first, record.id, "pig_lvl_1");
    assertEquals(record.completionBossId, plugin.dungeons().get(record.id).completionBossId);
    plugin.dungeons().setBoss(first, record.id, "tm_boss_guardian_lvl_5");
    plugin.reloadPlugin();
    assertEquals("tm_boss_guardian_lvl_5", plugin.dungeons().get(record.id).completionBossId);
    plugin.dungeons().setBoss(first, record.id, "off");
    assertEquals("", plugin.dungeons().get(record.id).completionBossId);
  }

  @Test
  void queryAndClaimIntersectionUsesTheExistingPublicDungeonRegionContract() {
    var api = server.getServicesManager().load(TheMobDungeonApi.class);
    assertEquals(plugin.api().dungeonAt(first.getLocation()), api.at(first.getLocation()));
    assertTrue(api.blocksClaim(new Location(world, 90, 73, 150), 10));
    assertFalse(api.blocksClaim(new Location(world, 89, 73, 150), 10));
    assertTrue(api.at(new Location(world, 100, -50, 100)).isPresent());
    assertTrue(api.at(new Location(world, 213, 73, 150)).isEmpty());
    assertThrows(
        IllegalArgumentException.class, () -> api.blocksClaim(first.getLocation(), Double.NaN));
  }
}
