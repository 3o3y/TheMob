package org.plugin.theMob.boss;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class BossTemplate {

    private final String mobId;

    // ✅ Arena definition (in chunks)
    private final int arenaRadiusChunks;

    // Reihenfolge behalten (YAML Ordnung)
    private final Map<String, BossPhase> phases = new LinkedHashMap<>();
    private final Map<String, ConfigurationSection> phaseConfigs = new HashMap<>();

    public BossTemplate(String mobId, int arenaRadiusChunks) {
        this.mobId = Objects.requireNonNull(mobId, "mobId");
        this.arenaRadiusChunks = Math.max(1, arenaRadiusChunks);
    }

    // =====================================================
    // ARENA
    // =====================================================

    public int arenaRadiusChunks() {
        return arenaRadiusChunks;
    }

    // =====================================================
    // PHASES
    // =====================================================

    public void addPhase(BossPhase phase, ConfigurationSection cfg) {
        if (phase == null || cfg == null) return;
        phases.put(phase.id(), phase);
        phaseConfigs.put(phase.id(), cfg);
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

    public ConfigurationSection phaseConfig(String phaseId) {
        return phaseConfigs.get(phaseId);
    }

    // =====================================================
    // META
    // =====================================================

    public String mobId() {
        return mobId;
    }
}
