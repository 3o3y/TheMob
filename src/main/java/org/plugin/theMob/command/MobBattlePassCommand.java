package org.plugin.theMob.command;

import org.bukkit.command.CommandSender;
import org.plugin.theMob.TheMob;

/** Configuration help never mutates BattlePass seasons, quests or player progress. */
final class MobBattlePassCommand {
  static void show(TheMob plugin, CommandSender sender) {
    if (!sender.hasPermission("themob.admin")) {
      sender.sendMessage("§cNo permission.");
      return;
    }
    plugin.integrations().statuses().stream()
        .filter(s -> s.plugin().equals("BattlePass"))
        .forEach(s -> sender.sendMessage("§6BattlePass §f" + s.state() + " §7" + s.detail()));
    sender.sendMessage("§eTypen: themob_kill, themob_boss_kill, themob_minion_kill");
    sender.sendMessage("§eKampf: themob_phase_enter (Phase), themob_damage (Mob-ID)");
    sender.sendMessage("§eBeute: themob_drop, themob_loot (Material oder none)");
    sender.sendMessage(
        "§7Filter: mob, phase, level/min-level/max-level; Beute auch item, rarity,"
            + " stars/min-stars/max-stars, own-kill");
    sender.sendMessage("§7variable: <Mob-ID> oder none fuer alle. Mob-IDs:");
    plugin.api().definitions().stream()
        .sorted(java.util.Comparator.comparing(d -> d.id()))
        .limit(10)
        .forEach(d -> sender.sendMessage("§f- " + d.id() + (d.boss() ? " §6(Boss)" : "")));
    sender.sendMessage(
        "§7Alle " + plugin.mobs().registeredIds().size() + " Mob-IDs: /mob mobs [Seite]");
    sender.sendMessage("§eGruppen: type: themob_objective, variable: <Profil-ID>");
    sender.sendMessage(
        "§eDungeons: type: themob_dungeon_complete, variable: <Dungeon-ID> oder none");
    sender.sendMessage("§7Endboss festlegen: /mob dungeon boss <ID> <Boss-Mob-ID>");
    var profiles = plugin.integrations().battlePassObjectives();
    for (var rule : profiles.rules())
      sender.sendMessage("§f- " + rule.id() + " §7(" + rule.kind() + ")");
    for (String problem : profiles.problems()) sender.sendMessage("§cProfilfehler: " + problem);
    sender.sendMessage("§7Profile: plugins/TheMob/battlepass.yml; danach /mob reload");
    sender.sendMessage(
        "§7Beispiele: plugins/TheMob/battlepass[-combat]-examples.yml; Anleitung: BATTLEPASS.md");
    sender.sendMessage(
        "§7Quest-Eintraege in eine aktive BattlePass-Woche uebernehmen; danach /bpa reload.");
  }
}
