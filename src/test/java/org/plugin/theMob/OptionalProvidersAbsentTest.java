package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Run in a separate JVM with every optional provider JAR removed from its classpath. */
class OptionalProvidersAbsentTest {
  @Test
  void standaloneLifecycleWorksWithoutOptionalApiClasses() {
    if (Boolean.getBoolean("standalone.nooptional")) {
      assertThrows(
          ClassNotFoundException.class, () -> Class.forName("kr.toxicity.hud.api.BetterHudAPI"));
      assertThrows(
          ClassNotFoundException.class,
          () -> Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion"));
    }
    MockBukkit.mock();
    try {
      var plugin = MockBukkit.load(TheMob.class);
      assertTrue(plugin.isEnabled());
      plugin.reloadPlugin();
      assertEquals(20, plugin.api().integrations().size());
    } finally {
      MockBukkit.unmock();
    }
  }
}
