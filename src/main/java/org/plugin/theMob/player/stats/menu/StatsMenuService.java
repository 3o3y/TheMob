package org.plugin.theMob.player.stats.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.player.stats.PlayerStatCache;

import java.text.DecimalFormat;
import java.util.*;

public final class StatsMenuService {

    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private final PlayerStatCache cache;
    private final String title;
    private final int size;
    private final Map<String, StatsDefinition> stats = new LinkedHashMap<>();
    private final Map<String, List<Double>> tiers = new HashMap<>();
    public StatsMenuService(TheMob plugin, PlayerStatCache cache) {
        this.cache = cache;
        ConfigurationSection root = plugin.configs().stats();
        ConfigurationSection menu = root.getConfigurationSection("menu");
        this.title = menu.getString("title", "Your Stats");
        this.size = menu.getInt("size", 54);
        loadStats(root.getConfigurationSection("stats"));
        loadTiers(root.getConfigurationSection("status_system"));
    }
// LOAD
    private void loadStats(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            stats.put(key, new StatsDefinition(
                    key,
                    s.getString("name"),
                    s.getInt("slot"),
                    Material.valueOf(s.getString("icon"))
            ));
        }
    }
    private void loadTiers(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            List<Double> list = new ArrayList<>();
            for (Object o : section.getList(key)) {
                list.add(Double.parseDouble(o.toString()));
            }
            tiers.put(key.replace("_tiers", ""), list);
        }
    }
// OPEN
    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, size, title);
        Map<String, Double> values = cache.get(p);
        for (StatsDefinition def : stats.values()) {
            String mapped = mapKey(def.key());
            double value = values.getOrDefault(mapped, 0.0);
            List<Double> tierList = tiers.get(def.key());
            int tier = tierList != null
                    ? TierResolver.tier(value, tierList)
                    : 0;
            double next = tierList != null
                    ? TierResolver.next(value, tierList)
                    : -1;
            inv.setItem(def.slot(), buildStatItem(def, value, tier, next));
        }
        inv.setItem(13, cloneItem(p.getInventory().getHelmet()));
        inv.setItem(22, cloneItem(p.getInventory().getChestplate()));
        inv.setItem(31, cloneItem(p.getInventory().getLeggings()));
        inv.setItem(40, cloneItem(p.getInventory().getBoots()));
        inv.setItem(21, cloneItem(p.getInventory().getItemInOffHand()));
        inv.setItem(23, cloneItem(p.getInventory().getItemInMainHand()));
        p.openInventory(inv);
    }
// ITEM
    private ItemStack buildStatItem(StatsDefinition def, double value, int tier, double next) {
        ItemStack it = new ItemStack(def.icon());
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(def.name());
        List<String> lore = new ArrayList<>();
        lore.add("§7Value: §f" + DF.format(value));
        lore.add("§7Tier: §e" + tier);
        if (next > 0) {
            double prev = tier > 0 ? tiers.get(def.key()).get(tier - 1) : 0;
            lore.add("§7Next Tier: §a" + DF.format(next));
            lore.add("§7Progress: §b" + (int) (TierResolver.progress(value, prev, next) * 100) + "%");
        } else {
            lore.add("§aMAX TIER");
        }
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }
    private ItemStack cloneItem(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        return it.clone();
    }
    private String mapKey(String key) {
        return switch (key) {
            case "health" -> "bonus_health";
            case "crit_chance" -> "crit";
            default -> key;
        };
    }
}
