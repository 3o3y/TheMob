package org.plugin.theMob.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

class ObjectiveEventServiceTest {
  ServerMock server;
  TheMob plugin;
  LivingEntity mob;
  Player player;
  List<Event> seen;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    plugin = MockBukkit.load(TheMob.class);
    mob = plugin.api().spawn("pig_lvl_1", new Location(world, .5, 64, .5)).orElseThrow();
    assertTrue(mob.getHealth() > 0, "fresh spawned mob health");
    player = server.addPlayer();
    player.teleport(mob.getLocation());
    assertTrue(mob.getHealth() > 0, "health after player teleport");
    // Isolate the objective collector from unrelated AI/visual recurring jobs in this fixture.
    server.getScheduler().cancelTasks(plugin);
    seen = new ArrayList<>();
    for (var type :
        List.of(
            TheMobDamageEvent.class,
            TheMobPhaseChangeEvent.class,
            TheMobDropEvent.class,
            TheMobLootEvent.class))
      server
          .getPluginManager()
          .registerEvent(
              type, new Listener() {}, EventPriority.MONITOR, (l, e) -> seen.add(e), plugin);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  void tick() {
    server.getScheduler().performOneTick();
  }

  <T> List<T> events(Class<T> type) {
    return seen.stream().filter(type::isInstance).map(type::cast).toList();
  }

  Item drop(ItemStack item) {
    if (mob.getKiller() == null) mob.setKiller(player); // MockBukkit also sets health to zero here
    var death =
        new EntityDeathEvent(
            mob,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>(List.of(item)));
    plugin.objectives().death(death); // final native death outputs, after the drop engine
    Item entity = mob.getWorld().dropItem(mob.getLocation(), death.getDrops().getFirst());
    server.getPluginManager().callEvent(new ItemSpawnEvent(entity));
    return entity;
  }

  @Test
  void canceledDamageAndCreativePlayersProduceNoCreditAndOverkillIsCapped() {
    var canceled =
        new EntityDamageByEntityEvent(player, mob, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 3);
    plugin.objectives().damage(canceled);
    canceled.setCancelled(true); // even a later cancellation is respected
    tick();
    assertTrue(events(TheMobDamageEvent.class).isEmpty());
    player.setGameMode(GameMode.CREATIVE);
    plugin
        .objectives()
        .damage(
            new EntityDamageByEntityEvent(
                player, mob, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2));
    tick();
    assertTrue(events(TheMobDamageEvent.class).isEmpty());
    player.setGameMode(GameMode.SURVIVAL);
    double remaining = mob.getHealth();
    var hit =
        new EntityDamageByEntityEvent(
            player, mob, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 99999);
    assertTrue(org.plugin.theMob.boss.behavior.BossTargets.valid(player), "eligible player");
    assertTrue(remaining > 0, "remaining health");
    assertTrue(hit.getFinalDamage() > 0, "native final damage");
    plugin.objectives().damage(hit);
    tick();
    assertEquals(remaining, events(TheMobDamageEvent.class).getFirst().getDamage());
  }

  @Test
  void phaseCreditIncludesRecentFightersButNotBystandersOrAbsentPlayers() {
    mob.getPersistentDataContainer().set(plugin.keys().IS_BOSS, PersistentDataType.INTEGER, 1);
    Player bystander = server.addPlayer();
    bystander.teleport(mob.getLocation());
    plugin
        .objectives()
        .damage(
            new EntityDamageByEntityEvent(
                player, mob, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.25));
    tick();
    plugin.api().phase(mob, "phase1", "phase2");
    var change = events(TheMobPhaseChangeEvent.class).getFirst();
    assertEquals(List.of(player), change.getParticipants());
    assertThrows(UnsupportedOperationException.class, () -> change.getParticipants().clear());
    player.teleport(mob.getLocation().add(200, 0, 0));
    plugin.api().phase(mob, "phase2", "phase3");
    assertTrue(events(TheMobPhaseChangeEvent.class).get(1).getParticipants().isEmpty());
    plugin.api().phase(mob, null, "phase1");
    assertTrue(events(TheMobPhaseChangeEvent.class).get(2).getParticipants().isEmpty());
  }

  @Test
  void partialPickupCountsActualQuantityAndPlayerRedropsHaveNoProvenance() {
    Item entity = drop(new ItemStack(Material.DIAMOND, 7));
    tick();
    assertEquals(1, events(TheMobDropEvent.class).size());
    assertEquals(7, events(TheMobDropEvent.class).getFirst().getItemSnapshot().amount());
    assertFalse(
        entity
            .getItemStack()
            .getItemMeta()
            .getPersistentDataContainer()
            .has(new NamespacedKey(plugin, "loot_receipt")));
    var pickup = new EntityPickupItemEvent(player, entity, 4);
    server.getPluginManager().callEvent(pickup);
    server.getPluginManager().callEvent(pickup); // same dispatch cannot count twice
    tick();
    assertEquals(3, events(TheMobLootEvent.class).getFirst().getItemSnapshot().amount());
    assertEquals(1, events(TheMobLootEvent.class).size());
    var inventoryItem = events(TheMobLootEvent.class).getFirst().getItem();
    inventoryItem.setAmount(42);
    assertEquals(3, events(TheMobLootEvent.class).getFirst().getItem().getAmount());
    Item redrop = mob.getWorld().dropItem(mob.getLocation(), inventoryItem);
    server.getPluginManager().callEvent(new EntityPickupItemEvent(player, redrop, 0));
    tick();
    assertEquals(1, events(TheMobLootEvent.class).size());
  }

  @Test
  void canceledSpawnsAndPickupsNeverGrantProgress() {
    Item entity = drop(new ItemStack(Material.EMERALD, 4));
    var pickup = new EntityPickupItemEvent(player, entity, 0);
    plugin.objectives().pickup(pickup);
    pickup.setCancelled(true);
    tick();
    assertTrue(events(TheMobLootEvent.class).isEmpty());
    seen.clear();
    var stack = new ItemStack(Material.GOLD_INGOT, 2);
    var death =
        new EntityDeathEvent(
            mob,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>(List.of(stack)));
    plugin.objectives().death(death);
    // Create the entity before applying the tagged final drop: WorldMock.dropItem already fires
    // spawn.
    Item canceledEntity =
        mob.getWorld().dropItem(mob.getLocation(), new ItemStack(Material.GOLD_INGOT));
    canceledEntity.setItemStack(stack);
    var spawn = new ItemSpawnEvent(canceledEntity);
    spawn.setCancelled(true);
    plugin.objectives().spawn(spawn);
    plugin.objectives().spawned(spawn);
    tick();
    assertTrue(events(TheMobDropEvent.class).isEmpty());
  }

  @Test
  void mergesOnlyCombineEquivalentSourceTraitsAndProvenanceSurvivesServiceRestart() {
    Item first = drop(new ItemStack(Material.DIAMOND, 2));
    Item same = drop(new ItemStack(Material.DIAMOND, 3));
    Item ordinary = mob.getWorld().dropItem(mob.getLocation(), new ItemStack(Material.DIAMOND));
    var safe = new ItemMergeEvent(first, same);
    server.getPluginManager().callEvent(safe);
    assertFalse(safe.isCancelled());
    var unsafe = new ItemMergeEvent(first, ordinary);
    server.getPluginManager().callEvent(unsafe);
    assertTrue(unsafe.isCancelled());
    mob.setKiller(server.addPlayer());
    Item otherKiller = drop(new ItemStack(Material.DIAMOND));
    var other = new ItemMergeEvent(first, otherKiller);
    server.getPluginManager().callEvent(other);
    assertTrue(other.isCancelled());
    tick();
    plugin.objectives().close();
    var restored = new ObjectiveEventService(plugin);
    try {
      server.getPluginManager().callEvent(new EntityPickupItemEvent(player, first, 0));
      tick();
      var loot = events(TheMobLootEvent.class).getFirst();
      assertEquals(player.getUniqueId(), loot.getSource().killerId());
      assertEquals("pig_lvl_1", loot.getSource().definitionId());
    } finally {
      restored.close();
    }
  }

  @Test
  void noDropsAndAdministrativeRewardsCannotCreateLootProgress() {
    mob.getPersistentDataContainer().set(plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 1);
    Item suppressed = drop(new ItemStack(Material.DIAMOND));
    Item reward =
        mob.getWorld()
            .dropItem(mob.getLocation(), plugin.api().createItem("epic_sword_5").orElseThrow());
    server.getPluginManager().callEvent(new EntityPickupItemEvent(player, suppressed, 0));
    server.getPluginManager().callEvent(new EntityPickupItemEvent(player, reward, 0));
    tick();
    assertTrue(events(TheMobDropEvent.class).isEmpty());
    assertTrue(events(TheMobLootEvent.class).isEmpty());
  }

  @Test
  void publicItemApiUsesClonesAndAuthoritativeStarsAndRejectsUnknowns() {
    assertTrue(plugin.api().supportsCombatObjectives());
    assertEquals(160, plugin.api().itemIds().size());
    var item = plugin.api().createItem("epic_sword_5").orElseThrow();
    var info = plugin.api().inspectItem(item).orElseThrow();
    assertEquals("epic_sword_5", info.id());
    assertEquals("epic", info.rarity());
    assertEquals(5, info.stars());
    item.setAmount(30);
    assertEquals(1, plugin.api().createItem("epic_sword_5").orElseThrow().getAmount());
    assertEquals(0, plugin.api().definitionLevel(null));
    assertEquals(5, plugin.api().definitionLevel("tm_zombie_lvl_5"));
    assertTrue(plugin.api().createItem(null).isEmpty());
    assertTrue(plugin.api().createItem("unknown").isEmpty());
    assertTrue(plugin.api().inspectItem(new ItemStack(Material.AIR)).isEmpty());
    assertEquals(
        "vanilla",
        plugin.api().inspectItem(new ItemStack(Material.DIAMOND)).orElseThrow().rarity());
  }
}
