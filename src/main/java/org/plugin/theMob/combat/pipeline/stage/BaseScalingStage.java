package org.plugin.theMob.combat.pipeline.stage;

import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

public final class BaseScalingStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        if (ctx == null) return;

        double dmg = ctx.damage();

        double base = ctx.weaponStat("damage");
        double extra = ctx.weaponStat("extra_damage");

        if (base != 0.0) dmg += base;
        if (extra != 0.0) dmg += extra;

        if (dmg < 0.0) dmg = 0.0;

        ctx.setDamage(dmg);
    }
}
