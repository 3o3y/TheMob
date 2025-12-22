package org.plugin.theMob.player.stats;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public final class PlayerEquipListener implements Listener {

    private final Plugin plugin;
    private final PlayerStatCache cache;
    public PlayerEquipListener(Plugin plugin, PlayerStatCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }
// EQUIP EVENTS
    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> recompute(p));
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> recompute(p));
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        Bukkit.getScheduler().runTask(plugin, () -> recompute(e.getPlayer()));
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cache.invalidate(e.getPlayer());
    }
// APPLY
    private void recompute(Player p) {
        cache.recompute(p);
        applyAttributes(p, cache.get(p));
    }
    private void applyAttributes(Player p, Map<String, Double> stats) {
// HEALTH
        double totalHealth = stats.getOrDefault("health", 20.0);
        set(p, Attribute.MAX_HEALTH, totalHealth);
// ARMOR / DEFENSE
        set(p, Attribute.ARMOR, stats.getOrDefault("armor", 0.0));
        set(p, Attribute.ARMOR_TOUGHNESS, stats.getOrDefault("defense", 0.0));
// MOVEMENT
        double baseMoveSpeed = 0.1;
        set(p, Attribute.MOVEMENT_SPEED,
                baseMoveSpeed + stats.getOrDefault("movement_speed", 0.0)
        );
// ATTACK SPEED
        double baseAttackSpeed = 4.0;
        set(p, Attribute.ATTACK_SPEED,
                baseAttackSpeed + stats.getOrDefault("attack_speed", 0.0)
        );
// OTHER
        set(p, Attribute.KNOCKBACK_RESISTANCE,
                stats.getOrDefault("knockback_resistance", 0.0)
        );
        set(p, Attribute.LUCK,
                stats.getOrDefault("luck", 0.0)
        );
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            p.setHealth(Math.min(p.getHealth(), hp.getValue()));
        }
    }
    private void set(Player p, Attribute attr, double value) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }
}
