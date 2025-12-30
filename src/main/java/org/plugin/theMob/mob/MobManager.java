package org.plugin.theMob.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.BossTemplateParser;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.spawn.MobSpawnService;
import org.plugin.theMob.ui.MobHealthDisplay;

import java.util.*;

public final class MobManager {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final KeyRegistry keys;

    private MobDropEngine dropEngine;
    private MobHealthDisplay healthDisplay;
    private MobSpawnService spawnService;

    private final Map<String, FileConfiguration> mobConfigs = new HashMap<>();
    private final Map<String, BossTemplate> bossTemplates = new HashMap<>();

    public MobManager(JavaPlugin plugin, ConfigService configs, KeyRegistry keys) {
        this.plugin = plugin;
        this.configs = configs;
        this.keys = keys;
    }
    // =====================================================
    // INIT / RELOAD
    // =====================================================

    public void reloadFromConfigs() {
        mobConfigs.clear();
        mobConfigs.putAll(configs.mobConfigs());

        bossTemplates.clear();
        for (Map.Entry<String, FileConfiguration> e : mobConfigs.entrySet()) {
            BossTemplate tpl = BossTemplateParser.tryParse(e.getKey(), e.getValue());
            if (tpl != null && tpl.hasPhases()) {
                bossTemplates.put(e.getKey(), tpl);
            }
        }

        plugin.getLogger().info(
                "[TheMob] Loaded " + mobConfigs.size() +
                        " mob configs | bosses=" + bossTemplates.size()
        );
    }
    // =====================================================
    // SPAWN
    // =====================================================

    public LivingEntity spawnCustomMob(String mobId, String spawnId, Location loc) {
        if (spawnService == null) {
            plugin.getLogger().severe("[TheMob] SpawnService not set!");
            return null;
        }
        return spawnService.spawn(mobId, spawnId, loc);
    }
    // =====================================================
    // LOOKUPS
    // =====================================================
    public String baseNameOf(LivingEntity e) {
        if (e == null) return null;
        return e.getPersistentDataContainer()
                .get(keys.BASE_NAME, PersistentDataType.STRING);
    }

    public FileConfiguration mobConfigOf(LivingEntity mob) {
        if (mob == null) return null;

        String id = mobIdOf(mob);
        if (id == null || id.isBlank()) return null;

        return mobConfigs.get(id.toLowerCase(java.util.Locale.ROOT));
    }
    public FileConfiguration mobConfigById(String id) {
        if (id == null) return null;
        return mobConfigs.get(id.toLowerCase(java.util.Locale.ROOT));
    }
    public boolean hasBossTemplate(String mobId) {
        if (mobId == null) return false;
        return bossTemplates.containsKey(mobId.toLowerCase(java.util.Locale.ROOT));
    }
    public BossTemplate bossTemplate(String id) {
        if (id == null) return null;
        return bossTemplates.get(id.toLowerCase(java.util.Locale.ROOT));
    }
    public java.util.Set<String> registeredIds() {
        return java.util.Collections.unmodifiableSet(mobConfigs.keySet());
    }


    public boolean mobExists(String id) {
        return id != null && mobConfigs.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public boolean isCustomMob(LivingEntity e) {
        return e != null && mobIdOf(e) != null;
    }

    public String mobIdOf(LivingEntity e) {
        if (e == null) return null;
        return e.getPersistentDataContainer()
                .get(keys.MOB_ID, PersistentDataType.STRING);
    }

    public boolean isBoss(LivingEntity e) {
        if (e == null) return false;

        Integer flag = e.getPersistentDataContainer()
                .get(keys.IS_BOSS, PersistentDataType.INTEGER);
        if (flag != null) return flag == 1;

        String id = mobIdOf(e);
        return id != null && bossTemplates.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public KeyRegistry keys() {
        return keys;
    }
    // =====================================================
    // DEATH COMMANDS
    // =====================================================

    public List<String> getDeathCommands(LivingEntity mob) {
        if (mob == null) return List.of();

        String id = mobIdOf(mob);
        if (id == null) return List.of();

        FileConfiguration cfg = mobConfigs.get(id.toLowerCase(Locale.ROOT));
        if (cfg == null) return List.of();

        if (!cfg.contains("death-commands")) return List.of();

        List<String> list = cfg.getStringList("death-commands");
        return list != null ? list : List.of();
    }

    // =====================================================
    // DEATH HANDLING
    // =====================================================

    public void onMobDeath(LivingEntity mob, EntityDeathEvent e) {
        if (dropEngine != null) {
            dropEngine.handleDeath(mob, e);
        }
        if (healthDisplay != null) {
            healthDisplay.onDeath(mob);
        }
    }
    // =====================================================
    // ADMIN
    // =====================================================

    public void killAll() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!isCustomMob(entity)) continue;
                entity.remove();
                removed++;
            }
        }

    }
    // =====================================================
    // SETTERS
    // =====================================================

    public void setSpawnService(MobSpawnService spawnService) {
        this.spawnService = spawnService;
    }

    public void setDropEngine(MobDropEngine dropEngine) {
        this.dropEngine = dropEngine;
    }

    public void setHealthDisplay(MobHealthDisplay display) {
        this.healthDisplay = display;
    }
}
