package org.plugin.theMob.item.equipment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Map;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.plugin.theMob.TheMob;

class PlayerVitalsTest {
  ServerMock server;
  TheMob plugin;
  PlayerMock player;
  EquipmentBonusListener equipment;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    player = server.addPlayer();
    equipment =
        Arrays.stream(PlayerJoinEvent.getHandlerList().getRegisteredListeners())
            .map(registered -> registered.getListener())
            .filter(EquipmentBonusListener.class::isInstance)
            .map(EquipmentBonusListener.class::cast)
            .findFirst()
            .orElseThrow();
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  ItemStack item(Material material, Map<String, Double> stats) {
    ItemStack result = new ItemStack(material);
    var meta = result.getItemMeta();
    stats.forEach(
        (key, value) ->
            meta.getPersistentDataContainer()
                .set(new NamespacedKey(plugin, key), PersistentDataType.DOUBLE, value));
    result.setItemMeta(meta);
    return result;
  }

  void omega() {
    player
        .getInventory()
        .setHelmet(item(Material.NETHERITE_HELMET, Map.of("health", 14.0, "armor", 18.0)));
    player
        .getInventory()
        .setChestplate(item(Material.NETHERITE_CHESTPLATE, Map.of("health", 30.0, "armor", 28.0)));
    player
        .getInventory()
        .setLeggings(item(Material.NETHERITE_LEGGINGS, Map.of("health", 18.0, "armor", 20.0)));
    player
        .getInventory()
        .setBoots(item(Material.NETHERITE_BOOTS, Map.of("health", 12.0, "armor", 16.0)));
    equipment.onJoin(new PlayerJoinEvent(player, ""));
    assertEquals(94, player.getAttribute(Attribute.MAX_HEALTH).getValue());
  }

  void detach() {
    equipment.onQuit(new PlayerQuitEvent(player, ""));
  }

  void attach() {
    equipment.onJoin(new PlayerJoinEvent(player, ""));
  }

  @ParameterizedTest
  @ValueSource(doubles = {94, 70, 28, 12})
  void reconnectPreservesActualHealthIncludingWounds(double health) {
    omega();
    player.setHealth(health);
    player.setFoodLevel(17);
    player.setSaturation(7);
    player.setExhaustion(2);
    player.setAbsorptionAmount(16);
    detach();
    assertEquals(20, player.getAttribute(Attribute.MAX_HEALTH).getValue());
    assertEquals(Math.min(20, health), player.getHealth());
    attach();
    assertEquals(94, player.getAttribute(Attribute.MAX_HEALTH).getValue());
    assertEquals(health, player.getHealth());
    assertEquals(17, player.getFoodLevel());
    assertEquals(7, player.getSaturation());
    assertEquals(2, player.getExhaustion());
    assertEquals(16, player.getAbsorptionAmount());
    attach();
    assertEquals(health, player.getHealth(), "Credit is consumed once");
  }

  @Test
  void reloadPreservesWoundsAndNeverStacksModifiers() {
    omega();
    player.setHealth(70);
    for (int i = 0; i < 3; i++) {
      plugin.reloadPlugin();
      assertEquals(70, player.getHealth());
      assertEquals(94, player.getAttribute(Attribute.MAX_HEALTH).getValue());
      assertEquals(
          1,
          player.getAttribute(Attribute.MAX_HEALTH).getModifiers().stream()
              .filter(mod -> mod.getKey().getNamespace().equals("themob"))
              .count());
    }
  }

  @Test
  void disconnectBeforePendingRestoreRetainsTheUnconsumedCredit() {
    omega();
    player.setHealth(94);
    detach();
    detach();
    attach();
    assertEquals(94, player.getHealth());
    player.setHealth(80);
    attach();
    assertEquals(80, player.getHealth());
  }

  @Test
  void changedEquipmentOnReconnectCapsHealthWithoutBankingAFreeHeal() {
    omega();
    player.setHealth(94);
    detach();
    player.getInventory().clear();
    attach();
    assertEquals(20, player.getHealth());
    omega();
    assertEquals(20, player.getHealth());
  }

  @Test
  void realHealthCreditDoesNotRefundDamageTakenWhileBonusesLoad() {
    omega();
    player.setHealth(94);
    detach();
    player.setHealth(15); // Five damage between removal and restoration.
    attach();
    assertEquals(89, player.getHealth());
  }

  @Test
  void foreignHealthModifiersAndBaseValuesSurviveEquipmentUpdates() {
    var max = player.getAttribute(Attribute.MAX_HEALTH);
    max.setBaseValue(24);
    var key = new NamespacedKey("foreign", "vitality");
    max.addModifier(
        new AttributeModifier(
            key, 10, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    player
        .getInventory()
        .setChestplate(item(Material.NETHERITE_CHESTPLATE, Map.of("health", 30.0)));
    attach();
    assertEquals(64, max.getValue());
    player.setHealth(60);
    detach();
    assertEquals(34, max.getValue());
    attach();
    assertEquals(60, player.getHealth());
    assertEquals(64, max.getValue());
    assertTrue(max.getModifiers().stream().anyMatch(mod -> mod.getKey().equals(key)));
  }

  @Test
  void armorBonusChangesDoNotHealAnAlreadyWoundedPlayer() {
    omega();
    player.setHealth(50);
    server.getScheduler().performTicks(11);
    assertEquals(50, player.getHealth());
    player.getInventory().setChestplate(null);
    server.getScheduler().performTicks(10);
    assertEquals(64, player.getAttribute(Attribute.MAX_HEALTH).getValue());
    assertEquals(50, player.getHealth());
  }

  @Test
  void defenseAppliesOnceToMeleeAndProjectilesAndStillHonorsProtectionCancellation() {
    player
        .getInventory()
        .setChestplate(item(Material.NETHERITE_CHESTPLATE, Map.of("defense", 200.0)));
    LivingEntity attacker =
        server.getWorld("world").spawn(new Location(server.getWorld("world"), 2, 64, 0), Pig.class);
    var melee =
        new EntityDamageByEntityEvent(
            attacker, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 20);
    server.getPluginManager().callEvent(melee);
    assertEquals(10, melee.getDamage());
    var arrow = mock(Arrow.class);
    when(arrow.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
    when(arrow.getPersistentDataContainer())
        .thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
    when(arrow.getShooter()).thenReturn(attacker);
    var ranged =
        new EntityDamageByEntityEvent(arrow, player, EntityDamageEvent.DamageCause.PROJECTILE, 20);
    server.getPluginManager().callEvent(ranged);
    assertEquals(10, ranged.getDamage());
    server
        .getPluginManager()
        .registerEvent(
            EntityDamageByEntityEvent.class,
            new Listener() {},
            EventPriority.LOWEST, // Veto before the NORMAL item/RPG calculation stage.
            (listener, event) -> ((EntityDamageByEntityEvent) event).setCancelled(true),
            plugin);
    var cancelled =
        new EntityDamageByEntityEvent(
            attacker, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 20);
    server.getPluginManager().callEvent(cancelled);
    assertTrue(cancelled.isCancelled());
    assertEquals(20, cancelled.getDamage());
  }

  @Test
  void defenseDoesNotReducePvpTwiceOrChangeStarvationAndFallDamage() {
    player
        .getInventory()
        .setChestplate(item(Material.NETHERITE_CHESTPLATE, Map.of("defense", 200.0)));
    var attacker = server.addPlayer();
    var pvp =
        new EntityDamageByEntityEvent(
            attacker, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 20);
    server.getPluginManager().callEvent(pvp);
    assertEquals(10, pvp.getDamage());
    for (var cause :
        new EntityDamageEvent.DamageCause[] {
          EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.STARVATION
        }) {
      var event = new EntityDamageEvent(player, cause, 10);
      server.getPluginManager().callEvent(event);
      assertEquals(10, event.getDamage());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deathAndRespawnNeverReplayHealthFromThePreviousLife(boolean respawn) {
    omega();
    player.setHealth(94);
    detach();
    if (respawn) {
      var event = mock(PlayerRespawnEvent.class);
      when(event.getPlayer()).thenReturn(player);
      equipment.onRespawn(event);
    } else {
      var event = mock(PlayerDeathEvent.class);
      when(event.getEntity()).thenReturn(player);
      equipment.onDeath(event);
    }
    attach();
    assertEquals(20, player.getHealth());
    assertEquals(94, player.getAttribute(Attribute.MAX_HEALTH).getValue());
  }
}
