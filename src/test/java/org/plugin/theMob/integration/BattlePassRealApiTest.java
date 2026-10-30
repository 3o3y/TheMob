package org.plugin.theMob.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

/** Uses the user's actual premium API classes; no provider classes are bundled or imitated. */
class BattlePassRealApiTest {
  ServerMock server;
  TheMob plugin;
  Player player;
  LivingEntity mob;
  URLClassLoader loader;
  Object registry;
  Class<?> registryType;
  List<Object> executions;

  Class<?> type(String name) throws Exception {
    return Class.forName("net.advancedplugins.bp.impl.actions." + name, true, loader);
  }

  URLClassLoader providerLoader() throws Exception {
    return new URLClassLoader(
        new java.net.URL[] {Path.of(System.getProperty("battlepass.test.jar")).toUri().toURL()},
        getClass().getClassLoader()) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("me.clip.") || name.startsWith("net.milkbowl."))
          throw new ClassNotFoundException("Optional provider intentionally absent: " + name);
        return super.loadClass(name, resolve);
      }
    };
  }

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    plugin = MockBukkit.load(TheMob.class);
    player = server.addPlayer();
    mob = plugin.api().spawn("pig_lvl_1", new Location(world, .5, 64, .5)).orElseThrow();
    loader = providerLoader();
    executions = new ArrayList<>();
    var readerType = type("ActionsReader");
    Object reader =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {readerType},
            (p, m, a) -> {
              if (m.getName().equals("onAction")) {
                executions.add(a[0]);
                return null;
              }
              if (m.getName().equals("toString")) return "TheMob API test reader";
              throw new UnsupportedOperationException(m.toString());
            });
    registryType = type("ActionRegistry");
    registry =
        registryType
            .getConstructor(JavaPlugin.class, readerType, boolean.class, Set.class)
            .newInstance(plugin, reader, false, Set.of());
    var hook =
        new BattlePassHook(
            plugin,
            type("containers.ExternalActionContainer"),
            BattlePassHook.currentRegistry(registryType));
    var field = IntegrationManager.class.getDeclaredField("battlepass");
    field.setAccessible(true);
    field.set(plugin.integrations(), hook);
    active();
  }

  @SuppressWarnings("unchecked")
  void active() throws Exception {
    var field = IntegrationManager.class.getDeclaredField("states");
    field.setAccessible(true);
    ((Map<String, IntegrationStatus>) field.get(plugin.integrations()))
        .put(
            "BattlePass",
            new IntegrationStatus("BattlePass", IntegrationStatus.State.ACTIVE, "5.0.12 real API"));
  }

  @AfterEach
  void teardown() throws Exception {
    try {
      MockBukkit.unmock();
    } finally {
      if (loader != null) loader.close();
    }
  }

  TheMobDeathEvent death(boolean boss, boolean minion, SpawnReason reason, Player killer) {
    return new TheMobDeathEvent(
        mob,
        new MobSnapshot(
            mob.getUniqueId(),
            boss || minion ? "zombie_boss_template" : "pig_lvl_1",
            "Mob",
            boss,
            minion,
            boss ? "rage" : null,
            reason),
        killer);
  }

  List<String> actions() throws Exception {
    var list = new ArrayList<String>();
    for (Object execution : executions) list.add((String) Reflect.call(execution, "getQuestType"));
    return list;
  }

  double eligible(Object execution, String root, Map<String, String> filters) throws Exception {
    Object variable =
        type("objects.variable.Variable")
            .getConstructor(String.class, Map.class)
            .newInstance(root, filters);
    return ((Number)
            Reflect.call(Reflect.call(execution, "getQuestResult"), "isEligible", player, variable))
        .doubleValue();
  }

  // Test-only constructor bypass: starting BattlePlugin would also start its DB and network
  // services.
  Object nativeShell(Class<?> type) throws Exception {
    Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
    var access = unsafeType.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    return unsafeType.getMethod("allocateInstance", Class.class).invoke(access.get(null), type);
  }

  void nativeField(Class<?> owner, Object target, String name, Class<?> fieldType, Object value)
      throws Exception {
    var lookup =
        java.lang.invoke.MethodHandles.privateLookupIn(
            owner, java.lang.invoke.MethodHandles.lookup());
    if (target == null) lookup.findStaticVarHandle(owner, name, fieldType).set(value);
    else {
      var handle = lookup.findVarHandle(owner, name, fieldType);
      if (handle.isAccessModeSupported(java.lang.invoke.VarHandle.AccessMode.SET))
        handle.set(target, value);
      else {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
      }
    }
  }

  @Test
  void nativeQuestPipelineRecordsFirstKillAsOneOfTwentyWithoutPlaceholderApi() throws Exception {
    assertNativePipelineProgress(false);
  }

  @Test
  void nativeQuestPipelineRecordsDungeonCompletionWithoutPlaceholderApi() throws Exception {
    assertNativePipelineProgress(true);
  }

  org.plugin.theMob.api.dungeon.DungeonCompletedEvent completion(List<Player> players) {
    return new org.plugin.theMob.api.dungeon.DungeonCompletedEvent(
        new DungeonRegion("d00001", player.getWorld().getUID(), 0, 0, 113, 133, true),
        UUID.randomUUID(),
        players,
        "zombie_boss_template",
        "gottesturm",
        player.getWorld().getName(),
        10);
  }

  @Test
  void dungeonCompletionPublishesSeparateNativeActionsForEveryParticipantWithFilters()
      throws Exception {
    Player second = server.addPlayer();
    server.getPluginManager().callEvent(completion(List.of(player, second)));
    assertEquals(List.of("themob_dungeon_complete", "themob_dungeon_complete"), actions());
    assertEquals(
        Set.of(player, second),
        Set.of(
            Reflect.call(executions.get(0), "getPlayer"),
            Reflect.call(executions.get(1), "getPlayer")));
    assertEquals(
        1,
        eligible(
            executions.getFirst(),
            "d00001",
            Map.of(
                "style",
                "gottesturm",
                "world",
                "world",
                "boss",
                "zombie_boss_template",
                "levels",
                "10")));
    assertEquals(0, eligible(executions.getFirst(), "other_dungeon", Map.of()));
    assertEquals(0, eligible(executions.getFirst(), "none", Map.of("style", "mine")));
  }

  private void assertNativePipelineProgress(boolean dungeon) throws Exception {
    assertNativePipelineProgress(
        dungeon ? "themob_dungeon_complete" : "themob_kill",
        java.math.BigDecimal.ONE,
        () -> {
          mob.setKiller(player);
          if (dungeon) server.getPluginManager().callEvent(completion(List.of(player)));
          else
            server
                .getPluginManager()
                .callEvent(
                    new EntityDeathEvent(
                        mob,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC)
                            .build(),
                        new ArrayList<>()));
        });
  }

  private void assertNativePipelineProgress(
      String action, java.math.BigDecimal expected, Runnable emit) throws Exception {
    Class<?> mainType = Class.forName("io.github.battlepass.BattlePlugin", true, loader);
    Object main = nativeShell(mainType);
    Class<?> advanced = mainType.getSuperclass();
    nativeField(advanced, null, "instance", advanced, main);
    nativeField(JavaPlugin.class, main, "server", Server.class, server);
    Class<?> userType = loader.loadClass("io.github.battlepass.entity.base.UserEntity");
    Object user =
        userType.getConstructor(UUID.class, String.class).newInstance(player.getUniqueId(), "free");
    Class<?> cacheType =
        loader.loadClass("net.advancedplugins.utils.data.cache.iface.IAsyncSavableCache");
    Object users =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {cacheType},
            (p, m, a) -> {
              if (m.getName().equals("getOrNull")) return user;
              if (m.getName().equals("toString")) return "In-memory BattlePass test cache";
              return null;
            });
    nativeField(mainType, main, "userCache", cacheType, users);
    Class<?> boostersType = loader.loadClass("io.github.battlepass.controller.BoostersController");
    nativeField(mainType, main, "boostersController", boostersType, mock(boostersType));
    Object variable =
        type("objects.variable.Variable").getConstructor(String.class).newInstance("none");
    Class<?> stepType = loader.loadClass("io.github.battlepass.objects.quests.Quest$Step");
    Object step =
        stepType.getConstructors()[0].newInstance(
            "default",
            action,
            variable,
            java.math.BigDecimal.valueOf(20),
            10,
            Map.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            List.of(),
            Map.of(),
            null,
            false,
            List.of());
    Class<?> questType = loader.loadClass("io.github.battlepass.objects.quests.Quest");
    Class<?> configType = loader.loadClass("me.hyfe.simplespigot.config.Config");
    Object quest =
        questType
            .getConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                org.bukkit.inventory.ItemStack.class,
                Set.class,
                List.class,
                configType)
            .newInstance(
                "9101",
                "week-0",
                "Twenty kills",
                "easy",
                new org.bukkit.inventory.ItemStack(Material.IRON_SWORD),
                Set.of(),
                List.of(step),
                null);
    Class<?> controllerType = loader.loadClass("io.github.battlepass.controller.QuestController");
    Object controller = controllerType.getConstructor(mainType).newInstance(main);
    Class<?> apiType = loader.loadClass("io.github.battlepass.api.BattlePassApi");
    Object api = mock(apiType);
    Class<?> completionType =
        loader.loadClass("io.github.battlepass.quests.workers.pipeline.steps.QuestCompletionStep");
    Object completion = nativeShell(completionType);
    nativeField(completionType, completion, "controller", controllerType, controller);
    Class<?> notificationType =
        loader.loadClass("io.github.battlepass.quests.workers.pipeline.steps.NotificationStep");
    // Notifications/client rendering are outside this progression test.
    nativeField(
        completionType, completion, "notificationStep", notificationType, mock(notificationType));
    Class<?> validationType =
        loader.loadClass("io.github.battlepass.quests.workers.pipeline.steps.QuestValidationStep");
    Object validation = nativeShell(validationType);
    nativeField(validationType, validation, "plugin", mainType, main);
    nativeField(validationType, validation, "api", apiType, api);
    nativeField(validationType, validation, "controller", controllerType, controller);
    nativeField(validationType, validation, "completionStep", completionType, completion);
    nativeField(
        validationType,
        validation,
        "questLock",
        java.util.concurrent.locks.ReentrantLock.class,
        new java.util.concurrent.locks.ReentrantLock());
    nativeField(validationType, validation, "whitelistedWorlds", Set.class, Set.of());
    nativeField(validationType, validation, "blacklistedWorlds", Set.class, Set.of());
    Class<?> abuseType =
        loader.loadClass(
            "io.github.battlepass.quests.workers.pipeline.processors.AntiAbuseProcessor");
    Object abuse = nativeShell(abuseType);
    nativeField(abuseType, abuse, "controller", controllerType, controller);
    nativeField(validationType, validation, "antiAbuseProcessor", abuseType, abuse);
    Class<?> questsType = loader.loadClass("io.github.battlepass.cache.QuestCache");
    Object quests = mock(questsType);
    when(Reflect.call(quests, "getAllQuests")).thenReturn(Set.of(quest));
    Class<?> pipelineType =
        loader.loadClass("io.github.battlepass.quests.workers.pipeline.QuestPipeline");
    Object pipeline = nativeShell(pipelineType);
    nativeField(pipelineType, pipeline, "plugin", mainType, main);
    nativeField(pipelineType, pipeline, "userCache", cacheType, users);
    nativeField(pipelineType, pipeline, "questCache", questsType, quests);
    nativeField(pipelineType, pipeline, "questValidationStep", validationType, validation);
    assertEquals(
        java.math.BigDecimal.ZERO, Reflect.call(controller, "getQuestProgress", user, quest));
    emit.run();
    for (Object execution : executions) Reflect.call(pipeline, "handle", execution);
    assertEquals(
        0,
        expected.compareTo(
            (java.math.BigDecimal) Reflect.call(controller, "getQuestProgress", user, quest)));
    assertEquals(1, ((Map<?, ?>) Reflect.call(user, "getQuestData")).size());
    assertEquals(java.math.BigDecimal.valueOf(20), Reflect.call(quest, "getRequiredProgress"));
  }

  LootSource source() {
    return new LootSource(
        "zombie_boss_template",
        "phase4",
        SpawnReason.API,
        true,
        false,
        5,
        "world",
        player.getUniqueId());
  }

  MobSnapshot bossSnapshot() {
    return new MobSnapshot(
        mob.getUniqueId(), "zombie_boss_template", "Boss", true, false, "phase4", SpawnReason.API);
  }

  TheMobLootEvent loot(int amount) {
    var item = plugin.api().createItem("epic_sword_5").orElseThrow();
    item.setAmount(amount);
    return new TheMobLootEvent(
        player, source(), item, plugin.api().inspectItem(item).orElseThrow());
  }

  @Test
  void phaseAndDamageUseRealFiltersAndDecimalProgress() throws Exception {
    server
        .getPluginManager()
        .callEvent(
            new TheMobPhaseChangeEvent(mob, bossSnapshot(), null, "phase1", List.of(player)));
    assertTrue(executions.isEmpty());
    var second = server.addPlayer();
    server
        .getPluginManager()
        .callEvent(
            new TheMobPhaseChangeEvent(
                mob, bossSnapshot(), "phase3", "phase4", List.of(player, second)));
    assertEquals(List.of("themob_phase_enter", "themob_phase_enter"), actions());
    assertEquals(
        1,
        eligible(
            executions.getFirst(),
            "phase4",
            Map.of("mob", "zombie_boss_template", "previous-phase", "phase3", "min-level", "3")));
    assertEquals(0, eligible(executions.getFirst(), "phase2", Map.of()));
    executions.clear();
    server.getPluginManager().callEvent(new TheMobDamageEvent(player, mob, bossSnapshot(), 2.75));
    assertEquals(List.of("themob_damage"), actions());
    assertEquals(
        new java.math.BigDecimal("2.75"),
        Reflect.call(executions.getFirst(), "getDecimalProgress"));
    assertEquals(
        1,
        eligible(
            executions.getFirst(),
            "zombie_boss_template",
            Map.of("phase", "phase4", "min-level", "5", "max-level", "5")));
    assertEquals(0, eligible(executions.getFirst(), "none", Map.of("min-level", "6")));
    assertEquals(0, eligible(executions.getFirst(), "none", Map.of("min-level", "broken")));
  }

  @Test
  void lootFiltersUseActualAmountIdentityRarityStarsAndOwnKill() throws Exception {
    server.getPluginManager().callEvent(loot(3));
    Object execution = executions.getFirst();
    assertEquals(List.of("themob_loot"), actions());
    assertEquals(new java.math.BigDecimal("3"), Reflect.call(execution, "getDecimalProgress"));
    assertEquals(
        1,
        eligible(
            execution,
            "diamond_sword",
            Map.of(
                "item",
                "epic_sword_5",
                "rarity",
                "epic",
                "stars",
                "5",
                "min-stars",
                "3",
                "max-stars",
                "5",
                "own-kill",
                "true")));
    assertEquals(0, eligible(execution, "none", Map.of("rarity", "rare")));
    assertEquals(0, eligible(execution, "none", Map.of("max-stars", "4")));
    assertEquals(0, eligible(execution, "none", Map.of("min-stars", "-1")));
    assertEquals(0, eligible(execution, "none", Map.of("min-stars", "6")));
    assertEquals(0, eligible(execution, "none", Map.of("own-kill", "false")));
    var event = loot(3);
    var item = mob.getWorld().dropItem(mob.getLocation(), event.getItem());
    server
        .getPluginManager()
        .callEvent(
            new TheMobDropEvent(
                item, bossSnapshot(), source(), event.getItem(), event.getItemSnapshot()));
    assertEquals("themob_drop", actions().getLast());
    assertEquals(
        new java.math.BigDecimal("3"), Reflect.call(executions.getLast(), "getDecimalProgress"));
  }

  @Test
  void nativeQuestPipelineRecordsFractionalDamage() throws Exception {
    assertNativePipelineProgress(
        "themob_damage",
        new java.math.BigDecimal("2.75"),
        () ->
            server
                .getPluginManager()
                .callEvent(new TheMobDamageEvent(player, mob, bossSnapshot(), 2.75)));
  }

  @Test
  void nativeQuestPipelineRecordsLootAmountRatherThanOneStack() throws Exception {
    assertNativePipelineProgress(
        "themob_loot",
        java.math.BigDecimal.valueOf(3),
        () -> server.getPluginManager().callEvent(loot(3)));
  }

  @Test
  void nativeQuestPipelineRecordsParticipatingPhaseChange() throws Exception {
    assertNativePipelineProgress(
        "themob_phase_enter",
        java.math.BigDecimal.ONE,
        () ->
            server
                .getPluginManager()
                .callEvent(
                    new TheMobPhaseChangeEvent(
                        mob, bossSnapshot(), "phase3", "phase4", List.of(player))));
  }

  @Test
  void nativeQuestPipelineRecordsDropAmount() throws Exception {
    var loot = loot(3);
    Item item = mob.getWorld().dropItem(mob.getLocation(), loot.getItem());
    assertNativePipelineProgress(
        "themob_drop",
        java.math.BigDecimal.valueOf(3),
        () ->
            server
                .getPluginManager()
                .callEvent(
                    new TheMobDropEvent(
                        item, bossSnapshot(), source(), loot.getItem(), loot.getItemSnapshot())));
  }

  @Test
  void startupDiscoveryAvoidsLinkingOptionalPlaceholderAndEconomyApis() throws Exception {
    assertThrows(
        ClassNotFoundException.class,
        () -> loader.loadClass("me.clip.placeholderapi.expansion.PlaceholderExpansion"));
    assertThrows(
        ClassNotFoundException.class, () -> loader.loadClass("net.milkbowl.vault.economy.Economy"));
    Class<?> main = Class.forName("io.github.battlepass.BattlePlugin", false, loader);
    // Reproduce the old startup path. Enumerating unrelated main-plugin methods links PAPI.
    assertThrows(NoClassDefFoundError.class, main::getDeclaredMethods);
    assertSame(registry, BattlePassHook.currentRegistry(registryType));
    plugin.integrations().killed(death(false, false, SpawnReason.API, player));
    assertEquals(List.of("themob_kill", "themob_objective"), actions());
  }

  @Test
  void registersV5FactoryAndExecutesNativeFiltersAndProfiles() throws Exception {
    plugin.integrations().killed(death(true, false, SpawnReason.AUTO_SPAWN, player));
    assertEquals(
        List.of(
            "themob_kill",
            "themob_boss_kill",
            "themob_objective",
            "themob_objective",
            "themob_objective"),
        actions());
    Object kill = executions.getFirst();
    assertSame(player, Reflect.call(kill, "getPlayer"));
    assertEquals(java.math.BigInteger.ONE, Reflect.call(kill, "getProgress"));
    assertFalse((Boolean) Reflect.call(kill, "canBeAsync"));
    assertEquals(
        1,
        eligible(
            kill,
            "zombie_boss_template",
            Map.of(
                "world", "world", "phase", "rage", "spawn-reason", "auto_spawn", "boss", "true")));
    assertEquals(0, eligible(kill, "pig_lvl_1", Map.of()));
    assertEquals(0, eligible(kill, "none", Map.of("world", "arena")));
    assertEquals(0, eligible(kill, "none", Map.of("phase", "base")));
    assertEquals(0, eligible(kill, "none", Map.of("spawn-reason", "egg")));
    assertEquals(
        1, eligible(executions.get(3), "bossjaeger", Map.of("mob", "zombie_boss_template")));
  }

  @Test
  void ignoresUncreditedAndNpcKillsAndMinionsUnlessEnabled() throws Exception {
    plugin.integrations().killed(death(false, false, SpawnReason.API, null));
    player.setMetadata("NPC", new FixedMetadataValue(plugin, true));
    plugin.integrations().killed(death(false, false, SpawnReason.API, player));
    player.removeMetadata("NPC", plugin);
    mob.setMetadata("NPC", new FixedMetadataValue(plugin, true));
    plugin.integrations().killed(death(false, false, SpawnReason.API, player));
    mob.removeMetadata("NPC", plugin);
    plugin.integrations().killed(death(false, true, SpawnReason.MINION, player));
    assertTrue(executions.isEmpty());
    plugin.getConfig().set("integrations.track-minion-kills", true);
    plugin.integrations().killed(death(false, true, SpawnReason.MINION, player));
    assertEquals(List.of("themob_kill", "themob_minion_kill", "themob_objective"), actions());
    assertEquals(1, eligible(executions.get(1), "zombie_boss_template", Map.of("minion", "true")));
  }

  @Test
  void nativeDeathCountsOnceAndAdminRemovalGivesNoProgress() throws Exception {
    mob.setKiller(player);
    var event =
        new EntityDeathEvent(
            mob,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>());
    server.getPluginManager().callEvent(event);
    int count = executions.size();
    assertEquals(2, count);
    server.getPluginManager().callEvent(event);
    assertEquals(count, executions.size());
    var other =
        plugin.api().spawn("pig_lvl_1", mob.getLocation().clone().add(4, 0, 0)).orElseThrow();
    other.setKiller(player);
    assertTrue(plugin.api().remove(other.getUniqueId()));
    assertEquals(count, executions.size());
  }

  @Test
  void reloadKeepsSingleHookAndLoadsChangedProfiles() throws Exception {
    var hookField = IntegrationManager.class.getDeclaredField("battlepass");
    hookField.setAccessible(true);
    Object before = hookField.get(plugin.integrations());
    var yaml = new org.bukkit.configuration.file.YamlConfiguration();
    var file = new java.io.File(plugin.getDataFolder(), "battlepass.yml");
    yaml.load(file);
    yaml.set("objectives", null);
    yaml.set("objectives.only_pigs.mobs", List.of("pig_lvl_1"));
    yaml.save(file);
    plugin.reloadPlugin();
    assertSame(before, hookField.get(plugin.integrations()));
    active(); // The actual provider main plugin is not bootstrapped in this API harness.
    assertEquals(
        List.of("only_pigs"),
        plugin.integrations().battlePassObjectives().rules().stream()
            .map(BattlePassObjectives.Rule::id)
            .toList());
    plugin.integrations().killed(death(false, false, SpawnReason.API, player));
    assertEquals(List.of("themob_kill", "themob_objective"), actions());
    assertEquals(1, eligible(executions.get(1), "only_pigs", Map.of()));
  }

  @Test
  void bundledQuestYamlUsesNativeVariablesIncludingHeldItem() throws Exception {
    var yaml = new org.bukkit.configuration.file.YamlConfiguration();
    try (var input =
        new java.io.InputStreamReader(
            plugin.getResource("battlepass-examples.yml"),
            java.nio.charset.StandardCharsets.UTF_8)) {
      yaml.load(input);
    }
    var quests = yaml.getConfigurationSection("quests");
    assertEquals(8, quests.getKeys(false).size());
    server.getPluginManager().callEvent(completion(List.of(player)));
    player
        .getInventory()
        .setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD));
    plugin.getConfig().set("integrations.track-minion-kills", true);
    plugin.integrations().killed(death(true, false, SpawnReason.AUTO_SPAWN, player));
    plugin.integrations().killed(death(false, true, SpawnReason.MINION, player));
    plugin
        .integrations()
        .killed(
            new TheMobDeathEvent(
                mob,
                new MobSnapshot(
                    mob.getUniqueId(),
                    "zombie_mob_template",
                    "Zombie",
                    false,
                    false,
                    null,
                    SpawnReason.AUTO_SPAWN),
                player));
    for (String id : quests.getKeys(false)) {
      var quest = quests.getConfigurationSection(id);
      assertTrue(quest.getInt("required-progress") > 0);
      assertTrue(quest.getInt("points") > 0);
      assertNotNull(Material.matchMaterial(quest.getString("item.material")));
      Object variable =
          type("objects.variable.Variable")
              .getMethod("of", org.bukkit.configuration.ConfigurationSection.class, String.class)
              .invoke(null, quest, "");
      int matched = 0;
      for (Object execution : executions) {
        if (!quest.getString("type").equals(Reflect.call(execution, "getQuestType"))) continue;
        double result =
            ((Number)
                    Reflect.call(
                        Reflect.call(execution, "getQuestResult"), "isEligible", player, variable))
                .doubleValue();
        if (result > 0) matched++;
      }
      assertTrue(matched > 0, "No matching native execution for example " + id);
    }
  }

  @Test
  void disabledIntegrationDoesNotPublishProgress() throws Exception {
    plugin.getConfig().set("integrations.battlepass.enabled", false);
    plugin.saveConfig();
    plugin.reloadPlugin();
    assertTrue(
        plugin.integrations().statuses().stream()
            .anyMatch(
                s ->
                    s.plugin().equals("BattlePass")
                        && s.state() == IntegrationStatus.State.DISABLED));
    plugin.integrations().killed(death(true, false, SpawnReason.API, player));
    assertTrue(executions.isEmpty());
  }

  @Test
  void allTwentyAdvancedTemplatesMatchActualNativeVariablesAndCatalogItems() throws Exception {
    var yaml = new org.bukkit.configuration.file.YamlConfiguration();
    try (var reader =
        new java.io.InputStreamReader(
            plugin.getResource("battlepass-combat-examples.yml"),
            java.nio.charset.StandardCharsets.UTF_8)) {
      yaml.load(reader);
    }
    var quests = yaml.getConfigurationSection("quests");
    assertEquals(20, quests.getKeys(false).size());
    for (String id : quests.getKeys(false)) {
      executions.clear();
      var quest = quests.getConfigurationSection(id);
      String action = quest.getString("type");
      var filters = quest.getConfigurationSection("variable");
      String root =
          filters == null ? quest.getString("variable") : filters.getString("root", "none");
      String mobId =
          filters == null
              ? "zombie_boss_template"
              : filters.getString("mob", "zombie_boss_template");
      if (action.equals("themob_kill")) mobId = "tm_zombie_lvl_5";
      var snapshot =
          new MobSnapshot(
              mob.getUniqueId(),
              mobId,
              "Boss",
              !action.equals("themob_kill"),
              false,
              "phase4",
              SpawnReason.API);
      switch (action) {
        case "themob_kill", "themob_boss_kill" ->
            server.getPluginManager().callEvent(new TheMobDeathEvent(mob, snapshot, player));
        case "themob_damage" ->
            server.getPluginManager().callEvent(new TheMobDamageEvent(player, mob, snapshot, 2.75));
        case "themob_phase_enter" ->
            server
                .getPluginManager()
                .callEvent(
                    new TheMobPhaseChangeEvent(
                        mob,
                        snapshot,
                        "phase1",
                        root.equals("none") ? "phase4" : root,
                        List.of(player)));
        case "themob_dungeon_complete" ->
            server.getPluginManager().callEvent(completion(List.of(player)));
        case "themob_drop", "themob_loot" -> {
          String itemId = filters == null ? "" : filters.getString("item", "");
          var item =
              plugin
                  .api()
                  .createItem(itemId)
                  .orElseGet(
                      () ->
                          new org.bukkit.inventory.ItemStack(
                              root.equals("none")
                                  ? Material.DIAMOND
                                  : Objects.requireNonNull(Material.matchMaterial(root))));
          if (plugin.api().createItem(itemId).isEmpty()) {
            var meta = item.getItemMeta();
            var data = meta.getPersistentDataContainer();
            if (filters != null && filters.contains("rarity"))
              data.set(
                  new NamespacedKey(plugin, "rarity"),
                  org.bukkit.persistence.PersistentDataType.STRING,
                  filters.getString("rarity"));
            if (filters != null && (filters.contains("stars") || filters.contains("min-stars")))
              data.set(
                  new NamespacedKey(plugin, "stars"),
                  org.bukkit.persistence.PersistentDataType.INTEGER,
                  Integer.parseInt(
                      filters.getString("stars", filters.getString("min-stars", "0"))));
            if (itemId.startsWith("essence:")) {
              data.set(
                  new NamespacedKey(plugin, "essence"),
                  org.bukkit.persistence.PersistentDataType.STRING,
                  "zombie");
              data.set(
                  new NamespacedKey(plugin, "level"),
                  org.bukkit.persistence.PersistentDataType.INTEGER,
                  5);
            }
            item.setItemMeta(meta);
          }
          var source =
              new LootSource(
                  mobId, "phase4", SpawnReason.API, true, false, 5, "world", player.getUniqueId());
          var info = plugin.api().inspectItem(item).orElseThrow();
          if (action.equals("themob_loot"))
            server.getPluginManager().callEvent(new TheMobLootEvent(player, source, item, info));
          else
            server
                .getPluginManager()
                .callEvent(
                    new TheMobDropEvent(
                        mob.getWorld().dropItem(mob.getLocation(), item),
                        snapshot,
                        source,
                        item,
                        info));
        }
        default -> fail("Unsupported template type " + action);
      }
      Object variable =
          type("objects.variable.Variable")
              .getMethod("of", org.bukkit.configuration.ConfigurationSection.class, String.class)
              .invoke(null, quest, "");
      boolean matched = false;
      for (Object execution : executions)
        if (action.equals(Reflect.call(execution, "getQuestType")))
          matched |=
              ((Number)
                          Reflect.call(
                              Reflect.call(execution, "getQuestResult"),
                              "isEligible",
                              player,
                              variable))
                      .doubleValue()
                  > 0;
      assertTrue(matched, "Template does not match its actual native item/mob filters: " + id);
    }
  }
}
