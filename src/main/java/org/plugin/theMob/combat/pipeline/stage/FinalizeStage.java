package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.event.entity.EntityDamageEvent;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

public final class FinalizeStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        if (ctx == null) return;

        EntityDamageEvent event = ctx.event();
        if (event == null) return;

        if (ctx.cancelled()) {
            event.setCancelled(true);
            return;
        }

        double dmg = ctx.damage();
        if (dmg < 0.0) dmg = 0.0;

        event.setDamage(dmg);
    }
}
