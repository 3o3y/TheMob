package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.ConfigurationSection;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;
import org.plugin.theMob.mob.MobManager;

public final class BossPhaseReadStage implements DamageStage {

    private final MobManager mobs;
    public BossPhaseReadStage(MobManager mobs) {
        this.mobs = mobs;
    }
    @Override
    public void apply(DamageContext ctx) {
        LivingEntity victim = ctx.victim();
        if (victim == null) return;
        if (mobs == null) return;
        if (!mobs.isBoss(victim)) return;
        String mobId = mobs.mobIdOf(victim);
        if (mobId == null) return;
        BossTemplate tpl = mobs.bossTemplate(mobId);
        if (tpl == null) return;
        AttributeInstance maxAttr = victim.getAttribute(Attribute.MAX_HEALTH);
        double max = (maxAttr != null ? maxAttr.getValue() : 0.0);
        if (max <= 0.0) return;
        double hpPercent = (victim.getHealth() / max) * 100.0;
        BossPhase phase = tpl.findPhase(hpPercent);
        if (phase == null) return;
        ctx.setBossTemplate(tpl);
        ctx.setBossPhase(phase);
        ConfigurationSection combat = phase.cfg().getConfigurationSection("combat");
        if (combat != null) {
            double mult = combat.getDouble("receive-damage-multiplier", 1.0);
            if (mult <= 0) mult = 0.0;
            ctx.setReceiveMultiplier(mult);
            double ls = combat.getDouble("lifesteal", 0.0);
            double dk = combat.getDouble("deal-knockback", 0.0);
            if (ls != 0.0) ctx.setLifestealPercent(ctx.lifestealPercent() + ls);
            if (dk != 0.0) ctx.setDealKnockback(ctx.dealKnockback() + dk);
        }
        ConfigurationSection imm = phase.cfg().getConfigurationSection("immunities");
        if (imm != null) {
            if (imm.getBoolean("projectile", false) && ctx.event().getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE) {
                ctx.cancel();
            }
            if (imm.getBoolean("fire", false) && (ctx.event().getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE
                    || ctx.event().getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK
                    || ctx.event().getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.LAVA)) {
                ctx.cancel();
            }
        }
    }
}
