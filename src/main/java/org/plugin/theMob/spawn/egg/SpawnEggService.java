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

    public SpawnEggService(
            MobManager mobs,
            KeyRegistry keys,
            ConfigService configs
    ) {
        this.mobs = mobs;
        this.keys = keys;
        this.configs = configs;
    }

    // =====================================================
    // THROWABLE EGG
    // =====================================================
    public boolean spawnFromThrownEgg(Player player, ItemStack egg, Location location) {
        if (egg == null || location == null) return false;
        if (!egg.hasItemMeta()) return false;

        String mobId = resolveMobIdFromEgg(egg, player);
        if (mobId == null) return true;

        mobs.spawnCustomMob(mobId, SPAWN_SOURCE, location);
        return true;
    }

    // =====================================================
    // VALIDATION
    // =====================================================
    public boolean isTheMobEgg(ItemStack egg) {
        if (egg == null || !egg.hasItemMeta()) return false;

        return egg.getItemMeta()
                .getPersistentDataContainer()
                .has(keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING);
    }

    // =====================================================
    // INTERNAL RESOLVE
    // =====================================================
    public String resolveMobIdFromEgg(ItemStack egg, Player player) {
        PersistentDataContainer pdc =
                egg.getItemMeta().getPersistentDataContainer();

        String eggKey =
                pdc.get(keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING);

        if (eggKey == null || eggKey.isBlank()) return null;

        FileConfiguration cfg = configs.spawnEggs();
        String path = "spawn-eggs." + eggKey.toLowerCase();

        if (!cfg.isString(path)) {
            if (player != null) {
                player.sendMessage("§cNo spawn-egg mapping for §f" + eggKey);
            }
            return null;
        }

        String mobId = cfg.getString(path).toLowerCase();

        if (!mobs.mobExists(mobId)) {
            if (player != null) {
                player.sendMessage("§cTheMob mob not loaded: §f" + mobId);
            }
            return null;
        }

        return mobId;
    }
}
