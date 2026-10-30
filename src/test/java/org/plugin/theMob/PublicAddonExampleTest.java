package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.dungeon.DungeonCompletedEvent;
import org.plugin.theMob.api.event.*;

/** Tests the separately compiled public example; it has no main-plugin implementation classpath. */
class PublicAddonExampleTest {
  ServerMock server;
  TheMob plugin;
  URLClassLoader loader;

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    server.getScheduler().performTicks(1);
    loader =
        new URLClassLoader(
            new java.net.URL[] {
              Path.of(System.getProperty("themob.example.classes")).toUri().toURL(),
              Path.of(System.getProperty("themob.example.resources")).toUri().toURL()
            },
            getClass().getClassLoader());
  }

  @AfterEach
  void close() throws Exception {
    try {
      MockBukkit.unmock();
    } finally {
      if (loader != null) loader.close();
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  JavaPlugin example() throws Exception {
    var type = Class.forName("example.MobAddon").asSubclass(JavaPlugin.class);
    var resource = Path.of(System.getProperty("themob.example.resources"), "plugin.yml");
    PluginDescriptionFile descriptor;
    try (var input = java.nio.file.Files.newInputStream(resource)) {
      descriptor = new PluginDescriptionFile(input);
    }
    return (JavaPlugin) MockBukkit.loadWith((Class) type, descriptor);
  }

  @Test
  void exampleRegistersPublicMenuAndNativeItemServicesAndRemovesThemOnDisable() throws Exception {
    var addon = example();
    var registration =
        Bukkit.getServicesManager().getRegistrations(AdminMenuExtension.class).stream()
            .filter(r -> r.getPlugin() == addon)
            .findFirst()
            .orElseThrow();
    assertEquals("example_progress", registration.getProvider().id());
    var item = plugin.api().createItem("themobexample:token").orElseThrow();
    assertEquals(org.bukkit.Material.PAPER, item.getType());
    assertEquals("themobexample:token", plugin.api().inspectItem(item).orElseThrow().id());
    assertTrue(plugin.api().createItem("themobexample:missing").isEmpty());
    Bukkit.getPluginManager().disablePlugin(addon);
    assertTrue(plugin.api().createItem("themobexample:token").isEmpty());
    assertTrue(
        Bukkit.getServicesManager().getRegistrations(AdminMenuExtension.class).stream()
            .noneMatch(r -> r.getPlugin() == addon));
  }

  @Test
  void exampleConnectsAndDoesNotSpawnWithoutAdminPermissionOrWhenCancelled() throws Exception {
    JavaPlugin addon = example();
    assertTrue(addon.isEnabled());
    var player = server.addPlayer();
    assertTrue(server.dispatchCommand(player, "themobexample spawn pig_lvl_1"));
    assertTrue(plugin.api().activeMobs().isEmpty());
    int[] attempts = {0};
    server
        .getPluginManager()
        .registerEvent(
            TheMobPreSpawnEvent.class,
            new Listener() {},
            EventPriority.HIGH,
            (listener, event) -> {
              attempts[0]++;
              ((TheMobPreSpawnEvent) event).setCancelled(true);
            },
            plugin);
    player.setOp(true);
    server.dispatchCommand(player, "themobexample spawn pig_lvl_1");
    assertEquals(1, attempts[0]);
    assertTrue(plugin.api().activeMobs().isEmpty());
  }

  @Test
  void nativeDeathCountsOnceAndMinionsOrNpcsDoNotAddExampleProgress() throws Exception {
    JavaPlugin addon = example();
    var world = server.getWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    var original = plugin.api().spawn("pig_lvl_1", new Location(world, .5, 64, .5)).orElseThrow();
    var player = server.addPlayer();
    LivingEntity entity = spy(original);
    doReturn(player).when(entity).getKiller();
    var event =
        new EntityDeathEvent(
            entity,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>());
    server.getPluginManager().callEvent(event);
    server.getPluginManager().callEvent(event);
    var key = new NamespacedKey(addon, "example_kills");
    assertEquals(1, player.getPersistentDataContainer().get(key, PersistentDataType.LONG));
    var minion =
        new MobSnapshot(
            entity.getUniqueId(), "boss", "minion", false, true, "phase1", SpawnReason.MINION);
    server.getPluginManager().callEvent(new TheMobDeathEvent(entity, minion, player));
    assertEquals(1, player.getPersistentDataContainer().get(key, PersistentDataType.LONG));
    player.setMetadata("NPC", new FixedMetadataValue(plugin, true));
    server
        .getPluginManager()
        .callEvent(new TheMobDeathEvent(entity, plugin.api().mob(entity).orElseThrow(), player));
    assertEquals(1, player.getPersistentDataContainer().get(key, PersistentDataType.LONG));
  }

  @Test
  void dungeonObjectiveExampleForwardsOnlyTheAuthoritativeParticipants() throws Exception {
    JavaPlugin addon = example();
    var first = server.addPlayer();
    var second = server.addPlayer();
    var outside = server.addPlayer();
    var region = new DungeonRegion("d001", first.getWorld().getUID(), 0, 0, 113, 133, true);
    server
        .getPluginManager()
        .callEvent(
            new DungeonCompletedEvent(
                region,
                UUID.randomUUID(),
                List.of(first, second),
                "zombie_boss_template",
                "krypta",
                "world",
                4));
    var key = new NamespacedKey(addon, "example_dungeons");
    assertEquals(1, first.getPersistentDataContainer().get(key, PersistentDataType.LONG));
    assertEquals(1, second.getPersistentDataContainer().get(key, PersistentDataType.LONG));
    assertNull(outside.getPersistentDataContainer().get(key, PersistentDataType.LONG));
  }

  @Test
  void addonServicesRemainUsableAfterTheMobReload() throws Exception {
    JavaPlugin addon = example();
    var api = plugin.api();
    plugin.reloadPlugin();
    assertSame(api, Bukkit.getServicesManager().load(TheMobApi.class));
    assertTrue(addon.isEnabled());
    assertTrue(server.dispatchCommand(server.getConsoleSender(), "themobexample status"));
  }

  @Test
  void exampleDisablesItselfWhenRequiredServiceIsMissing() throws Exception {
    Bukkit.getServicesManager().unregisterAll(plugin);
    assertFalse(example().isEnabled());
  }
}
