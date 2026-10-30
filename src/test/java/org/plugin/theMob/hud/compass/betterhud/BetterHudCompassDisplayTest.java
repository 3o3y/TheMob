package org.plugin.theMob.hud.compass.betterhud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.toxicity.hud.api.*;
import kr.toxicity.hud.api.compass.Compass;
import kr.toxicity.hud.api.configuration.*;
import kr.toxicity.hud.api.hud.Hud;
import kr.toxicity.hud.api.player.HudPlayer;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.hud.compass.CompassService;

class BetterHudCompassDisplayTest {
  @Test
  void bridgeKeepsOneCompassAndArrowAcrossRefreshVisibilityAndTheMobReload() throws Exception {
    var server = MockBukkit.mock();
    try {
      var plugin = MockBukkit.load(TheMob.class);
      var rendererPlugin = MockBukkit.createMockPlugin("BetterHud");
      java.nio.file.Files.createDirectories(rendererPlugin.getDataFolder().toPath());
      java.nio.file.Files.writeString(
          rendererPlugin.getDataFolder().toPath().resolve("config.yml"),
          "bossbar-line: 1\nmerge-boss-bar: true\n");
      var renderer = mock(BetterHud.class, RETURNS_DEEP_STUBS);
      BetterHudAPI.inst(renderer);
      var owner = mock(CompassService.class);
      when(owner.plugin()).thenReturn(plugin);
      UUID id = UUID.randomUUID();
      var visible = new AtomicBoolean(true);
      when(owner.visible(id)).thenAnswer(i -> visible.get());
      HudPlayer player = mock(HudPlayer.class);
      when(player.uuid()).thenReturn(id);
      Map<HudObject.Identifier, HudComponentSupplier<?>> objects = new HashMap<>();
      when(player.getHudObjects()).thenReturn(objects);
      when(renderer.getPlayerManager().getHudPlayer(id)).thenReturn(player);
      when(renderer.getPlayerManager().getAllHudPlayer()).thenReturn(List.of(player));
      Compass defaultCompass = mock(Compass.class, CALLS_REAL_METHODS);
      when(defaultCompass.getName()).thenReturn("default_compass");
      doReturn(HudObjectType.COMPASS).when(defaultCompass).getType();
      var originalDefault = HudComponentSupplier.of(defaultCompass, List::of);
      objects.put(defaultCompass.identifier(), originalDefault);
      Compass compass = mock(Compass.class, CALLS_REAL_METHODS);
      when(compass.getName()).thenReturn("themob_compass");
      doReturn(HudObjectType.COMPASS).when(compass).getType();
      when(compass.indicate(player))
          .thenAnswer(
              i ->
                  HudComponentSupplier.of(
                      compass, () -> List.of(CompassPaletteTest.ribbon(0, false))));
      Hud marker = mock(Hud.class, CALLS_REAL_METHODS);
      when(marker.getName()).thenReturn("themob_compass_marker");
      doReturn(HudObjectType.HUD).when(marker).getType();
      when(marker.createRenderer(player))
          .thenAnswer(i -> HudComponentSupplier.of(marker, List::of));
      when(renderer.getCompassManager().getCompass("themob_compass")).thenReturn(compass);
      when(renderer.getHudManager().getHud("themob_compass_marker")).thenReturn(marker);
      try (var api = mockStatic(BetterHudAPI.class)) {
        api.when(BetterHudAPI::inst).thenReturn(renderer);
        var display = new BetterHudCompassDisplay(owner, rendererPlugin);
        try {
          for (int i = 0; i < 10; i++) display.refresh(id);
          assertEquals(2, objects.size());
          assertInstanceOf(RainbowCompass.class, objects.get(compass.identifier()));
          visible.set(false);
          display.refresh(id);
          assertFalse(objects.containsKey(marker.identifier()));
          assertTrue(objects.get(compass.identifier()).get().isEmpty());
          visible.set(true);
          display.refresh(id);
          assertEquals(2, objects.size());
        } finally {
          display.close();
        }
        assertEquals(Map.of(defaultCompass.identifier(), originalDefault), objects);
        var reloaded = new BetterHudCompassDisplay(owner, rendererPlugin);
        reloaded.refresh(id);
        when(owner.sharedCoinOverlayPack()).thenReturn(true);
        var builds = new java.util.concurrent.atomic.AtomicInteger();
        server
            .getCommandMap()
            .register(
                "coinoverlay",
                new org.bukkit.command.Command("coinsadmin") {
                  public boolean execute(
                      org.bukkit.command.CommandSender sender, String label, String[] args) {
                    assertSame(org.bukkit.Bukkit.getConsoleSender(), sender);
                    assertArrayEquals(new String[] {"hud", "reload"}, args);
                    builds.incrementAndGet();
                    return true;
                  }
                });
        reloaded.reload(org.bukkit.Bukkit.getConsoleSender());
        assertEquals(1, builds.get());
        verify(renderer, never()).reload();
        var start = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(renderer, times(1)).addReloadStartTask(start.capture());
        var compassFile =
            rendererPlugin.getDataFolder().toPath().resolve("compasses/themob-compass.yml");
        var oldLayout =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(compassFile.toFile());
        oldLayout.set("themob_compass.pixel.y", 48);
        oldLayout.save(compassFile.toFile());
        start.getValue().run();
        assertEquals(
            8,
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(compassFile.toFile())
                .getInt("themob_compass.pixel.y"));
        var end = org.mockito.ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(renderer, times(1)).addReloadEndTask(end.capture());
        Map<String, byte[]> pack = new TreeMap<>();
        String shader = "#version 330\n#define DEFAULT_OFFSET 27\n";
        pack.put(
            org.plugin.theMob.hud.compass.CompassHeaderLayout.SHADER,
            shader.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        end.getValue().accept(new kr.toxicity.hud.api.plugin.ReloadState.Success(pack, null, 0));
        assertTrue(
            new String(
                    pack.get(org.plugin.theMob.hud.compass.CompassHeaderLayout.SHADER),
                    java.nio.charset.StandardCharsets.UTF_8)
                .contains("DEFAULT_OFFSET 29"));
        reloaded.close();
        verify(renderer, times(1)).addReloadEndTask(any());
        assertEquals(Map.of(defaultCompass.identifier(), originalDefault), objects);
      }
    } finally {
      MockBukkit.unmock();
    }
  }
}
