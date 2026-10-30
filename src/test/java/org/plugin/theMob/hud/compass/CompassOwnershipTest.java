package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.StringReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

class CompassOwnershipTest {
  private Plugin coins(boolean compass) throws Exception {
    Plugin plugin = mock(Plugin.class);
    when(plugin.isEnabled()).thenReturn(true);
    when(plugin.getConfig()).thenReturn(new YamlConfiguration());
    when(plugin.getDescription())
        .thenReturn(
            new PluginDescriptionFile(
                new StringReader(
                    "name: CoinOverlay\nversion: test\nmain: example.CoinOverlay\ncommands:\n"
                        + (compass ? "  kompass: {}\n" : "  coins: {}\n"))));
    return plugin;
  }

  @Test
  void sharesTheCompassWithOlderCoinOverlayThatStillOwnsIt() throws Exception {
    assertTrue(CompassService.usesCoinOverlayCompass(coins(true)));
  }

  @Test
  void newCoinOverlayWithoutCompassDoesNotPreventTheMobFromRendering() throws Exception {
    assertFalse(CompassService.usesCoinOverlayCompass(coins(false)));
    assertFalse(CompassService.usesCoinOverlayCompass(null));
  }
}
