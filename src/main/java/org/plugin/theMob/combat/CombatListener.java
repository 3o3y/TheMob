package org.plugin.theMob.combat;

import java.util.IdentityHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.combat.visual.DamageNumberService;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.item.equipment.EquipmentBonuses;

public final class CombatListener implements Listener {
  private final TheMob plugin;
  private final EquipmentBonuses cache;
  private final DamageCalculator calculator;
  private final CombatDebugService debug;
  private final CustomEnchantSystem enchants;
  private final Map<EntityDamageByEntityEvent, Pending> pending = new IdentityHashMap<>();

  private record Pending(
      Player player, LivingEntity target, Map<String, Double> stats, DamageResult result) {}

  public CombatListener(
      TheMob plugin,
      EquipmentBonuses cache,
      DamageCalculator calc,
      CombatDebugService debug,
      CustomEnchantSystem enchants) {
    this.plugin = plugin;
    this.cache = cache;
    this.calculator = calc;
    this.debug = debug;
    this.enchants = enchants;
  }

  // Apply item bonuses before other plugins adjust outgoing/incoming damage.
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (CustomEnchantSystem.isSecondaryHit()) return;
    Player player = resolveAttacker(event.getDamager());
    if (player == null) {
      // Player-vs-player defense is already part of DamageCalculator. Mob/projectile damage
      // reaches the same curve here, after boss phase modifiers, exactly once.
      if (event.getEntity() instanceof Player target
          && target.getGameMode() != GameMode.CREATIVE
          && target.getGameMode() != GameMode.SPECTATOR) {
        double defense = cache.get(target).getOrDefault("defense", 0.0);
        double k = plugin.getConfig().getDouble("combat.defense.k", 200);
        event.setDamage(
            DamageFormula.clamp(
                event.getDamage() * DamageFormula.defenseMultiplier(defense, k), 0, 1_000_000));
      }
      return;
    }
    if (!(event.getEntity() instanceof LivingEntity target)
        || player.getGameMode() == GameMode.CREATIVE
        || player.getGameMode() == GameMode.SPECTATOR) return;
    Map<String, Double> stats = plugin.abilities().itemEffects(cache.get(player));
    DamageResult result =
        calculator.calculate(
            player,
            target,
            event.getDamage(),
            stats,
            plugin.getConfig().getConfigurationSection("combat"));
    event.setDamage(DamageFormula.clamp(result.finalDamage(), 0, 1_000_000));
    pending.put(event, new Pending(player, target, stats, result));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void afterDamage(EntityDamageByEntityEvent event) {
    Pending hit = pending.remove(event);
    if (hit == null || event.isCancelled()) return;
    double effective = Math.max(0, Math.min(event.getFinalDamage(), hit.target().getHealth()));
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (event.isCancelled()
                  || effective <= 0
                  || !hit.player().isOnline()
                  || hit.player().isDead()) return;
              double heal =
                  effective
                      * DamageFormula.clamp(
                          hit.stats().getOrDefault("lifesteal", 0.0) / 100, 0, .5);
              if (heal > 0) {
                heal = PlayerHealing.heal(hit.player(), heal);
              }
              if (enchants != null)
                enchants.trigger(hit.player(), hit.target(), hit.stats(), effective);
              ConfigurationSection config =
                  plugin.getConfig().getConfigurationSection("combat.damage-indicator");
              if (config != null
                  && config.getBoolean("enabled", false)
                  && ((hit.player().isOp() && config.getStringList("show-to").contains("op"))
                      || (hit.player().hasPermission("themob.admin")
                          && config.getStringList("show-to").contains("admin")))) {
                if (!(hit.target() instanceof Player))
                  DamageNumberService.spawn(plugin, hit.target(), effective, hit.result().crit());
                if (heal > 0) DamageNumberService.spawnHeal(plugin, hit.player(), heal);
              }
              if (debug != null) debug.send(hit.player(), hit.result());
            });
  }

  private Player resolveAttacker(Entity damager) {
    if (damager instanceof Player p) return p;
    if (damager instanceof Projectile p && p.getShooter() instanceof Player owner) return owner;
    if (damager instanceof Tameable t && t.getOwner() instanceof Player owner) return owner;
    return null;
  }
}
