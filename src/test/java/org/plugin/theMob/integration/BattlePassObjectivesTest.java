package org.plugin.theMob.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.TheMobDeathEvent;

class BattlePassObjectivesTest {
  BattlePassObjectives parse(String yaml) throws Exception {
    var config = new YamlConfiguration();
    config.loadFromString(yaml);
    return BattlePassObjectives.parse(config, List.of("pig_lvl_1", "zombie_boss_template"));
  }

  TheMobDeathEvent event(
      String id, boolean boss, boolean minion, String world, String phase, SpawnReason reason) {
    var entity = mock(LivingEntity.class);
    var w = mock(World.class);
    when(entity.getWorld()).thenReturn(w);
    when(w.getName()).thenReturn(world);
    return new TheMobDeathEvent(
        entity, new MobSnapshot(UUID.randomUUID(), id, id, boss, minion, phase, reason), null);
  }

  @Test
  void combinesMobGroupWorldPhaseAndSpawnReason() throws Exception {
    var profiles =
        parse(
            """
            objectives:
              arena:
                mobs: [PIG_LVL_1, zombie_boss_template]
                kind: boss
                worlds: [Arena]
                phases: [RAGE]
                spawn-reasons: [API, NPC]
            """);
    assertTrue(profiles.problems().isEmpty());
    var rule = profiles.rules().getFirst();
    assertTrue(
        rule.matches(event("zombie_boss_template", true, false, "arena", "rage", SpawnReason.API)));
    assertFalse(
        rule.matches(event("zombie_boss_template", true, false, "world", "rage", SpawnReason.API)));
    assertFalse(
        rule.matches(event("zombie_boss_template", true, false, "arena", "base", SpawnReason.API)));
    assertFalse(
        rule.matches(
            event("zombie_boss_template", true, false, "arena", "rage", SpawnReason.MANUAL)));
    assertFalse(rule.matches(event("other", true, false, "arena", "rage", SpawnReason.API)));
    assertFalse(rule.matches(event("pig_lvl_1", false, false, "arena", "rage", SpawnReason.API)));
  }

  @Test
  void distinguishesRegularMobsBossesAndMinions() throws Exception {
    var profiles =
        parse(
            """
            objectives:
              all: {kind: any}
              normal: {kind: mob}
              boss: {kind: boss}
              minions: {kind: minion}
            """);
    var mob = event("pig_lvl_1", false, false, "world", null, SpawnReason.API);
    var boss = event("zombie_boss_template", true, false, "world", "base", SpawnReason.API);
    var minion = event("zombie_boss_template", false, true, "world", null, SpawnReason.MINION);
    assertEquals(
        List.of("all", "normal"),
        profiles.rules().stream()
            .filter(r -> r.matches(mob))
            .map(BattlePassObjectives.Rule::id)
            .toList());
    assertEquals(
        List.of("all", "boss"),
        profiles.rules().stream()
            .filter(r -> r.matches(boss))
            .map(BattlePassObjectives.Rule::id)
            .toList());
    assertEquals(
        List.of("all", "minions"),
        profiles.rules().stream()
            .filter(r -> r.matches(minion))
            .map(BattlePassObjectives.Rule::id)
            .toList());
  }

  @Test
  void rejectsBrokenFiltersWithoutBroadeningToEveryMob() throws Exception {
    var profiles =
        parse(
            """
            objectives:
              typo: {mobs: [pig_lvl_100]}
              scalar: {worlds: world}
              badreason: {spawn-reasons: [NATURAL]}
              empty: {mobs: []}
              badfield: {world: arena}
              numeric: {phases: [2]}
              off: {enabled: false, mobs: [missing]}
              valid: {mobs: [pig_lvl_1]}
            """);
    assertEquals(6, profiles.problems().size());
    assertEquals(
        List.of("valid"), profiles.rules().stream().map(BattlePassObjectives.Rule::id).toList());
  }

  @Test
  void missingSectionFailsClosedAndCollectionsAreImmutable() throws Exception {
    assertFalse(parse("objectives: []").problems().isEmpty());
    var profiles = parse("objectives: {test: {mobs: ['*']}}");
    assertThrows(UnsupportedOperationException.class, () -> profiles.rules().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> profiles.rules().getFirst().mobs().clear());
    assertThrows(UnsupportedOperationException.class, () -> profiles.problems().add("test"));
  }
}
