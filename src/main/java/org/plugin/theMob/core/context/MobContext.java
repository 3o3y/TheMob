package org.plugin.theMob.core.context;

import org.bukkit.entity.LivingEntity;

import java.util.UUID;

public record MobContext(
        UUID uuid,
        String mobId,
        boolean boss,
        LivingEntity entity
) {
    public boolean valid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }
}
