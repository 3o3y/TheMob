package org.plugin.theMob.hud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;

class RadarCacheTest {
  @Test
  void sharesChunkEnumerationWithinOneHudFrameAndRefreshesNextFrame() {
    var server = MockBukkit.mock();
    try {
      var world = server.addSimpleWorld("world");
      var plugin = MockBukkit.load(TheMob.class);
      var mob = (LivingEntity) world.spawnEntity(new Location(world, 1, 64, 1), EntityType.COW);
      var actual = server.addPlayer();
      actual.teleport(new Location(world, 2, 64, 2));
      var player = spy(actual);
      var adapter = spy((World) world);
      var chunk = spy(world.getChunkAt(0, 0));
      doReturn(adapter).when(player).getWorld();
      doAnswer(call -> (int) call.getArgument(0) == 0 && (int) call.getArgument(1) == 0)
          .when(adapter)
          .isChunkLoaded(anyInt(), anyInt());
      doReturn(chunk).when(adapter).getChunkAt(0, 0);
      var radar = new MobRadarResolver(plugin.mobs(), true, 20, true, true, false);
      radar.beginFrame();
      assertNotNull(radar.find(player));
      assertNotNull(radar.find(player));
      radar.endFrame();
      verify(chunk, times(1)).getEntities();
      mob.remove();
      radar.beginFrame();
      assertNull(radar.find(player));
      radar.endFrame();
      verify(chunk, times(2)).getEntities();
    } finally {
      MockBukkit.unmock();
    }
  }
}
