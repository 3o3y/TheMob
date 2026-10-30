package org.plugin.theMob.api;

import java.util.UUID;

/** Immutable event/query data. A minion's definitionId is its parent boss's mob-id. */
public record MobSnapshot(
    UUID entityId,
    String definitionId,
    String displayName,
    boolean boss,
    boolean minion,
    String phaseId,
    SpawnReason spawnReason) {}
