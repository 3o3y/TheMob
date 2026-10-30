package org.plugin.theMob.boss;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossTemplateTest {
  @Test
  void instancesHaveIndependentMonotonicPhaseProgress() {
    var cfg = new YamlConfiguration();
    var definition = new BossTemplate("same_type", 1);
    definition.addPhase(new BossPhase("healthy", 50, 100, "", cfg), cfg);
    definition.addPhase(new BossPhase("enraged", 0, 50, "", cfg), cfg);
    var a = definition.newInstance();
    var b = definition.newInstance();
    assertEquals("enraged", a.resolvePhase(25).id());
    assertEquals("healthy", b.resolvePhase(99).id());
    assertEquals("enraged", a.resolvePhase(99).id());
    assertEquals("healthy", definition.newInstance().resolvePhase(99).id());
  }
}
