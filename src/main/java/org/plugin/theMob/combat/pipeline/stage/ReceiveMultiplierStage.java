package org.plugin.theMob.combat.pipeline.stage;

import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

public final class ReceiveMultiplierStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        double mult = ctx.receiveMultiplier();
        if (mult == 1.0) return;
        double dmg = ctx.damage() * mult;
        if (dmg < 0) dmg = 0;
        ctx.setDamage(dmg);
    }
}
