package org.plugin.theMob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.boss.*;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.behavior.BossBehaviorController;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.phase.BossPhaseResolver;
import org.plugin.theMob.combat.CombatBootstrap;
import org.plugin.theMob.combat.CombatDebugService;
import org.plugin.theMob.combat.DamageCalculator;
import org.plugin.theMob.combat.MobMultiplierService;
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
import org.plugin.theMob.item.CustomEnchantSystem;
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

import java.util.Iterator;

import static org.plugin.theMob.hud.NaviHudService.HUD_KEY;

public final class TheMob extends JavaPlugin {


    // v1.5 Combat
    private CombatBootstrap combat;
    private CombatDebugService combatDebug;
    private DamageCalculator damageCalculator;
    private MobMultiplierService mobMultipliers;
    private CustomEnchantSystem customEnchants;


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
    private BossBehaviorController behaviorController;
    private BossActionEngine bossActionEngine;
    private BossLockService bossLocks;

    private NaviHudService hud;

    private ItemBuilderFromConfig itemBuilder;
    private ItemLoreRenderer loreRenderer;
    private ItemStatReader itemStatReader;

    private PlayerStatCache playerStatCache;
    private StatsMenuService statsMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cleanupStaleHudBars();

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

        bossActionEngine = new BossActionEngine(this);
        BossPhaseResolver resolver = new BossPhaseResolver();

        bossBars = new BossBarService(this, mobManager, playerBars);
        bossBars.start();

        phaseController = new BossPhaseController(
                resolver,
                bossActionEngine,
                bossBars
        );

        behaviorController = new BossBehaviorController(
                this,
                mobManager,
                phaseController
        );

        MobSpawnService spawnService = new MobSpawnService(
                this,
                mobManager,
                keys,
                healthDisplay,
                bossBars,
                phaseController
        );
        mobManager.setSpawnService(spawnService);

        bossLocks = new BossLockService();

        autoSpawnManager = new AutoSpawnManager(
                this,
                mobManager,
                keys,
                bossLocks
        );

        spawnController = new SpawnController(
                this,
                mobManager,
                autoSpawnManager
        );
        spawnController.start();

        mobManager.setAutoSpawnManager(autoSpawnManager);

        boolean hudEnabled = getConfig().getBoolean(
                "plugin.navigation-hud.enabled",
                true
        );

        if (hudEnabled) {
            hud = new NaviHudService(this, mobManager);
            hud.start();
            Bukkit.getPluginManager().registerEvents(
                    new NaviHudListener(hud),
                    this
            );
        }

        // =========================
        // v1.5 Combat INIT (ONCE)
        // =========================
        playerStatCache = new PlayerStatCache(this);
        customEnchants = new CustomEnchantSystem(this);

        combat = new CombatBootstrap(this);
        combat.enable(playerStatCache, customEnchants);

        // =========================
        // Stats
        // =========================
        statsMenu = new StatsMenuService(this, playerStatCache);

        registerAllListeners();
        registerCommands();

        PluginCommand stats = getCommand("stats");
        if (stats != null) {
            stats.setExecutor(new StatsCommand(statsMenu, playerStatCache));
        }

        Bukkit.getPluginManager().registerEvents(
                new BossImmunityListener(mobManager, phaseController),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new BossCombatListener(this, mobManager, phaseController),
                this
        );


        getLogger().info("[TheMob] Enabled.");
    }


    @Override
    public void onDisable() {
        hardShutdown();
        getLogger().info("[TheMob] Disabled cleanly.");
    }




    public void reloadPlugin() {
        getLogger().info("[TheMob] Reloading (hard reset)...");

        cleanupStaleHudBars();
        hardShutdown();

        reloadConfig();
        configService.reloadAll();

        ticks = new TickScheduler(this);

        mobManager.reloadFromConfigs();

        healthDisplay = new MobHealthDisplay(this, mobManager);
        mobManager.setHealthDisplay(healthDisplay);

        playerBars = new PlayerBarCoordinator();

        bossActionEngine = new BossActionEngine(this);
        BossPhaseResolver resolver = new BossPhaseResolver();

        bossBars = new BossBarService(this, mobManager, playerBars);
        bossBars.start();

        phaseController = new BossPhaseController(
                resolver,
                bossActionEngine,
                bossBars
        );

        behaviorController = new BossBehaviorController(
                this,
                mobManager,
                phaseController
        );

        MobSpawnService spawnService = new MobSpawnService(
                this,
                mobManager,
                keys,
                healthDisplay,
                bossBars,
                phaseController
        );
        mobManager.setSpawnService(spawnService);

        bossLocks = new BossLockService();

        autoSpawnManager = new AutoSpawnManager(
                this,
                mobManager,
                keys,
                bossLocks
        );

        spawnController = new SpawnController(
                this,
                mobManager,
                autoSpawnManager
        );
        spawnController.start();

        mobManager.setAutoSpawnManager(autoSpawnManager);

        boolean hudEnabled = getConfig().getBoolean(
                "plugin.navigation-hud.enabled",
                true
        );

        if (hudEnabled) {
            hud = new NaviHudService(this, mobManager);
            hud.start();
            Bukkit.getPluginManager().registerEvents(
                    new NaviHudListener(hud),
                    this
            );
        }

        registerAllListeners();
        registerCommands();

        Bukkit.getPluginManager().registerEvents(
                new BossImmunityListener(mobManager, phaseController),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new BossCombatListener(this, mobManager, phaseController),
                this
        );


        // =========================
        // v1.5 Combat RE-INIT (ONLY HERE)
        // =========================
        customEnchants = new CustomEnchantSystem(this);

        combat = new CombatBootstrap(this);
        combat.enable(playerStatCache, customEnchants);

        getLogger().info("[TheMob] Reload complete (full restart behavior).");
    }


    private void hardShutdown() {
        try {
            if (spawnController != null) {
                spawnController.stop();
                spawnController = null;
            }
            if (mobManager != null) {
                mobManager.hardReset();
            }

            if (autoSpawnManager != null) {
                autoSpawnManager.stop();
                autoSpawnManager = null;
            }

            if (hud != null) {
                hud.shutdown();
                hud = null;
            }

            if (bossBars != null) {
                bossBars.shutdown();
                bossBars = null;
            }

            if (bossActionEngine != null) {
                bossActionEngine.shutdown();
                bossActionEngine = null;
            }

            if (bossLocks != null) {
                bossLocks.clearAll();
                bossLocks = null;
            }

            if (playerBars != null) {
                playerBars.clearAll();
                playerBars = null;
            }

            if (ticks != null) {
                ticks.shutdown();
                ticks = null;
            }

        } catch (Throwable t) {
            getLogger().severe("[TheMob] HARD SHUTDOWN FAILED");
            t.printStackTrace();
        } finally {
            HandlerList.unregisterAll(this);
        }
    }

    private void registerAllListeners() {

        Bukkit.getPluginManager().registerEvents(
                new BossBarListenerAdapter(mobManager, phaseController),
                this
        );

        MobListener mobListener = new MobListener(
                mobManager,
                healthDisplay,
                bossBars,
                bossActionEngine,
                keys,
                autoSpawnManager
        );
        Bukkit.getPluginManager().registerEvents(mobListener, this);

        Bukkit.getPluginManager().registerEvents(
                new PlayerEquipListener(this, playerStatCache),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new StatsMenuListener(),
                this
        );

        if (spawnController != null) {
            Bukkit.getPluginManager().registerEvents(spawnController, this);
        }
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

    private Player findNearestPlayer(Location loc, double radius) {
        if (loc == null || loc.getWorld() == null) return null;

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
    }

    private void cleanupStaleHudBars() {
        Iterator<KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            KeyedBossBar bar = it.next();
            if (HUD_KEY.equals(bar.getKey())
                    || BossBarService.BOSSBAR_KEY.equals(bar.getKey())) {
                bar.removeAll();
            }
        }
    }


    public BossPhaseController bossPhases() {
        return phaseController;
    }


}
