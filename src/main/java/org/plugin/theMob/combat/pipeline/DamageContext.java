package org.plugin.theMob.combat.pipeline;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    public DamageContext(
            EntityDamageByEntityEvent event,
            Player attacker,
            LivingEntity victim
    ) {
        this.event = Objects.requireNonNull(event, "event");
        this.attacker = attacker;
        this.victim = victim;

        this.baseDamage = Math.max(0.0, event.getDamage());
        this.damage = this.baseDamage;
    }

    // =====================================================
    // CORE
    // =====================================================

    public EntityDamageByEntityEvent event() {
        return event;
    }

    public Player attacker() {
        return attacker;
    }

    public LivingEntity victim() {
        return victim;
    }

    public double baseDamage() {
        return baseDamage;
    }

    public double damage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = Math.max(0.0, damage);
    }

    // =====================================================
    // STATE
    // =====================================================

    public boolean cancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCrit() {
        return crit;
    }

    public void setCrit(boolean crit) {
        this.crit = crit;
    }

    // =====================================================
    // MODIFIERS
    // =====================================================

    public double lifestealPercent() {
        return lifestealPercent;
    }

    public void setLifestealPercent(double lifestealPercent) {
        this.lifestealPercent = lifestealPercent;
    }

    public double dealKnockback() {
        return dealKnockback;
    }

    public void setDealKnockback(double dealKnockback) {
        this.dealKnockback = dealKnockback;
    }

    public double receiveMultiplier() {
        return receiveMultiplier;
    }

    public void setReceiveMultiplier(double receiveMultiplier) {
        this.receiveMultiplier = Math.max(0.0, receiveMultiplier);
    }

    // =====================================================
    // BOSS CONTEXT
    // =====================================================

    public BossTemplate bossTemplate() {
        return bossTemplate;
    }

    public void setBossTemplate(BossTemplate bossTemplate) {
        this.bossTemplate = bossTemplate;
    }

    public BossPhase bossPhase() {
        return bossPhase;
    }

    public void setBossPhase(BossPhase bossPhase) {
        this.bossPhase = bossPhase;
    }

    // =====================================================
    // WEAPON STATS
    // =====================================================

    public Map<String, Double> weaponStats() {
        return Collections.unmodifiableMap(weaponStats);
    }

    public void putWeaponStat(String key, double value) {
        if (key == null || value == 0.0) return;
        weaponStats.put(key, value);
    }

    public double weaponStat(String key) {
        return weaponStats.getOrDefault(key, 0.0);
    }

    // =====================================================
    // PLAYER TOTALS
    // =====================================================

    public Map<String, Double> playerTotals() {
        return playerTotals;
    }

    public void setPlayerTotals(Map<String, Double> playerTotals) {
        this.playerTotals = (playerTotals != null ? playerTotals : Collections.emptyMap());
    }

    public double playerTotal(String key) {
        return playerTotals.getOrDefault(key, 0.0);
    }
}
