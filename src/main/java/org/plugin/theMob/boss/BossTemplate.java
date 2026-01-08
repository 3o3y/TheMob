package org.plugin.theMob.boss;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BossTemplate {

    private final String mobId;
    private final Map<String, BossPhase> phases = new LinkedHashMap<>();

    public BossTemplate(String mobId) {
        this.mobId = Objects.requireNonNull(mobId, "mobId");
    }

    public void addPhase(BossPhase phase) {
        if (phase == null) return;
        phases.put(phase.id(), phase);
    }

    public boolean hasPhases() {
        return !phases.isEmpty();
    }

    public Collection<BossPhase> phases() {
        return Collections.unmodifiableCollection(phases.values());
    }

    public BossPhase findPhase(double hpPercent0to100) {
        for (BossPhase p : phases.values()) {
            if (p.matches(hpPercent0to100)) {
                return p;
            }
        }
        return null;
    }

    public String mobId() {
        return mobId;
    }
}
