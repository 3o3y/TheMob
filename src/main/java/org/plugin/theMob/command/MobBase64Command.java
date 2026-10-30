package org.plugin.theMob.command;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class MobBase64Command implements CommandExecutor {
  private final Plugin plugin;

  public MobBase64Command(Plugin plugin) {
    this.plugin = plugin;
  }

  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission("themob.base64")) {
      sender.sendMessage("§cNo permission.");
      return true;
    }
    if (sender instanceof Player player) {
      if (args.length == 2
          && args[0].equalsIgnoreCase("get")
          && args[1].equalsIgnoreCase("base64")) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && !item.getType().isAir()) {
          String base64;
          try {
            base64 = this.toBase64(item);
          } catch (Exception var15) {
            player.sendMessage(ChatColor.RED + "Failed to serialize item.");
            var15.printStackTrace();
            return true;
          }

          File dir = new File(this.plugin.getDataFolder(), "Items");
          if (!dir.exists()) {
            dir.mkdirs();
          }

          File file = new File(dir, "base64_items_db.yml");
          YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
          if (!cfg.isConfigurationSection("items")) {
            cfg.createSection("items");
          }

          String id = this.generateId(cfg, item);
          String path = "items." + id;
          cfg.set(path + ".type", "BASE64");
          cfg.set(path + ".base64", base64);
          this.writePreview(cfg, path + ".preview", item);

          try {
            cfg.save(file);
          } catch (IOException var14) {
            player.sendMessage(ChatColor.RED + "Failed to write YAML file.");
            var14.printStackTrace();
            return true;
          }

          player.sendMessage(ChatColor.GREEN + "Base64 item saved as:");
          player.sendMessage(ChatColor.YELLOW + id);
          player.sendMessage(ChatColor.GRAY + "File: Items/base64_items_db.yml");
          return true;
        } else {
          player.sendMessage(ChatColor.RED + "Hold an item in your hand.");
          return true;
        }
      } else {
        player.sendMessage(ChatColor.RED + "Usage: /mob get base64");
        return true;
      }
    } else {
      sender.sendMessage(ChatColor.RED + "Only players can use this command.");
      return true;
    }
  }

  private String toBase64(ItemStack item) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BukkitObjectOutputStream dataOut = new BukkitObjectOutputStream(out);

    try {
      dataOut.writeObject(item);
    } catch (Throwable var7) {
      try {
        dataOut.close();
      } catch (Throwable var6) {
        var7.addSuppressed(var6);
      }

      throw var7;
    }

    dataOut.close();
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
    if (meta != null) {
      cfg.set(path + ".material", item.getType().name());
      cfg.set(path + ".amount", item.getAmount());
      if (meta.hasDisplayName()) {
        cfg.set(path + ".name", List.of(meta.getDisplayName().replace('§', '&')));
      }

      if (meta.hasLore()) {
        List<String> lore = new ArrayList<>();

        for (String line : meta.getLore()) {
          lore.add(line.replace('§', '&'));
        }

        cfg.set(path + ".lore", lore);
      }

      if (!meta.getEnchants().isEmpty()) {
        Map<String, Integer> ench = new LinkedHashMap<>();
        meta.getEnchants().forEach((e, lvl) -> ench.put(e.getKey().getKey(), lvl));
        cfg.set(path + ".enchants", ench);
      }

      Map<String, Object> stats = new LinkedHashMap<>();
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      meta.getPersistentDataContainer()
          .getKeys()
          .forEach(
              key -> {
                if (key.getNamespace().equalsIgnoreCase("themob")) {
                  if (pdc.has(key, PersistentDataType.DOUBLE)) {
                    stats.put(key.getKey(), pdc.get(key, PersistentDataType.DOUBLE));
                  } else if (pdc.has(key, PersistentDataType.INTEGER)) {
                    stats.put(key.getKey(), pdc.get(key, PersistentDataType.INTEGER));
                  } else if (pdc.has(key, PersistentDataType.FLOAT)) {
                    stats.put(key.getKey(), pdc.get(key, PersistentDataType.FLOAT));
                  } else {
                    if (pdc.has(key, PersistentDataType.LONG)) {
                      stats.put(key.getKey(), pdc.get(key, PersistentDataType.LONG));
                    }
                  }
                }
              });
      if (!stats.isEmpty()) {
        cfg.set(path + ".stats", stats);
      }
    }
  }
}
