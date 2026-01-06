package org.plugin.theMob.boss.minion;

import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;

public final class MinionRules {

    private MinionRules() {}

    // Global safety cap (server-wide)
    public static final int MAX_MINIONS_GLOBAL = 200;

    // Disallowed minion entity types (dangerous / weird / boss-class)
    private static final Set<EntityType> DISALLOWED = EnumSet.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.ARMOR_STAND,
            EntityType.PLAYER
    );

    public static boolean isAllowed(EntityType type) {
        if (type == null) return false;
        if (!type.isAlive() || !type.isSpawnable()) return false;
        return !DISALLOWED.contains(type);
    }
}
