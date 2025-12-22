package org.plugin.theMob.boss;

import org.bukkit.attribute.Attribute;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AttributeResolver {

    private static final Map<String, Attribute> MAP = new HashMap<>();

    static {
        register("movement-speed", Attribute.MOVEMENT_SPEED);
        register("damage", Attribute.ATTACK_DAMAGE);
        register("attack-damage", Attribute.ATTACK_DAMAGE);
        register("armor", Attribute.ARMOR);
        register("armor-toughness", Attribute.ARMOR_TOUGHNESS);
        register("knockback-resistance", Attribute.KNOCKBACK_RESISTANCE);
        register("max-health", Attribute.MAX_HEALTH);
        register("health", Attribute.MAX_HEALTH);
        register("follow-range", Attribute.FOLLOW_RANGE);
        register("attack-speed", Attribute.ATTACK_SPEED);
        register("luck", Attribute.LUCK);
    }
    private static void register(String key, Attribute attr) {
        MAP.put(key.toLowerCase(Locale.ROOT), attr);
    }
    public static Attribute resolve(String yamlKey) {
        if (yamlKey == null) return null;
        return MAP.get(yamlKey.toLowerCase(Locale.ROOT));
    }
    private AttributeResolver() {}
}
