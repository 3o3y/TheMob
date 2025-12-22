package org.plugin.theMob.combat.pipeline.stage;

import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

public final class FinalizeStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        if (ctx.cancelled()) {
            ctx.event().setCancelled(true);
            return;
        }
        ctx.event().setDamage(ctx.damage());
    }
}
