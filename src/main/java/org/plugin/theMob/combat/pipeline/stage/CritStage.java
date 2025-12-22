package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

import java.util.Random;

public final class CritStage implements DamageStage {

    private final Random rnd = new Random();
    @Override
    public void apply(DamageContext ctx) {
        Player p = ctx.attacker();
        if (p == null) return;
        double chance = ctx.weaponStat("crit");
        if (chance <= 0) return;
        double roll = rnd.nextDouble() * 100.0;
        if (roll > chance) return;
        double mul = ctx.weaponStat("crit_multiplier");
        if (mul <= 0) mul = 1.0;
        if (mul < 1.0) mul = 1.0;
        ctx.setCrit(true);
        ctx.setDamage(ctx.damage() * mul);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
    }
}
