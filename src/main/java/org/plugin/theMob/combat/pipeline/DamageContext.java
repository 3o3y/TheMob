package org.plugin.theMob.combat.pipeline;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DamageContext {

    private final EntityDamageByEntityEvent event;
    private final Player attacker;
    private final LivingEntity victim;
    private double baseDamage;
    private double damage;
    private boolean cancelled;
    private boolean crit;
    private double lifestealPercent;
    private double dealKnockback;            // optional (Phase 3+)
    private double receiveMultiplier = 1.0;  // boss phase receive-damage-multiplier
    private BossTemplate bossTemplate;        // optional
    private BossPhase bossPhase;              // optional
    private final Map<String, Double> weaponStats = new HashMap<>();
    private Map<String, Double> playerTotals = Collections.emptyMap(); // from cache
    public DamageContext(EntityDamageByEntityEvent event, Player attacker, LivingEntity victim) {
        this.event = event;
        this.attacker = attacker;
        this.victim = victim;
        this.baseDamage = event.getDamage();
        this.damage = this.baseDamage;
    }
    public EntityDamageByEntityEvent event() { return event; }
    public Player attacker() { return attacker; }
    public LivingEntity victim() { return victim; }
    public double baseDamage() { return baseDamage; }
    public double damage() { return damage; }
    public void setDamage(double damage) { this.damage = damage; }
    public boolean cancelled() { return cancelled; }
    public void cancel() { this.cancelled = true; }
    public boolean crit() { return crit; }
    public void setCrit(boolean crit) { this.crit = crit; }
    public double lifestealPercent() { return lifestealPercent; }
    public void setLifestealPercent(double lifestealPercent) { this.lifestealPercent = lifestealPercent; }
    public double dealKnockback() { return dealKnockback; }
    public void setDealKnockback(double dealKnockback) { this.dealKnockback = dealKnockback; }
    public double receiveMultiplier() { return receiveMultiplier; }
    public void setReceiveMultiplier(double receiveMultiplier) { this.receiveMultiplier = receiveMultiplier; }
    public BossTemplate bossTemplate() { return bossTemplate; }
    public void setBossTemplate(BossTemplate bossTemplate) { this.bossTemplate = bossTemplate; }
    public BossPhase bossPhase() { return bossPhase; }
    public void setBossPhase(BossPhase bossPhase) { this.bossPhase = bossPhase; }
    public Map<String, Double> weaponStats() { return weaponStats; }
    public void putWeaponStat(String key, double value) { weaponStats.put(key, value); }
    public double weaponStat(String key) { return weaponStats.getOrDefault(key, 0.0); }
    public Map<String, Double> playerTotals() { return playerTotals; }
    public void setPlayerTotals(Map<String, Double> playerTotals) {
        this.playerTotals = (playerTotals != null ? playerTotals : Collections.emptyMap());
    }
    public double playerTotal(String key) {
        return playerTotals.getOrDefault(key, 0.0);
    }
}
