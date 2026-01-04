package org.plugin.theMob.player.stats;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public final class PlayerEquipListener implements Listener {

    private final Plugin plugin;
    private final PlayerStatCache cache;

    public PlayerEquipListener(Plugin plugin, PlayerStatCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    // =========================
    // INVENTORY
    // =========================
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

    // =========================
    // HOTBAR / MAINHAND
    // =========================
    @EventHandler
    public void onHotbarSwap(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> recompute(e.getPlayer()));
    }

    // =========================
    // CLEANUP
    // =========================
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cache.invalidate(e.getPlayer());
    }

    // =========================
    // CORE
    // =========================
    private void recompute(Player p) {
        cache.recompute(p);
        applyAttributes(p, cache.get(p));
    }
    private double vanillaWeaponBase(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();

        if (it == null || it.getType().isAir()) {
            return 1.0;
        }

        return switch (it.getType()) {

            // =========================
            // SWORDS
            // =========================
            case WOODEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case GOLDEN_SWORD -> 4.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;

            // =========================
            // AXES
            // =========================
            case WOODEN_AXE -> 7.0;
            case STONE_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case GOLDEN_AXE -> 7.0;
            case DIAMOND_AXE -> 9.0;
            case NETHERITE_AXE -> 10.0;

            // =========================
            // PICKAXES
            // =========================
            case WOODEN_PICKAXE -> 2.0;
            case STONE_PICKAXE -> 3.0;
            case IRON_PICKAXE -> 4.0;
            case GOLDEN_PICKAXE -> 2.0;
            case DIAMOND_PICKAXE -> 5.0;
            case NETHERITE_PICKAXE -> 6.0;

            // =========================
            // SHOVELS
            // =========================
            case WOODEN_SHOVEL -> 2.5;
            case STONE_SHOVEL -> 3.5;
            case IRON_SHOVEL -> 4.5;
            case GOLDEN_SHOVEL -> 2.5;
            case DIAMOND_SHOVEL -> 5.5;
            case NETHERITE_SHOVEL -> 6.5;

            // =========================
            // HOES (fast, low dmg)
            // =========================
            case WOODEN_HOE -> 1.5;
            case STONE_HOE -> 2.0;
            case IRON_HOE -> 2.5;
            case GOLDEN_HOE -> 1.5;
            case DIAMOND_HOE -> 3.0;
            case NETHERITE_HOE -> 4.0;

            // =========================
            // SPECIAL WEAPONS
            // =========================
            case TRIDENT -> 8.0;
            case MACE -> 7.0;

            // =========================
            // FALLBACK
            // =========================
            default -> 1.0;
        };
    }
    private double vanillaArmor(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0.0;

        return switch (it.getType()) {

            // LEATHER
            case LEATHER_HELMET -> 1;
            case LEATHER_CHESTPLATE -> 3;
            case LEATHER_LEGGINGS -> 2;
            case LEATHER_BOOTS -> 1;

            // CHAINMAIL
            case CHAINMAIL_HELMET -> 2;
            case CHAINMAIL_CHESTPLATE -> 5;
            case CHAINMAIL_LEGGINGS -> 4;
            case CHAINMAIL_BOOTS -> 1;

            // GOLD
            case GOLDEN_HELMET -> 2;
            case GOLDEN_CHESTPLATE -> 5;
            case GOLDEN_LEGGINGS -> 3;
            case GOLDEN_BOOTS -> 1;

            // IRON
            case IRON_HELMET -> 2;
            case IRON_CHESTPLATE -> 6;
            case IRON_LEGGINGS -> 5;
            case IRON_BOOTS -> 2;

            // DIAMOND
            case DIAMOND_HELMET -> 3;
            case DIAMOND_CHESTPLATE -> 8;
            case DIAMOND_LEGGINGS -> 6;
            case DIAMOND_BOOTS -> 3;

            // NETHERITE
            case NETHERITE_HELMET -> 3;
            case NETHERITE_CHESTPLATE -> 8;
            case NETHERITE_LEGGINGS -> 6;
            case NETHERITE_BOOTS -> 3;

            default -> 0;
        };
    }
    private double vanillaToughness(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0.0;

        return switch (it.getType()) {
            case DIAMOND_HELMET,
                 DIAMOND_CHESTPLATE,
                 DIAMOND_LEGGINGS,
                 DIAMOND_BOOTS -> 2.0;

            case NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE,
                 NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS -> 3.0;

            default -> 0.0;
        };
    }


    private void applyAttributes(Player p, Map<String, Double> stats) {

        // HEALTH
        double baseHealth = 20.0;
        double bonusHealth = stats.getOrDefault("bonus_health", 0.0);
        set(p, Attribute.MAX_HEALTH, baseHealth + bonusHealth);

        // ARMOR / DEFENSE
        set(p, Attribute.ARMOR, stats.getOrDefault("armor", 0.0));
        set(p, Attribute.ARMOR_TOUGHNESS, stats.getOrDefault("defense", 0.0));

        // MOVEMENT
        set(p, Attribute.MOVEMENT_SPEED,
                0.1 + stats.getOrDefault("movement_speed", 0.0)
        );

        // ATTACK SPEED
        set(p, Attribute.ATTACK_SPEED,
                4.0 + stats.getOrDefault("attack_speed", 0.0)
        );

        // KNOCKBACK / LUCK
        set(p, Attribute.KNOCKBACK_RESISTANCE,
                stats.getOrDefault("knockback_resistance", 0.0)
        );
        set(p, Attribute.LUCK,
                stats.getOrDefault("luck", 0.0)
        );

        // CLAMP HEALTH
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
