package org.plugin.theMob.item;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class CustomEnchantSystem {

    private final Plugin plugin;
    private final Random rnd = new Random();

    public CustomEnchantSystem(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Double> collect(org.bukkit.inventory.meta.ItemMeta meta) {
        Map<String, Double> stats = new HashMap<>();
        if (meta == null) return stats;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        for (NamespacedKey key : pdc.getKeys()) {
            if (pdc.has(key, PersistentDataType.DOUBLE)) {
                Double v = pdc.get(key, PersistentDataType.DOUBLE);
                if (v != null) stats.put(key.getKey(), v);
                continue;
            }
            if (pdc.has(key, PersistentDataType.INTEGER)) {
                Integer v = pdc.get(key, PersistentDataType.INTEGER);
                if (v != null) stats.put(key.getKey(), v.doubleValue());
            }
        }
        return stats;
    }

    public void trigger(Player p, LivingEntity target, Map<String, Double> stats, double finalDamage) {
        if (p == null || target == null || stats == null || stats.isEmpty()) return;
        if (!target.isValid() || target.isDead()) return;

        // ---------------------------
        // LIFESTEAL
        // ---------------------------
        double lifesteal = get(stats, "lifesteal");
        if (lifesteal > 0 && finalDamage > 0) {
            heal(p, finalDamage * clamp01(lifesteal));
        }

        // ---------------------------
        // POISON
        // ---------------------------
        tryChance(stats, "poison_chance", () -> {
            int sec = (int) Math.max(1, get(stats, "poison_seconds", 3));
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.POISON, sec * 20, 0, true, true
            ));
        });

        // ---------------------------
        // IGNITE
        // ---------------------------
        tryChance(stats, "ignite_chance", () -> {
            int sec = (int) Math.max(1, get(stats, "ignite_seconds", 3));
            target.setFireTicks(Math.max(target.getFireTicks(), sec * 20));
        });

        // ---------------------------
        // SLOW
        // ---------------------------
        tryChance(stats, "slow_chance", () -> {
            int sec = (int) Math.max(1, get(stats, "slow_seconds", 2));
            int amp = (int) Math.max(0, get(stats, "slow_amplifier", 0));
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, sec * 20, amp, true, true
            ));
        });

        // ---------------------------
        // EXECUTE
        // ---------------------------
        double threshold = get(stats, "execute_threshold");
        if (threshold > 0) {
            double max = Math.max(1.0, target.getMaxHealth());
            if ((target.getHealth() / max) <= clamp01(threshold)) {
                double bonus = get(stats, "execute_damage");
                if (bonus > 0) {
                    target.setHealth(Math.max(0, target.getHealth() - bonus));
                }
            }
        }

        // ---------------------------
        // KNOCKBACK
        // ---------------------------
        double kb = get(stats, "knockback");
        if (kb > 0) {
            Vector dir = target.getLocation().toVector()
                    .subtract(p.getLocation().toVector());
            if (dir.lengthSquared() > 0.01) {
                dir.normalize().multiply(Math.min(2.5, kb));
                dir.setY(Math.min(0.6, dir.getY() + 0.2));
                target.setVelocity(target.getVelocity().add(dir));
            }
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void tryChance(Map<String, Double> stats, String key, Runnable run) {
        double chance = get(stats, key);
        if (chance <= 0) return;
        if (rnd.nextDouble() <= clamp01(chance)) run.run();
    }

    private double get(Map<String, Double> stats, String key) {
        return get(stats, key, 0.0);
    }

    private double get(Map<String, Double> stats, String key, double def) {
        if (stats == null) return def;
        Double v = stats.get(key);
        if (v == null) v = stats.get(key.replace("-", "_"));
        return v == null ? def : v;
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void heal(Player p, double amount) {
        if (amount <= 0) return;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + amount));
    }
}
