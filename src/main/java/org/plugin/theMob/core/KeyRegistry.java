package org.plugin.theMob.core;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyRegistry {
// ENTITY / MOB
    public final NamespacedKey MOB_ID;
    public final NamespacedKey BASE_NAME;
    public final NamespacedKey IS_BOSS;
    public final NamespacedKey NO_DROPS;
// ITEM STATS
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
    public final Map<String, NamespacedKey> ALL_STATS;
    public final NamespacedKey AUTO_SPAWNED;
    public KeyRegistry(Plugin plugin) {
        this.MOB_ID = new NamespacedKey(plugin, "mob_id");
        this.BASE_NAME = new NamespacedKey(plugin, "base_name");
        this.IS_BOSS = new NamespacedKey(plugin, "is_boss");
        this.NO_DROPS = new NamespacedKey(plugin, "no_drops");
        this.DAMAGE = new NamespacedKey(plugin, "damage");
        this.EXTRA_DAMAGE = new NamespacedKey(plugin, "extra_damage");
        this.CRIT = new NamespacedKey(plugin, "crit");
        this.CRIT_MULTIPLIER = new NamespacedKey(plugin, "crit_multiplier");
        this.LIFESTEAL = new NamespacedKey(plugin, "lifesteal");
        this.ARMOR = new NamespacedKey(plugin, "armor");
        this.DEFENSE = new NamespacedKey(plugin, "defense");
        this.HEALTH = new NamespacedKey(plugin, "health");
        this.MOVEMENT_SPEED = new NamespacedKey(plugin, "movement_speed");
        this.ATTACK_SPEED = new NamespacedKey(plugin, "attack_speed");
        this.KNOCKBACK_RESISTANCE = new NamespacedKey(plugin, "knockback_resistance");
        this.LUCK = new NamespacedKey(plugin, "luck");
        this.AUTO_SPAWNED = new NamespacedKey(plugin, "auto_spawned");
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
        this.ALL_STATS = Map.copyOf(map);
    }
}
