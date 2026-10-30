package org.plugin.theMob.core.context;

import java.util.UUID;
import org.bukkit.entity.LivingEntity;

public record MobContext(UUID uuid, String mobId, boolean boss, LivingEntity entity) {
  public boolean valid() {
    return this.entity != null && this.entity.isValid() && !this.entity.isDead();
  }
}
