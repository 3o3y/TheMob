package org.plugin.theMob.boss;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;

public final class BossImmunityListener implements Listener {
  private final MobManager mobs;
  private final BossPhaseController phases;
  private static final Map<DamageCause, String> IMMUNITY_KEYS = new EnumMap<>(DamageCause.class);

  public BossImmunityListener(MobManager mobs, BossPhaseController phases) {
    this.mobs = mobs;
    this.phases = phases;
  }

  @EventHandler(ignoreCancelled = true)
  public void onDamage(EntityDamageEvent event) {
    var owner = org.bukkit.Bukkit.getPluginManager().getPlugin("TheMob");
    if (owner instanceof org.plugin.theMob.TheMob plugin
        && !plugin.abilities().enabled("bosses.phase-abilities")) return;
    if (event.getEntity() instanceof LivingEntity boss) {
      if (this.mobs.isCustomMob(boss)) {
        String baseKey = IMMUNITY_KEYS.get(event.getCause());
        if (baseKey != null
            && boss.getPersistentDataContainer()
                    .getOrDefault(
                        new org.bukkit.NamespacedKey(
                            this.mobs.keys().MOB_ID.getNamespace(), "immune_" + baseKey),
                        org.bukkit.persistence.PersistentDataType.INTEGER,
                        0)
                == 1) {
          event.setCancelled(true);
          return;
        }
        if (!this.mobs.isBoss(boss)) return;
        if (boss.isValid() && !boss.isDead()) {
          BossPhase phase = this.phases.currentPhase(boss);
          if (phase != null) {
            ConfigurationSection imm = phase.cfg().getConfigurationSection("immunities");
            if (imm != null && !imm.getKeys(false).isEmpty()) {
              String key = IMMUNITY_KEYS.get(event.getCause());
              if (key != null) {
                if (imm.getBoolean(key, false)) {
                  event.setCancelled(true);
                }
              }
            }
          }
        }
      }
    }
  }

  static {
    IMMUNITY_KEYS.put(DamageCause.FIRE, "fire");
    IMMUNITY_KEYS.put(DamageCause.FIRE_TICK, "fire");
    IMMUNITY_KEYS.put(DamageCause.LAVA, "fire");
    IMMUNITY_KEYS.put(DamageCause.FALL, "fall");
    IMMUNITY_KEYS.put(DamageCause.DROWNING, "drowning");
    IMMUNITY_KEYS.put(DamageCause.POISON, "poison");
    IMMUNITY_KEYS.put(DamageCause.LIGHTNING, "lightning");
    IMMUNITY_KEYS.put(DamageCause.PROJECTILE, "projectile");
    IMMUNITY_KEYS.put(DamageCause.ENTITY_EXPLOSION, "explosion");
    IMMUNITY_KEYS.put(DamageCause.BLOCK_EXPLOSION, "explosion");
  }
}
