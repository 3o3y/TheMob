package org.plugin.theMob.api.internal;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.ItemSnapshot;

/** Reads authoritative metadata; display names/lore never grant a rarity or identity. */
public final class ItemSnapshots {
  private ItemSnapshots() {}

  public static Optional<ItemSnapshot> inspect(TheMob plugin, ItemStack item) {
    if (item == null || item.getType().isAir() || item.getAmount() < 1) return Optional.empty();
    String id = null, rarity = "vanilla";
    int stars = 0;
    if (item.hasItemMeta()) {
      var data = item.getItemMeta().getPersistentDataContainer();
      id = data.get(new NamespacedKey(plugin, "themob_item_id"), PersistentDataType.STRING);
      if (id == null)
        id = data.get(new NamespacedKey(plugin, "catalog_item"), PersistentDataType.STRING);
      if (id == null && plugin.integrations() != null && plugin.integrations().items() != null)
        id = plugin.integrations().items().identity(item).orElse(null);
      String essence = data.get(new NamespacedKey(plugin, "essence"), PersistentDataType.STRING);
      if (id == null && essence != null)
        id =
            "essence:"
                + essence
                + ":"
                + data.getOrDefault(
                    new NamespacedKey(plugin, "level"), PersistentDataType.INTEGER, 1);
      String raw = data.get(new NamespacedKey(plugin, "rarity"), PersistentDataType.STRING);
      if (raw != null && Set.of("rare", "epic", "legendary").contains(raw.toLowerCase(Locale.ROOT)))
        rarity = raw.toLowerCase(Locale.ROOT);
      else if (id != null
          || data.getKeys().stream()
              .anyMatch(
                  key ->
                      key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))
                          && !key.getKey().equals("loot_receipt"))) rarity = "custom";
      stars =
          org.plugin.theMob.compat.Numbers.clamp(
              data.getOrDefault(new NamespacedKey(plugin, "stars"), PersistentDataType.INTEGER, 0),
              0,
              5);
    }
    if (id == null && plugin.integrations() != null && plugin.integrations().items() != null)
      id = plugin.integrations().items().identity(item).orElse(null);
    if (id != null && rarity.equals("vanilla")) rarity = "custom";
    boolean custom = id != null || !rarity.equals("vanilla");
    if (id == null || id.isBlank())
      id = "minecraft:" + item.getType().name().toLowerCase(Locale.ROOT);
    return Optional.of(
        new ItemSnapshot(id, item.getType(), rarity, stars, item.getAmount(), custom));
  }
}
