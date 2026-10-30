package org.plugin.theMob.api;

import org.bukkit.entity.EntityType;

/** Immutable configuration summary. The ID is the YAML mob-id. */
public record MobDefinition(String id, String displayName, EntityType entityType, boolean boss) {}
