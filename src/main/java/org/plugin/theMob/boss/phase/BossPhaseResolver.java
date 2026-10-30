package org.plugin.theMob.boss.phase;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;

public final class BossPhaseResolver {
  public BossPhase resolve(LivingEntity boss, BossTemplate tpl) {
    if (boss == null || tpl == null) {
      return null;
    } else if (boss.isValid() && !boss.isDead()) {
      AttributeInstance maxAttr = org.plugin.theMob.compat.BukkitTypes.instance(boss, "MAX_HEALTH");
      if (maxAttr == null) {
        return null;
      } else {
        double max = maxAttr.getValue();
        if (max <= 0.0) {
          return null;
        } else {
          double hp = Math.max(0.0, boss.getHealth());
          double hpPercent = hp / max * 100.0;
          if (hpPercent >= 100.0) {
            hpPercent = 99.999;
          }

          return tpl.resolvePhase(hpPercent);
        }
      }
    } else {
      return null;
    }
  }
}
