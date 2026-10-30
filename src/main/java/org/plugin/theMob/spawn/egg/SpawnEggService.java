package org.plugin.theMob.spawn.egg;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;

public final class SpawnEggService {
  private static final String SPAWN_SOURCE = "egg";
  private final MobManager mobs;
  private final KeyRegistry keys;
  private final ConfigService configs;

  public SpawnEggService(MobManager mobs, KeyRegistry keys, ConfigService configs) {
    this.mobs = mobs;
    this.keys = keys;
    this.configs = configs;
  }

  public boolean spawnFromThrownEgg(Player player, ItemStack egg, Location location) {
    if (egg == null || location == null) {
      return false;
    } else if (!egg.hasItemMeta()) {
      return false;
    } else {
      String mobId = this.resolveMobIdFromEgg(egg, player);
      if (mobId == null) {
        return false;
      } else {
        return this.mobs.spawnCustomMob(mobId, "egg", location) != null;
      }
    }
  }

  public boolean isTheMobEgg(ItemStack egg) {
    return egg != null && egg.hasItemMeta()
        ? egg.getItemMeta()
            .getPersistentDataContainer()
            .has(this.keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING)
        : false;
  }

  public String resolveMobIdFromEgg(ItemStack egg, Player player) {
    PersistentDataContainer pdc = egg.getItemMeta().getPersistentDataContainer();
    String eggKey = (String) pdc.get(this.keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING);
    if (eggKey != null && !eggKey.isBlank()) {
      FileConfiguration cfg = this.configs.spawnEggs();
      String path = "spawn-eggs." + eggKey.toLowerCase();
      if (!cfg.isString(path)) {
        if (player != null) {
          player.sendMessage("§cNo spawn-egg mapping for §f" + eggKey);
        }

        return null;
      } else {
        String mobId = cfg.getString(path).toLowerCase();
        if (!this.mobs.mobExists(mobId)) {
          if (player != null) {
            player.sendMessage("§cTheMob mob not loaded: §f" + mobId);
          }

          return null;
        } else {
          return mobId;
        }
      }
    } else {
      return null;
    }
  }
}
