package org.plugin.theMob.item.equipment;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;

/** Runs the real Skilltree effects from its built JAR; no copied provider classes. */
class SkilltreeHealthCompatibilityTest {
  @TempDir Path directory;
  ServerMock server;
  TheMob plugin;
  Player player;
  EquipmentBonusListener equipment;
  URLClassLoader loader;
  Object effects, profile, store;
  Class<?> profileType;

  static Stream<Arguments> orders() {
    return Stream.of(114.0, 70.0, 28.0)
        .flatMap(
            health ->
                Stream.of(false, true)
                    .flatMap(
                        detachFirst ->
                            Stream.of(false, true)
                                .map(
                                    restoreFirst ->
                                        Arguments.of(health, detachFirst, restoreFirst))));
  }

  static Object call(Object target, String name, Class<?>[] types, Object... args)
      throws Exception {
    Class<?> owner = target instanceof Class<?> type ? type : target.getClass();
    Method method = owner.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(target instanceof Class<?> ? null : target, args);
  }

  @BeforeEach
  void setup() throws Exception {
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
    ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
    var meta = chest.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "health"), PersistentDataType.DOUBLE, 74.0);
    chest.setItemMeta(meta);
    player.getInventory().setChestplate(chest);
    equipAttach();
    loader =
        new URLClassLoader(
            new java.net.URL[] {Path.of(System.getProperty("skilltree.test.jar")).toUri().toURL()},
            getClass().getClassLoader());
    Class<?> settingsType = loader.loadClass("ch.sasch.skilltree.SkillSettings");
    Object settings =
        call(
            settingsType,
            "read",
            new Class<?>[] {org.bukkit.configuration.file.FileConfiguration.class},
            new YamlConfiguration());
    Class<?> storeType = loader.loadClass("ch.sasch.skilltree.SkillStore");
    Constructor<?> storeConstructor = storeType.getDeclaredConstructor(Path.class);
    storeConstructor.setAccessible(true);
    store = storeConstructor.newInstance(directory.resolve("skills.properties"));
    Class<?> effectsType = loader.loadClass("ch.sasch.skilltree.SkillEffects");
    Constructor<?> effectsConstructor =
        effectsType.getDeclaredConstructor(storeType, Supplier.class);
    effectsConstructor.setAccessible(true);
    effects = effectsConstructor.newInstance(store, (Supplier<Object>) () -> settings);
    profileType = loader.loadClass("ch.sasch.skilltree.SkillProfile");
    Field empty = profileType.getDeclaredField("EMPTY");
    empty.setAccessible(true);
    Object observed = call(empty.get(null), "observe", new Class<?>[] {int.class}, 100);
    Class<?> skillType = loader.loadClass("ch.sasch.skilltree.Skill");
    Object health =
        Arrays.stream(skillType.getEnumConstants())
            .filter(value -> ((Enum<?>) value).name().equals("HEALTH"))
            .findFirst()
            .orElseThrow();
    profile = call(observed, "upgrade", new Class<?>[] {skillType, int.class}, health, 100);
    skillAttach();
    assertEquals(114, player.getAttribute(Attribute.MAX_HEALTH).getValue());
  }

  void equipAttach() {
    equipment.onJoin(new PlayerJoinEvent(player, ""));
  }

  void upgradeAndStore(String name, int ranks) throws Exception {
    Class<?> skillType = loader.loadClass("ch.sasch.skilltree.Skill");
    Object skill =
        Arrays.stream(skillType.getEnumConstants())
            .filter(value -> ((Enum<?>) value).name().equals(name))
            .findFirst()
            .orElseThrow();
    profile = call(profile, "upgrade", new Class<?>[] {skillType, int.class}, skill, ranks);
    call(
        store,
        "update",
        new Class<?>[] {java.util.UUID.class, java.util.function.UnaryOperator.class},
        player.getUniqueId(),
        (java.util.function.UnaryOperator<Object>) old -> profile);
    skillAttach();
  }

  @Test
  void currentSkilltreeSwordMasteryScalesTheMobWeaponBonusOnce() throws Exception {
    upgradeAndStore("SWORD_MASTERY", 5);
    server.getPluginManager().registerEvents((org.bukkit.event.Listener) effects, plugin);
    var sword = new ItemStack(Material.DIAMOND_SWORD);
    var meta = sword.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "damage"), PersistentDataType.DOUBLE, 25.0);
    sword.setItemMeta(meta);
    player.getInventory().setItemInMainHand(sword);
    var target =
        (org.bukkit.entity.LivingEntity)
            player
                .getWorld()
                .spawnEntity(
                    new Location(player.getWorld(), 2, 64, 0), org.bukkit.entity.EntityType.PIG);
    var hit =
        new org.bukkit.event.entity.EntityDamageByEntityEvent(
            player,
            target,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            10);
    server.getPluginManager().callEvent(hit);
    assertEquals(38.5, hit.getDamage(), 1e-8); // (10 native + 25 TheMob) * 1.10
  }

  @Test
  void currentSkilltreeHealingImprovesTheMobHealingThroughItsNativeEvent() throws Exception {
    upgradeAndStore("HEALING", 5);
    server.getPluginManager().registerEvents((org.bukkit.event.Listener) effects, plugin);
    player.setHealth(10);
    assertEquals(2.3, org.plugin.theMob.combat.PlayerHealing.heal(player, 2), 1e-8);
    assertEquals(12.3, player.getHealth(), 1e-8);
  }

  void equipDetach() {
    equipment.onQuit(new PlayerQuitEvent(player, ""));
  }

  void skillAttach() throws Exception {
    call(effects, "apply", new Class<?>[] {Player.class, profileType}, player, profile);
    call(effects, "restoreHealth", new Class<?>[] {Player.class}, player);
  }

  void skillDetach() throws Exception {
    call(effects, "detach", new Class<?>[] {Player.class}, player);
  }

  @AfterEach
  void close() throws Exception {
    try {
      MockBukkit.unmock();
    } finally {
      if (loader != null) loader.close();
    }
  }

  @ParameterizedTest
  @MethodSource("orders")
  void realEquipmentAndSkillModifiersPreserveHealthInEveryLoadOrder(
      double health, boolean equipmentDetachesFirst, boolean equipmentRestoresFirst)
      throws Exception {
    player.setHealth(health);
    player.setFoodLevel(17);
    if (equipmentDetachesFirst) {
      equipDetach();
      skillDetach();
    } else {
      skillDetach();
      equipDetach();
    }
    assertEquals(20, player.getAttribute(Attribute.MAX_HEALTH).getValue());
    assertEquals(20, player.getHealth());
    if (equipmentRestoresFirst) equipAttach();
    else skillAttach();
    player.setHealth(player.getHealth() - 5); // Damage while the second plugin is still loading.
    if (equipmentRestoresFirst) skillAttach();
    else equipAttach();
    assertEquals(114, player.getAttribute(Attribute.MAX_HEALTH).getValue());
    assertEquals(health - 5, player.getHealth());
    assertEquals(17, player.getFoodLevel());
    skillAttach();
    equipAttach();
    assertEquals(health - 5, player.getHealth(), "Neither provider may replay a health credit");
  }
}
