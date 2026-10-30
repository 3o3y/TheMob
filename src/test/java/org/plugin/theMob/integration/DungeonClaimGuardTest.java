package org.plugin.theMob.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.TheMobDungeonPreCreateEvent;

class DungeonClaimGuardTest {
  ServerMock server;
  TheMob plugin;
  org.bukkit.World world;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  @Test
  void pkUsesStoredPerMarkerRadiusRatherThanAnOldThirtyTwoBlockDefault() {
    var region = new DungeonRegion("pending", world.getUID(), 50, 0, 163, 133, false);
    assertFalse(DungeonClaimGuard.circleOverlaps(region, world.getUID(), .5, .5, 32));
    assertTrue(DungeonClaimGuard.circleOverlaps(region, world.getUID(), .5, .5, 64));
    assertFalse(DungeonClaimGuard.circleOverlaps(region, UUID.randomUUID(), .5, .5, 64));
    assertFalse(DungeonClaimGuard.circleOverlaps(region, world.getUID(), -14, 0, 63));
  }

  @Test
  void installedPkWithMissingPublicMarkerContractBlocksRepeatedBuilds() {
    MockBukkit.createMockPlugin("PKPlugin");
    plugin.reloadPlugin();
    var player = server.addPlayer();
    for (int i = 0; i < 2; i++) {
      var event =
          new TheMobDungeonPreCreateEvent(
              player, new DungeonRegion("pending", world.getUID(), 0, 0, 113, 133, false), "mine");
      server.getPluginManager().callEvent(event);
      assertTrue(event.isCancelled());
    }
  }

  @Test
  void footprintOverlapIsHeightIndependentAndChecksWholeClaimsIncludingThinEdges() {
    var region = new DungeonRegion("test", world.getUID(), -20, -30, 93, 103, false);
    assertTrue(
        DungeonClaimGuard.overlaps(
            region, new Location(world, 92, 200, 102), new Location(world, 92, 201, 102)));
    assertFalse(
        DungeonClaimGuard.overlaps(
            region, new Location(world, 93, 0, 0), new Location(world, 95, 5, 2)));
    assertTrue(
        DungeonClaimGuard.overlaps(
            region, new Location(world, -21, 0, -31), new Location(world, -20, 4, -30)));
    var other = server.addSimpleWorld("other");
    assertFalse(
        DungeonClaimGuard.overlaps(
            region, new Location(other, 0, 0, 0), new Location(other, 1, 1, 1)));
  }

  @Test
  void incompatibleInstalledProtectionFailsClosedAcrossRepeatedAttempts() {
    MockBukkit.createMockPlugin("GriefPrevention");
    plugin.reloadPlugin();
    var player = server.addPlayer();
    for (int i = 0; i < 2; i++) {
      var event =
          new TheMobDungeonPreCreateEvent(
              player, new DungeonRegion("pending", world.getUID(), 0, 0, 113, 133, false), "mine");
      server.getPluginManager().callEvent(event);
      assertTrue(event.isCancelled());
    }
    assertEquals(0, plugin.dungeons().records().size());
  }

  @Test
  void publicVetoPreventsReservationAndPlanningAndConsoleResumeCanBeRepresented() {
    var player = server.addPlayer();
    player.setOp(true);
    player.teleport(new Location(world, 0, 64, 0));
    server
        .getPluginManager()
        .registerEvent(
            TheMobDungeonPreCreateEvent.class,
            new Listener() {},
            EventPriority.HIGH,
            (l, e) -> ((TheMobDungeonPreCreateEvent) e).setCancelled(true),
            plugin);
    plugin.dungeons().create(player, org.plugin.theMob.dungeon.DungeonStyle.MINE);
    assertFalse(plugin.dungeons().busy());
    assertTrue(plugin.dungeons().records().isEmpty());
    var event =
        new TheMobDungeonPreCreateEvent(
            server.getConsoleSender(),
            new DungeonRegion("old", world.getUID(), 0, 0, 113, 133, false),
            "mine");
    assertNull(event.getPlayer());
    assertSame(server.getConsoleSender(), event.getSender());
  }
}
