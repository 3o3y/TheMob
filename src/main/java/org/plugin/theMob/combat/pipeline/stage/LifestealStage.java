package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

public final class LifestealStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        if (ctx == null) return;

        Player p = ctx.attacker();
        if (p == null || !p.isOnline()) return;

        double lifesteal = ctx.weaponStat("lifesteal") + ctx.lifestealPercent();
        if (lifesteal <= 0.0) return;

        double dmg = ctx.damage();
        if (dmg <= 0.0) return;

        AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr == null) return;

        double heal = dmg * (lifesteal / 100.0);
        if (heal <= 0.0) return;

        double maxHp = maxAttr.getValue();
        double newHp = Math.min(maxHp, p.getHealth() + heal);

        if (newHp > p.getHealth()) {
            p.setHealth(newHp);
        }
    }
}
