package org.plugin.theMob.combat.pipeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;

public final class DamageContext {
  private final EntityDamageByEntityEvent event;
  private final Player attacker;
  private final LivingEntity victim;
  private final double baseDamage;
  private double damage;
  private boolean cancelled;
  private boolean crit;
  private double lifestealPercent;
  private double dealKnockback;
  private double receiveMultiplier = 1.0;
  private BossTemplate bossTemplate;
  private BossPhase bossPhase;
  private final Map<String, Double> weaponStats = new HashMap<>();
  private Map<String, Double> playerTotals = Collections.emptyMap();

  public DamageContext(EntityDamageByEntityEvent event, Player attacker, LivingEntity victim) {
    this.event = Objects.requireNonNull(event, "event");
    this.attacker = attacker;
    this.victim = victim;
    this.baseDamage = Math.max(0.0, event.getDamage());
    this.damage = this.baseDamage;
  }

  public EntityDamageByEntityEvent event() {
    return this.event;
  }

  public Player attacker() {
    return this.attacker;
  }

  public LivingEntity victim() {
    return this.victim;
  }

  public double baseDamage() {
    return this.baseDamage;
  }

  public double damage() {
    return this.damage;
  }

  public void setDamage(double damage) {
    this.damage = Math.max(0.0, damage);
  }

  public boolean cancelled() {
    return this.cancelled;
  }

  public void cancel() {
    this.cancelled = true;
  }

  public boolean isCrit() {
    return this.crit;
  }

  public void setCrit(boolean crit) {
    this.crit = crit;
  }

  public double lifestealPercent() {
    return this.lifestealPercent;
  }

  public void setLifestealPercent(double lifestealPercent) {
    this.lifestealPercent = lifestealPercent;
  }

  public double dealKnockback() {
    return this.dealKnockback;
  }

  public void setDealKnockback(double dealKnockback) {
    this.dealKnockback = dealKnockback;
  }

  public double receiveMultiplier() {
    return this.receiveMultiplier;
  }

  public void setReceiveMultiplier(double receiveMultiplier) {
    this.receiveMultiplier = Math.max(0.0, receiveMultiplier);
  }

  public BossTemplate bossTemplate() {
    return this.bossTemplate;
  }

  public void setBossTemplate(BossTemplate bossTemplate) {
    this.bossTemplate = bossTemplate;
  }

  public BossPhase bossPhase() {
    return this.bossPhase;
  }

  public void setBossPhase(BossPhase bossPhase) {
    this.bossPhase = bossPhase;
  }

  public Map<String, Double> weaponStats() {
    return Collections.unmodifiableMap(this.weaponStats);
  }

  public void putWeaponStat(String key, double value) {
    if (key != null && value != 0.0) {
      this.weaponStats.put(key, value);
    }
  }

  public double weaponStat(String key) {
    return this.weaponStats.getOrDefault(key, 0.0);
  }

  public double stat(String key) {
    if (key == null) {
      return 0.0;
    } else {
      Double w = this.weaponStats.get(key);
      if (w != null) {
        return w;
      } else {
        Double p = this.playerTotals.get(key);
        return p != null ? p : 0.0;
      }
    }
  }

  public boolean hasStat(String key) {
    return this.weaponStats.containsKey(key) || this.playerTotals.containsKey(key);
  }

  public Map<String, Double> playerTotals() {
    return this.playerTotals;
  }

  public void setPlayerTotals(Map<String, Double> playerTotals) {
    this.playerTotals = playerTotals != null ? playerTotals : Collections.emptyMap();
  }

  public double playerTotal(String key) {
    return this.playerTotals.getOrDefault(key, 0.0);
  }
}
