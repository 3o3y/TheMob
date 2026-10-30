package org.plugin.theMob.spawn.spawner;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;

public final class SpawnerService {
  private static final boolean DEBUG = true;
  private static final String SPAWN_SOURCE = "spawner";
  private static final int PLAYER_RANGE = 16;
  private static final int SPAWN_RANGE = 4;
  private static final int LIMIT_RADIUS = 24;
  private static final int MAX_NEARBY = 6;
  private static final int SPAWN_COUNT = 4;
  private static final int MIN_DELAY_TICKS = 200;
  private static final int MAX_DELAY_TICKS = 800;
  private final Random random = new Random();
  private final TheMob plugin;
  private final MobManager mobs;
  private final KeyRegistry keys;
  private final ConfigService configs;

  public SpawnerService(TheMob plugin, MobManager mobs, KeyRegistry keys, ConfigService configs) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.keys = keys;
    this.configs = configs;
  }

  public void applyEggToSpawner(Player player, TileState tile, ItemStack egg) {
    if (tile instanceof CreatureSpawner spawner) {
      if (egg != null && egg.hasItemMeta()) {
        String eggKey =
            (String)
                egg.getItemMeta()
                    .getPersistentDataContainer()
                    .get(this.keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING);
        if (eggKey == null) {
          this.sendMessage(player, "errors.invalid-egg", Map.of());
        } else {
          FileConfiguration eggCfg = this.configs.spawnEggs();
          String mobId = eggCfg.getString("spawn-eggs." + eggKey.toLowerCase());
          if (mobId != null && this.mobs.mobExists(mobId)) {
            tile.getPersistentDataContainer()
                .set(this.keys.THEMOB_SPAWNER, PersistentDataType.BYTE, (byte) 1);
            tile.getPersistentDataContainer()
                .set(this.keys.THEMOB_SPAWNER_MOB, PersistentDataType.STRING, mobId);
            tile.getPersistentDataContainer()
                .set(
                    this.keys.THEMOB_SPAWNER_NEXT_TICK,
                    PersistentDataType.LONG,
                    System.currentTimeMillis() + this.delayMs());
            spawner.setSpawnedType(this.resolveBaseType(mobId));
            spawner.update(true);
            egg.setAmount(Math.max(0, egg.getAmount() - 1));
            this.sendMessage(player, "linked-message", Map.of("mob_key", eggKey));
          } else {
            this.sendMessage(player, "errors.mob-not-loaded", Map.of("mob_key", eggKey));
          }
        }
      }
    }
  }

  public void trySpawn(TileState tile) {
    if (tile instanceof CreatureSpawner) {
      String mobId =
          (String)
              tile.getPersistentDataContainer()
                  .get(this.keys.THEMOB_SPAWNER_MOB, PersistentDataType.STRING);
      if (mobId != null && this.mobs.mobExists(mobId)) {
        long now = System.currentTimeMillis();
        long next =
            (Long)
                tile.getPersistentDataContainer()
                    .getOrDefault(this.keys.THEMOB_SPAWNER_NEXT_TICK, PersistentDataType.LONG, 0L);
        if (now >= next) {
          Location center = tile.getBlock().getLocation().add(0.5, 1.0, 0.5);
          boolean playerNearby =
              center.getWorld().getPlayers().stream()
                  .anyMatch(p -> p.getLocation().distanceSquared(center) <= 256.0);
          if (!playerNearby) {
            this.reschedule(tile);
          } else {
            long nearby =
                center
                    .getWorld()
                    .getNearbyEntities(
                        center,
                        24.0,
                        24.0,
                        24.0,
                        e -> {
                          if (e instanceof LivingEntity le) {
                            String id =
                                (String)
                                    le.getPersistentDataContainer()
                                        .get(this.keys.MOB_ID, PersistentDataType.STRING);
                            return mobId.equals(id);
                          } else {
                            return false;
                          }
                        })
                    .size();
            if (nearby >= 6L) {
              // Limit reached; reschedule without per-spawner console spam.
              this.reschedule(tile);
            } else {
              for (int i = 0; i < 4 && nearby < 6L; i++) {
                Location spawn =
                    center
                        .clone()
                        .add(
                            (this.random.nextDouble() - 0.5) * 4.0 * 2.0,
                            0.0,
                            (this.random.nextDouble() - 0.5) * 4.0 * 2.0);
                if (spawn.getBlock().isPassable()) {
                  if (this.mobs.spawnCustomMob(mobId, "spawner", spawn) != null) nearby++;
                }
              }

              this.reschedule(tile);
            }
          }
        }
      }
    }
  }

  private EntityType resolveBaseType(String mobId) {
    try {
      FileConfiguration mobCfg = this.configs.mobConfigs().get(mobId);
      return mobCfg == null
          ? EntityType.ZOMBIE
          : EntityType.valueOf(mobCfg.getString("base-type", "ZOMBIE").toUpperCase());
    } catch (Throwable var3) {
      return EntityType.ZOMBIE;
    }
  }

  private void reschedule(TileState tile) {
    tile.getPersistentDataContainer()
        .set(
            this.keys.THEMOB_SPAWNER_NEXT_TICK,
            PersistentDataType.LONG,
            System.currentTimeMillis() + this.delayMs());
    tile.update(false);
  }

  private long delayMs() {
    int ticks = 200 + this.random.nextInt(601);
    return ticks * 50L;
  }

  private void sendMessage(Player player, String key, Map<String, String> placeholders) {
    FileConfiguration cfg = this.configs.spawnEggs();
    if (cfg.getBoolean("messages.enabled", true)) {
      String msg = cfg.getString("messages." + key);
      if (msg != null && !msg.isBlank()) {
        for (Entry<String, String> e : placeholders.entrySet()) {
          msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }

        player.sendMessage(msg.replace("&", "§"));
      }
    }
  }
}
