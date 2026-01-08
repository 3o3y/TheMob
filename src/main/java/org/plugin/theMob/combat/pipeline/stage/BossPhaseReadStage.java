package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
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
        if (ctx == null || mobs == null) return;

        LivingEntity boss = ctx.victim();
        if (boss == null || !boss.isValid()) return;
        if (!mobs.isBoss(boss)) return;

        String mobId = mobs.mobIdOf(boss);
        if (mobId == null) return;

        BossTemplate tpl = mobs.bossTemplate(mobId);
        if (tpl == null || !tpl.hasPhases()) return;

        AttributeInstance maxAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr == null || maxAttr.getValue() <= 0.0) return;

        double hpPercent = (boss.getHealth() / maxAttr.getValue()) * 100.0;
        BossPhase phase = tpl.findPhase(hpPercent);
        if (phase == null) return;

        ctx.setBossTemplate(tpl);
        ctx.setBossPhase(phase);

        // =====================================================
        // COMBAT MODIFIERS
        // =====================================================

        ConfigurationSection combat = phase.cfg().getConfigurationSection("combat");
        if (combat != null) {

            double receiveMul = combat.getDouble("receive-damage-multiplier", 1.0);
            if (receiveMul < 0.0) receiveMul = 0.0;
            ctx.setReceiveMultiplier(receiveMul);

            double lifesteal = combat.getDouble("lifesteal", 0.0);
            if (lifesteal != 0.0) {
                ctx.setLifestealPercent(ctx.lifestealPercent() + lifesteal);
            }

            double knockback = combat.getDouble("deal-knockback", 0.0);
            if (knockback != 0.0) {
                ctx.setDealKnockback(ctx.dealKnockback() + knockback);
            }
        }

        // =====================================================
        // IMMUNITIES (PIPELINE-LEVEL)
        // =====================================================

        ConfigurationSection imm = phase.cfg().getConfigurationSection("immunities");
        if (imm != null) {
            EntityDamageEvent.DamageCause cause = ctx.event().getCause();

            if (imm.getBoolean("projectile", false)
                    && cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                ctx.cancel();
                return;
            }

            if (imm.getBoolean("fire", false)) {
                if (cause == EntityDamageEvent.DamageCause.FIRE
                        || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                        || cause == EntityDamageEvent.DamageCause.LAVA) {
                    ctx.cancel();
                }
            }
        }
    }
}
