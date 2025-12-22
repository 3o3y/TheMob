package org.plugin.theMob.item;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemLoreRenderer {

    private static final NamespacedKey RENDERED_KEY =
            new NamespacedKey("themob", "lore_rendered");
    public void apply(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(RENDERED_KEY, PersistentDataType.INTEGER)) {
            return;
        }
        List<Component> lore = new ArrayList<>();
// EXISTING LORE (FLAVOR)
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
// PROPERTIES
        lore.add(Component.empty());
        lore.add(cc("&bProperties:"));
        add(meta, lore, "damage", "Damage", "+");
        add(meta, lore, "extra_damage", "Bonus Damage", "+");
        add(meta, lore, "crit", "Critical Chance", "+", "%");
        add(meta, lore, "lifesteal", "Lifesteal", "+", "%");
        add(meta, lore, "armor", "Armor", "+");
        add(meta, lore, "health", "Health", "+");
        add(meta, lore, "defense", "Defense", "+");
// ENCHANTMENTS
        if (!meta.getEnchants().isEmpty()) {
            lore.add(Component.empty());
            lore.add(cc("&dEnchantments:"));
            for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                lore.add(cc("&f- " + formatEnchant(e.getKey()) + " " + e.getValue()));
            }
        }
        meta.lore(lore);
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP
        );
        meta.getPersistentDataContainer().set(
                RENDERED_KEY,
                PersistentDataType.INTEGER,
                1
        );
        item.setItemMeta(meta);
    }
// HELPERS
    private void add(ItemMeta meta, List<Component> lore,
                     String key, String label, String prefix) {
        add(meta, lore, key, label, prefix, "");
    }
    private void add(ItemMeta meta, List<Component> lore,
                     String key, String label, String prefix, String suffix) {
        Double v = meta.getPersistentDataContainer().get(
                new NamespacedKey("themob", key),
                PersistentDataType.DOUBLE
        );
        if (v != null && v != 0) {
            lore.add(cc("&f- " + label + ": &a" + prefix + trim(v) + suffix));
        }
    }
    private Component cc(String s) {
        return Component.text(ChatColor.translateAlternateColorCodes('&', s));
    }
    private String trim(double d) {
        return d % 1 == 0 ? String.valueOf((int) d) : String.valueOf(d);
    }
    private String formatEnchant(Enchantment e) {
        String raw = e.getKey().getKey().replace("_", " ");
        String[] p = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String s : p) {
            sb.append(Character.toUpperCase(s.charAt(0)))
                    .append(s.substring(1))
                    .append(" ");
        }
        return sb.toString().trim();
    }
}
