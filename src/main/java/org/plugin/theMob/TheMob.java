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

        healthDisplay = new MobHealthDisplay(this, mobManager);
        mobManager.setHealthDisplay(healthDisplay);

        playerBars = new PlayerBarCoordinator();

        BossActionEngine actionEngine = new BossActionEngine(this);
        BossPhaseResolver resolver = new BossPhaseResolver();

        bossBars = new BossBarService(this, mobManager, playerBars);
        bossBars.start();

        phaseController = new BossPhaseController(resolver, actionEngine, bossBars);

        MobSpawnService spawnService = new MobSpawnService(
                this,
                mobManager,
                keys,
                healthDisplay,
                bossBars,
                phaseController
        );
        mobManager.setSpawnService(spawnService);
        autoSpawnManager = new AutoSpawnManager(this, mobManager, keys);
        spawnController = new SpawnController(
                this,
                mobManager,
                autoSpawnManager,
                keys
        );
        spawnController.start();

        boolean hudEnabled = getConfig().getBoolean(
                "plugin.navigation-hud.enabled",
                true
        );

        if (hudEnabled) {
            hud = new NaviHudService(
                    this,
                    playerBars,
                    new CompassRenderer(),
                    mobManager
            );
            hud.start();

            Bukkit.getPluginManager().registerEvents(
                    new NaviHudListener(hud),
                    this
            );
        }

        playerStatCache = new PlayerStatCache(this);
        statsMenu = new StatsMenuService(this, playerStatCache);
        registerAllListeners();
        registerCommands();
        PluginCommand stats = getCommand("stats");
        if (stats != null) {
            stats.setExecutor(new StatsCommand(statsMenu));
        }
    }
    @Override
    public void onDisable() {
        if (spawnController != null) spawnController.stop();
        if (hud != null) hud.shutdown();
        if (bossBars != null) bossBars.shutdown();
        if (ticks != null) ticks.shutdown();

        HandlerList.unregisterAll(this);
        getLogger().info("[TheMob] Disabled.");
    }

    public void reloadPlugin() {
        getLogger().info("[TheMob] Reloading...");

        try {
            if (spawnController != null) {
                spawnController.stop(); // stoppt intern autoSpawnManager
            }
            spawnController = null;
            autoSpawnManager = null;


            if (hud != null) {
                hud.shutdown();
                hud = null;
            }

            if (bossBars != null) {
                bossBars.shutdown();
                bossBars = null;
            }

            if (ticks != null) {
                ticks.shutdown();
            }

            if (playerBars != null) {
                playerBars.clearAll();
            }

            HandlerList.unregisterAll(this);

        } catch (Throwable t) {
            getLogger().severe("[TheMob] Error during reload shutdown!");
            t.printStackTrace();
        }

        reloadConfig();
        configService.reloadAll();
        mobManager.reloadFromConfigs();

        healthDisplay = new MobHealthDisplay(this, mobManager);
        mobManager.setHealthDisplay(healthDisplay);

        playerBars = new PlayerBarCoordinator();

        BossActionEngine actionEngine = new BossActionEngine(this);
        BossPhaseResolver resolver = new BossPhaseResolver();

        bossBars = new BossBarService(this, mobManager, playerBars);
        bossBars.start();

        phaseController = new BossPhaseController(resolver, actionEngine, bossBars);

        MobSpawnService spawnService = new MobSpawnService(
                this,
                mobManager,
                keys,
                healthDisplay,
                bossBars,
                phaseController
        );
        mobManager.setSpawnService(spawnService);
        autoSpawnManager = new AutoSpawnManager(this, mobManager, keys);
        spawnController = new SpawnController(
                this,
                mobManager,
                autoSpawnManager,
                keys
        );
        spawnController.start();
        boolean hudEnabled = getConfig().getBoolean(
                "plugin.navigation-hud.enabled",
                true
        );

        if (hudEnabled) {
            hud = new NaviHudService(
                    this,
                    playerBars,
                    new CompassRenderer(),
                    mobManager
            );
            hud.start();

            Bukkit.getPluginManager().registerEvents(
                    new NaviHudListener(hud),
                    this
            );
        }


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
                new MobListener(mobManager, healthDisplay, bossBars, keys),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new PlayerEquipListener(this, playerStatCache),
                this
        );
        Bukkit.getPluginManager().registerEvents(new StatsMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(spawnController, this);
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
