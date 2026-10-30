package org.plugin.theMob.content;

import java.io.File;
import java.util.*;
import java.util.random.RandomGenerator;
import org.bukkit.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.item.*;

/** Item prototypes are built once per reload. Death events only roll and clone. */
public final class EquipmentCatalog {
  private final Map<String, ItemStack> items = new LinkedHashMap<>();
  private final List<String> pieces = new ArrayList<>();
  private final TheMob plugin;
  private final FileConfiguration settings;

  public EquipmentCatalog(TheMob plugin) {
    this.plugin = plugin;
    settings = MobCatalog.settings(plugin);
    File file = new File(plugin.getDataFolder(), "equipment.yml");
    if (!file.exists()) plugin.saveResource("equipment.yml", false);
    var config = YamlConfiguration.loadConfiguration(file);
    var templates = config.getConfigurationSection("pieces");
    if (templates == null) return;
    var builder = new ItemBuilderFromConfig(plugin);
    var lore = new ItemLoreRenderer();
    for (String piece : templates.getKeys(false)) {
      var entry = templates.getConfigurationSection(piece);
      if (entry == null) continue;
      for (String rarity : List.of("rare", "epic"))
        for (int stars = 1; stars <= 5; stars++) {
          String id = rarity + "_" + piece + "_" + stars;
          String material =
              entry.getString(rarity + "-material", entry.getString("material", "IRON_SWORD"));
          String enchant = entry.getString("enchant", "unbreaking");
          int strength =
              Math.min(stars + (rarity.equals("epic") ? 1 : 0), entry.getInt("max-enchant", 5));
          var raw = new HashMap<String, Object>();
          raw.put("material", material);
          raw.put(
              "name",
              (rarity.equals("epic") ? "&5Epic " : "&9Rare ")
                  + entry.getString("name", MobCatalog.title(piece))
                  + " &6"
                  + "★".repeat(stars));
          raw.put(
              "lore",
              List.of(
                  "&7TheMob Ausruestung",
                  "&7Seltenheit: " + rarity.toUpperCase(Locale.ROOT),
                  "&7Sterne: &6" + stars + "/5"));
          var enchants = new LinkedHashMap<String, Integer>();
          enchants.put(enchant, strength);
          enchants.putIfAbsent("unbreaking", Math.min(3, 1 + stars / 2));
          raw.put("enchants", enchants);
          double bonus = Math.round(stars * (rarity.equals("epic") ? .85 : .5) * 100) / 100.0;
          raw.put("stats", Map.of(entry.getString("stat", "defense"), bonus));
          ItemStack item = builder.build(raw);
          if (item == null) continue;
          var meta = item.getItemMeta();
          meta.getPersistentDataContainer()
              .set(new NamespacedKey(plugin, "catalog_item"), PersistentDataType.STRING, id);
          meta.getPersistentDataContainer()
              .set(new NamespacedKey(plugin, "themob_item_id"), PersistentDataType.STRING, id);
          meta.getPersistentDataContainer()
              .set(
                  new NamespacedKey(plugin, "rarity"),
                  PersistentDataType.STRING,
                  rarity.toUpperCase(Locale.ROOT));
          meta.getPersistentDataContainer()
              .set(new NamespacedKey(plugin, "stars"), PersistentDataType.INTEGER, stars);
          item.setItemMeta(meta);
          lore.apply(item);
          items.put(id, item);
        }
      if (items.containsKey("rare_" + piece + "_1") && items.containsKey("epic_" + piece + "_5"))
        pieces.add(piece);
    }
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(items.keySet());
  }

  public ItemStack item(String id) {
    ItemStack prototype = items.get(id.toLowerCase(Locale.ROOT));
    return prototype == null ? null : prototype.clone();
  }

  public List<ItemStack> roll(LivingEntity mob, int level, boolean boss, RandomGenerator random) {
    if (!settings.getBoolean("loot.enabled", true)) return List.of();
    if (settings.getBoolean("loot.require-player-kill", true)
        && (mob.getKiller() == null
            || plugin.integrations().isNpc(mob.getKiller())
            || plugin.integrations().isNpc(mob))) return List.of();
    level = org.plugin.theMob.compat.Numbers.clamp(level, 1, 5);
    var drops = new ArrayList<ItemStack>();
    double chance =
        boss
            ? settings.getDouble("loot.boss-equipment-chance", 1)
            : settings.getDouble("loot.equipment-chance", .04)
                + (level - 1) * settings.getDouble("loot.equipment-chance-per-level", .02);
    if (!pieces.isEmpty()
        && random.nextDouble() < org.plugin.theMob.compat.Numbers.clamp(chance, 0, 1)) {
      double epic =
          boss
              ? settings.getDouble("loot.boss-epic-chance", .6)
              : settings.getDouble("loot.epic-chance", .08)
                  + (level - 1) * settings.getDouble("loot.epic-chance-per-level", .035);
      String rarity =
          random.nextDouble() < org.plugin.theMob.compat.Numbers.clamp(epic, 0, 1)
              ? "epic"
              : "rare";
      int stars = boss ? level : 1 + random.nextInt(level);
      drops.add(item(rarity + "_" + pieces.get(random.nextInt(pieces.size())) + "_" + stars));
    }
    if (boss
        || random.nextDouble()
            < org.plugin.theMob.compat.Numbers.clamp(
                settings.getDouble("loot.essence-chance", .5), 0, 1)) {
      ItemStack essence =
          new ItemStack(Material.AMETHYST_SHARD, boss ? 2 + level : 1 + (level - 1) / 2);
      var meta = essence.getItemMeta();
      String species = mob.getType().name().toLowerCase(Locale.ROOT);
      meta.setDisplayName(ChatColor.LIGHT_PURPLE + MobCatalog.title(species) + "-Essenz");
      meta.setLore(
          List.of(
              ChatColor.GRAY + "Beute von Level " + level,
              ChatColor.DARK_GRAY + "TheMob Sammelmaterial"));
      meta.getPersistentDataContainer()
          .set(new NamespacedKey(plugin, "essence"), PersistentDataType.STRING, species);
      meta.getPersistentDataContainer()
          .set(new NamespacedKey(plugin, "level"), PersistentDataType.INTEGER, level);
      essence.setItemMeta(meta);
      drops.add(essence);
    }
    return List.copyOf(drops);
  }
}
