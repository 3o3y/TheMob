// src/main/java/org/plugin/theMob/TheMob.java
package org.plugin.theMob;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.phase.BossPhaseResolver;
import org.plugin.theMob.command.MobCommand;
import org.plugin.theMob.command.MobTabCompleter;
import org.plugin.theMob.command.StatsCommand;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.core.TickScheduler;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.hud.NaviHudListener;
import org.plugin.theMob.hud.NaviHudService;
import org.plugin.theMob.hud.compass.CompassRenderer;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.item.ItemLoreRenderer;
import org.plugin.theMob.item.ItemStatReader;
import org.plugin.theMob.mob.MobDropEngine;
import org.plugin.theMob.mob.MobListener;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;
import org.plugin.theMob.mob.spawn.MobSpawnService;
import org.plugin.theMob.player.stats.PlayerEquipListener;
import org.plugin.theMob.player.stats.PlayerStatCache;
import org.plugin.theMob.player.stats.menu.StatsMenuListener;
import org.plugin.theMob.player.stats.menu.StatsMenuService;
import org.plugin.theMob.spawn.SpawnController;
import org.plugin.theMob.ui.MobHealthDisplay;

public final class TheMob extends JavaPlugin {

    private ConfigService configService;
    private KeyRegistry keys;
    private TickScheduler ticks;
    private MobManager mobManager;
    private MobHealthDisplay healthDisplay;
    private MobDropEngine dropEngine;
    private AutoSpawnManager autoSpawnManager;
    private SpawnController spawnController;
    private PlayerBarCoordinator playerBars;
    private BossBarService bossBars;
    private BossPhaseController phaseController;
    private NaviHudService hud;
    private ItemBuilderFromConfig itemBuilder;
    private ItemLoreRenderer loreRenderer;
    private ItemStatReader itemStatReader;
    private PlayerStatCache playerStatCache;
    private StatsMenuService statsMenu;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        configService = new ConfigService(this);
        configService.ensureFoldersAndDefaults();
        configService.reloadAll();
        keys = new KeyRegistry(this);
        ticks = new TickScheduler(this);
        mobManager = new MobManager(this, configService, keys);
        mobManager.reloadFromConfigs();
        itemBuilder = new ItemBuilderFromConfig(this);
        loreRenderer = new ItemLoreRenderer();
        itemStatReader = new ItemStatReader(this);
        dropEngine = new MobDropEngine(itemBuilder);
        dropEngine.bind(mobManager);
        mobManager.setDropEngine(dropEngine);
        healthDisplay = new MobHealthDisplay(mobManager);
        mobManager.setHealthDisplay(healthDisplay);
        playerBars = new PlayerBarCoordinator();
        BossActionEngine actionEngine = new BossActionEngine(this);
        BossPhaseResolver resolver = new BossPhaseResolver();
        phaseController = new BossPhaseController(resolver, actionEngine, null);
        bossBars = new BossBarService(
                this,
                mobManager,
                playerBars
        );
        bossBars.start();
        phaseController = new BossPhaseController(resolver, actionEngine, bossBars);
        MobSpawnService spawnService =
                new MobSpawnService(
                        this,
                        mobManager,
                        keys,
                        healthDisplay,
                        bossBars,
                        phaseController
                );
        mobManager.setSpawnService(spawnService);
        autoSpawnManager = new AutoSpawnManager(this);
        autoSpawnManager.load();
        spawnController = new SpawnController(
                this,
                mobManager,
                autoSpawnManager,
                keys
        );
        spawnController.start();
        hud = new NaviHudService(
                this,
                playerBars,
                new CompassRenderer(),
                mobManager
        );
        hud.start();
        playerStatCache = new PlayerStatCache(this);
        statsMenu = new StatsMenuService(this, playerStatCache);
        registerAllListeners();
        registerCommands();
        PluginCommand stats = getCommand("stats");
        if (stats != null) stats.setExecutor(new StatsCommand(statsMenu));
        getLogger().info("[TheMob] Enabled.");
    }
    @Override
    public void onDisable() {
        if (autoSpawnManager != null) autoSpawnManager.saveState();
        if (spawnController != null) spawnController.shutdown();
        if (hud != null) hud.shutdown();
        if (bossBars != null) bossBars.shutdown();
        if (ticks != null) ticks.shutdown();
        HandlerList.unregisterAll(this);
        getLogger().info("[TheMob] Disabled.");
    }
    public void reloadPlugin() {
        if (autoSpawnManager != null) autoSpawnManager.saveState();
        if (spawnController != null) spawnController.shutdown();
        if (hud != null) hud.shutdown();
        if (bossBars != null) bossBars.shutdown();
        HandlerList.unregisterAll(this);
        configService.reloadAll();
        mobManager.reloadFromConfigs();
        healthDisplay = new MobHealthDisplay(mobManager);
        mobManager.setHealthDisplay(healthDisplay);
        playerBars.clearAll();
        BossActionEngine actionEngine = new BossActionEngine(this);
        BossPhaseResolver resolver = new BossPhaseResolver();
        phaseController = new BossPhaseController(resolver, actionEngine, null);
        bossBars = new BossBarService(
                this,
                mobManager,
                playerBars
        );
        bossBars.start();
        phaseController = new BossPhaseController(resolver, actionEngine, bossBars);
        MobSpawnService spawnService =
                new MobSpawnService(
                        this,
                        mobManager,
                        keys,
                        healthDisplay,
                        bossBars,
                        phaseController
                );
        mobManager.setSpawnService(spawnService);
        autoSpawnManager = new AutoSpawnManager(this);
        autoSpawnManager.load();
        spawnController = new SpawnController(
                this,
                mobManager,
                autoSpawnManager,
                keys
        );
        spawnController.start();
        hud = new NaviHudService(
                this,
                playerBars,
                new CompassRenderer(),
                mobManager
        );
        hud.start();
        registerAllListeners();
        registerCommands();
        getLogger().info("[TheMob] Reload complete.");
    }
    private void registerAllListeners() {
        Bukkit.getPluginManager().registerEvents(
                new org.plugin.theMob.combat.CombatListener(
                        this,
                        mobManager,
                        new org.plugin.theMob.player.stats.PlayerStatCacheAdapter(playerStatCache)
                ),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new BossBarListenerAdapter(mobManager, phaseController),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new MobListener(
                        mobManager,
                        healthDisplay,
                        autoSpawnManager,
                        keys,
                        bossBars
                ),
                this
        );
        Bukkit.getPluginManager().registerEvents(spawnController, this);
        Bukkit.getPluginManager().registerEvents(new NaviHudListener(hud), this);
        Bukkit.getPluginManager().registerEvents(
                new PlayerEquipListener(this, playerStatCache),
                this
        );
        Bukkit.getPluginManager().registerEvents(new StatsMenuListener(), this);
    }
    private void registerCommands() {
        PluginCommand mob = getCommand("mob");
        if (mob != null) {
            mob.setExecutor(new MobCommand(this, mobManager, spawnController));
            mob.setTabCompleter(new MobTabCompleter(mobManager));
        }
    }
    public MobManager mobs() { return mobManager; }
    public ConfigService configs() { return configService; }
    public KeyRegistry keys() { return keys; }
    public ItemStatReader itemStats() { return itemStatReader; }
}
