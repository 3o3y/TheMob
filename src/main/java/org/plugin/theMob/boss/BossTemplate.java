package org.plugin.theMob.boss;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public final class BossTemplate {
  private final String mobId;
  private final int arenaRadiusChunks;
  private final Map<String, BossPhase> phases = new LinkedHashMap<>();
  private final Map<String, ConfigurationSection> phaseConfigs = new HashMap<>();
  private BossPhase lastResolvedPhase;

  public BossTemplate(String mobId, int arenaRadiusChunks) {
    this.mobId = Objects.requireNonNull(mobId, "mobId");
    this.arenaRadiusChunks = Math.max(1, arenaRadiusChunks);
  }

  public int arenaRadiusChunks() {
    return this.arenaRadiusChunks;
  }

  public void addPhase(BossPhase phase, ConfigurationSection cfg) {
    if (phase != null && cfg != null) {
      this.phases.put(phase.id(), phase);
      this.phaseConfigs.put(phase.id(), cfg);
    }
  }

  public boolean hasPhases() {
    return !this.phases.isEmpty();
  }

  public Collection<BossPhase> phases() {
    return Collections.unmodifiableCollection(this.phases.values());
  }

  public BossPhase resolvePhase(double hpPercent0to100) {
    BossPhase matched = null;

    for (BossPhase p : this.phases.values()) {
      if (p.matches(hpPercent0to100)) {
        matched = p;
        break;
      }
    }

    if (matched == null) {
      return this.lastResolvedPhase;
    } else {
      if (this.lastResolvedPhase == null || matched.min() < this.lastResolvedPhase.min()) {
        this.lastResolvedPhase = matched;
      }

      return this.lastResolvedPhase;
    }
  }

  public ConfigurationSection phaseConfig(String phaseId) {
    return this.phaseConfigs.get(phaseId);
  }

  public String mobId() {
    return this.mobId;
  }

  public BossTemplate newInstance() {
    BossTemplate copy = new BossTemplate(this.mobId, this.arenaRadiusChunks);
    for (BossPhase phase : this.phases.values())
      copy.addPhase(phase, this.phaseConfigs.get(phase.id()));
    return copy;
  }

  public void resetPhaseState() {
    this.lastResolvedPhase = null;
  }
}
