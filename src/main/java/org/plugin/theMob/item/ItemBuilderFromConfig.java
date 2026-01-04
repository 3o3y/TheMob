package org.plugin.theMob.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemBuilderFromConfig {

    private final Plugin plugin;

    public ItemBuilderFromConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack build(Map<?, ?> cfg) {
        if (cfg == null) return null;

        Material mat;
        try {
            mat = Material.valueOf(cfg.get("item").toString().toUpperCase());
        } catch (Exception e) {
            return null;
        }

        ItemStack item = new ItemStack(mat, parseInt(cfg.get("amount"), 1));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // ---------- NAME + LORE ----------
        List<Component> lore = new ArrayList<>();

        Object name = cfg.get("name");
        if (name instanceof String s) {
            meta.displayName(Component.text(cc(s)));
        } else if (name instanceof List<?> list && !list.isEmpty()) {
            meta.displayName(Component.text(cc(list.get(0).toString())));
            for (int i = 1; i < list.size(); i++) {
                lore.add(Component.text(cc(list.get(i).toString())));
            }
        }

        Object loreObj = cfg.get("lore");
        if (loreObj instanceof List<?> list) {
            for (Object o : list) {
                lore.add(Component.text(cc(o.toString())));
            }
        }

        if (!lore.isEmpty()) meta.lore(lore);

        // ---------- ENCHANTS ----------
        Object enchants = cfg.get("enchants");
        if (enchants instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                Enchantment ench = Enchantment.getByKey(
                        NamespacedKey.minecraft(e.getKey().toString().toLowerCase())
                );
                if (ench != null) {
                    meta.addEnchant(ench, parseInt(e.getValue(), 1), true);
                }
            }
        }

        // ---------- VANILLA ATTRIBUTES ----------
        Object attrs = cfg.get("attributes");
        if (attrs instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                applyAttribute(meta, e.getKey().toString(), e.getValue());
            }
        }

        // ---------- CUSTOM STATS (PDC) ----------
        Object stats = cfg.get("stats");
        if (stats instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                setDouble(meta, normalize(e.getKey().toString()), e.getValue());
            }
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private void applyAttribute(ItemMeta meta, String key, Object val) {
        Attribute attr = switch (key.toLowerCase()) {
            case "armor" -> Attribute.ARMOR;
            case "armor_toughness", "armor-toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "attack_damage", "attack-damage" -> Attribute.ATTACK_DAMAGE;
            case "attack_speed", "attack-speed" -> Attribute.ATTACK_SPEED;
            case "movement_speed", "movement-speed" -> Attribute.MOVEMENT_SPEED;
            case "max_health", "max-health" -> Attribute.MAX_HEALTH;
            case "knockback_resistance", "knockback-resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            default -> null;
        };
        if (attr == null) return;

        try {
            meta.addAttributeModifier(
                    attr,
                    new AttributeModifier(
                            new NamespacedKey(plugin, "themob_" + normalize(key)),
                            Double.parseDouble(val.toString()),
                            AttributeModifier.Operation.ADD_NUMBER
                    )
            );
        } catch (Exception ignored) {}
    }

    private void setDouble(ItemMeta meta, String key, Object val) {
        try {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, key),
                    PersistentDataType.DOUBLE,
                    Double.parseDouble(val.toString())
            );
        } catch (Exception ignored) {}
    }

    private int parseInt(Object o, int def) {
        try { return Integer.parseInt(o.toString()); }
        catch (Exception e) { return def; }
    }

    private String cc(String s) {
        return s.replace('&', '§');
    }

    private String normalize(String s) {
        return s.toLowerCase().replace("-", "_").trim();
    }
}
