package org.plugin.theMob.mob.ability;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.plugin.theMob.core.KeyRegistry;

/** Phase abilities restore base flags, effects and immunity tags on exit. */
public final class AbilityEngine {
  private record Snapshot(
      boolean silent,
      boolean invulnerable,
      boolean gravity,
      boolean ai,
      boolean glowing,
      Collection<PotionEffect> potions,
      Map<NamespacedKey, Integer> immunities) {}

  private final KeyRegistry keys;
  private final Map<UUID, Snapshot> snapshots = new HashMap<>();

  public AbilityEngine(KeyRegistry keys) {
    this.keys = keys;
  }

  public void apply(LivingEntity mob, ConfigurationSection config) {
    if (mob == null || config == null) return;
    ConfigurationSection abilities = config.getConfigurationSection("abilities");
    if (abilities == null) return;
    var oldImmunities = new HashMap<NamespacedKey, Integer>();
    var pdc = mob.getPersistentDataContainer();
    for (var key : pdc.getKeys())
      if (key.getNamespace().equals(keys.MOB_ID.getNamespace())
          && key.getKey().startsWith("immune_")
          && pdc.has(key, PersistentDataType.INTEGER))
        oldImmunities.put(key, pdc.get(key, PersistentDataType.INTEGER));
    snapshots.put(
        mob.getUniqueId(),
        new Snapshot(
            mob.isSilent(),
            mob.isInvulnerable(),
            mob.hasGravity(),
            mob.hasAI(),
            mob.isGlowing(),
            List.copyOf(mob.getActivePotionEffects()),
            oldImmunities));
    mob.setSilent(abilities.getBoolean("silent", mob.isSilent()));
    mob.setInvulnerable(abilities.getBoolean("invulnerable", mob.isInvulnerable()));
    mob.setGravity(abilities.getBoolean("gravity", mob.hasGravity()));
    if (abilities.isSet("no-ai")) mob.setAI(!abilities.getBoolean("no-ai"));
    mob.setGlowing(abilities.getBoolean("glowing", mob.isGlowing()));
    if (abilities.isSet("can-pickup-items"))
      mob.setCanPickupItems(abilities.getBoolean("can-pickup-items"));
    if (abilities.isSet("persistent")) mob.setPersistent(abilities.getBoolean("persistent"));
    var immune = abilities.getConfigurationSection("immune");
    if (immune != null)
      for (String key : immune.getKeys(false))
        pdc.set(immunityKey(key), PersistentDataType.INTEGER, immune.getBoolean(key) ? 1 : 0);
    var effects = abilities.getConfigurationSection("effects");
    Map<String, PotionEffectType> types =
        Map.ofEntries(
            Map.entry("speed", org.plugin.theMob.compat.BukkitTypes.potion("SPEED")),
            Map.entry("strength", org.plugin.theMob.compat.BukkitTypes.potion("STRENGTH")),
            Map.entry("resistance", org.plugin.theMob.compat.BukkitTypes.potion("RESISTANCE")),
            Map.entry("regeneration", org.plugin.theMob.compat.BukkitTypes.potion("REGENERATION")),
            Map.entry("jump-boost", org.plugin.theMob.compat.BukkitTypes.potion("JUMP_BOOST")),
            Map.entry(
                "fire-resistance", org.plugin.theMob.compat.BukkitTypes.potion("FIRE_RESISTANCE")),
            Map.entry("invisibility", org.plugin.theMob.compat.BukkitTypes.potion("INVISIBILITY")),
            Map.entry(
                "water-breathing", org.plugin.theMob.compat.BukkitTypes.potion("WATER_BREATHING")),
            Map.entry("slow-falling", org.plugin.theMob.compat.BukkitTypes.potion("SLOW_FALLING")));
    for (var entry : types.entrySet())
      if (effects != null && effects.contains(entry.getKey()))
        applyPotion(mob, effects, entry.getKey(), entry.getValue());
    if (abilities.getBoolean("fire-immunity", false))
      mob.addPotionEffect(
          new PotionEffect(
              org.plugin.theMob.compat.BukkitTypes.potion("FIRE_RESISTANCE"),
              Integer.MAX_VALUE,
              0,
              false,
              false));
    if (abilities.getBoolean("water-breathing", false))
      mob.addPotionEffect(
          new PotionEffect(
              org.plugin.theMob.compat.BukkitTypes.potion("WATER_BREATHING"),
              Integer.MAX_VALUE,
              0,
              false,
              false));
    if (abilities.getBoolean("invisibility", false))
      mob.addPotionEffect(
          new PotionEffect(
              org.plugin.theMob.compat.BukkitTypes.potion("INVISIBILITY"),
              Integer.MAX_VALUE,
              0,
              false,
              false));
  }

  private NamespacedKey immunityKey(String key) {
    return new NamespacedKey(
        keys.MOB_ID.getNamespace(), "immune_" + key.toLowerCase(Locale.ROOT).replace('-', '_'));
  }

  private void applyPotion(
      LivingEntity mob, ConfigurationSection effects, String key, PotionEffectType type) {
    if (Boolean.FALSE.equals(effects.get(key))) return;
    int amplifier =
        org.plugin.theMob.compat.Numbers.clamp(
            effects.getInt(key + ".amplifier", effects.getInt(key, 0)), 0, 255);
    mob.addPotionEffect(
        new PotionEffect(
            type,
            effects.getBoolean(key + ".permanent", false) ? Integer.MAX_VALUE : 1200,
            amplifier,
            false,
            false));
  }

  public void rollback(LivingEntity mob) {
    if (mob == null) return;
    Snapshot old = snapshots.remove(mob.getUniqueId());
    if (old == null) return;
    mob.setSilent(old.silent());
    mob.setInvulnerable(old.invulnerable());
    mob.setGravity(old.gravity());
    mob.setAI(old.ai());
    mob.setGlowing(old.glowing());
    for (var effect : List.copyOf(mob.getActivePotionEffects()))
      mob.removePotionEffect(effect.getType());
    for (var effect : old.potions()) mob.addPotionEffect(effect);
    var pdc = mob.getPersistentDataContainer();
    for (var key : Set.copyOf(pdc.getKeys()))
      if (key.getNamespace().equals(keys.MOB_ID.getNamespace())
          && key.getKey().startsWith("immune_")) pdc.remove(key);
    old.immunities().forEach((key, value) -> pdc.set(key, PersistentDataType.INTEGER, value));
  }
}
