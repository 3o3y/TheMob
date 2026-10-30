package org.plugin.theMob.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossTemplateParser;

class ContentExpansionTest {
  ServerMock server;
  TheMob plugin;
  World world;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    plugin = MockBukkit.load(TheMob.class);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  @Test
  void coversEverySpawnableMobWithFiveLevelsAndTwentyBossVariants() {
    var species = MobCatalog.species();
    assertTrue(
        species.containsAll(
            List.of(
                EntityType.ZOMBIE,
                EntityType.WITHER,
                EntityType.ENDER_DRAGON,
                EntityType.COD,
                EntityType.ALLAY)));
    assertFalse(species.contains(EntityType.PLAYER));
    assertFalse(species.contains(EntityType.ARMOR_STAND));
    assertFalse(species.contains(EntityType.MANNEQUIN));
    for (EntityType type : species) {
      double previous = 0;
      for (int level = 1; level <= 5; level++) {
        String id = "tm_" + type.name().toLowerCase(Locale.ROOT) + "_lvl_" + level;
        var config = plugin.mobs().mobConfigById(id);
        assertNotNull(config, id);
        assertEquals(type.name(), config.getString("base-type"));
        assertEquals(level, config.getInt("level"));
        assertTrue(config.getDouble("stats.health.max") > previous);
        previous = config.getDouble("stats.health.max");
        assertEquals(id, plugin.configs().spawnEggs().getString("spawn-eggs." + id));
      }
    }
    for (String archetype : List.of("warlord", "marksman", "broodmother", "guardian"))
      for (int level = 1; level <= 5; level++) {
        String id = "tm_boss_" + archetype + "_lvl_" + level;
        var template = BossTemplateParser.tryParse(id, plugin.mobs().mobConfigById(id));
        assertNotNull(template);
        assertEquals(3, template.phases().size());
        assertEquals("guard", template.resolvePhase(99.999).id());
        assertEquals("pressure", template.resolvePhase(50).id());
        assertEquals("rage", template.resolvePhase(5).id());
      }
    System.out.println(
        "Catalog: "
            + species.size()
            + " species, "
            + (species.size() * 5)
            + " level variants, 20 archetype bosses; "
            + plugin.gear().ids().size()
            + " equipment variants");
  }

  @Test
  void allEquipmentHasRealEnchantmentsStarsStatsAndIndependentCopies() {
    assertEquals(160, plugin.gear().ids().size());
    for (String id : plugin.gear().ids()) {
      var item = plugin.gear().item(id);
      assertNotNull(item);
      var meta = item.getItemMeta();
      int stars =
          meta.getPersistentDataContainer()
              .get(new NamespacedKey(plugin, "stars"), PersistentDataType.INTEGER);
      assertTrue(stars >= 1 && stars <= 5);
      assertFalse(meta.getEnchants().isEmpty());
      meta.getEnchants()
          .forEach(
              (enchantment, level) -> {
                assertTrue(
                    level >= 1 && level <= enchantment.getMaxLevel(), id + " " + enchantment);
                assertTrue(
                    enchantment.canEnchantItem(item), id + " does not support " + enchantment);
              });
      assertEquals(
          id,
          meta.getPersistentDataContainer()
              .get(new NamespacedKey(plugin, "themob_item_id"), PersistentDataType.STRING));
      item.setAmount(20);
      assertEquals(1, plugin.gear().item(id).getAmount());
    }
  }

  @Test
  void gearCommandsAndCatalogReferencesProduceUsableItems() {
    var player = server.addPlayer();
    player.setOp(true);
    assertTrue(server.dispatchCommand(player, "mob item epic_sword_5"));
    assertEquals(Material.DIAMOND_SWORD, player.getInventory().getItem(0).getType());
    var item =
        new org.plugin.theMob.item.ItemBuilderFromConfig(plugin)
            .build(Map.of("catalog-item", "rare_helmet_3"));
    assertNotNull(item);
    assertEquals(
        3,
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(new NamespacedKey(plugin, "stars"), PersistentDataType.INTEGER));
    assertTrue(server.dispatchCommand(player, "mob egg tm_zombie_lvl_5"));
    var egg = player.getInventory().getItem(1);
    var eggs =
        new org.plugin.theMob.spawn.egg.SpawnEggService(
            plugin.mobs(), plugin.keys(), plugin.configs());
    assertEquals("tm_zombie_lvl_5", eggs.resolveMobIdFromEgg(egg, player));
  }

  @Test
  void sharedLootRequiresCreditedPlayerAndPreservesVanillaDropsAndNoDropFlag() {
    var pig = plugin.api().spawn("tm_pig_lvl_5", new Location(world, .5, 64, .5)).orElseThrow();
    var random = new Random(42);
    assertTrue(plugin.gear().roll(pig, 5, true, random).isEmpty());
    var killer = server.addPlayer();
    pig.setKiller(killer);
    var guaranteed = plugin.gear().roll(pig, 5, true, random);
    assertEquals(2, guaranteed.size());
    assertTrue(
        guaranteed.stream()
            .anyMatch(
                item ->
                    item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(new NamespacedKey(plugin, "stars"), PersistentDataType.INTEGER)));
    var death =
        new EntityDeathEvent(
            pig,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>(List.of(new ItemStack(Material.PORKCHOP))));
    server.getPluginManager().callEvent(death);
    assertTrue(death.getDrops().stream().anyMatch(item -> item.getType() == Material.PORKCHOP));
    var blocked = plugin.api().spawn("tm_pig_lvl_1", new Location(world, 4, 64, 4)).orElseThrow();
    blocked.setKiller(killer);
    blocked.getPersistentDataContainer().set(plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 1);
    var suppressed =
        new EntityDeathEvent(
            blocked,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>(List.of(new ItemStack(Material.PORKCHOP))));
    server.getPluginManager().callEvent(suppressed);
    assertTrue(suppressed.getDrops().isEmpty());
    assertEquals(0, suppressed.getDroppedExp());
  }

  @Test
  void customDefinitionWinsAndCatalogCanBeDisabledWithoutDeletingFiles() throws Exception {
    var file = new java.io.File(plugin.getDataFolder(), "mobs/own.yml");
    var config = new YamlConfiguration();
    config.set("mob-id", "tm_pig_lvl_1");
    config.set("base-type", "PIG");
    config.set("stats.health.max", 77);
    config.save(file);
    plugin.reloadPlugin();
    assertEquals(77, plugin.mobs().mobConfigById("tm_pig_lvl_1").getDouble("stats.health.max"));
    var settings = MobCatalog.settings(plugin);
    settings.set("enabled", false);
    settings.save(new java.io.File(plugin.getDataFolder(), "catalog.yml"));
    plugin.reloadPlugin();
    assertNull(plugin.mobs().mobConfigById("tm_zombie_lvl_5"));
    assertEquals(77, plugin.mobs().mobConfigById("tm_pig_lvl_1").getDouble("stats.health.max"));
  }

  @Test
  void nativeMeleeAndRangedDamageScaleWithLevelWithoutDoubleScalingBossSkills() {
    var mob = plugin.api().spawn("tm_zombie_lvl_5", new Location(world, .5, 64, .5)).orElseThrow();
    // MockBukkit omits the native zombie ATTACK_DAMAGE attribute; install that fixture explicitly.
    ((org.mockbukkit.mockbukkit.entity.LivingEntityMock) mob)
        .registerAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
    new org.plugin.theMob.mob.stats.BaseMobStatApplier(plugin.keys())
        .apply(mob, plugin.mobs().mobConfigOf(mob).getConfigurationSection("stats"));
    assertEquals(
        5.4, mob.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue(), .0001);
    var projectile = mock(Arrow.class);
    when(projectile.getShooter()).thenReturn(mob);
    when(projectile.getPersistentDataContainer()).thenReturn(mob.getPersistentDataContainer());
    var player = server.addPlayer();
    var damage =
        new org.bukkit.event.entity.EntityDamageByEntityEvent(
            projectile,
            player,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            4);
    var listener = new CatalogCombatListener(plugin);
    listener.damage(damage);
    assertEquals(7.2, damage.getDamage(), .0001);
    projectile
        .getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "catalog_skill"), PersistentDataType.BYTE, (byte) 1);
    damage.setDamage(4);
    listener.damage(damage);
    assertEquals(4, damage.getDamage());
  }
}
