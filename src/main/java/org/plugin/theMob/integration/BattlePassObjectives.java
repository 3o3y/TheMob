package org.plugin.theMob.integration;

import java.io.File;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.SpawnReason;
import org.plugin.theMob.api.event.TheMobDeathEvent;

/** Immutable task filters. Invalid filters fail closed instead of silently counting every kill. */
public record BattlePassObjectives(List<Rule> rules, List<String> problems) {
  public BattlePassObjectives {
    rules = List.copyOf(rules);
    problems = List.copyOf(problems);
  }

  static BattlePassObjectives load(TheMob plugin) {
    var file = new File(plugin.getDataFolder(), "battlepass.yml");
    try {
      if (!file.exists()) plugin.saveResource("battlepass.yml", false);
      if (!new File(plugin.getDataFolder(), "battlepass-examples.yml").exists())
        plugin.saveResource("battlepass-examples.yml", false);
      if (!new File(plugin.getDataFolder(), "battlepass-combat-examples.yml").exists())
        plugin.saveResource("battlepass-combat-examples.yml", false);
      var yaml = new YamlConfiguration();
      yaml.load(file);
      return parse(yaml, plugin.mobs().registeredIds());
    } catch (Exception failure) {
      return new BattlePassObjectives(
          List.of(), List.of("battlepass.yml: " + failure.getMessage()));
    }
  }

  static BattlePassObjectives parse(ConfigurationSection config, Collection<String> mobIds) {
    var rules = new ArrayList<Rule>();
    var problems = new ArrayList<String>();
    var objectives = config.getConfigurationSection("objectives");
    if (objectives == null) {
      problems.add("battlepass.yml: Abschnitt objectives fehlt oder ist keine Map");
      return new BattlePassObjectives(rules, problems);
    }
    Set<String> known = new HashSet<>();
    mobIds.forEach(id -> known.add(id.toLowerCase(Locale.ROOT)));
    for (String id : objectives.getKeys(false)) {
      try {
        if (!id.matches("[a-z0-9_-]{1,64}"))
          throw new IllegalArgumentException("ID muss a-z, 0-9, _ oder - enthalten (max. 64)");
        var entry = objectives.getConfigurationSection(id);
        if (entry == null) throw new IllegalArgumentException("Aufgabe muss eine Map sein");
        if (entry.contains("enabled") && !entry.isBoolean("enabled"))
          throw new IllegalArgumentException("enabled muss true/false sein");
        if (!entry.getBoolean("enabled", true)) continue;
        Set<String> fields = Set.of("enabled", "mobs", "kind", "worlds", "spawn-reasons", "phases");
        for (String field : entry.getKeys(false))
          if (!fields.contains(field))
            throw new IllegalArgumentException("Unbekannter Filter: " + field);
        var mobs = strings(entry, "mobs", Set.of("*"));
        if (mobs.isEmpty())
          throw new IllegalArgumentException(
              "mobs darf nicht leer sein; ['*'] fuer alle verwenden");
        for (String mob : mobs)
          if (!mob.equals("*") && !known.contains(mob))
            throw new IllegalArgumentException("Unbekannte Mob-ID: " + mob);
        String kind = entry.getString("kind", "any").toLowerCase(Locale.ROOT);
        if (!Set.of("any", "mob", "boss", "minion").contains(kind))
          throw new IllegalArgumentException("kind: any, mob, boss oder minion erwartet");
        var reasons = new HashSet<SpawnReason>();
        for (String reason : strings(entry, "spawn-reasons", Set.of()))
          reasons.add(SpawnReason.valueOf(reason.toUpperCase(Locale.ROOT)));
        rules.add(
            new Rule(
                id,
                mobs,
                kind,
                strings(entry, "worlds", Set.of()),
                reasons,
                strings(entry, "phases", Set.of())));
      } catch (IllegalArgumentException failure) {
        problems.add(id + ": " + failure.getMessage());
      }
    }
    return new BattlePassObjectives(rules, problems);
  }

  private static Set<String> strings(
      ConfigurationSection section, String key, Set<String> fallback) {
    if (!section.contains(key)) return fallback;
    Object value = section.get(key);
    if (!(value instanceof List<?> list))
      throw new IllegalArgumentException(key + " muss eine Liste sein");
    var values = new HashSet<String>();
    for (Object item : list) {
      if (!(item instanceof String text) || text.isBlank())
        throw new IllegalArgumentException(key + " enthaelt einen leeren/ungueltigen Wert");
      values.add(text.trim().toLowerCase(Locale.ROOT));
    }
    return values;
  }

  public record Rule(
      String id,
      Set<String> mobs,
      String kind,
      Set<String> worlds,
      Set<SpawnReason> reasons,
      Set<String> phases) {
    public Rule {
      mobs = Set.copyOf(mobs);
      worlds = Set.copyOf(worlds);
      reasons = Set.copyOf(reasons);
      phases = Set.copyOf(phases);
    }

    boolean matches(TheMobDeathEvent event) {
      var mob = event.getMob();
      if (!mobs.contains("*") && !mobs.contains(mob.definitionId().toLowerCase(Locale.ROOT)))
        return false;
      if (kind.equals("boss") && !mob.boss()
          || kind.equals("minion") && !mob.minion()
          || kind.equals("mob") && (mob.boss() || mob.minion())) return false;
      return (worlds.isEmpty()
              || worlds.contains(event.getEntity().getWorld().getName().toLowerCase(Locale.ROOT)))
          && (reasons.isEmpty() || reasons.contains(mob.spawnReason()))
          && (phases.isEmpty()
              || phases.contains(
                  mob.phaseId() == null ? "unphased" : mob.phaseId().toLowerCase(Locale.ROOT)));
    }
  }
}
