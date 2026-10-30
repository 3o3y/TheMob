package org.plugin.theMob.player.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.plugin.theMob.TheMob;

public final class PlayerStatCache {
  private final TheMob plugin;
  private final Map<Player, Map<String, Double>> cache = new HashMap<>();

  public PlayerStatCache(TheMob plugin) {
    this.plugin = plugin;
  }

  public Map<String, Double> get(Player p) {
    Map<String, Double> current = this.compute(p);
    this.cache.put(p, current);
    return current;
  }

  public void recompute(Player p) {
    this.cache.put(p, this.compute(p));
  }

  public void invalidate(Player p) {
    this.cache.remove(p);
  }

  private Map<String, Double> compute(Player p) {
    Map<String, Double> stats = new HashMap<>();
    this.add(stats, p.getInventory().getHelmet());
    this.add(stats, p.getInventory().getChestplate());
    this.add(stats, p.getInventory().getLeggings());
    this.add(stats, p.getInventory().getBoots());
    this.add(stats, p.getInventory().getItemInMainHand());
    this.add(stats, p.getInventory().getItemInOffHand());
    stats.putIfAbsent("damage", 0.0);
    stats.putIfAbsent("extra_damage", 0.0);
    stats.putIfAbsent("crit_chance", 0.0);
    stats.putIfAbsent("crit_multiplier", 1.0);
    stats.putIfAbsent("lifesteal", 0.0);
    stats.putIfAbsent("armor", 0.0);
    stats.putIfAbsent("defense", 0.0);
    stats.putIfAbsent("movement_speed", 0.0);
    stats.putIfAbsent("attack_speed", 0.0);
    stats.putIfAbsent("knockback_resistance", 0.0);
    stats.putIfAbsent("luck", 0.0);
    double bonusHealth = stats.getOrDefault("health", 0.0);
    stats.put("bonus_health", bonusHealth);
    stats.put("health", 20.0 + bonusHealth);
    return stats;
  }

  private void add(Map<String, Double> stats, ItemStack it) {
    if (it != null && !it.getType().isAir()) {
      Map<String, Double> itemStats = this.plugin.itemStats().read(it);
      if (itemStats != null) {
        for (Entry<String, Double> e : itemStats.entrySet()) {
          String k = e.getKey().toLowerCase().replace("-", "_").trim();
          double v = e.getValue();
          switch (k) {
            case "crit":
            case "crit_chance":
              k = "crit_chance";
              break;
            case "bonus_damage":
              k = "extra_damage";
              break;
            case "base_damage":
              k = "damage";
          }

          stats.merge(k, v, Double::sum);
        }
      }
    }
  }
}
