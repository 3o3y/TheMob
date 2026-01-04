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

    private Map<String, Double> compute(Player p) {
        Map<String, Double> stats = new HashMap<>();

        add(stats, p.getInventory().getHelmet());
        add(stats, p.getInventory().getChestplate());
        add(stats, p.getInventory().getLeggings());
        add(stats, p.getInventory().getBoots());
        add(stats, p.getInventory().getItemInMainHand());
        add(stats, p.getInventory().getItemInOffHand());

        // ---------- v1.5 Defaults ----------
        // We keep internal keys consistent:
        // damage, extra_damage, crit (chance), crit_multiplier, lifesteal, defense, armor, movement_speed, attack_speed, knockback_resistance, luck

        stats.putIfAbsent("damage", 0.0);
        stats.putIfAbsent("extra_damage", 0.0);

        // Crit chance key: allow both "crit" and "crit_chance" on items (menu maps crit_chance->crit already).
        double crit = stats.getOrDefault("crit", stats.getOrDefault("crit_chance", 0.0));
        stats.put("crit", crit);

        // Crit multiplier default
        stats.putIfAbsent("crit_multiplier", 1.0);

        // Lifesteal default
        stats.putIfAbsent("lifesteal", 0.0);

        // Defensive defaults
        stats.putIfAbsent("armor", 0.0);
        stats.putIfAbsent("defense", 0.0);

        // Movement/Attack defaults
        stats.putIfAbsent("movement_speed", 0.0);
        stats.putIfAbsent("attack_speed", 0.0);
        stats.putIfAbsent("knockback_resistance", 0.0);
        stats.putIfAbsent("luck", 0.0);

        // Health handling: items can give "health" as bonus health in your system
        double bonusHealth = stats.getOrDefault("health", 0.0);
        stats.put("bonus_health", bonusHealth);
        stats.put("health", 20.0 + bonusHealth); // TOTAL health for menu & attributes

        return stats;
    }

    private void add(Map<String, Double> stats, ItemStack it) {
        if (it == null || it.getType().isAir()) return;
        Map<String, Double> itemStats = plugin.itemStats().read(it);
        if (itemStats == null) return;

        for (var e : itemStats.entrySet()) {
            String k = e.getKey();
            double v = e.getValue() == null ? 0.0 : e.getValue();

            // Normalize common aliases
            if ("crit_chance".equalsIgnoreCase(k)) k = "crit";
            if ("bonus_damage".equalsIgnoreCase(k)) k = "extra_damage";
            if ("base_damage".equalsIgnoreCase(k)) k = "damage";

            stats.merge(k.toLowerCase().trim(), v, Double::sum);
        }
    }
}
