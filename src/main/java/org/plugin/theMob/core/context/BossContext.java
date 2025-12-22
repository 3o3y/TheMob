package org.plugin.theMob.core.context;

import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossTemplate;

import java.util.UUID;

public record BossContext(
        UUID uuid,
        String mobId,
        LivingEntity entity,
        BossTemplate template
) {
    public boolean valid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }
}
