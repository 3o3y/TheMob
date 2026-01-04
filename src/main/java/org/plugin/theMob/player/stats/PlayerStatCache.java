package org.plugin.theMob.player.stats;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.plugin.theMob.TheMob;

import java.util.HashMap;
import java.util.Map;

public final class PlayerStatCache {

    private final TheMob plugin;
    private final Map<Player, Map<String, Double>> cache = new HashMap<>();

    public PlayerStatCache(TheMob plugin) {
        this.plugin = plugin;
    }

    public Map<String, Double> get(Player p) {
        return cache.computeIfAbsent(p, this::compute);
    }

    public void recompute(Player p) {
        cache.put(p, compute(p));
    }

    public void invalidate(Player p) {
        cache.remove(p);
    }

    // =====================================================
    // COMPUTE (v1.5 FINAL)
    // =====================================================
    private Map<String, Double> compute(Player p) {
        Map<String, Double> stats = new HashMap<>();

        add(stats, p.getInventory().getHelmet());
        add(stats, p.getInventory().getChestplate());
        add(stats, p.getInventory().getLeggings());
        add(stats, p.getInventory().getBoots());
        add(stats, p.getInventory().getItemInMainHand());
        add(stats, p.getInventory().getItemInOffHand());


        stats.putIfAbsent("damage", 1.0);
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

        // ---------- HEALTH ----------
        double bonusHealth = stats.getOrDefault("health", 0.0);
        stats.put("bonus_health", bonusHealth);
        stats.put("health", 20.0 + bonusHealth);

        return stats;
    }

    // =====================================================
    // ITEM MERGE
    // =====================================================
    private void add(Map<String, Double> stats, ItemStack it) {
        if (it == null || it.getType().isAir()) return;

        Map<String, Double> itemStats = plugin.itemStats().read(it);
        if (itemStats == null) return;

        for (var e : itemStats.entrySet()) {
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
                    break;
            }

            stats.merge(k, v, Double::sum);
        }
    }
}
