package org.plugin.theMob.combat.pipeline.stage;

import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

public final class BaseScalingStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        double dmg = ctx.damage();
        dmg += ctx.weaponStat("damage");
        dmg += ctx.weaponStat("extra_damage");
        if (dmg < 0) dmg = 0;
        ctx.setDamage(dmg);
    }
}
