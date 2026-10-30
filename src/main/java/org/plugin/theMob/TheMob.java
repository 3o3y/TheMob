package org.plugin.theMob;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.BossCombatListener;
import org.plugin.theMob.boss.BossImmunityListener;
import org.plugin.theMob.boss.BossLockService;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.behavior.BossBehaviorController;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.phase.BossPhaseResolver;
import org.plugin.theMob.boss.world.BossWorldEffectController;
import org.plugin.theMob.combat.CombatBootstrap;
import org.plugin.theMob.command.MobCommand;
import org.plugin.theMob.command.MobEggCommand;
import org.plugin.theMob.command.MobTabCompleter;
import org.plugin.theMob.command.StatsCommand;
import org.plugin.theMob.control.AutomationScalingSystem;
import org.plugin.theMob.control.feedback.SpawnBlockFeedbackService;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.core.TickScheduler;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.hud.NaviHudListener;
import org.plugin.theMob.hud.NaviHudService;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.item.ItemLoreRenderer;
import org.plugin.theMob.item.ItemStatReader;
import org.plugin.theMob.mob.MobDropEngine;
import org.plugin.theMob.mob.MobListener;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.PlayerDeathListener;
import org.plugin.theMob.mob.ability.StuckDefensePath;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;
import org.plugin.theMob.mob.spawn.MobSpawnService;
import org.plugin.theMob.player.stats.PlayerEquipListener;
import org.plugin.theMob.player.stats.PlayerStatCache;
import org.plugin.theMob.player.stats.menu.StatsMenuListener;
import org.plugin.theMob.player.stats.menu.StatsMenuService;
import org.plugin.theMob.progression.ProgressionBootstrap;
import org.plugin.theMob.progression.ProgressionV19Bootstrap;
import org.plugin.theMob.spawn.SpawnController;
import org.plugin.theMob.spawn.egg.SpawnEggItemFactory;
import org.plugin.theMob.spawn.egg.SpawnEggListener;
import org.plugin.theMob.spawn.egg.SpawnEggService;
import org.plugin.theMob.spawn.spawner.SpawnerFactory;
import org.plugin.theMob.spawn.spawner.SpawnerListener;
import org.plugin.theMob.spawn.spawner.SpawnerService;
import org.plugin.theMob.spawn.spawner.SpawnerTickService;
import org.plugin.theMob.ui.MobHealthDisplay;

public class TheMob extends JavaPlugin {
  public TheMob() {
    super();
  }

  /** Supported Bukkit constructor also used by isolated older server test environments. */
  protected TheMob(
      org.bukkit.plugin.java.JavaPluginLoader loader,
      org.bukkit.plugin.PluginDescriptionFile description,
      java.io.File dataFolder,
      java.io.File file) {
    super(loader, description, dataFolder, file);
  }

  private org.plugin.theMob.admin.AdminMenus adminMenus;

  public org.plugin.theMob.admin.AdminMenus adminMenus() {
    return adminMenus;
  }

  private org.plugin.theMob.dungeon.DungeonService dungeons;
  private org.plugin.theMob.dungeon.DungeonApiService dungeonApi;

  public org.plugin.theMob.dungeon.DungeonService dungeons() {
    return dungeons;
  }

  private org.plugin.theMob.control.MobActivityService activity;

  public org.plugin.theMob.control.MobActivityService activity() {
    return activity;
  }

  private org.plugin.theMob.content.EquipmentCatalog gear;

  public org.plugin.theMob.content.EquipmentCatalog gear() {
    return gear;
  }

  private org.plugin.theMob.api.internal.TheMobApiService api;
  private org.plugin.theMob.integration.IntegrationManager integrations;
  private org.plugin.theMob.integration.ObjectiveEventService objectives;

  public org.plugin.theMob.integration.ObjectiveEventService objectives() {
    return objectives;
  }

  public org.plugin.theMob.api.internal.TheMobApiService api() {
    return api;
  }

  public org.plugin.theMob.integration.IntegrationManager integrations() {
    return integrations;
  }

  private ConfigService configService;
  private org.plugin.theMob.core.TheMobSkills skills;

  public org.plugin.theMob.core.TheMobSkills skills() {
    return skills;
  }

  private String runtimeSession;

  public String runtimeSession() {
    return this.runtimeSession;
  }

  private KeyRegistry keys;
  private TickScheduler ticks;
  private StuckDefensePath stuckDefense;
  private MobManager mobManager;
  private MobHealthDisplay healthDisplay;
  private MobDropEngine dropEngine;
  private AutoSpawnManager autoSpawnManager;
  private SpawnController spawnController;
  private SpawnEggService spawnEggService;
  private SpawnerFactory spawnerFactory;
  private SpawnerService spawnerService;
  private PlayerBarCoordinator playerBars;
  private BossBarService bossBars;
  private BossPhaseController phaseController;
  private BossActionEngine bossActionEngine;
  private BossLockService bossLocks;
  private BossBehaviorController behaviorController;
  private MobEggCommand eggCommand;
  private NaviHudService hud;
  private ItemBuilderFromConfig itemBuilder;
  private ItemLoreRenderer loreRenderer;
  private ItemStatReader itemStatReader;
  private PlayerStatCache playerStatCache;
  private StatsMenuService statsMenu;
  private CombatBootstrap combat;
  private CustomEnchantSystem customEnchants;
  private PlayerEquipListener equipmentListener;
  private BossWorldEffectController worldEffects;
  private AutomationScalingSystem automationScaling;
  private ProgressionBootstrap progressionBootstrap;
  private ProgressionV19Bootstrap progressionV19;

  public void onLoad() {
    var renderer = Bukkit.getPluginManager().getPlugin("BetterHud");
    var coinPlugin = Bukkit.getPluginManager().getPlugin("CoinOverlay");
    boolean delegatedCompass =
        coinPlugin != null && coinPlugin.getDescription().getCommands().containsKey("kompass");
    if (renderer != null
        && !delegatedCompass
        && isNavigationHudEnabled()
        && !"legacy"
            .equalsIgnoreCase(
                getConfig().getString("navigation-hud.compass-style", "coinoverlay"))) {
      try {
        org.plugin.theMob.hud.compass.CompassHeaderLayout.reserve(
            renderer.getDataFolder().toPath(), renderer::getResource);
      } catch (java.io.IOException failure) {
        getLogger()
            .log(
                java.util.logging.Level.SEVERE,
                "Kompass-Kopfbereich konnte beim Laden nicht vorbereitet werden.",
                failure);
      }
    }
    integrations = new org.plugin.theMob.integration.IntegrationManager(this);
    integrations.prepareQuests();
  }

  public void onEnable() {
    this.saveDefaultConfig();
    this.configService = new ConfigService(this);
    this.configService.ensureFoldersAndDefaults();
    this.configService.reloadAll();
    this.cleanupStaleHudBars();
    this.boot(true);
    this.getLogger().info("[TheMob] Enabled.");
  }

  public void onDisable() {
    if (this.autoSpawnManager != null) {
      this.autoSpawnManager.stop();
    }

    this.cleanupStaleHudBars();
    this.hardShutdown();
    if (integrations != null) integrations.close();
    Bukkit.getServicesManager().unregisterAll(this);
    this.getLogger().info("[TheMob] Disabled cleanly.");
  }

  public void reloadPlugin() {
    this.getLogger().info("[TheMob] Reloading (hard reset) ...");
    this.cleanupStaleHudBars();
    this.hardShutdown();
    this.reloadConfig();
    if (this.configService != null) {
      this.configService.reloadAll();
    }

    this.boot(false);
    this.getLogger().info("[TheMob] Reload complete (full restart behavior).");
  }

  private void boot(boolean firstEnable) {
    if (skills == null) skills = new org.plugin.theMob.core.TheMobSkills(this);
    if (api == null) {
      api = new org.plugin.theMob.api.internal.TheMobApiService(this);
      Bukkit.getServicesManager()
          .register(
              org.plugin.theMob.api.TheMobApi.class,
              api,
              this,
              org.bukkit.plugin.ServicePriority.Normal);
    }
    this.runtimeSession = java.util.UUID.randomUUID().toString();
    if (this.configService == null) {
      this.configService = new ConfigService(this);
      this.configService.ensureFoldersAndDefaults();
    }

    this.configService.reloadAll();
    if (this.keys == null) {
      this.keys = new KeyRegistry(this);
      Placeholder.init(this.keys);
    }

    this.automationScaling = new AutomationScalingSystem(this);
    this.automationScaling.reload(this.getConfig());
    this.automationScaling.register();
    this.spawnerFactory = new SpawnerFactory(this.keys);
    if (this.ticks != null) {
      try {
        this.ticks.shutdown();
      } catch (Throwable var7) {
      }

      this.ticks = null;
    }

    this.ticks = new TickScheduler(this);
    this.mobManager = new MobManager(this, this.configService, this.keys);
    this.mobManager.reloadFromConfigs();
    this.activity = new org.plugin.theMob.control.MobActivityService(this);
    this.objectives = new org.plugin.theMob.integration.ObjectiveEventService(this);
    Bukkit.getPluginManager()
        .registerEvents(new org.plugin.theMob.content.CatalogCombatListener(this), this);
    Bukkit.getPluginManager()
        .registerEvents(new org.plugin.theMob.mob.MobEntityLifecycle(this, this.mobManager), this);
    this.stuckDefense = new StuckDefensePath();
    this.spawnerService = new SpawnerService(this, this.mobManager, this.keys, this.configService);
    new SpawnerTickService(this, this.spawnerService, this.keys).start();
    this.ticks.registerRepeatingTask(
        20L,
        () -> {
          if (this.mobManager != null) {
            for (LivingEntity mob : this.mobManager.getAllLivingMobs()) {
              if (this.activity.active(mob)) this.stuckDefense.tick(mob);
            }
          }
        });
    this.eggCommand = new MobEggCommand(new SpawnEggItemFactory(this.keys), this.configService);
    this.itemBuilder = new ItemBuilderFromConfig(this);
    this.gear = new org.plugin.theMob.content.EquipmentCatalog(this);
    this.loreRenderer = new ItemLoreRenderer();
    this.itemStatReader = new ItemStatReader(this);
    this.dropEngine = new MobDropEngine(this);
    this.dropEngine.bind(this.mobManager);
    this.mobManager.setDropEngine(this.dropEngine);
    this.playerBars = new PlayerBarCoordinator();
    this.bossActionEngine = new BossActionEngine(this);
    BossPhaseResolver resolver = new BossPhaseResolver();
    this.bossBars = new BossBarService(this, this.mobManager, this.playerBars);
    this.bossBars.start();
    this.bossBars.restore();
    Bukkit.getPluginManager().registerEvents(this.bossBars, this);
    this.worldEffects = new BossWorldEffectController(this);
    this.phaseController =
        new BossPhaseController(
            this, resolver, this.bossActionEngine, this.bossBars, this.keys, worldEffects);
    this.behaviorController =
        new BossBehaviorController(this, this.mobManager, this.phaseController);
    this.phaseController.bindBehaviorController(this.behaviorController);
    this.healthDisplay = new MobHealthDisplay(this, this.mobManager, this.bossBars);
    this.mobManager.setHealthDisplay(this.healthDisplay);
    Bukkit.getPluginManager()
        .registerEvents(
            new BossBarListenerAdapter(
                this, this.mobManager, this.phaseController, this.healthDisplay),
            this);
    MobSpawnService spawnService =
        new MobSpawnService(
            this,
            this.mobManager,
            this.keys,
            this.healthDisplay,
            this.bossBars,
            this.phaseController);
    this.mobManager.setSpawnService(spawnService);
    this.bossLocks = new BossLockService();
    this.autoSpawnManager = new AutoSpawnManager(this, this.mobManager, this.keys, this.bossLocks);
    this.spawnController = new SpawnController(this, this.mobManager, this.autoSpawnManager);
    this.spawnController.start();
    this.mobManager.setAutoSpawnManager(this.autoSpawnManager);
    this.spawnEggService = new SpawnEggService(this.mobManager, this.keys, this.configService);
    boolean hudEnabled = this.isNavigationHudEnabled();
    if (hudEnabled) {
      this.hud = new NaviHudService(this, this.mobManager);
      this.hud.start();
      Bukkit.getPluginManager().registerEvents(new NaviHudListener(this.hud), this);
    } else {
      this.cleanupStaleHudBars();
    }

    this.playerStatCache = new PlayerStatCache(this);
    this.statsMenu = new StatsMenuService(this, this.playerStatCache);
    this.customEnchants = new CustomEnchantSystem(this);
    this.combat = new CombatBootstrap(this);
    this.progressionV19 = new ProgressionV19Bootstrap(this);
    this.progressionBootstrap = new ProgressionBootstrap(this, this.progressionV19.players());
    this.dropEngine.setTieredLoot(this.progressionV19.loot());
    this.combat.enable(
        this.playerStatCache,
        this.customEnchants,
        this.progressionV19.players(),
        this.progressionV19.combat());
    new SpawnBlockFeedbackService(this, this.automationScaling).start();
    this.registerAllListeners();
    this.dungeons = new org.plugin.theMob.dungeon.DungeonService(this);
    if (dungeonApi == null) {
      dungeonApi = new org.plugin.theMob.dungeon.DungeonApiService(this);
      Bukkit.getServicesManager()
          .register(
              org.plugin.theMob.api.dungeon.TheMobDungeonApi.class,
              dungeonApi,
              this,
              org.bukkit.plugin.ServicePriority.Normal);
    }
    this.registerCommands();
    if (integrations == null)
      integrations = new org.plugin.theMob.integration.IntegrationManager(this);
    integrations.register();
    PluginCommand stats = this.getCommand("stats");
    if (stats != null) {
      stats.setExecutor(new StatsCommand(this.statsMenu, this.playerStatCache));
    }

    Bukkit.getPluginManager()
        .registerEvents(new BossImmunityListener(this.mobManager, this.phaseController), this);
    Bukkit.getPluginManager()
        .registerEvents(new BossCombatListener(this, this.mobManager, this.phaseController), this);
    this.adminMenus = new org.plugin.theMob.admin.AdminMenus(this);
  }

  private boolean isNavigationHudEnabled() {
    return this.getConfig().isSet("navigation-hud.enabled")
        ? this.getConfig().getBoolean("navigation-hud.enabled", true)
        : this.getConfig().getBoolean("plugin.navigation-hud.enabled", true);
  }

  private void hardShutdown() {
    if (integrations != null) integrations.suspendExtended();
    if (adminMenus != null) {
      adminMenus.close();
      adminMenus = null;
    }
    if (dungeons != null) {
      dungeons.close();
      dungeons = null;
    }
    Bukkit.getScheduler().cancelTasks(this);
    if (objectives != null) {
      objectives.close();
      objectives = null;
    }
    if (activity != null && mobManager != null) {
      activity.close();
      activity = null;
    }
    for (var world : Bukkit.getWorlds())
      for (var entity : world.getEntities())
        if (entity
            .getPersistentDataContainer()
            .has(
                new NamespacedKey(this, "temporary_visual"),
                org.bukkit.persistence.PersistentDataType.BYTE)) entity.remove();
    if (this.worldEffects != null) {
      this.worldEffects.shutdown();
      this.worldEffects = null;
    }
    for (Player player : Bukkit.getOnlinePlayers())
      if (org.plugin.theMob.compat.Views.top(player.getOpenInventory()) != null
          && org.plugin.theMob.compat.Views.top(player.getOpenInventory()).getHolder()
              instanceof org.plugin.theMob.player.stats.menu.StatsMenuHolder)
        player.closeInventory();
    if (this.equipmentListener != null) {
      this.equipmentListener.shutdown();
      this.equipmentListener = null;
    }
    try {
      this.progressionV19 = null;
      this.progressionBootstrap = null;
      if (this.automationScaling != null) {
        this.automationScaling.shutdown();
        this.automationScaling = null;
      }

      if (this.spawnController != null) {
        this.spawnController.stop();
        this.spawnController = null;
      }

      if (this.behaviorController != null) {
        this.behaviorController.shutdown();
        this.behaviorController = null;
      }

      if (this.phaseController != null) {
        try {
          for (LivingEntity b : this.phaseController.activeBosses()) {
            if (b != null && b.isValid() && !b.isDead()) {
              this.phaseController.onBossDeath(b);
            }
          }
        } catch (Throwable var7) {
        }

        this.phaseController = null;
      }

      if (this.mobManager != null) {
        this.mobManager.hardReset();
        this.mobManager = null;
      }

      if (this.autoSpawnManager != null) {
        this.autoSpawnManager.stop();
        this.autoSpawnManager = null;
      }

      if (this.hud != null) {
        this.hud.shutdown();
        this.hud = null;
      }

      if (this.bossBars != null) {
        this.bossBars.shutdown();
        this.bossBars = null;
      }

      if (this.bossActionEngine != null) {
        this.bossActionEngine.shutdown();
        this.bossActionEngine = null;
      }

      if (this.bossLocks != null) {
        this.bossLocks.clearAll();
        this.bossLocks = null;
      }

      if (this.playerBars != null) {
        this.playerBars.clearAll();
        this.playerBars = null;
      }

      if (this.ticks != null) {
        this.ticks.shutdown();
        this.ticks = null;
      }

      if (this.stuckDefense != null) {
        this.stuckDefense.clear();
        this.stuckDefense = null;
      }

      this.healthDisplay = null;
      this.dropEngine = null;
      this.itemBuilder = null;
      this.loreRenderer = null;
      this.itemStatReader = null;
      this.playerStatCache = null;
      this.statsMenu = null;
      this.combat = null;
      this.customEnchants = null;
      this.spawnEggService = null;
      this.spawnerService = null;
      this.spawnerFactory = null;
    } catch (Throwable var8) {
      this.getLogger().severe("[TheMob] HARD SHUTDOWN FAILED");
      var8.printStackTrace();
    } finally {
      HandlerList.unregisterAll(this);
    }
  }

  private void registerAllListeners() {
    Bukkit.getPluginManager()
        .registerEvents(
            new MobListener(
                this,
                this.mobManager,
                this.healthDisplay,
                this.bossBars,
                this.bossActionEngine,
                this.behaviorController,
                this.keys,
                this.autoSpawnManager),
            this);
    Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this, this.mobManager), this);
    this.equipmentListener = new PlayerEquipListener(this, this.playerStatCache);
    Bukkit.getPluginManager().registerEvents(this.equipmentListener, this);
    Bukkit.getPluginManager().registerEvents(new StatsMenuListener(this), this);
    if (this.spawnController != null) {
      Bukkit.getPluginManager().registerEvents(this.spawnController, this);
    }

    Bukkit.getPluginManager().registerEvents(new SpawnEggListener(this.spawnEggService), this);
    Bukkit.getPluginManager()
        .registerEvents(new SpawnerListener(this.spawnerService, this.spawnEggService), this);
    Bukkit.getPluginManager()
        .registerEvents(new org.plugin.theMob.spawn.spawner.SpawnerSpawnListener(this.keys), this);
  }

  private void registerCommands() {
    if (hud == null && getCommand("mobkompass") != null)
      getCommand("mobkompass")
          .setExecutor(
              (sender, command, label, args) -> {
                sender.sendMessage("§e[TheMob] navigation-hud.enabled ist deaktiviert.");
                return true;
              });
    PluginCommand mob = this.getCommand("mob");
    if (mob != null) {
      mob.setExecutor(new MobCommand(this, this.mobManager, this.spawnController));
      mob.setTabCompleter(new MobTabCompleter(this.mobManager, this.configService));
    }
  }

  public MobManager mobs() {
    return this.mobManager;
  }

  public SpawnController spawns() {
    return this.spawnController;
  }

  public ConfigService configs() {
    return this.configService;
  }

  public KeyRegistry keys() {
    return this.keys;
  }

  public ItemStatReader itemStats() {
    return this.itemStatReader;
  }

  public BossPhaseController bossPhases() {
    return this.phaseController;
  }

  public AutomationScalingSystem automation() {
    return this.automationScaling;
  }

  public StuckDefensePath getStuckDefense() {
    return this.stuckDefense;
  }

  private Player findNearestPlayer(Location loc, double radius) {
    if (loc != null && loc.getWorld() != null) {
      double best = radius * radius;
      Player nearest = null;

      for (Player p : loc.getWorld().getPlayers()) {
        double d = p.getLocation().distanceSquared(loc);
        if (d < best) {
          best = d;
          nearest = p;
        }
      }

      return nearest;
    } else {
      return null;
    }
  }

  private void cleanupStaleHudBars() {
    List<NamespacedKey> toRemove = new ArrayList<>();
    Iterator<KeyedBossBar> it = Bukkit.getBossBars();

    while (it.hasNext()) {
      KeyedBossBar bar = it.next();
      if (BossBarService.BOSSBAR_KEY.equals(bar.getKey())
          || NaviHudService.HUD_KEY.equals(bar.getKey())) {
        bar.removeAll();
        toRemove.add(bar.getKey());
      }
    }

    for (NamespacedKey key : toRemove) {
      Bukkit.removeBossBar(key);
    }
  }
}
