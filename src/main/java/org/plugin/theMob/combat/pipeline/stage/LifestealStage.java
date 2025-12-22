package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

public final class LifestealStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        Player p = ctx.attacker();
        if (p == null) return;
        double ls = ctx.weaponStat("lifesteal");
        ls += ctx.lifestealPercent();
        if (ls <= 0) return;
        double heal = ctx.damage() * (ls / 100.0);
        if (heal <= 0) return;
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = (max != null ? max.getValue() : 20.0);
        double newHp = Math.min(maxHp, p.getHealth() + heal);
        if (newHp > p.getHealth()) p.setHealth(newHp);
    }
}
