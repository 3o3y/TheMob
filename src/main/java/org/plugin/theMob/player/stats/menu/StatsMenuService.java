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

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, size, title);
        Map<String, Double> values = cache.get(p);

        for (StatsDefinition def : stats.values()) {

            double value = values.getOrDefault(def.key(), 0.0);
            List<Double> tierList = tiers.get(def.key());

            int tier = 0;
            double next = -1;
            double progress = 0.0;

            if (tierList != null && !tierList.isEmpty()) {
                tier = TierResolver.tier(value, tierList);
                next = TierResolver.nextValue(tier, tierList);
                progress = TierResolver.progress(value, tier, tierList);
            }

            inv.setItem(
                    def.slot(),
                    buildStatItem(def, value, tier, next, progress, tierList)
            );
        }

        // Equipment Preview
        inv.setItem(13, clone(p.getInventory().getHelmet()));
        inv.setItem(22, clone(p.getInventory().getChestplate()));
        inv.setItem(31, clone(p.getInventory().getLeggings()));
        inv.setItem(40, clone(p.getInventory().getBoots()));
        inv.setItem(21, clone(p.getInventory().getItemInOffHand()));
        inv.setItem(23, clone(p.getInventory().getItemInMainHand()));

        p.openInventory(inv);
    }

    private ItemStack buildStatItem(
            StatsDefinition def,
            double value,
            int tier,
            double next,
            double progress,
            List<Double> tierList
    ) {
        ItemStack it = new ItemStack(def.icon());
        ItemMeta meta = it.getItemMeta();

        meta.setDisplayName(def.name());

        List<String> lore = new ArrayList<>();
        lore.add("§7Value: §f" + DF.format(value));
        lore.add("§7Tier: §e" + tier);

        if (tierList != null && tier < tierList.size()) {
            lore.add("§7Next Tier: §a" + DF.format(next));
            lore.add("§7Progress: §b" + (int) (progress * 100) + "%");
        } else {
            lore.add("§aMAX TIER");
        }

        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack clone(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        return it.clone();
    }
}
