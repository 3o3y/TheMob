package org.plugin.theMob.core;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.plugin.theMob.TheMob;

/** Immutable startup policy: no mid-fight phase reset or surprise resummoning. */
public final class TheMobSkills {
  private static final Map<String, String> PATHS;

  static {
    var paths = new LinkedHashMap<String, String>();
    paths.put("all", "enabled");
    paths.put("bosses", "bosses.enabled");
    paths.put("items", "items.enabled");
    for (String id :
        List.of(
            "zombie_king",
            "zombie-cleave",
            "zombie-lunge",
            "zombie-stomp",
            "zombie-retreat",
            "bruiser",
            "ranged",
            "guardian",
            "berserker",
            "aggressive",
            "passive",
            "flee",
            "phase-abilities",
            "phase-buffs",
            "phase-combat",
            "phase-effects",
            "world-effects",
            "summon-minions")) paths.put("bosses." + id, "bosses." + id);
    for (String id :
        List.of("critical", "lifesteal", "poison", "ignite", "slow", "execute", "knockback"))
      paths.put("items." + id, "items." + id);
    PATHS = Collections.unmodifiableMap(paths);
  }

  private final Map<String, Boolean> flags;

  public TheMobSkills(TheMob plugin) {
    var file = plugin.getDataFolder().toPath().resolve("skills.yml");
    if (!java.nio.file.Files.exists(file)) plugin.saveResource("skills.yml", false);
    Map<String, Boolean> values;
    try {
      values = parse(AtomicYaml.read(file));
    } catch (Exception invalid) {
      plugin
          .getLogger()
          .severe(
              "Skills deaktiviert: skills.yml ist ungueltig; Datei bleibt erhalten. "
                  + invalid.getMessage());
      values = Map.of("all", false);
    }
    flags = Map.copyOf(values);
  }

  private static Map<String, Boolean> parse(YamlConfiguration config) {
    var values = new LinkedHashMap<String, Boolean>();
    for (String section : List.of("bosses", "items"))
      if (config.contains(section) && !config.isConfigurationSection(section))
        throw new IllegalArgumentException(section + " muss ein Abschnitt sein");
    for (var entry : PATHS.entrySet()) {
      if (config.contains(entry.getValue()) && !config.isBoolean(entry.getValue()))
        throw new IllegalArgumentException(entry.getValue() + " muss true/false sein");
      values.put(entry.getKey(), config.getBoolean(entry.getValue(), true));
    }
    return values;
  }

  public boolean enabled(String id) {
    if (!PATHS.containsKey(id) || !flags.getOrDefault("all", false)) return false;
    String group = id.startsWith("bosses.") ? "bosses" : id.startsWith("items.") ? "items" : id;
    return flags.getOrDefault(group, true) && flags.getOrDefault(id, true);
  }

  public static List<String> targets() {
    return List.copyOf(PATHS.keySet());
  }

  /** Filters only TheMob proc mechanics; never removes base attributes or Skilltree modifiers. */
  public Map<String, Double> itemEffects(Map<String, Double> stats) {
    if (stats == null) return Map.of();
    var filtered = new HashMap<>(stats);
    for (String effect :
        List.of("critical", "lifesteal", "poison", "ignite", "slow", "execute", "knockback")) {
      if (enabled("items." + effect)) continue;
      if (effect.equals("critical")) {
        filtered.remove("crit");
        filtered.remove("crit_chance");
      } else filtered.keySet().removeIf(key -> key.equals(effect) || key.startsWith(effect + "_"));
    }
    return Map.copyOf(filtered);
  }

  public static boolean mayManage(CommandSender sender) {
    return sender.hasPermission("themob.admin") || sender.hasPermission("themob.skills.manage");
  }

  public static void command(TheMob plugin, CommandSender sender, String[] args) {
    if (!mayManage(sender)) {
      sender.sendMessage("§cDafuer fehlt dir themob.skills.manage.");
      return;
    }
    try {
      var file = plugin.getDataFolder().toPath().resolve("skills.yml");
      var config = AtomicYaml.read(file);
      var requested = parse(config);
      if (args.length <= 1 || args.length == 2 && args[1].equalsIgnoreCase("status")) {
        sender.sendMessage("§6TheMob-Skills – laufend / fuer Neustart gespeichert");
        for (String id : targets())
          sender.sendMessage(
              "§7"
                  + id
                  + ": "
                  + (plugin.skills().enabled(id) ? "§aan" : "§caus")
                  + " §7/ "
                  + (requested.get(id) ? "an" : "aus"));
        var skilltree = Bukkit.getPluginManager().getPlugin("SkilltreePlugin");
        sender.sendMessage(
            "§7SkilltreePlugin: "
                + (skilltree != null && skilltree.isEnabled()
                    ? skilltree.getDescription().getVersion() + " – /skills"
                    : "nicht aktiv"));
        sender.sendMessage(
            "§e/mob skills <Schalter> <an|aus> §7– wirksam nach vollstaendigem Neustart");
        return;
      }
      String id = args[1].toLowerCase(Locale.ROOT);
      if (args.length != 3
          || !PATHS.containsKey(id)
          || !Set.of("an", "aus", "enable", "disable").contains(args[2].toLowerCase(Locale.ROOT))) {
        sender.sendMessage("§e/mob skills <Schalter> <an|aus>; Schalter: /mob skills status");
        return;
      }
      boolean on = args[2].equalsIgnoreCase("an") || args[2].equalsIgnoreCase("enable");
      config.set(PATHS.get(id), on);
      AtomicYaml.replace(file, config, "skills.yml.bak");
      sender.sendMessage(
          "§a"
              + id
              + " "
              + (on ? "aktiviert" : "deaktiviert")
              + " gespeichert. §eWirksam nach vollstaendigem Serverneustart.");
      sender.sendMessage(
          "§7Vorheriger Stand: skills.yml.bak. Skilltree-Punkte und Bossphasen bleiben erhalten.");
    } catch (Exception failure) {
      sender.sendMessage(
          "§cSkills konnten nicht sicher gelesen/gespeichert werden. Keine Aenderung; Konsole"
              + " pruefen.");
      plugin.getLogger().warning("[Skills] " + failure.getMessage());
    }
  }
}
