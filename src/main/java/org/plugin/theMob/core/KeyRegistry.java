package org.plugin.theMob.core;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class KeyRegistry {
  public final NamespacedKey MOB_ID;
  public final NamespacedKey BASE_NAME;
  public final NamespacedKey IS_BOSS;
  public final NamespacedKey NO_DROPS;
  public final NamespacedKey VISUAL_HEAD;
  public final NamespacedKey SPAWN_TYPE;
  public final NamespacedKey AUTO_SPAWN_ID;
  public final NamespacedKey BOSS_SPAWN_TIME;
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
  public final NamespacedKey BOSS_DEATH_HANDLED;
  public final NamespacedKey SPAWN_EGG_MOB_ID;
  public final NamespacedKey THEMOB_SPAWNER;
  public final NamespacedKey THEMOB_SPAWNER_MOB;
  public final NamespacedKey THEMOB_SPAWNER_NEXT_TICK;
  public final Map<String, NamespacedKey> ALL_STATS;

  public KeyRegistry(Plugin plugin) {
    this.MOB_ID = new NamespacedKey(plugin, "mob_id");
    this.BASE_NAME = new NamespacedKey(plugin, "base_name");
    this.IS_BOSS = new NamespacedKey(plugin, "is_boss");
    this.NO_DROPS = new NamespacedKey(plugin, "no_drops");
    this.VISUAL_HEAD = new NamespacedKey(plugin, "visual_head");
    this.SPAWN_TYPE = new NamespacedKey(plugin, "spawn_type");
    this.AUTO_SPAWN_ID = new NamespacedKey(plugin, "auto_spawn_id");
    this.BOSS_SPAWN_TIME = new NamespacedKey(plugin, "boss_spawn_time");
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
    this.DROPS_DONE = new NamespacedKey(plugin, "drops_done");
    this.BOSS_DEATH_HANDLED = new NamespacedKey(plugin, "boss_death_handled");
    this.SPAWN_EGG_MOB_ID = new NamespacedKey(plugin, "spawn_egg_mob_id");
    this.THEMOB_SPAWNER = new NamespacedKey(plugin, "themob_spawner");
    this.THEMOB_SPAWNER_MOB = new NamespacedKey(plugin, "themob_spawner_mob");
    this.THEMOB_SPAWNER_NEXT_TICK = new NamespacedKey(plugin, "themob_spawner_next_tick");
    Map<String, NamespacedKey> map = new LinkedHashMap<>();
    map.put("damage", this.DAMAGE);
    map.put("crit_chance", this.CRIT);
    map.put("deal_damage_multiplier", new NamespacedKey(plugin, "deal_damage_multiplier"));
    map.put("receive_damage_multiplier", new NamespacedKey(plugin, "receive_damage_multiplier"));
    map.put("deal_knockback", new NamespacedKey(plugin, "deal_knockback"));
    map.put("extra_damage", this.EXTRA_DAMAGE);
    map.put("crit", this.CRIT);
    map.put("crit_multiplier", this.CRIT_MULTIPLIER);
    map.put("lifesteal", this.LIFESTEAL);
    map.put("armor", this.ARMOR);
    map.put("defense", this.DEFENSE);
    map.put("health", this.HEALTH);
    map.put("movement_speed", this.MOVEMENT_SPEED);
    map.put("attack_speed", this.ATTACK_SPEED);
    map.put("knockback_resistance", this.KNOCKBACK_RESISTANCE);
    map.put("luck", this.LUCK);
    this.ALL_STATS = Map.copyOf(map);
  }

  public NamespacedKey mobStat(String key) {
    return key == null
        ? null
        : this.ALL_STATS.get(key.toLowerCase(java.util.Locale.ROOT).replace("-", "_"));
  }

  public boolean isTheMobSpawnerKey(NamespacedKey key) {
    return this.THEMOB_SPAWNER.equals(key)
        || this.THEMOB_SPAWNER_MOB.equals(key)
        || this.THEMOB_SPAWNER_NEXT_TICK.equals(key);
  }
}
