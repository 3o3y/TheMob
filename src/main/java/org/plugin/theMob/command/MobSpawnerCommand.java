package org.plugin.theMob.command;

import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;

public final class MobSpawnerCommand implements CommandExecutor {
  private final KeyRegistry keys;
  private final ConfigService configs;

  public MobSpawnerCommand(KeyRegistry keys, ConfigService configs) {
    this.keys = keys;
    this.configs = configs;
  }

  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (sender instanceof Player player) {
      if (!player.hasPermission("themob.spawner")) {
        player.sendMessage("§cNo permission.");
        return true;
      } else if (args.length == 2 && args[0].equalsIgnoreCase("spawner")) {
        int amount;
        try {
          amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException var8) {
          player.sendMessage("§cAmount must be a number.");
          return true;
        }

        if (amount > 0 && amount <= 64) {
          ItemStack spawner = this.createTheMobSpawner(amount);
          player.getInventory().addItem(new ItemStack[] {spawner});
          this.sendMessage(player, "spawner-message", "{amount}", String.valueOf(amount));
          return true;
        } else {
          player.sendMessage("§cAmount must be between 1 and 64.");
          return true;
        }
      } else {
        player.sendMessage("§cUsage: /mob spawner <amount>");
        return true;
      }
    } else {
      sender.sendMessage("§cOnly players can use this command.");
      return true;
    }
  }

  private ItemStack createTheMobSpawner(int amount) {
    ItemStack item = new ItemStack(Material.SPAWNER, amount);
    if (item.getItemMeta() instanceof BlockStateMeta meta) {
      if (meta.getBlockState() instanceof CreatureSpawner spawner) {
        spawner
            .getPersistentDataContainer()
            .set(this.keys.THEMOB_SPAWNER, PersistentDataType.BYTE, (byte) 1);
        meta.setBlockState(spawner);
        item.setItemMeta(meta);
        return item;
      } else {
        return item;
      }
    } else {
      return item;
    }
  }

  private void sendMessage(Player player, String path, String... replacements) {
    FileConfiguration cfg = this.configs.spawnEggs();
    String msg = cfg.getString("messages." + path, "&cMissing message: " + path);

    for (int i = 0; i < replacements.length; i += 2) {
      msg = msg.replace(replacements[i], replacements[i + 1]);
    }

    player.sendMessage(msg.replace("&", "§"));
  }
}
