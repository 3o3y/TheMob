package org.plugin.theMob.boss;

import org.bukkit.attribute.Attribute;

import java.util.Locale;
import java.util.Map;

public final class AttributeResolver {

    private static final Map<String, Attribute> MAP = Map.ofEntries(
            Map.entry("movement-speed", Attribute.MOVEMENT_SPEED),
            Map.entry("speed", Attribute.MOVEMENT_SPEED),

            Map.entry("damage", Attribute.ATTACK_DAMAGE),
            Map.entry("attack-damage", Attribute.ATTACK_DAMAGE),

            Map.entry("armor", Attribute.ARMOR),
            Map.entry("armor-toughness", Attribute.ARMOR_TOUGHNESS),

            Map.entry("knockback-resistance", Attribute.KNOCKBACK_RESISTANCE),

            Map.entry("max-health", Attribute.MAX_HEALTH),
            Map.entry("health", Attribute.MAX_HEALTH),

            Map.entry("follow-range", Attribute.FOLLOW_RANGE),
            Map.entry("attack-speed", Attribute.ATTACK_SPEED),
            Map.entry("luck", Attribute.LUCK)
    );

    private AttributeResolver() {}

    public static Attribute resolve(String yamlKey) {
        if (yamlKey == null || yamlKey.isBlank()) return null;
        return MAP.get(yamlKey.toLowerCase(Locale.ROOT));
    }
}
