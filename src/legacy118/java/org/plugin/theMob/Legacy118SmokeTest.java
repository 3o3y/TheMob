package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.*;
import org.bukkit.*;
import org.junit.jupiter.api.*;

/** A separate JVM/classpath with the real 1.18.2 API and the matching old server fixture. */
class Legacy118SmokeTest {
  ServerMock server;
  TheMob plugin;

  static final class CompleteAttribute
      extends be.seeseemelk.mockbukkit.attribute.AttributeInstanceMock {
    CompleteAttribute() {
      super(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH, 20);
    }

    @Override
    public double getValue() {
      // The old fixture stores modifiers but its getValue() ignores every modifier.
      double value = getBaseValue();
      for (var modifier : getModifiers())
        if (modifier.getOperation() == org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER)
          value += modifier.getAmount();
      double base = value;
      for (var modifier : getModifiers())
        if (modifier.getOperation() == org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR)
          value += base * modifier.getAmount();
      for (var modifier : getModifiers())
        if (modifier.getOperation()
            == org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1)
          value *= 1 + modifier.getAmount();
      return value;
    }
  }

  static final class CompletePlayer extends be.seeseemelk.mockbukkit.entity.PlayerMock {
    private CompleteAttribute maxHealth;

    CompletePlayer(ServerMock server) {
      super(server, "LegacyAdmin");
    }

    @Override
    public org.bukkit.attribute.AttributeInstance getAttribute(
        org.bukkit.attribute.Attribute attribute) {
      if (attribute == org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) {
        if (maxHealth == null) maxHealth = new CompleteAttribute();
        return maxHealth;
      }
      try {
        return super.getAttribute(attribute);
      }
      // Bukkit returns null for attributes absent from this entity; old MockBukkit throws instead.
      catch (UnimplementedOperationException missing) {
        return null;
      }
    }
  }

  static final class CompleteWorld extends WorldMock {
    CompleteWorld() {
      super(new WorldCreator("world"));
    }

    @Override
    public java.util.List<org.bukkit.entity.LivingEntity> getLivingEntities() {
      return getEntities().stream()
          .filter(org.bukkit.entity.LivingEntity.class::isInstance)
          .map(org.bukkit.entity.LivingEntity.class::cast)
          .toList();
    }
  }

  static final class CompleteItems extends be.seeseemelk.mockbukkit.inventory.ItemFactoryMock {
    private org.bukkit.inventory.meta.ItemMeta complete(org.bukkit.inventory.meta.ItemMeta meta) {
      // MockBukkit 2.85.2's copy constructor forgets the empty projectile list.
      if (meta instanceof be.seeseemelk.mockbukkit.inventory.meta.CrossbowMetaMock) {
        try {
          var field = meta.getClass().getDeclaredField("projectiles");
          field.setAccessible(true);
          if (field.get(meta) == null)
            field.set(meta, new java.util.ArrayList<org.bukkit.inventory.ItemStack>());
        } catch (ReflectiveOperationException failure) {
          throw new AssertionError(failure);
        }
      }
      return meta;
    }

    @Override
    public org.bukkit.inventory.meta.ItemMeta asMetaFor(
        org.bukkit.inventory.meta.ItemMeta meta, Material material) {
      return complete(super.asMetaFor(meta, material));
    }

    @Override
    public org.bukkit.inventory.meta.ItemMeta asMetaFor(
        org.bukkit.inventory.meta.ItemMeta meta, org.bukkit.inventory.ItemStack item) {
      return complete(super.asMetaFor(meta, item));
    }

    @Override
    public org.bukkit.inventory.meta.ItemMeta getItemMeta(Material material) {
      var meta = super.getItemMeta(material);
      if (meta instanceof org.bukkit.inventory.meta.CrossbowMeta crossbow)
        crossbow.setChargedProjectiles(java.util.List.of());
      return meta;
    }
  }

  static final class CompleteServer extends ServerMock {
    private final CompleteItems items = new CompleteItems();
    private final MockUnsafeValues unsafe =
        new MockUnsafeValues() {
          // Paper's 1.18 registry initialization requests configured structures. This fixture has
          // none.
          @Override
          public <T extends Keyed> Registry<T> registryFor(Class<T> type) {
            return new Registry<>() {
              @Override
              public T get(NamespacedKey key) {
                return null;
              }

              @Override
              public java.util.Iterator<T> iterator() {
                return java.util.Collections.emptyIterator();
              }
            };
          }
        };

    @Override
    public be.seeseemelk.mockbukkit.inventory.ItemFactoryMock getItemFactory() {
      return items;
    }

    @Override
    public MockUnsafeValues getUnsafe() {
      return unsafe;
    }
  }

  @BeforeEach
  void start() {
    server = MockBukkit.mock(new CompleteServer());
    server.addWorld(new CompleteWorld());
    plugin = MockBukkit.load(TheMob.class);
  }

  @AfterEach
  void stop() {
    MockBukkit.unmock();
  }

  @Test
  void startsWithoutAnyModernOptionalProvider() {
    assertTrue(plugin.isEnabled());
    assertEquals(1, plugin.api().apiVersion());
    assertFalse(plugin.api().definitions().isEmpty());
    assertFalse(org.plugin.theMob.compat.Displays.available());
    assertNotNull(org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH"));
    assertNull(org.plugin.theMob.compat.BukkitTypes.attribute("SCALE"));
  }

  @Test
  void adminMenuAndOldCommandsRemainAvailable() {
    var player = new CompletePlayer(server);
    server.addPlayer(player);
    player.setOp(true);
    server.dispatchCommand(player, "mob");
    assertEquals(54, org.plugin.theMob.compat.Views.top(player.getOpenInventory()).getSize());
    server.dispatchCommand(player, "mob connect check");
  }

  @Test
  void unsupportedNewVanillaItemsAreOmitted() {
    assertTrue(plugin.api().createItem("epic_sword_5").isPresent());
    assertTrue(plugin.api().createItem("epic_mace_5").isEmpty());
  }

  @Test
  void oldUuidModifiersPreserveHealthAcrossRejoin() {
    var player = new CompletePlayer(server);
    server.addPlayer(player);
    var item = new org.bukkit.inventory.ItemStack(Material.NETHERITE_CHESTPLATE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            new NamespacedKey(plugin, "health"),
            org.bukkit.persistence.PersistentDataType.DOUBLE,
            74.0);
    item.setItemMeta(meta);
    player.getInventory().setChestplate(item);
    var listener =
        java.util.Arrays.stream(
                org.bukkit.event.player.PlayerJoinEvent.getHandlerList().getRegisteredListeners())
            .map(org.bukkit.plugin.RegisteredListener::getListener)
            .filter(org.plugin.theMob.player.stats.PlayerEquipListener.class::isInstance)
            .map(org.plugin.theMob.player.stats.PlayerEquipListener.class::cast)
            .findFirst()
            .orElseThrow();
    listener.onJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
    assertEquals(94, player.getMaxHealth());
    player.setHealth(70);
    listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player, ""));
    assertEquals(20, player.getMaxHealth());
    listener.onJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
    assertEquals(94, player.getMaxHealth());
    assertEquals(70, player.getHealth());
    listener.onJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
    assertEquals(94, player.getMaxHealth());
    assertEquals(70, player.getHealth());
  }
}
