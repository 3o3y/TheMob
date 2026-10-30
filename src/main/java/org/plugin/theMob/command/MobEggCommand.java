package org.plugin.theMob.command;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.spawn.egg.SpawnEggItemFactory;

public final class MobEggCommand {
  private static final int MAX_STACK = 64;
  private final SpawnEggItemFactory eggFactory;
  private final ConfigService configs;

  public MobEggCommand(SpawnEggItemFactory eggFactory, ConfigService configs) {
    this.eggFactory = eggFactory;
    this.configs = configs;
  }

  public boolean handle(Player player, String[] args) {
    if (!player.hasPermission("themob.command.egg")) {
      player.sendMessage("§cNo permission.");
      return true;
    } else if (args.length < 2) {
      player.sendMessage("§7Usage: §f/mob egg <spawn-key> [amount]");
      return true;
    } else {
      String eggKey = args[1].toLowerCase();
      FileConfiguration cfg = this.configs.spawnEggs();
      String path = "spawn-eggs." + eggKey;
      if (!cfg.isString(path)) {
        player.sendMessage("§cUnknown spawn egg key: §f" + eggKey);
        return true;
      } else {
        int amount = 1;
        if (args.length >= 3) {
          try {
            amount = Integer.parseInt(args[2]);
          } catch (NumberFormatException var10) {
            player.sendMessage("§cAmount must be a number.");
            return true;
          }
        }

        if (amount < 1) {
          amount = 1;
        }

        if (amount > 64) {
          amount = 64;
        }

        String displayName = "§aTheMob Spawn Egg {mob_key}".replace("{mob_key}", eggKey);
        List<String> lore = List.of("§7Spawns a §fTheMob §7mob", "§8Key: " + eggKey);
        ItemStack egg = this.eggFactory.createEgg(eggKey, displayName, lore);
        egg.setAmount(amount);
        player.getInventory().addItem(new ItemStack[] {egg});
        this.sendReceiveMessage(player, eggKey, amount);
        return true;
      }
    }
  }

  private void sendReceiveMessage(Player player, String eggKey, int amount) {
    FileConfiguration cfg = this.configs.spawnEggs();
    if (cfg.getBoolean("messages.enabled", true)) {
      String msg = cfg.getString("messages.receive-message");
      if (msg != null && !msg.isBlank()) {
        msg =
            msg.replace("{mob_key}", eggKey)
                .replace("{amount}", String.valueOf(amount))
                .replace("{player}", player.getName());
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        player.sendMessage(msg);
      }
    }
  }
}
