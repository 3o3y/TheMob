package org.plugin.theMob.core.context;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.mob.MobManager;

public final class ContextFactory {
  private final MobManager mobs;

  public ContextFactory(MobManager mobs) {
    this.mobs = mobs;
  }

  public MobContext mob(LivingEntity e) {
    if (e == null) {
      return null;
    } else {
      String id = this.mobs.mobIdOf(e);
      if (id == null) {
        return null;
      } else {
        boolean boss = this.mobs.isBoss(e);
        return new MobContext(e.getUniqueId(), id, boss, e);
      }
    }
  }

  public BossContext boss(LivingEntity e) {
    if (e == null) {
      return null;
    } else {
      String id = this.mobs.mobIdOf(e);
      if (id == null) {
        return null;
      } else {
        Integer flag =
            (Integer)
                e.getPersistentDataContainer()
                    .get(this.mobs.keys().IS_BOSS, PersistentDataType.INTEGER);
        boolean isBoss = flag != null ? flag == 1 : this.mobs.isBoss(e);
        if (!isBoss) {
          return null;
        } else {
          BossTemplate tpl = this.mobs.bossTemplate(id);
          return tpl == null ? null : new BossContext(e.getUniqueId(), id, e, tpl);
        }
      }
    }
  }
}
