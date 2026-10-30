package org.plugin.theMob.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

class ProviderItemsTest {
  ServerMock server;
  TheMob plugin;
  LivingEntity mob;
  Player player;
  ItemStack prototype;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    plugin = MockBukkit.load(TheMob.class);
    mob = plugin.api().spawn("pig_lvl_1", new Location(world, .5, 64, .5)).orElseThrow();
    player = server.addPlayer();
    server.getScheduler().cancelTasks(plugin);
    prototype = new ItemStack(Material.TRIPWIRE_HOOK);
    var meta = prototype.getItemMeta();
    meta.setDisplayName("Native key");
    meta.getPersistentDataContainer()
        .set(new NamespacedKey("crateplugin", "key"), PersistentDataType.STRING, "starter");
    prototype.setItemMeta(meta);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  TheMobItemProvider provider(String namespace, ItemStack item) {
    return new TheMobItemProvider() {
      public String id() {
        return namespace;
      }

      public Optional<ItemStack> create(String id) {
        return id.equals("starter") ? Optional.of(item) : Optional.empty();
      }
    };
  }

  void register(TheMobItemProvider factory, ServicePriority priority) {
    var addon = MockBukkit.createMockPlugin();
    Bukkit.getServicesManager().register(TheMobItemProvider.class, factory, addon, priority);
  }

  @Test
  void identicalUnmodifiedPlainRewardsKeepDistinctFactoryIdentityOnTheirOriginalInstances() {
    var addon = MockBukkit.createMockPlugin();
    Bukkit.getServicesManager()
        .register(
            TheMobItemProvider.class,
            new TheMobItemProvider() {
              public String id() {
                return "plain";
              }

              public Optional<ItemStack> create(String id) {
                return Optional.of(new ItemStack(Material.STONE));
              }
            },
            addon,
            ServicePriority.Normal);
    var first = plugin.api().createItem("plain:first").orElseThrow();
    var second = plugin.api().createItem("plain:second").orElseThrow();
    assertEquals(first, second);
    assertTrue(first.getItemMeta().getPersistentDataContainer().getKeys().isEmpty());
    assertEquals("plain:first", plugin.api().inspectItem(first).orElseThrow().id());
    assertEquals("plain:second", plugin.api().inspectItem(second).orElseThrow().id());
    assertEquals("custom", plugin.api().inspectItem(first).orElseThrow().rarity());
  }

  @Test
  void publicNamespacesAreValidatedAndNativeCatalogCannotBeOverridden() {
    register(provider("themob", new ItemStack(Material.DIRT)), ServicePriority.Highest);
    register(provider("bad:name", prototype), ServicePriority.Normal);
    assertEquals(List.of("themob"), plugin.api().itemProviders());
    assertEquals(
        Material.DIAMOND_SWORD, plugin.api().createItem("epic_sword_5").orElseThrow().getType());
    assertTrue(plugin.api().createItem("unknown:starter").isEmpty());
    assertTrue(plugin.api().createItem("unknown:id with spaces").isEmpty());
  }

  @Test
  void nativeForeignMetadataIsUnchangedAndItemsAreDefensivelyCloned() {
    register(provider("crateplugin", prototype), ServicePriority.Normal);
    var result = plugin.api().createItem("crateplugin:starter").orElseThrow();
    assertNotSame(prototype, result);
    assertEquals(prototype, result);
    assertEquals(
        Set.of(new NamespacedKey("crateplugin", "key")),
        result.getItemMeta().getPersistentDataContainer().getKeys());
    assertEquals("crateplugin:starter", plugin.api().inspectItem(result).orElseThrow().id());
    result.setAmount(12);
    assertEquals(1, prototype.getAmount());
    assertEquals(1, plugin.api().createItem("crateplugin:starter").orElseThrow().getAmount());
  }

  @Test
  void highestPriorityMatchingAddonWinsAndUnregisteredFactoriesStopCreatingItems() {
    register(provider("items", new ItemStack(Material.STONE)), ServicePriority.Low);
    var high = provider("items", new ItemStack(Material.DIAMOND));
    register(high, ServicePriority.Highest);
    assertEquals(
        Material.DIAMOND, plugin.api().createItem("items:starter").orElseThrow().getType());
    Bukkit.getServicesManager().unregister(TheMobItemProvider.class, high);
    assertEquals(Material.STONE, plugin.api().createItem("items:starter").orElseThrow().getType());
  }

  @Test
  void nativeDropAndPickupKeepTheKeyWorkingAndPersistIdentityBeyondProviderCache() {
    register(provider("crateplugin", prototype), ServicePriority.Normal);
    ItemStack reward = plugin.api().createItem("crateplugin:starter").orElseThrow();
    mob.setKiller(player);
    var death =
        new EntityDeathEvent(
            mob,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>(List.of(reward)));
    plugin.objectives().death(death);
    Item ground = mob.getWorld().dropItem(mob.getLocation(), death.getDrops().getFirst());
    server.getPluginManager().callEvent(new ItemSpawnEvent(ground));
    server.getScheduler().performOneTick();
    assertEquals(prototype, ground.getItemStack());
    plugin.integrations().suspendExtended();
    var found = new ArrayList<TheMobLootEvent>();
    server
        .getPluginManager()
        .registerEvent(
            TheMobLootEvent.class,
            new Listener() {},
            EventPriority.MONITOR,
            (l, e) -> found.add((TheMobLootEvent) e),
            plugin);
    server.getPluginManager().callEvent(new EntityPickupItemEvent(player, ground, 0));
    server.getScheduler().performOneTick();
    assertEquals(1, found.size());
    assertEquals("crateplugin:starter", found.getFirst().getItemSnapshot().id());
    assertEquals(prototype, found.getFirst().getItem());
  }

  @Test
  void legacyOmegaStatsAreRecognizedAsCustomWithoutInventedRarity() {
    var oldItem = new ItemStack(Material.NETHERITE_CHESTPLATE);
    var meta = oldItem.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "health"), PersistentDataType.DOUBLE, 30.0);
    oldItem.setItemMeta(meta);
    var info = plugin.api().inspectItem(oldItem).orElseThrow();
    assertTrue(info.custom());
    assertEquals("custom", info.rarity());
    assertEquals(0, info.stars());
  }

  @Test
  void catalogDefinesExactlyTwentyDistinctOptionalTargetsWithExplicitFeatures() {
    var all = IntegrationCatalog.targets();
    assertEquals(20, all.size());
    assertEquals(20, all.stream().map(IntegrationCatalog.Target::id).distinct().count());
    assertTrue(
        all.stream().allMatch(t -> !t.feature().isBlank() && t.source().startsWith("https://")));
    assertEquals(
        "economyshopgui", IntegrationCatalog.find("EconomyShopGUI-Premium").orElseThrow().id());
    assertEquals("essentialsx", IntegrationCatalog.find("Essentials").orElseThrow().id());
  }
}
