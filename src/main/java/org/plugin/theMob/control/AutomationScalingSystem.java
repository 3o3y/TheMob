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
    this.gate = new SpawnGate(this.budgets, this.throttling, this.scaling, this.tps);
    this.bossCooldowns = new BossCooldownService(plugin);
    this.bossArenaState = new BossArenaStateService(this);
  }

  public void reload(FileConfiguration cfg) {
    this.budgets.reload(cfg);
    this.throttling.reload(cfg);
    this.scaling.reload(cfg);
    this.bossCooldowns.reload(cfg);
  }

  public void register() {
    this.plugin.getServer().getPluginManager().registerEvents(this.budgets, this.plugin);
    this.tps.start(this.plugin);
    this.budgets.rebuild();
  }

  public void shutdown() {
    this.bossCooldowns.shutdown();
    this.tps.stop();
  }

  public String resolveArenaForBoss(String bossId) {
    var idKey = new org.bukkit.NamespacedKey(this.plugin, "mob_id");
    var arenaKey = new org.bukkit.NamespacedKey(this.plugin, "auto_spawn_id");
    for (var uuid : this.budgets.aliveBossUuids()) {
      var entity = org.bukkit.Bukkit.getEntity(uuid);
      if (entity != null
          && bossId.equalsIgnoreCase(
              entity
                  .getPersistentDataContainer()
                  .get(idKey, org.bukkit.persistence.PersistentDataType.STRING)))
        return entity
            .getPersistentDataContainer()
            .get(arenaKey, org.bukkit.persistence.PersistentDataType.STRING);
    }
    return null;
  }

  public JavaPlugin plugin() {
    return this.plugin;
  }

  public SpawnBudgetManager budgets() {
    return this.budgets;
  }

  public ThrottleManager throttling() {
    return this.throttling;
  }

  public ScalingManager scaling() {
    return this.scaling;
  }

  public SpawnGate gate() {
    return this.gate;
  }

  public BossCooldownService bossCooldowns() {
    return this.bossCooldowns;
  }

  public TpsTracker tps() {
    return this.tps;
  }

  public BossArenaStateService bossArenaState() {
    return this.bossArenaState;
  }
}
