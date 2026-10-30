package org.plugin.theMob.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ItemLoreRenderer {
  private static final NamespacedKey RENDERED_KEY = new NamespacedKey("themob", "lore_rendered");

  public void apply(ItemStack item) {
    if (item != null && item.hasItemMeta()) {
      ItemMeta meta = item.getItemMeta();
      if (!meta.getPersistentDataContainer().has(RENDERED_KEY, PersistentDataType.INTEGER)) {
        List<String> lore = new ArrayList<>();
        if (meta.getLore() != null) {
          lore.addAll(meta.getLore());
        }

        if (this.hasAnyProperties(meta)) {
          lore.add("");
          lore.add(this.cc("&bProperties:"));
          this.add(meta, lore, "damage", "Damage", "+");
          this.add(meta, lore, "extra_damage", "Bonus Damage", "+");
          this.add(meta, lore, "crit", "Critical Chance", "+", "%");
          this.add(meta, lore, "crit_chance", "Critical Chance", "+", "%");
          this.add(meta, lore, "crit_multiplier", "Critical Multiplier", "+");
          this.add(meta, lore, "lifesteal", "Lifesteal", "+", "%");
          this.add(meta, lore, "armor", "Armor", "+");
          this.add(meta, lore, "health", "Health", "+");
          this.add(meta, lore, "defense", "Defense", "+");
        }

        if (!meta.getEnchants().isEmpty()) {
          lore.add("");
          lore.add(this.cc("&dEnchantments:"));

          for (Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
            lore.add(this.cc("&f- " + this.formatEnchant(e.getKey()) + " " + e.getValue()));
          }
        }

        meta.setLore(lore);
        meta.addItemFlags(
            new ItemFlag[] {
              ItemFlag.HIDE_ATTRIBUTES,
              ItemFlag.HIDE_ENCHANTS,
              org.plugin.theMob.compat.BukkitTypes.flag("HIDE_ADDITIONAL_TOOLTIP")
            });
        meta.getPersistentDataContainer().set(RENDERED_KEY, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
      }
    }
  }

  private boolean hasAnyProperties(ItemMeta meta) {
    return this.has(meta, "damage")
        || this.has(meta, "extra_damage")
        || this.has(meta, "crit")
        || this.has(meta, "crit_chance")
        || this.has(meta, "crit_multiplier")
        || this.has(meta, "lifesteal")
        || this.has(meta, "armor")
        || this.has(meta, "health")
        || this.has(meta, "defense");
  }

  private boolean has(ItemMeta meta, String key) {
    Double v =
        (Double)
            meta.getPersistentDataContainer()
                .get(new NamespacedKey("themob", key), PersistentDataType.DOUBLE);
    return v != null && v != 0.0;
  }

  private void add(ItemMeta meta, List<String> lore, String key, String label, String prefix) {
    this.add(meta, lore, key, label, prefix, "");
  }

  private void add(
      ItemMeta meta, List<String> lore, String key, String label, String prefix, String suffix) {
    Double v =
        (Double)
            meta.getPersistentDataContainer()
                .get(new NamespacedKey("themob", key), PersistentDataType.DOUBLE);
    if (v != null && v != 0.0) {
      lore.add(this.cc("&f- " + label + ": &a" + prefix + this.trim(v) + suffix));
    }
  }

  private String cc(String s) {
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  private String trim(double d) {
    return d % 1.0 == 0.0 ? String.valueOf((int) d) : String.valueOf(d);
  }

  private String formatEnchant(Enchantment e) {
    String raw = e.getKey().getKey().replace("_", " ");
    String[] p = raw.split(" ");
    StringBuilder sb = new StringBuilder();

    for (String s : p) {
      sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(" ");
    }

    return sb.toString().trim();
  }
}
