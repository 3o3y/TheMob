package org.plugin.theMob.hud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.mob.MobManager;

class CosmeticRadarTest {
  @Test
  void markedPetsDisappearFromCachedRadarWhileWildMobsRemain() {
    var world = mock(World.class);
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(world.isChunkLoaded(anyInt(), anyInt()))
        .thenAnswer(c -> (int) c.getArgument(0) == 0 && (int) c.getArgument(1) == 0);
    var chunk = mock(Chunk.class);
    when(world.getChunkAt(0, 0)).thenReturn(chunk);
    var player = mock(Player.class);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    var pet = mock(Cow.class);
    var wild = mock(Cow.class);
    var marked = new java.util.concurrent.atomic.AtomicBoolean(false);
    for (var entity : List.of(pet, wild)) {
      when(entity.isValid()).thenReturn(true);
      var pdc = mock(PersistentDataContainer.class);
      when(entity.getPersistentDataContainer()).thenReturn(pdc);
      if (entity == pet)
        when(pdc.has(new NamespacedKey("cosmetics", "visual"), PersistentDataType.BYTE))
            .thenAnswer(c -> marked.get());
    }
    when(pet.getLocation()).thenReturn(new Location(world, 1, 64, 0));
    when(wild.getLocation()).thenReturn(new Location(world, 3, 64, 0));
    when(chunk.getEntities()).thenReturn(new Entity[] {pet, wild});
    var radar = new MobRadarResolver(mock(MobManager.class), true, 20, false, false, false);
    radar.beginFrame();
    assertSame(pet, radar.find(player).entity());
    marked.set(true);
    assertSame(wild, radar.find(player).entity());
    verify(chunk, times(1)).getEntities();
    radar.endFrame();
  }

  @Test
  void cosmeticBossesAreNeverRegisteredAndAlreadyMarkedOnesArePruned() throws Exception {
    try (var bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
      var mobs = mock(MobManager.class);
      var pet = mock(LivingEntity.class);
      var pdc = mock(PersistentDataContainer.class);
      when(pet.getPersistentDataContainer()).thenReturn(pdc);
      when(pet.getUniqueId()).thenReturn(UUID.randomUUID());
      when(pet.isValid()).thenReturn(true);
      when(mobs.isBoss(pet)).thenReturn(true);
      var bars = new BossBarService(mock(TheMob.class), mobs, mock(PlayerBarCoordinator.class));
      var field = BossBarService.class.getDeclaredField("bosses");
      field.setAccessible(true);
      when(pdc.has(new NamespacedKey("cosmetics", "visual"), PersistentDataType.BYTE))
          .thenReturn(true);
      bars.registerBoss(pet);
      assertTrue(((Map<?, ?>) field.get(bars)).isEmpty());
      when(pdc.has(new NamespacedKey("cosmetics", "visual"), PersistentDataType.BYTE))
          .thenReturn(false);
      bars.registerBoss(pet);
      assertEquals(1, ((Map<?, ?>) field.get(bars)).size());
      when(pdc.has(new NamespacedKey("cosmetics", "visual"), PersistentDataType.BYTE))
          .thenReturn(true);
      var tick = BossBarService.class.getDeclaredMethod("tick");
      tick.setAccessible(true);
      tick.invoke(bars);
      assertTrue(((Map<?, ?>) field.get(bars)).isEmpty());
    }
  }
}
