package org.plugin.theMob.hud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.hud.compass.CompassService;

class NaviVanillaFallbackTest {
  @Test
  void playersWithoutLoadedGraphicsKeepTheirOwnTextCompass() throws Exception {
    var server = MockBukkit.mock();
    try {
      var plugin = MockBukkit.load(TheMob.class);
      plugin.getConfig().set("mob-radar.enabled", false);
      plugin.getConfig().set("navigation-hud.hide-when-idle", false);
      var hudField = TheMob.class.getDeclaredField("hud");
      hudField.setAccessible(true);
      ((NaviHudService) hudField.get(plugin)).shutdown();
      var service = new NaviHudService(plugin, null);
      var field = NaviHudService.class.getDeclaredField("resourceCompass");
      field.setAccessible(true);
      ((CompassService) field.get(service)).close();
      var compass = mock(CompassService.class);
      field.set(service, compass);
      Player vanilla = server.addPlayer(), graphical = server.addPlayer();
      when(compass.personalVisible(vanilla.getUniqueId())).thenReturn(true);
      when(compass.personalVisible(graphical.getUniqueId())).thenReturn(true);
      when(compass.resourceCompass(graphical.getUniqueId())).thenReturn(true);
      var tick = NaviHudService.class.getDeclaredMethod("tick");
      tick.setAccessible(true);
      tick.invoke(service);
      Field barsField = NaviHudService.class.getDeclaredField("hudBars");
      barsField.setAccessible(true);
      @SuppressWarnings("unchecked")
      var bars = (Map<UUID, BossBar>) barsField.get(service);
      assertTrue(bars.containsKey(vanilla.getUniqueId()));
      assertTrue(bars.get(vanilla.getUniqueId()).isVisible());
      assertFalse(bars.get(vanilla.getUniqueId()).getTitle().isBlank());
      assertFalse(
          bars.containsKey(graphical.getUniqueId()),
          "No duplicate text ribbon for a loaded graphical compass");
      when(compass.resourceCompass(vanilla.getUniqueId())).thenReturn(true);
      tick.invoke(service);
      assertFalse(bars.containsKey(vanilla.getUniqueId()), "Fallback is removed after loading");
      when(compass.resourceCompass(vanilla.getUniqueId())).thenReturn(false);
      tick.invoke(service);
      assertTrue(bars.containsKey(vanilla.getUniqueId()), "Discarding the pack restores fallback");
      when(compass.personalVisible(vanilla.getUniqueId())).thenReturn(false);
      tick.invoke(service);
      assertFalse(
          bars.containsKey(vanilla.getUniqueId()),
          "Personal/server navigation switches still apply");
      service.shutdown();
    } finally {
      MockBukkit.unmock();
    }
  }
}
