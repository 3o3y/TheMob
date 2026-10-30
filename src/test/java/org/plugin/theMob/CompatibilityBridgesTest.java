package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.plugin.theMob.boss.phase.AttributeSnapshot;
import org.plugin.theMob.compat.*;

class CompatibilityBridgesTest {
  @AfterEach
  void cleanup() {
    if (Bukkit.getServer() != null) MockBukkit.unmock();
  }

  @Test
  void java17ClampPreservesModernMathIncludingBoundaryValues() {
    var random = new Random(3190);
    for (int i = 0; i < 10000; i++) {
      double min = random.nextDouble() * 100 - 50,
          max = min + random.nextDouble() * 100,
          value = random.nextGaussian() * 100;
      assertEquals(Math.clamp(value, min, max), Numbers.clamp(value, min, max));
      long integer = random.nextLong();
      assertEquals(Math.clamp(integer, -100, 100), Numbers.clamp(integer, -100, 100));
    }
    assertTrue(Double.isNaN(Numbers.clamp(Double.NaN, 0, 1)));
    assertEquals(1, Numbers.clamp(Double.POSITIVE_INFINITY, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> Numbers.clamp(0, Double.NaN, 1));
    assertThrows(IllegalArgumentException.class, () -> Numbers.clamp(0, +0.0, -0.0));
  }

  @Test
  void historicalAttributeSnapshotsRestoreAcrossNamingChange() {
    var server = MockBukkit.mock();
    var player = server.addPlayer();
    var tag = player.getPersistentDataContainer();
    tag.set(
        new NamespacedKey("themob", "attr_generic.max_health"), PersistentDataType.DOUBLE, 94.0);
    AttributeSnapshot.readFrom(tag).restore(player);
    assertEquals(94, player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
    tag.set(new NamespacedKey("themob", "attr_max_health"), PersistentDataType.DOUBLE, 70.0);
    AttributeSnapshot.readFrom(tag).restore(player);
    assertEquals(70, player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
  }

  @Test
  void attributeAliasesAndModernModifierIdentityStayStable() {
    var server = MockBukkit.mock();
    server.addSimpleWorld("world");
    assertSame(Attribute.MAX_HEALTH, BukkitTypes.attribute("generic.max_health"));
    assertSame(Attribute.MAX_HEALTH, BukkitTypes.attribute("GENERIC_MAX_HEALTH"));
    assertNull(BukkitTypes.attribute("not_a_real_attribute"));
    var key = new NamespacedKey("themob", "player_bonus_health");
    var modifier = Modifiers.additive(key, 74);
    assertEquals(key.toString(), Modifiers.identity(modifier));
    assertEquals(74, modifier.getAmount());
  }

  @Test
  void inventoryBridgeReturnsTheActualWindow() {
    var server = MockBukkit.mock();
    var player = server.addPlayer();
    var menu = Bukkit.createInventory(null, 54, "TheMob");
    player.openInventory(menu);
    assertSame(menu, Views.top(player.getOpenInventory()));
    assertNull(Views.top(null));
  }
}
