package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PluginIntegrationTest {
  ServerMock server;
  TheMob plugin;

  @Test
  void realBossSpawnPathKeepsIndependentPhasesAndConfiguredHealth() {
    var apiEvents = new ArrayList<org.bukkit.event.Event>();
    for (Class<? extends org.bukkit.event.Event> type :
        List.of(
            org.plugin.theMob.api.event.TheMobSpawnEvent.class,
            org.plugin.theMob.api.event.TheMobPhaseChangeEvent.class))
      server
          .getPluginManager()
          .registerEvent(
              type,
              new org.bukkit.event.Listener() {},
              org.bukkit.event.EventPriority.MONITOR,
              (listener, event) -> apiEvents.add(event),
              plugin);
    server.getScheduler().performTicks(1);
    var config = plugin.mobs().mobConfigById("zombie_boss_template");
    for (String phase : config.getConfigurationSection("phases").getKeys(false)) {
      // Minion waves have their own test; this fixture avoids MockBukkit's missing persistence
      // toggle.
      config.set("phases." + phase + ".actions.summon-minions.enabled", false);
    }
    World realWorld = server.getWorld("world");
    var viewer = server.addPlayer();
    World adapter = org.mockito.Mockito.spy(realWorld);
    // Inside the title arena, outside MockBukkit's unimplemented nameplate LOS check.
    viewer.teleport(new Location(realWorld, 20, 64, 1));
    realWorld.getBlockAt(0, 63, 0).setType(Material.STONE);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              Location nativeLocation = ((Location) invocation.getArgument(0)).clone();
              nativeLocation.setWorld(realWorld);
              LivingEntity mob =
                  org.mockito.Mockito.spy(
                      (LivingEntity) realWorld.spawnEntity(nativeLocation, EntityType.ZOMBIE));
              org.mockito.Mockito.doNothing().when(mob).setRemoveWhenFarAway(false);
              org.mockito.Mockito.doNothing().when(mob).setCanPickupItems(false);
              org.mockito.Mockito.doNothing().when((Mob) mob).setAware(true);
              assertTrue(mob.isValid(), "Boss fixture must be valid before initialization");
              return mob;
            })
        .when(adapter)
        .spawnEntity(
            org.mockito.ArgumentMatchers.any(Location.class),
            org.mockito.ArgumentMatchers.eq(EntityType.ZOMBIE));
    LivingEntity first =
        plugin
            .mobs()
            .spawnCustomMob("zombie_boss_template", "egg", new Location(adapter, .5, 64, .5));
    assertNotNull(first);
    assertInstanceOf(org.plugin.theMob.api.event.TheMobSpawnEvent.class, apiEvents.get(0));
    var initial = (org.plugin.theMob.api.event.TheMobPhaseChangeEvent) apiEvents.get(1);
    assertNull(initial.getPreviousPhase());
    assertEquals("phase1", initial.getNextPhase());
    assertEquals(org.plugin.theMob.api.SpawnReason.EGG, initial.getMob().spawnReason());
    assertEquals(
        ChatColor.translateAlternateColorCodes(
            '&', plugin.bossPhases().currentPhase(first).title()),
        viewer.nextTitle());
    assertEquals(
        config.getDouble("stats.health.max"),
        first.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue());
    first.setHealth(first.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * .6);
    plugin.bossPhases().onBossUpdate(first);
    assertEquals("phase2", plugin.bossPhases().currentPhase(first).id());
    assertEquals(
        "hunt", plugin.bossPhases().currentPhase(first).cfg().getString("behavior.zombie-stage"));
    assertTrue(first.hasMetadata(org.plugin.theMob.boss.behavior.core.ZombieKingBehavior.METADATA));
    var change = (org.plugin.theMob.api.event.TheMobPhaseChangeEvent) apiEvents.get(2);
    assertEquals("phase1", change.getPreviousPhase());
    assertEquals("phase2", change.getNextPhase());
    assertEquals(
        ChatColor.translateAlternateColorCodes(
            '&', plugin.bossPhases().currentPhase(first).title()),
        viewer.nextTitle());
    LivingEntity second =
        plugin
            .mobs()
            .spawnCustomMob("zombie_boss_template", "spawner", new Location(adapter, .5, 64, .5));
    assertNotNull(second);
    assertEquals("phase1", plugin.bossPhases().currentPhase(second).id());
    assertEquals("phase2", plugin.bossPhases().currentPhase(first).id());
    first.setHealth(first.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * .4);
    plugin.bossPhases().onBossUpdate(first);
    assertEquals(
        "frenzy", plugin.bossPhases().currentPhase(first).cfg().getString("behavior.zombie-stage"));
    first.setHealth(first.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * .2);
    plugin.bossPhases().onBossUpdate(first);
    assertEquals(
        "desperate",
        plugin.bossPhases().currentPhase(first).cfg().getString("behavior.zombie-stage"));
    first.setHealth(first.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * .9);
    plugin.bossPhases().onBossUpdate(first);
    assertEquals("phase4", plugin.bossPhases().currentPhase(first).id());
    assertEquals("phase1", plugin.bossPhases().currentPhase(second).id());
  }

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
  }

  @AfterEach
  void teardown() {
    MockBukkit.unmock();
  }

  @Test
  void loadsAllBundledMechanics() {
    assertTrue(plugin.isEnabled());
    assertEquals(
        8 + org.plugin.theMob.content.MobCatalog.species().size() * 5 + 20,
        plugin.mobs().registeredIds().size());
    assertNotNull(plugin.mobs().bossTemplate("zombie_boss_template"));
  }

  @Test
  void reportedAutoSpawnCommandAndScheduledTicksRunWithoutErrors() throws Exception {
    var player = server.addPlayer();
    player.setOp(true);
    player.teleport(new Location(server.getWorld("world"), .5, 64, .5));
    assertTrue(server.dispatchCommand(player, "mob set autospawn chicken_baby 10 5"));
    assertTrue(
        java.nio.file.Files.readString(
                new java.io.File(plugin.getDataFolder(), "auto_spawn.yml").toPath())
            .contains("chicken_baby"));
    // Leave the arena cold: native chunk tickets are not implemented by MockBukkit.
    player.teleport(new Location(server.getWorld("world"), 1000, 64, 1000));
    server.getScheduler().performTicks(41);
    assertEquals(41, plugin.automation().tps().currentTick());
  }

  @Test
  void configuredItemNameLoreAndStatsUseCommonBukkitMetadata() {
    var builder = new org.plugin.theMob.item.ItemBuilderFromConfig(plugin);
    var item =
        builder.build(
            Map.of(
                "material",
                "DIAMOND_SWORD",
                "name",
                List.of("&bMob Sword", "&7First line"),
                "lore",
                List.of("&aSecond line"),
                "stats",
                Map.of("damage", 25)));
    assertNotNull(item);
    assertEquals("§bMob Sword", item.getItemMeta().getDisplayName());
    assertEquals(List.of("§7First line", "§aSecond line"), item.getItemMeta().getLore());
    new org.plugin.theMob.item.ItemLoreRenderer().apply(item);
    assertTrue(item.getItemMeta().getLore().contains("§f- Damage: §a+25"));
    assertEquals(
        25.0,
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(new NamespacedKey(plugin, "damage"), PersistentDataType.DOUBLE));
  }

  @Test
  void bukkitBase64ItemsRoundTripWithTheirMetadata() throws Exception {
    var item = new ItemStack(Material.DIAMOND_SWORD);
    var meta = item.getItemMeta();
    meta.setDisplayName("§bSaved Sword");
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "damage"), PersistentDataType.DOUBLE, 27.0);
    item.setItemMeta(meta);
    var bytes = new java.io.ByteArrayOutputStream();
    try (var out = new org.bukkit.util.io.BukkitObjectOutputStream(bytes)) {
      out.writeObject(item);
    }
    var restored =
        new org.plugin.theMob.item.ItemBuilderFromConfig(plugin)
            .fromBase64(Base64.getEncoder().encodeToString(bytes.toByteArray()));
    assertEquals(item, restored);
  }

  @Test
  void tenReloadsDoNotAccumulateTasks() {
    int baseline = server.getScheduler().getPendingTasks().size();
    for (int i = 0; i < 10; i++) plugin.reloadPlugin();
    assertEquals(baseline, server.getScheduler().getPendingTasks().size());
  }

  @Test
  void itemStatsAreCountedOnceAndForeignNamespacesIgnored() {
    var player = server.addPlayer();
    var item = new ItemStack(Material.DIAMOND_SWORD);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "damage"), PersistentDataType.DOUBLE, 25.0);
    meta.getPersistentDataContainer()
        .set(new NamespacedKey("otherplugin", "damage"), PersistentDataType.DOUBLE, 900.0);
    item.setItemMeta(meta);
    player.getInventory().setItemInMainHand(item);
    var cache = new org.plugin.theMob.item.equipment.EquipmentBonuses(plugin);
    assertEquals(25, cache.get(player).get("damage"));
    var enchants = new org.plugin.theMob.item.CustomEnchantSystem(plugin);
    assertEquals(25, enchants.collect(meta).get("damage"));
  }

  @Test
  void sqliteAndFileCooldownsSurviveRecreation() {
    for (String backend : List.of("file", "sqlite")) {
      var config = new YamlConfiguration();
      config.set("boss-cooldowns.storage", backend);
      var first = new org.plugin.theMob.control.cooldown.BossCooldownService(plugin);
      first.reload(config);
      first.setCooldownSeconds("testboss", 60);
      first.shutdown();
      var second = new org.plugin.theMob.control.cooldown.BossCooldownService(plugin);
      second.reload(config);
      assertFalse(second.canSpawnNow("testboss"));
      assertTrue(second.secondsRemaining("testboss") > 0);
      second.shutdown();
    }
  }

  private LivingEntity spawnPig(String source) {
    server.getScheduler().performTicks(1);
    World world = server.getWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    LivingEntity entity =
        plugin.mobs().spawnCustomMob("pig_lvl_1", source, new Location(world, 0.5, 64, 0.5));
    assertNotNull(entity);
    return entity;
  }

  @Test
  void everyMobSpawnSourceUsesTheGlobalBudget() {
    plugin.getConfig().set("global-budgets.total-mobs", 1);
    plugin.automation().reload(plugin.getConfig());
    LivingEntity first = spawnPig("egg");
    assertEquals(1, plugin.automation().budgets().aliveTotal());
    for (String source : List.of("spawner", "egg", "manual-auto"))
      assertNull(plugin.mobs().spawnCustomMob("pig_lvl_1", source, first.getLocation()));
  }

  @Test
  void bossDeathAwardsConfiguredXpOnceWithoutSpawningExtraOrbs() {
    LivingEntity pig = spawnPig("egg");
    pig.getPersistentDataContainer().set(plugin.keys().IS_BOSS, PersistentDataType.INTEGER, 1);
    plugin.mobs().mobConfigOf(pig).set("xp", 250);
    var event =
        new EntityDeathEvent(
            pig,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>());
    server.getPluginManager().callEvent(event);
    assertEquals(250, event.getDroppedExp());
    assertTrue(pig.getWorld().getEntitiesByClass(ExperienceOrb.class).isEmpty());
  }

  @Test
  void minionNoDropsSuppressesVanillaDropsAndXp() {
    LivingEntity minion =
        (LivingEntity)
            server
                .getWorld("world")
                .spawnEntity(new Location(server.getWorld("world"), 0, 64, 0), EntityType.ZOMBIE);
    minion.getPersistentDataContainer().set(plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 1);
    var controller = new org.plugin.theMob.boss.minion.BossMinionController(plugin);
    controller.register(UUID.randomUUID(), "phase", minion);
    var event =
        new EntityDeathEvent(
            minion,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>(List.of(new ItemStack(Material.ROTTEN_FLESH))));
    event.setDroppedExp(10);
    server.getPluginManager().callEvent(event);
    assertEquals(0, event.getDroppedExp());
    assertTrue(event.getDrops().isEmpty());
  }

  @Test
  void normalDamageAndPercentLifestealUseResolvedDamage() {
    server.getScheduler().performTicks(1);
    var player = server.addPlayer();
    player.setHealth(10);
    var item = new ItemStack(Material.STICK);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(plugin.keys().LIFESTEAL, PersistentDataType.DOUBLE, 20.0);
    item.setItemMeta(meta);
    player.getInventory().setItemInMainHand(item);
    LivingEntity target =
        (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
    var event =
        new EntityDamageByEntityEvent(
            player,
            target,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK)
                .withCausingEntity(player)
                .withDirectEntity(player)
                .build(),
            10);
    server.getPluginManager().callEvent(event);
    assertEquals(10, event.getDamage());
    server.getScheduler().performTicks(1);
    assertEquals(12, player.getHealth(), 1e-9);
  }

  @Test
  void subsequentlyCancelledHitCannotForceDeathOrHeal() {
    server.getScheduler().performTicks(1);
    var player = server.addPlayer();
    player.setHealth(10);
    LivingEntity target =
        (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
    var event =
        new EntityDamageByEntityEvent(
            player,
            target,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK)
                .withCausingEntity(player)
                .withDirectEntity(player)
                .build(),
            100);
    server.getPluginManager().callEvent(event);
    event.setCancelled(true);
    server.getScheduler().performTicks(1);
    assertEquals(20, target.getHealth());
    assertEquals(10, player.getHealth());
  }

  @Test
  void activeBossCooldownBlocksAllDirectSpawnSources() {
    plugin.automation().bossCooldowns().setCooldownSeconds("zombie_boss_template", 60);
    for (String source : List.of("egg", "spawner", "auto"))
      assertNull(
          plugin
              .mobs()
              .spawnCustomMob(
                  "zombie_boss_template",
                  source,
                  new Location(server.getWorld("world"), 0, 64, 0)));
  }

  @Test
  void minionSpawnerObeysGlobalMinionCap() {
    server.getScheduler().performTicks(1);
    plugin.getConfig().set("global-budgets.minions", 1);
    plugin.automation().reload(plugin.getConfig());
    World realWorld = server.getWorld("world");
    World adapter = org.mockito.Mockito.spy(realWorld);
    LivingEntity boss =
        org.mockito.Mockito.spy(
            (LivingEntity)
                realWorld.spawnEntity(new Location(realWorld, 0, 64, 0), EntityType.ZOMBIE));
    org.mockito.Mockito.doReturn(adapter).when(boss).getWorld();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              LivingEntity minion =
                  org.mockito.Mockito.spy(
                      (LivingEntity)
                          realWorld.spawnEntity(
                              invocation.getArgument(0), invocation.getArgument(1)));
              // MockBukkit does not implement this persistence toggle; actual spawn/budget logic
              // remains real.
              org.mockito.Mockito.doNothing().when(minion).setRemoveWhenFarAway(false);
              return minion;
            })
        .when(adapter)
        .spawnEntity(
            org.mockito.ArgumentMatchers.any(Location.class),
            org.mockito.ArgumentMatchers.eq(EntityType.ZOMBIE));
    var controller = new org.plugin.theMob.boss.minion.BossMinionController(plugin);
    var spawner = new org.plugin.theMob.boss.minion.BossMinionSpawner(plugin, controller);
    var cfg = new YamlConfiguration();
    cfg.set("enabled", true);
    cfg.set("amount", 10);
    cfg.set("type", "ZOMBIE");
    spawner.onPhaseEnter(boss, new org.plugin.theMob.boss.BossPhase("phase", 0, 100, "", cfg), cfg);
    assertEquals(1, plugin.automation().budgets().aliveMinions());
    assertEquals(1, controller.countBossMinions(boss.getUniqueId()));
    controller.shutdown();
    spawner.shutdown();
  }

  @Test
  void linkedSpawnersCancelTheirVanillaSpawns() {
    var world = server.getWorld("world");
    var block = world.getBlockAt(0, 64, 0);
    block.setType(Material.SPAWNER);
    var spawner = (org.bukkit.block.CreatureSpawner) block.getState();
    spawner
        .getPersistentDataContainer()
        .set(plugin.keys().THEMOB_SPAWNER, PersistentDataType.BYTE, (byte) 1);
    spawner.update();
    var entity = world.spawnEntity(new Location(world, 1, 64, 1), EntityType.PIG);
    var event = new SpawnerSpawnEvent(entity, (org.bukkit.block.CreatureSpawner) block.getState());
    server.getPluginManager().callEvent(event);
    assertTrue(event.isCancelled());
  }

  @Test
  void customHealthUsesOwnModifierAndPreservesOtherPluginsBaseHealth() {
    server.getScheduler().performTicks(1);
    var player = server.addPlayer();
    var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
    attribute.setBaseValue(24);
    var helmet = new ItemStack(Material.DIAMOND_HELMET);
    var meta = helmet.getItemMeta();
    meta.getPersistentDataContainer().set(plugin.keys().HEALTH, PersistentDataType.DOUBLE, 10.0);
    helmet.setItemMeta(meta);
    player.getInventory().setHelmet(helmet);
    server.getScheduler().performTicks(10);
    assertEquals(24, attribute.getBaseValue());
    assertEquals(34, attribute.getValue());
    player.getInventory().setHelmet(null);
    server.getScheduler().performTicks(10);
    assertEquals(24, attribute.getValue());
  }

  @Test
  void independentBossWeatherResetsWhenLeavingBothArenas() {
    var player = server.addPlayer();
    World world = player.getWorld();
    LivingEntity first =
        (LivingEntity) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.ZOMBIE);
    LivingEntity second =
        (LivingEntity) world.spawnEntity(new Location(world, 100, 64, 0), EntityType.ZOMBIE);
    var effects = new org.plugin.theMob.boss.world.BossWorldEffectController(plugin);
    player.teleport(new Location(world, 1, 64, 0));
    effects.apply(first, 8, "RAIN", "NIGHT");
    effects.apply(second, 8, "CLEAR", "DAY");
    assertEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
    assertEquals(13000, player.getPlayerTime());
    player.teleport(new Location(world, 101, 64, 0));
    effects.tick();
    effects.reset(first);
    assertEquals(WeatherType.CLEAR, player.getPlayerWeather());
    player.teleport(new Location(world, 300, 64, 0));
    effects.tick();
    assertNull(player.getPlayerWeather());
    effects.shutdown();
  }
}
