package org.plugin.theMob.control;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.control.cooldown.BossCooldownService;

public final class AutomationScalingSystem {

    private final JavaPlugin plugin;

    private final SpawnBudgetManager budgets;
    private final ThrottleManager throttling;
    private final ScalingManager scaling;
    private final SpawnGate gate;
    private final BossCooldownService bossCooldowns;
    private final TpsTracker tps;
    private final BossArenaStateService bossArenaState;

    public AutomationScalingSystem(JavaPlugin plugin) {
        this.plugin = plugin;

        this.budgets = new SpawnBudgetManager(plugin);
        this.throttling = new ThrottleManager();
        this.scaling = new ScalingManager();

        this.tps = new TpsTracker();
        this.gate = new SpawnGate(budgets, throttling, scaling, tps);

        this.bossCooldowns = new BossCooldownService(plugin);
        this.bossArenaState = new BossArenaStateService(this);
    }

    public void reload(FileConfiguration cfg) {
        budgets.reload(cfg);
        throttling.reload(cfg);
        scaling.reload(cfg);
        bossCooldowns.reload(cfg);
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(budgets, plugin);
        tps.start(plugin);
    }

    public void shutdown() {
        bossCooldowns.shutdown();
        tps.stop();
    }

    /** v1.7: arena resolution not implemented */
    public String resolveArenaForBoss(String bossId) {
        return null;
    }

    public JavaPlugin plugin() { return plugin; }

    public SpawnBudgetManager budgets() { return budgets; }
    public ThrottleManager throttling() { return throttling; }
    public ScalingManager scaling() { return scaling; }
    public SpawnGate gate() { return gate; }
    public BossCooldownService bossCooldowns() { return bossCooldowns; }
    public TpsTracker tps() { return tps; }
    public BossArenaStateService bossArenaState() { return bossArenaState; }
}
