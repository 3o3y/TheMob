package org.plugin.theMob.boss;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.combat.DamageFormula;
import org.plugin.theMob.mob.MobManager;

public final class BossCombatListener implements Listener {
  private record Hit(LivingEntity boss, double steal) {}

  private final Plugin plugin;
  private final MobManager mobs;
  private final BossPhaseController phases;
  private final Map<EntityDamageByEntityEvent, Hit> pending = new IdentityHashMap<>();

  public BossCombatListener(Plugin plugin, MobManager mobs, BossPhaseController phases) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.phases = phases;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onCombat(EntityDamageByEntityEvent event) {
    if (plugin instanceof org.plugin.theMob.TheMob owner
        && !owner.abilities().enabled("bosses.phase-combat")) return;
    if (!(event.getDamager() instanceof LivingEntity boss) || !mobs.isBoss(boss) || boss.isDead())
      return;
    var phase = phases.currentPhase(boss);
    if (phase == null) return;
    var cfg = phase.cfg().getConfigurationSection("combat");
    if (cfg == null) return;
    double damage =
        event.getDamage() * DamageFormula.clamp(cfg.getDouble("deal-damage-multiplier", 1), 0, 50);
    if (ThreadLocalRandom.current().nextDouble() * 100
        < DamageFormula.clamp(cfg.getDouble("crit-chance", 0), 0, 100))
      damage *= 1 + DamageFormula.clamp(cfg.getDouble("crit-multiplier", 0), 0, 10);
    event.setDamage(DamageFormula.clamp(damage, 0, 1_000_000));
    pending.put(
        event, new Hit(boss, DamageFormula.clamp(cfg.getDouble("lifesteal", 0) / 100, 0, .5)));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void afterCombat(EntityDamageByEntityEvent event) {
    var hit = pending.remove(event);
    if (hit == null || event.isCancelled() || !(event.getEntity() instanceof LivingEntity target))
      return;
    double heal = Math.max(0, Math.min(event.getFinalDamage(), target.getHealth())) * hit.steal();
    if (heal <= 0) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!event.isCancelled() && hit.boss().isValid() && !hit.boss().isDead()) {
                var max =
                    hit.boss()
                        .getAttribute(org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH"));
                if (max != null)
                  hit.boss().setHealth(Math.min(max.getValue(), hit.boss().getHealth() + heal));
              }
            });
  }
}
