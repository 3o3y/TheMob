package org.plugin.theMob.spawn.spawner;

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

import java.util.Map;
import java.util.Random;

public final class SpawnerService {

    private static final boolean DEBUG = true;
    private static final String SPAWN_SOURCE = "spawner";

    private static final int PLAYER_RANGE = 16;

    private static final int SPAWN_RANGE = 4;     // wo gespawnt wird
    private static final int LIMIT_RADIUS = 24;   // 🔥 wo gezählt wird

    private static final int MAX_NEARBY = 6;
    private static final int SPAWN_COUNT = 4;

    private static final int MIN_DELAY_TICKS = 200;
    private static final int MAX_DELAY_TICKS = 800;

    private final Random random = new Random();

    private final TheMob plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final ConfigService configs;

    public SpawnerService(
            TheMob plugin,
            MobManager mobs,
            KeyRegistry keys,
            ConfigService configs
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.keys = keys;
        this.configs = configs;
    }

    // =================================================
    // APPLY EGG
    // =================================================
    public void applyEggToSpawner(Player player, TileState tile, ItemStack egg) {

        if (!(tile instanceof CreatureSpawner spawner)) return;
        if (egg == null || !egg.hasItemMeta()) return;

        String eggKey = egg.getItemMeta()
                .getPersistentDataContainer()
                .get(keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING);

        if (eggKey == null) {
            sendMessage(player, "errors.invalid-egg", Map.of());
            return;
        }

        FileConfiguration eggCfg = configs.spawnEggs();
        String mobId = eggCfg.getString("spawn-eggs." + eggKey.toLowerCase());

        if (mobId == null || !mobs.mobExists(mobId)) {
            sendMessage(player, "errors.mob-not-loaded",
                    Map.of("mob_key", eggKey)
            );
            return;
        }

        tile.getPersistentDataContainer().set(
                keys.THEMOB_SPAWNER,
                PersistentDataType.BYTE,
                (byte) 1
        );
        tile.getPersistentDataContainer().set(
                keys.THEMOB_SPAWNER_MOB,
                PersistentDataType.STRING,
                mobId
        );
        tile.getPersistentDataContainer().set(
                keys.THEMOB_SPAWNER_NEXT_TICK,
                PersistentDataType.LONG,
                System.currentTimeMillis() + delayMs()
        );

        // 🔥 Vanilla visual feedback
        spawner.setSpawnedType(resolveBaseType(mobId));
        spawner.update(true);

        egg.setAmount(Math.max(0, egg.getAmount() - 1));

        sendMessage(player, "linked-message", Map.of("mob_key", eggKey));
    }

    // =================================================
    // SPAWN LOGIC (HARD LIMITED)
    // =================================================
    public void trySpawn(TileState tile) {

        if (!(tile instanceof CreatureSpawner)) return;

        String mobId = tile.getPersistentDataContainer().get(
                keys.THEMOB_SPAWNER_MOB,
                PersistentDataType.STRING
        );
        if (mobId == null || !mobs.mobExists(mobId)) return;

        long now = System.currentTimeMillis();
        long next = tile.getPersistentDataContainer().getOrDefault(
                keys.THEMOB_SPAWNER_NEXT_TICK,
                PersistentDataType.LONG,
                0L
        );
        if (now < next) return;

        Location center = tile.getBlock().getLocation().add(0.5, 1, 0.5);

        boolean playerNearby = center.getWorld().getPlayers().stream()
                .anyMatch(p ->
                        p.getLocation().distanceSquared(center)
                                <= PLAYER_RANGE * PLAYER_RANGE
                );

        if (!playerNearby) {
            reschedule(tile);
            return;
        }

        // 🔥 HARD LIMIT (großer Radius)
        long nearby = center.getWorld().getNearbyEntities(
                center,
                LIMIT_RADIUS,
                LIMIT_RADIUS,
                LIMIT_RADIUS,
                e -> {
                    if (!(e instanceof LivingEntity le)) return false;
                    String id = le.getPersistentDataContainer()
                            .get(keys.MOB_ID, PersistentDataType.STRING);
                    return mobId.equals(id);
                }
        ).size();

        if (nearby >= MAX_NEARBY) {
            if (DEBUG) {
                plugin.getLogger().info(
                        "[Spawner] hard limit reached (" + nearby + "/" + MAX_NEARBY + ")"
                );
            }
            reschedule(tile);
            return;
        }
        for (int i = 0; i < SPAWN_COUNT && nearby < MAX_NEARBY; i++) {
            Location spawn = center.clone().add(
                    (random.nextDouble() - 0.5) * SPAWN_RANGE * 2,
                    0,
                    (random.nextDouble() - 0.5) * SPAWN_RANGE * 2
            );

            if (!spawn.getBlock().isPassable()) continue;
            mobs.spawnCustomMob(mobId, SPAWN_SOURCE, spawn);
            nearby++;
        }
        reschedule(tile);
    }

    // =================================================
    // HELPERS
    // =================================================
    private EntityType resolveBaseType(String mobId) {
        try {
            FileConfiguration mobCfg = configs.mobConfigs().get(mobId);
            if (mobCfg == null) return EntityType.ZOMBIE;
            return EntityType.valueOf(
                    mobCfg.getString("base-type", "ZOMBIE").toUpperCase()
            );
        } catch (Throwable t) {
            return EntityType.ZOMBIE;
        }
    }

    private void reschedule(TileState tile) {
        tile.getPersistentDataContainer().set(
                keys.THEMOB_SPAWNER_NEXT_TICK,
                PersistentDataType.LONG,
                System.currentTimeMillis() + delayMs()
        );
        tile.update(false);
    }

    private long delayMs() {
        int ticks = MIN_DELAY_TICKS
                + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
        return ticks * 50L;
    }

    private void sendMessage(
            Player player,
            String key,
            Map<String, String> placeholders
    ) {
        FileConfiguration cfg = configs.spawnEggs();
        if (!cfg.getBoolean("messages.enabled", true)) return;

        String msg = cfg.getString("messages." + key);
        if (msg == null || msg.isBlank()) return;

        for (var e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }

        player.sendMessage(msg.replace("&", "§"));
    }
}
