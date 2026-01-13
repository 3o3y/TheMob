package org.plugin.theMob.command;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Base64;

public final class MobBase64Command implements CommandExecutor {

    private final Plugin plugin;

    public MobBase64Command(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length != 2
                || !args[0].equalsIgnoreCase("get")
                || !args[1].equalsIgnoreCase("base64")) {
            player.sendMessage(ChatColor.RED + "Usage: /mob get base64");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Hold an item in your hand.");
            return true;
        }

        String base64;
        try {
            base64 = toBase64(item);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to serialize item.");
            e.printStackTrace();
            return true;
        }

        // =====================================================
        // FILE SETUP
        // =====================================================
        File dir = new File(plugin.getDataFolder(), "Items");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "base64_items_db.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        if (!cfg.isConfigurationSection("items")) {
            cfg.createSection("items");
        }

        String id = generateId(cfg, item);
        String path = "items." + id;

        // =====================================================
        // WRITE DATA
        // =====================================================
        cfg.set(path + ".type", "BASE64");
        cfg.set(path + ".base64", base64);

        writePreview(cfg, path + ".preview", item);

        try {
            cfg.save(file);
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Failed to write YAML file.");
            e.printStackTrace();
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Base64 item saved as:");
        player.sendMessage(ChatColor.YELLOW + id);
        player.sendMessage(ChatColor.GRAY + "File: Items/base64_items_db.yml");

        return true;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private String toBase64(ItemStack item) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOut =
                     new BukkitObjectOutputStream(out)) {
            dataOut.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private String generateId(YamlConfiguration cfg, ItemStack item) {
        String base = item.getType().name().toLowerCase();
        int i = 1;
        while (cfg.contains("items." + base + "_" + i)) {
            i++;
        }
        return base + "_" + i;
    }

    private void writePreview(YamlConfiguration cfg, String path, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        cfg.set(path + ".material", item.getType().name());
        cfg.set(path + ".amount", item.getAmount());

        // =========================
        // NAME
        // =========================
        if (meta.hasDisplayName()) {
            cfg.set(path + ".name",
                    List.of(meta.getDisplayName().replace('§', '&'))
            );
        }

        // =========================
        // LORE
        // =========================
        if (meta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : meta.getLore()) {
                lore.add(line.replace('§', '&'));
            }
            cfg.set(path + ".lore", lore);
        }

        // =========================
        // ENCHANTS
        // =========================
        if (!meta.getEnchants().isEmpty()) {
            Map<String, Integer> ench = new LinkedHashMap<>();
            meta.getEnchants().forEach((e, lvl) ->
                    ench.put(e.getKey().getKey(), lvl)
            );
            cfg.set(path + ".enchants", ench);
        }

        // =========================
// THEMOB STATS (CRASH-SAFE)
// =========================
        Map<String, Object> stats = new LinkedHashMap<>();
        var pdc = meta.getPersistentDataContainer();

        meta.getPersistentDataContainer().getKeys().forEach(key -> {
            if (!key.getNamespace().equalsIgnoreCase("themob")) return;

            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                stats.put(
                        key.getKey(),
                        pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE)
                );
                return;
            }

            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                stats.put(
                        key.getKey(),
                        pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER)
                );
                return;
            }

            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.FLOAT)) {
                stats.put(
                        key.getKey(),
                        pdc.get(key, org.bukkit.persistence.PersistentDataType.FLOAT)
                );
                return;
            }

            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.LONG)) {
                stats.put(
                        key.getKey(),
                        pdc.get(key, org.bukkit.persistence.PersistentDataType.LONG)
                );
            }
        });

        if (!stats.isEmpty()) {
            cfg.set(path + ".stats", stats);
        }

    }

}
