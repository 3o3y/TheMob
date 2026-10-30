package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.player.stats.PlayerStatCache;
import org.plugin.theMob.progression.PlayerProgressionManager;
import org.plugin.theMob.progression.ProgressionCombatApplier;

public final class CombatBootstrap {
  private final TheMob plugin;
  private CombatDebugService debug;
  private DamageCalculator calculator;
  private MobMultiplierService multipliers;
  private CombatListener listener;
  private boolean enabled;

  public CombatBootstrap(TheMob plugin) {
    this.plugin = plugin;
  }

  public void enable(PlayerStatCache cache, CustomEnchantSystem enchants) {
    this.enable(cache, enchants, null, null);
  }

  public synchronized void enable(
      PlayerStatCache cache,
      CustomEnchantSystem enchants,
      PlayerProgressionManager progression,
      ProgressionCombatApplier progressionCombat) {
    if (!this.enabled) {
      this.enabled = true;
      this.debug = new CombatDebugService();
      this.multipliers = new MobMultiplierService(this.plugin);
      this.calculator = new DamageCalculator(this.multipliers, cache);
      this.listener =
          new CombatListener(
              this.plugin,
              cache,
              this.calculator,
              this.debug,
              enchants,
              progression,
              progressionCombat);
      PluginManager pm = Bukkit.getPluginManager();
      pm.registerEvents(this.listener, this.plugin);
      PluginCommand cmd = this.plugin.getCommand("combatdebug");
      if (cmd != null) {
        cmd.setExecutor(new CombatDebugCommand(this.debug));
      }
    }
  }

  public synchronized void disable() {
    if (this.enabled) {
      this.enabled = false;
      if (this.listener != null) {
        HandlerList.unregisterAll(this.listener);
        this.listener = null;
      }

      PluginCommand cmd = this.plugin.getCommand("combatdebug");
      if (cmd != null) {
        cmd.setExecutor(null);
      }

      this.calculator = null;
      this.multipliers = null;
      this.debug = null;
    }
  }
}
