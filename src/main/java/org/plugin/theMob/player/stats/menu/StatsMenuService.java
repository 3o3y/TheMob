package org.plugin.theMob.player.stats.menu;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.player.stats.PlayerStatCache;

public final class StatsMenuService {
  private static final DecimalFormat DF = new DecimalFormat("#.##");
  private final PlayerStatCache cache;
  private final TheMob plugin;
  private final String title;
  private final int size;
  private final Map<String, StatsDefinition> stats = new LinkedHashMap<>();
  private final Map<String, List<Double>> tiers = new HashMap<>();

  public StatsMenuService(TheMob plugin, PlayerStatCache cache) {
    this.plugin = plugin;
    this.cache = cache;
    ConfigurationSection root = plugin.configs().stats();
    ConfigurationSection menu = root.getConfigurationSection("menu");
    this.title = menu == null ? "Your Stats" : menu.getString("title", "Your Stats");
    this.size =
        menu == null
            ? 54
            : org.plugin.theMob.compat.Numbers.clamp((menu.getInt("size", 54) + 8) / 9 * 9, 45, 54);
    this.loadStats(root.getConfigurationSection("stats"));
    this.loadTiers(root.getConfigurationSection("status_system"));
  }

  private void loadStats(ConfigurationSection section) {
    if (section == null) return;
    for (String key : section.getKeys(false)) {
      ConfigurationSection s = section.getConfigurationSection(key);
      if (s == null) continue;
      this.stats.put(
          key,
          new StatsDefinition(
              key,
              s.getString("name"),
              s.getInt("slot"),
              java.util.Objects.requireNonNullElse(
                  Material.matchMaterial(s.getString("icon", "PAPER")), Material.PAPER)));
    }
  }

  private void loadTiers(ConfigurationSection section) {
    if (section == null) return;
    for (String key : section.getKeys(false)) {
      List<Double> list = new ArrayList<>();

      if (!section.isList(key)) continue;
      for (Object o : section.getList(key)) {
        try {
          double value = Double.parseDouble(o.toString());
          if (Double.isFinite(value)) list.add(value);
        } catch (NumberFormatException invalid) {
          /* Ignore malformed tier entries, retain valid tiers. */
        }
      }
      list.sort(Double::compare);

      this.tiers.put(
          key.replace("_tiers", "")
              .replace("crit", "crit_chance")
              .replace("crit_chance_multiplier", "crit_multiplier"),
          list);
    }
  }

  public void open(Player p) {
    StatsMenuHolder holder = new StatsMenuHolder();
    Inventory inv = Bukkit.createInventory(holder, this.size, this.title);
    holder.bind(inv);
    Map<String, Double> values = new HashMap<>(this.cache.get(p));
    var maximum = org.plugin.theMob.compat.BukkitTypes.instance(p, "MAX_HEALTH");
    var armor = org.plugin.theMob.compat.BukkitTypes.instance(p, "ARMOR");
    if (maximum != null) {
      values.put("health", maximum.getValue());
      values.put("bonus_health", Math.max(0, maximum.getValue() - maximum.getDefaultValue()));
    }
    if (armor != null) values.put("armor", armor.getValue());
    for (var entry :
        Map.of(
                "movement_speed",
                org.plugin.theMob.compat.BukkitTypes.attribute("MOVEMENT_SPEED"),
                "attack_speed",
                org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_SPEED"),
                "knockback_resistance",
                org.plugin.theMob.compat.BukkitTypes.attribute("KNOCKBACK_RESISTANCE"),
                "luck",
                org.plugin.theMob.compat.BukkitTypes.attribute("LUCK"))
            .entrySet()) {
      var attribute = p.getAttribute(entry.getValue());
      if (attribute != null) values.put(entry.getKey(), attribute.getValue());
    }
    var skilltree = Bukkit.getPluginManager().getPlugin("SkilltreePlugin");
    boolean combined = skilltree != null && skilltree.isEnabled();

    for (StatsDefinition def : this.stats.values()) {
      double value = values.getOrDefault(def.key(), 0.0);
      List<Double> tierList = combined ? null : this.tiers.get(def.key());
      int tier = 0;
      double next = -1.0;
      double progress = 0.0;
      if (tierList != null && !tierList.isEmpty()) {
        tier = TierResolver.tier(value, tierList);
        next = TierResolver.nextValue(tier, tierList);
        progress = TierResolver.progress(value, tier, tierList);
      }

      if (def.slot() >= 0 && def.slot() < inv.getSize())
        inv.setItem(
            def.slot(), this.buildStatItem(def, value, tier, next, progress, tierList, combined));
    }

    inv.setItem(13, this.clone(p.getInventory().getHelmet()));
    inv.setItem(22, this.clone(p.getInventory().getChestplate()));
    inv.setItem(31, this.clone(p.getInventory().getLeggings()));
    inv.setItem(40, this.clone(p.getInventory().getBoots()));
    inv.setItem(21, this.clone(p.getInventory().getItemInOffHand()));
    inv.setItem(23, this.clone(p.getInventory().getItemInMainHand()));
    if (combined && inv.getSize() > 49 && inv.getItem(49) == null) {
      var button = new ItemStack(Material.NETHER_STAR);
      var meta = button.getItemMeta();
      meta.setDisplayName("§bSkilltree – Faehigkeiten und Stufen");
      meta.setLore(
          List.of("§7Gelernte Skills und naechste Stufen: /skills", "§7Klicken zum Oeffnen."));
      meta.getPersistentDataContainer()
          .set(
              new org.bukkit.NamespacedKey(plugin, "open_skilltree"),
              org.bukkit.persistence.PersistentDataType.BYTE,
              (byte) 1);
      button.setItemMeta(meta);
      inv.setItem(49, button);
    }
    p.openInventory(inv);
  }

  private ItemStack buildStatItem(
      StatsDefinition def,
      double value,
      int tier,
      double next,
      double progress,
      List<Double> tierList,
      boolean combined) {
    ItemStack it = new ItemStack(def.icon().isAir() ? Material.PAPER : def.icon());
    ItemMeta meta = it.getItemMeta();
    meta.setDisplayName(def.name());
    List<String> lore = new ArrayList<>();
    lore.add("§7Value: §f" + DF.format(value));
    if (combined) {
      boolean nativeValue =
          java.util.Set.of(
                  "health",
                  "bonus_health",
                  "armor",
                  "movement_speed",
                  "attack_speed",
                  "knockback_resistance",
                  "luck")
              .contains(def.key());
      lore.add(
          nativeValue
              ? "§7Aktueller Gesamtwert inkl. Skills/Ausruestung"
              : "§7TheMob-Ausrüstungseffekt");
      lore.add("§7Skill-Stufen und Ausbau: /skills");
    } else {
      lore.add("§7Tier: §e" + tier);
      if (tierList != null && tier < tierList.size()) {
        lore.add("§7Next Tier: §a" + DF.format(next));
        lore.add("§7Progress: §b" + (int) (progress * 100.0) + "%");
      } else {
        lore.add("§aMAX TIER");
      }
    }

    meta.setLore(lore);
    it.setItemMeta(meta);
    return it;
  }

  private ItemStack clone(ItemStack it) {
    return it != null && !it.getType().isAir() ? it.clone() : null;
  }
}
