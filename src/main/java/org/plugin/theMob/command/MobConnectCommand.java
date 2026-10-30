package org.plugin.theMob.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.IntegrationStatus;
import org.plugin.theMob.api.TheMobApi;
import org.plugin.theMob.api.dungeon.TheMobDungeonApi;

/** Read-only connection check and atomic, restart-staged configuration changes. */
public final class MobConnectCommand {
  private record Target(String id, String plugin, String hint) {}

  private static final List<Target> TARGETS =
      org.plugin.theMob.integration.IntegrationCatalog.targets().stream()
          .map(t -> new Target(t.id(), t.name(), t.feature()))
          .toList();

  private MobConnectCommand() {}

  public static List<String> targets() {
    return TARGETS.stream().map(Target::id).toList();
  }

  public static boolean canView(CommandSender sender) {
    return sender.hasPermission("themob.admin") || sender.hasPermission("themob.integrations.view");
  }

  public static boolean canManage(CommandSender sender) {
    return sender.hasPermission("themob.admin")
        || sender.hasPermission("themob.integrations.manage");
  }

  public static void execute(TheMob plugin, CommandSender sender, String[] args) {
    if (!canView(sender) && !canManage(sender)) {
      sender.sendMessage("§cDafuer fehlt dir themob.integrations.view.");
      return;
    }
    if (args.length <= 1
        || args.length == 2
            && Set.of("check", "status", "help").contains(args[1].toLowerCase(Locale.ROOT))) {
      show(plugin, sender, null);
      return;
    }
    Target target =
        TARGETS.stream()
            .filter(
                t ->
                    t.id()
                        .equalsIgnoreCase(
                            org.plugin
                                .theMob
                                .integration
                                .IntegrationCatalog
                                .find(args[1])
                                .map(org.plugin.theMob.integration.IntegrationCatalog.Target::id)
                                .orElse(args[1])))
            .findFirst()
            .orElse(null);
    if (target == null) {
      sender.sendMessage("§eAnbieter: " + String.join(", ", targets()));
      return;
    }
    if (args.length == 2
        || args.length == 3
            && Set.of("check", "status").contains(args[2].toLowerCase(Locale.ROOT))) {
      show(plugin, sender, target);
      return;
    }
    if (!canManage(sender)) {
      sender.sendMessage("§cAenderungen erfordern themob.integrations.manage.");
      return;
    }
    if (args.length != 3
        || !Set.of("enable", "disable", "an", "aus").contains(args[2].toLowerCase(Locale.ROOT))) {
      sender.sendMessage("§e/mob connect " + target.id() + " <enable|disable>");
      return;
    }
    boolean enabled = args[2].equalsIgnoreCase("enable") || args[2].equalsIgnoreCase("an");
    try {
      save(plugin.getDataFolder().toPath().resolve("config.yml"), target, enabled);
      plugin.getConfig().set("integrations." + target.id() + ".enabled", enabled);
      sender.sendMessage(
          "§a"
              + target.plugin()
              + ": "
              + (enabled ? "Aktivierung" : "Deaktivierung")
              + " gespeichert.");
      sender.sendMessage(
          "§eWirksam nach vollstaendigem Serverneustart. Die laufende Verbindung wurde nicht"
              + " umgeschaltet.");
      sender.sendMessage(
          "§7Vorherige Konfiguration: config.yml.connect.bak. Mob- und Questdaten wurden nicht"
              + " veraendert.");
    } catch (Exception failure) {
      plugin
          .getLogger()
          .warning("[Connect] Konfiguration nicht geaendert: " + failure.getMessage());
      sender.sendMessage(
          "§cKonfiguration konnte nicht sicher gespeichert werden. Keine Einstellung geaendert;"
              + " Serverkonsole pruefen.");
    }
  }

  private static void save(Path file, Target target, boolean enabled) throws Exception {
    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file))
      throw new IOException("config.yml ist keine regulaere Datei");
    var config = new YamlConfiguration();
    org.plugin.theMob.compat.YamlComments.enable(config);
    config.load(file.toFile()); // Strict parsing: malformed files are never replaced by defaults.
    for (String parent : List.of("integrations", "integrations." + target.id()))
      if (config.isSet(parent) && !config.isConfigurationSection(parent))
        throw new IOException(parent + " ist kein Konfigurationsabschnitt");
    config.set("integrations." + target.id() + ".enabled", enabled);
    Path temporary = Files.createTempFile(file.getParent(), "connect-", ".tmp");
    try {
      Files.writeString(temporary, config.saveToString(), StandardCharsets.UTF_8);
      Path backup = file.resolveSibling("config.yml.connect.bak");
      if (Files.isSymbolicLink(backup)) throw new IOException("Backup ist ein symbolischer Link");
      Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unavailable) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void show(TheMob plugin, CommandSender sender, Target only) {
    TheMobApi api = Bukkit.getServicesManager().load(TheMobApi.class);
    TheMobDungeonApi dungeons = Bukkit.getServicesManager().load(TheMobDungeonApi.class);
    sender.sendMessage("§6TheMob " + plugin.getDescription().getVersion() + " – Verbindungen");
    sender.sendMessage(
        "§7Mob-API: "
            + (api == null ? "§cfehlt" : "§abereit (API " + api.apiVersion() + ")")
            + " §7| Dungeon-API: "
            + (dungeons == null ? "§cfehlt" : "§abereit (API " + dungeons.apiVersion() + ")"));
    Map<String, IntegrationStatus> statuses = new HashMap<>();
    if (plugin.integrations() != null)
      plugin.integrations().statuses().forEach(s -> statuses.put(s.plugin(), s));
    for (Target target : TARGETS) {
      if (only != null && only != target) continue;
      IntegrationStatus status = statuses.get(target.plugin());
      boolean configured =
          plugin.getConfig().getBoolean("integrations." + target.id() + ".enabled", true);
      boolean runningEnabled = status != null && status.state() != IntegrationStatus.State.DISABLED;
      String label =
          status == null
              ? "§eNoch nicht initialisiert"
              : switch (status.state()) {
                case ACTIVE -> "§aAnbindung bereit";
                case ABSENT -> "§7Nicht installiert/aktiv";
                case DISABLED -> "§eDeaktiviert";
                case INCOMPATIBLE -> "§cNicht kompatibel – Version/Serverlog pruefen";
              };
      sender.sendMessage(
          "§6"
              + target.plugin()
              + " §8["
              + target.id()
              + "] §7"
              + label
              + (status != null && configured != runningEnabled ? " §e(Neustart ausstehend)" : ""));
      if (status != null && status.state() == IntegrationStatus.State.INCOMPATIBLE)
        sender.sendMessage("§7" + clean(status.detail()));
      sender.sendMessage("§7" + target.hint());
    }
    sender.sendMessage(
        "§e/mob connect <Anbieter> <enable|disable> §7– fuer den naechsten Neustart speichern");
    sender.sendMessage(
        "§7Andere Plugins benoetigen ein TheMob-kompatibles Add-on. Ein API-JAR ist nur eine"
            + " Entwicklerbibliothek.");
  }

  private static String clean(String text) {
    if (text == null) return "";
    String value = org.bukkit.ChatColor.stripColor(text).replaceAll("[\\p{Cntrl}]", " ");
    return value.length() <= 220 ? value : value.substring(0, 220) + "...";
  }
}
