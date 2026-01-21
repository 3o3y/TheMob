package org.plugin.theMob;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.boss.*;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.behavior.BossBehaviorController;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.phase.BossPhaseResolver;
import org.plugin.theMob.boss.world.BossWorldEffectController;
import org.plugin.theMob.combat.CombatBootstrap;
import org.plugin.theMob.control.AutomationScalingSystem;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.item.ItemLoreRenderer;
import org.plugin.theMob.item.ItemStatReader;
import org.plugin.theMob.command.MobCommand;
import org.plugin.theMob.command.MobTabCompleter;
import org.plugin.theMob.command.StatsCommand;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.core.TickScheduler;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.hud.NaviHudListener;
import org.plugin.theMob.hud.NaviHudService;
import org.plugin.theMob.metrics.MetricsService;
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
import org.plugin.theMob.spawn.SpawnController;
import org.plugin.theMob.ui.MobHealthDisplay;

import org.plugin.theMob.progression.ProgressionBootstrap;
import org.plugin.theMob.progression.ProgressionV19Bootstrap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.plugin.theMob.boss.bar.BossBarService.BOSSBAR_KEY;
import static org.plugin.theMob.hud.NaviHudService.HUD_KEY;

public final class TheMob extends JavaPlugin {

    // Core
    private ConfigService configService;
    private KeyRegistry keys;
    private TickScheduler ticks;
    private BossPhaseController phaseController;
    private StuckDefensePath stuckDefense;

    // Mobs
    private MobManager mobManager;
    private MobHealthDisplay healthDisplay;
    private MobDropEngine dropEngine;

    // Spawn
    private AutoSpawnManager autoSpawnManager;
    private SpawnController spawnController;

    // Boss
    private PlayerBarCoordinator playerBars;
    private BossBarService bossBars;
    public BossBehaviorController bossBehaviors() {
        return behaviorController;
    }
    private BossPhaseController bossPhases;

    private BossBehaviorController behaviorController;
    private BossActionEngine bossActionEngine;
    private BossLockService bossLocks;

    // HUD
    private NaviHudService hud;

    // Items / Stats
    private ItemBuilderFromConfig itemBuilder;
    private ItemLoreRenderer loreRenderer;
    private ItemStatReader itemStatReader;

    // Player Stats / Combat
    private PlayerStatCache playerStatCache;
    private StatsMenuService statsMenu;
    private CombatBootstrap combat;
    private CustomEnchantSystem customEnchants;

    // v1.7 Automation & Scaling
    private AutomationScalingSystem automationScaling;


    // v1.9 Progression (ADD ONLY)
    private ProgressionBootstrap progressionBootstrap;
    private ProgressionV19Bootstrap progressionV19;

    @Override
    public void onEnable() {

        // =====================
        // bStats Metrics
        // =====================
        MetricsService.init(this);
        registerMetricsCharts();

        // =====================
        // Config bootstrap
        // =====================
        saveDefaultConfig(); // config.yml ONLY

        this.configService = new ConfigService(this);
        configService.ensureFoldersAndDefaults();
        configService.reloadAll();



        cleanupStaleHudBars();
        boot(true);

        getLogger().info("[TheMob] Enabled.");
    }

    private void registerMetricsCharts() {
        if (MetricsService.chartsRegistered()) return;

        Metrics metrics = MetricsService.getMetrics();
        if (metrics == null) return;

        metrics.addCustomChart(new SimplePie(
                "plugin_version",
                () -> getDescription().getVersion()
        ));

        MetricsService.markChartsRegistered();
    }

    @Override
    public void onDisable() {

        // 1️⃣ Stop AutoSpawn & release chunk tickets
        if (autoSpawnManager != null) {
            autoSpawnManager.stop();
        }

        // 2️⃣ Cleanup HUD / BossBars (reload-sicher)
        cleanupStaleHudBars();

        // 3️⃣ Hard shutdown aller Systeme
        hardShutdown();

        getLogger().info("[TheMob] Disabled cleanly.");
    }

    // =====================================================
    // RELOAD
    // =====================================================
    public void reloadPlugin() {
        getLogger().info("[TheMob] Reloading (hard reset) ...");

        cleanupStaleHudBars();
        hardShutdown();

        reloadConfig();
        if (configService != null) {
            configService.reloadAll();
        }

        boot(false);

        getLogger().info("[TheMob] Reload complete (full restart behavior).");
    }

    // =====================================================
    // BOOTSTRAP
    // =====================================================
    private void boot(boolean firstEnable) {

        // ---------- Config / Keys / Ticks ----------
        if (configService == null) {
            configService = new ConfigService(this);
            configService.ensureFoldersAndDefaults();
        }
        configService.reloadAll();

        if (keys == null) {
            keys = new KeyRegistry(this);
            Placeholder.init(keys);
        }

        if (ticks != null) {
            try { ticks.shutdown(); } catch (Throwable ignored) {}
            ticks = null;
        }
        ticks = new TickScheduler(this);

        // ---------- Mob Manager ----------
        mobManager = new MobManager(this, configService, keys);
        mobManager.reloadFromConfigs();
        stuckDefense = new StuckDefensePath();

        ticks.registerRepeatingTask(20, () -> {
            if (mobManager == null) return;

            for (LivingEntity mob : mobManager.getAllLivingMobs()) {
                stuckDefense.tick(mob);
            }
        });


        // ---------- Items ----------
        itemBuilder = new ItemBuilderFromConfig(this);
        loreRenderer = new ItemLoreRenderer();
        itemStatReader = new ItemStatReader(this);

        // ---------- Drops ----------
        this.dropEngine = new MobDropEngine(this);
        this.dropEngine.bind(mobManager);
        mobManager.setDropEngine(this.dropEngine);

        // ---------- Boss: Bars + Phases ----------
        playerBars = new PlayerBarCoordinator();

        bossActionEngine = new BossActionEngine(this);
        BossPhaseResolver resolver = new BossPhaseResolver();

        bossBars = new BossBarService(this, mobManager, playerBars);
        bossBars.start();
        bossBars.restore();
        Bukkit.getPluginManager().registerEvents(bossBars, this);

        BossWorldEffectController worldEffects = new BossWorldEffectController(this);

        phaseController = new BossPhaseController(
                resolver,
                bossActionEngine,
                bossBars,
                keys,
                worldEffects
        );

        // ---------- Health Display (BRAUCHT bossBars!) ----------
        healthDisplay = new MobHealthDisplay(
                this,
                mobManager,
                bossBars
        );
        mobManager.setHealthDisplay(healthDisplay);

        // ---------- Boss Adapter (BRAUCHT phaseController + healthDisplay) ----------
        Bukkit.getPluginManager().registerEvents(
                new BossBarListenerAdapter(
                        mobManager,
                        phaseController,
                        healthDisplay
                ),
                this
        );

        // ---------- Boss Behavior ----------
        behaviorController = new BossBehaviorController(
                this,
                mobManager,
                phaseController
        );

        // ---------- Spawn Service ----------
        MobSpawnService spawnService = new MobSpawnService(
                this,
                mobManager,
                keys,
                healthDisplay,
                bossBars,
                phaseController
        );
        mobManager.setSpawnService(spawnService);

        // ---------- Auto Spawn ----------
        bossLocks = new BossLockService();

        autoSpawnManager = new AutoSpawnManager(
                this,
                mobManager,
                keys,
                bossLocks
        );

        spawnController = new SpawnController(this, mobManager, autoSpawnManager);
        spawnController.start();

        mobManager.setAutoSpawnManager(autoSpawnManager);

        // ---------- HUD ----------
        boolean hudEnabled = isNavigationHudEnabled();
        if (hudEnabled) {
            hud = new NaviHudService(this, mobManager);
            hud.start();
            Bukkit.getPluginManager().registerEvents(new NaviHudListener(hud), this);
        } else {
            cleanupStaleHudBars();
        }

        // ---------- v1.7 Automation & Scaling ----------
        automationScaling = new AutomationScalingSystem(this);
        automationScaling.reload(getConfig());
        automationScaling.register();

        // ---------- Player Stats / Combat ----------
        playerStatCache = new PlayerStatCache(this);
        statsMenu = new StatsMenuService(this, playerStatCache);

        customEnchants = new CustomEnchantSystem(this);
        combat = new CombatBootstrap(this);
        combat.enable(playerStatCache, customEnchants);

        // ---------- v1.9 Progression (ADD ONLY) ----------
        progressionBootstrap = new ProgressionBootstrap(this);
        progressionV19 = new ProgressionV19Bootstrap(this);

        // ---------- Spawn feedback (v1.8 UX fix) ----------
        new org.plugin.theMob.control.feedback.SpawnBlockFeedbackService(
                this,
                automationScaling
        ).start();

        // ---------- Listeners + Commands ----------
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
    }

    private boolean isNavigationHudEnabled() {
        if (getConfig().isSet("navigation-hud.enabled")) {
            return getConfig().getBoolean("navigation-hud.enabled", true);
        }
        return getConfig().getBoolean("plugin.navigation-hud.enabled", true);
    }

    // =====================================================
    // SHUTDOWN
    // =====================================================
    private void hardShutdown() {
        try {

            progressionV19 = null;
            progressionBootstrap = null;


            if (automationScaling != null) {
                automationScaling.shutdown();
                automationScaling = null;
            }

            if (spawnController != null) {
                spawnController.stop();
                spawnController = null;
            }

            if (mobManager != null) {
                mobManager.hardReset();
                mobManager = null;
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

            if (behaviorController != null) {
                behaviorController.shutdown();
                behaviorController = null;
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
            if (stuckDefense != null) {
                stuckDefense.clear();
                stuckDefense = null;
            }


        } catch (Throwable t) {
            getLogger().severe("[TheMob] HARD SHUTDOWN FAILED");
            t.printStackTrace();
        } finally {
            HandlerList.unregisterAll(this);
        }
    }

    // =====================================================
    // LISTENERS / COMMANDS
    // =====================================================
    private void registerAllListeners() {

        Bukkit.getPluginManager().registerEvents(
                new MobListener(
                        this,
                        mobManager,
                        healthDisplay,
                        bossBars,
                        bossActionEngine,
                        keys,
                        autoSpawnManager
                ),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new PlayerDeathListener(this, mobManager),
                this
        );

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

    // =====================================================
    // API
    // =====================================================
    public MobManager mobs() { return mobManager; }
    public ConfigService configs() { return configService; }
    public KeyRegistry keys() { return keys; }
    public ItemStatReader itemStats() { return itemStatReader; }
    public BossPhaseController bossPhases() { return phaseController; }
    public AutomationScalingSystem automation() { return automationScaling; }

    // =====================================================
    // HELPERS
    // =====================================================
    @SuppressWarnings("unused")
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
        List<NamespacedKey> toRemove = new ArrayList<>();

        Iterator<KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            KeyedBossBar bar = it.next();

            if (BOSSBAR_KEY.equals(bar.getKey()) || HUD_KEY.equals(bar.getKey())) {
                bar.removeAll();
                toRemove.add(bar.getKey());
            }
        }

        for (NamespacedKey key : toRemove) {
            Bukkit.removeBossBar(key);
        }
    }
    public StuckDefensePath getStuckDefense() {
        return stuckDefense;
    }

}
