package org.plugin.theMob.core.context;

import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossTemplate;

public record BossContext(UUID uuid, String mobId, LivingEntity entity, BossTemplate template) {
  public boolean valid() {
    return this.entity != null && this.entity.isValid() && !this.entity.isDead();
  }
}
