package org.plugin.theMob.core;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyRegistry {

    // =========================
    // CORE MOB TAGS
    // =========================
    public final NamespacedKey MOB_ID;
    public final NamespacedKey BASE_NAME;
    public final NamespacedKey IS_BOSS;
    public final NamespacedKey NO_DROPS;
    public final NamespacedKey VISUAL_HEAD;

    // =========================
    // SPAWN META
    // =========================
    public final NamespacedKey SPAWN_TYPE;        // STRING: MANUAL | AUTOSPAWN
    public final NamespacedKey AUTO_SPAWN_ID;     // STRING: spawnPointId

    // =========================
    // BOSS META
    // =========================
    public final NamespacedKey BOSS_SPAWN_TIME;   // LONG (ms)

    // =========================
    // STATS
    // =========================
    public final NamespacedKey DAMAGE;
    public final NamespacedKey EXTRA_DAMAGE;
    public final NamespacedKey CRIT;
    public final NamespacedKey CRIT_MULTIPLIER;
    public final NamespacedKey LIFESTEAL;
    public final NamespacedKey ARMOR;
    public final NamespacedKey DEFENSE;
    public final NamespacedKey HEALTH;
    public final NamespacedKey MOVEMENT_SPEED;
    public final NamespacedKey ATTACK_SPEED;
    public final NamespacedKey KNOCKBACK_RESISTANCE;
    public final NamespacedKey LUCK;
    public final NamespacedKey DROPS_DONE;

    public final Map<String, NamespacedKey> ALL_STATS;

    public KeyRegistry(Plugin plugin) {

        // CORE
        MOB_ID = new NamespacedKey(plugin, "mob_id");
        BASE_NAME = new NamespacedKey(plugin, "base_name");
        IS_BOSS = new NamespacedKey(plugin, "is_boss");
        NO_DROPS = new NamespacedKey(plugin, "no_drops");
        VISUAL_HEAD = new NamespacedKey(plugin, "visual_head");

        // SPAWN META
        SPAWN_TYPE    = new NamespacedKey(plugin, "spawn_type");
        AUTO_SPAWN_ID = new NamespacedKey(plugin, "auto_spawn_id");

        // BOSS META
        BOSS_SPAWN_TIME = new NamespacedKey(plugin, "boss_spawn_time");

        // STATS
        DAMAGE = new NamespacedKey(plugin, "damage");
        EXTRA_DAMAGE = new NamespacedKey(plugin, "extra_damage");
        CRIT = new NamespacedKey(plugin, "crit");
        CRIT_MULTIPLIER = new NamespacedKey(plugin, "crit_multiplier");
        LIFESTEAL = new NamespacedKey(plugin, "lifesteal");
        ARMOR = new NamespacedKey(plugin, "armor");
        DEFENSE = new NamespacedKey(plugin, "defense");
        HEALTH = new NamespacedKey(plugin, "health");
        MOVEMENT_SPEED = new NamespacedKey(plugin, "movement_speed");
        ATTACK_SPEED = new NamespacedKey(plugin, "attack_speed");
        KNOCKBACK_RESISTANCE = new NamespacedKey(plugin, "knockback_resistance");
        LUCK = new NamespacedKey(plugin, "luck");
        DROPS_DONE = new NamespacedKey(plugin, "drops_done");

        Map<String, NamespacedKey> map = new LinkedHashMap<>();
        map.put("damage", DAMAGE);
        map.put("extra_damage", EXTRA_DAMAGE);
        map.put("crit", CRIT);
        map.put("crit_multiplier", CRIT_MULTIPLIER);
        map.put("lifesteal", LIFESTEAL);
        map.put("armor", ARMOR);
        map.put("defense", DEFENSE);
        map.put("health", HEALTH);
        map.put("movement_speed", MOVEMENT_SPEED);
        map.put("attack_speed", ATTACK_SPEED);
        map.put("knockback_resistance", KNOCKBACK_RESISTANCE);
        map.put("luck", LUCK);

        ALL_STATS = Map.copyOf(map);
    }
}
