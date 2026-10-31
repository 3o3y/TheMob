package org.plugin.theMob.admin;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.plugin.theMob.core.AtomicYaml;

/** Typed tree editor for own configuration files; runtime databases are intentionally excluded. */
final class AdminConfigMenus {
  private final AdminMenus ui;
  private final Path data;

  AdminConfigMenus(AdminMenus ui) {
    this.ui = ui;
    data = ui.plugin.getDataFolder().toPath();
  }

  private boolean access(Player p) {
    if (ui.allowed(p, "themob.admin.edit")) return true;
    ui.denied(p);
    return false;
  }

  void files(Player p, int number) {
    if (!access(p)) return;
    var entries = new ArrayList<AdminMenus.Entry>();
    try (var walk = Files.walk(data, 9)) {
      for (Path file :
          walk.filter(Files::isRegularFile)
              .filter(f -> f.getFileName().toString().endsWith(".yml"))
              .sorted()
              .toList()) {
        Path relative = data.relativize(file);
        String name = relative.toString().replace('\\', '/');
        if (name.contains("/.") || name.startsWith(".") || Files.isSymbolicLink(file)) continue;
        boolean eligible = name.startsWith("mobs/");
        if (relative.getNameCount() == 1)
          try (var resource = ui.plugin.getResource(name)) {
            eligible =
                resource != null
                    && !name.equals("plugin.yml")
                    && !name.contains("examples")
                    && !name.equals("battlepass-dungeons.yml");
          }
        if (!eligible) continue;
        entries.add(
            new AdminMenus.Entry(
                Material.WRITABLE_BOOK,
                name,
                List.of(
                    "Vollstaendig bearbeiten: Werte, Abschnitte und Listen",
                    "Speicherung mit Versionspruefung und .admin.bak"),
                v -> open(v, name)));
      }
    } catch (Exception failure) {
      ui.error(p, failure);
    }
    ui.page(p, "TheMob-Einstellungen", entries, number, () -> ui.open(p), n -> files(p, n));
  }

  void open(Player p, String relative) {
    if (!access(p)) return;
    Runnable load =
        () -> {
          try {
            var document = new AdminDocument(data, relative);
            if (!relative.contains("/") && !relative.contains("\\"))
              try (var stream = ui.plugin.getResource(relative)) {
                if (stream != null) {
                  var defaults = new YamlConfiguration();
                  defaults.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
                  document.defaults(defaults);
                }
              }
            ui.session(p).document = document;
            tree(p, List.of(), 0);
          } catch (Exception failure) {
            ui.error(p, failure);
          }
        };
    var previous = ui.session(p).document;
    if (previous != null && previous.dirty())
      ui.confirm(
          p,
          "Entwurf wechseln",
          List.of(
              "Ungespeicherte Aenderungen in " + previous.name() + " verwerfen?",
              "Gespeicherte Dateien werden dadurch nicht geaendert"),
          "themob.admin.edit",
          load,
          () -> tree(p, List.of(), 0));
    else load.run();
  }

  void tree(Player p, List<Object> path, int requested) {
    if (!access(p)) return;
    AdminDocument doc = ui.session(p).document;
    if (doc == null) {
      files(p, 0);
      return;
    }
    Object node = doc.get(path);
    if (!(node instanceof Map<?, ?>) && !(node instanceof List<?>)) {
      scalar(p, path);
      return;
    }
    var entries = new ArrayList<AdminMenus.Entry>();
    entries.add(
        new AdminMenus.Entry(
            Material.EMERALD,
            "Speichern",
            List.of(
                doc.name(),
                doc.dirty() ? "Entwurf enthaelt Aenderungen" : "Keine Aenderung",
                "Erst speichern, danach bewusst anwenden/neustarten"),
            v -> save(v, path, requested)));
    entries.add(
        new AdminMenus.Entry(
            Material.WRITABLE_BOOK,
            "Eintrag hinzufuegen",
            List.of(
                node instanceof List<?>
                    ? "Neues Listenelement"
                    : "Neuer Schluessel mit frei waehlbarem Typ"),
            v -> add(v, path)));
    if (!path.isEmpty())
      entries.add(
          new AdminMenus.Entry(
              Material.RED_DYE,
              "Diesen Abschnitt entfernen",
              List.of("Loeschen erfordert Bestaetigung"),
              v -> remove(v, path)));
    if (node instanceof Map<?, ?> map)
      for (var entry : map.entrySet()) {
        var child = child(path, entry.getKey().toString());
        Object value = entry.getValue();
        entries.add(
            new AdminMenus.Entry(
                icon(value),
                entry.getKey().toString(),
                List.of(preview(value), "Klicken zum Oeffnen/Bearbeiten"),
                v -> tree(v, child, 0)));
      }
    else if (node instanceof List<?> list)
      for (int i = 0; i < list.size(); i++) {
        var child = child(path, i);
        Object value = list.get(i);
        entries.add(
            new AdminMenus.Entry(
                icon(value),
                "Eintrag " + (i + 1),
                List.of(preview(value), "Klicken zum Oeffnen/Bearbeiten"),
                v -> tree(v, child, 0)));
      }
    ui.page(
        p,
        doc.name() + " | " + (path.isEmpty() ? "Wurzel" : path.get(path.size() - 1)),
        entries,
        requested,
        () -> {
          if (path.isEmpty()) files(p, 0);
          else tree(p, parent(path), 0);
        },
        n -> tree(p, path, n));
  }

  private static Material icon(Object value) {
    return value instanceof Map<?, ?>
        ? Material.CHEST
        : value instanceof List<?>
            ? Material.BOOKSHELF
            : value instanceof Boolean b
                ? b ? Material.LIME_DYE : Material.GRAY_DYE
                : value instanceof Number ? Material.COMPARATOR : Material.PAPER;
  }

  private static String preview(Object value) {
    if (value instanceof Map<?, ?> map) return "Abschnitt: " + map.size() + " Eintraege";
    if (value instanceof List<?> list) return "Liste: " + list.size() + " Eintraege";
    return String.valueOf(value);
  }

  static List<Object> child(List<Object> path, Object key) {
    var result = new ArrayList<>(path);
    result.add(key);
    return List.copyOf(result);
  }

  static List<Object> parent(List<Object> path) {
    return List.copyOf(path.subList(0, path.size() - 1));
  }

  void scalar(Player p, List<Object> path) {
    if (!access(p)) return;
    var doc = ui.session(p).document;
    Object before = doc.get(path);
    var h = ui.screen(p, "Wert | " + path.get(path.size() - 1), () -> tree(p, parent(path), 0));
    ui.button(
        h,
        13,
        icon(before),
        path.get(path.size() - 1).toString(),
        List.of(
            "Aktuell: " + preview(before),
            "Typ: " + (before == null ? "Text" : before.getClass().getSimpleName()),
            "Datei: " + doc.name()),
        null);
    ui.button(
        h,
        29,
        Material.LIME_DYE,
        before instanceof Boolean ? "Umschalten" : "Wert aendern",
        List.of("Aendert nur den Entwurf"),
        v -> {
          if (!access(v)) return;
          if (before instanceof Boolean flag) {
            doc.set(path, !flag);
            scalar(v, path);
          } else
            ui.prompt(
                v,
                "Neuer Wert fuer " + path.get(path.size() - 1) + " (Typ bleibt erhalten)",
                text -> {
                  if (!access(v)) return;
                  doc.set(path, AdminDocument.scalar(before, text));
                  scalar(v, path);
                },
                () -> scalar(v, path));
        });
    ui.button(
        h,
        31,
        Material.EMERALD,
        "Speichern",
        List.of("Datei pruefen und sichern"),
        v -> save(v, path, 0));
    ui.button(
        h,
        33,
        Material.RED_DYE,
        "Eintrag entfernen",
        List.of("Bestaetigung erforderlich"),
        v -> remove(v, path));
    ui.show(p, h);
  }

  private void remove(Player p, List<Object> path) {
    var doc = ui.session(p).document;
    ui.confirm(
        p,
        "Eintrag loeschen",
        List.of(doc.name(), path.toString(), "Aendert nur den Entwurf; danach speichern"),
        "themob.admin.edit",
        () -> {
          doc.delete(path);
          tree(p, parent(path), 0);
        },
        () -> tree(p, path, 0));
  }

  private void add(Player p, List<Object> path) {
    if (!access(p)) return;
    var doc = ui.session(p).document;
    if (doc.get(path) instanceof List<?>) types(p, path, null);
    else
      ui.prompt(
          p,
          "Neuer Schluessel (ohne Punkt); z.B. neue_phase",
          key -> types(p, path, key),
          () -> tree(p, path, 0));
  }

  private void types(Player p, List<Object> path, String key) {
    var h = ui.screen(p, "Neuer Eintrag | Typ", () -> tree(p, path, 0));
    for (var entry :
        Map.of(
                10,
                "Text",
                12,
                "Zahl",
                14,
                "Schalter",
                16,
                "Abschnitt",
                28,
                "Liste",
                30,
                "Dezimalzahl")
            .entrySet())
      ui.button(
          h,
          entry.getKey(),
          Material.PAPER,
          entry.getValue(),
          List.of("Werttyp festlegen"),
          v -> {
            if (!access(v)) return;
            var doc = ui.session(v).document;
            Object value =
                switch (entry.getValue()) {
                  case "Zahl" -> 0;
                  case "Dezimalzahl" -> 0.0;
                  case "Schalter" -> false;
                  case "Abschnitt" -> new LinkedHashMap<String, Object>();
                  case "Liste" -> new ArrayList<>();
                  default -> "";
                };
            doc.add(path, key, value);
            tree(v, path, 0);
          });
    ui.show(p, h);
  }

  private void save(Player p, List<Object> path, int page) {
    if (!access(p)) return;
    var doc = ui.session(p).document;
    ui.confirm(
        p,
        "Konfiguration speichern",
        List.of(
            doc.name(),
            "Validiert den kompletten Dateibaum",
            "Vorherige Datei: " + doc.name() + ".admin.bak",
            "Noch keine laufenden Mobs oder Spielerwerte aendern",
            "Integrationen/Faehigkeiten: vollstaendiger Neustart"),
        "themob.admin.edit",
        () -> {
          try {
            doc.save();
            p.sendMessage(
                "§aGespeichert und gesichert. Einstellungen nach Neustart aktiv; Mobdaten optional"
                    + " ueber bestaetigten Hard-Reload laden.");
            tree(p, path, page);
          } catch (Exception failure) {
            ui.error(p, failure);
            tree(p, path, page);
          }
        },
        () -> tree(p, path, page));
  }

  void mob(Player p, String id) {
    if (!access(p)) return;
    try {
      Path folder = data.resolve("mobs");
      try (var files = Files.walk(folder, 9)) {
        for (Path file :
            files
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".yml"))
                .toList()) {
          if (Files.isSymbolicLink(file) || file.toString().contains(".installed")) continue;
          var yaml = AtomicYaml.read(file);
          String fallback =
              folder
                  .relativize(file)
                  .toString()
                  .replace('\\', '_')
                  .replace('/', '_')
                  .replaceFirst("\\.yml$", "");
          if (id.equalsIgnoreCase(yaml.getString("mob-id", fallback))) {
            open(p, data.relativize(file).toString());
            return;
          }
        }
      }
      var source = ui.plugin.mobs().mobConfigById(id);
      if (source == null) throw new IllegalArgumentException("Unbekannter Mob");
      ui.confirm(
          p,
          "Katalog-Override anlegen",
          List.of(
              id,
              "Erzeugt eine bearbeitbare Mobdatei mit derselben ID",
              "Der generierte Originalkatalog bleibt erhalten",
              "Erst nach Neustart/Hard-Reload wirksam"),
          "themob.admin.edit",
          () -> {
            try {
              create(p, id, source.saveToString(), true);
            } catch (Exception failure) {
              ui.error(p, failure);
            }
          },
          () -> ui.mob(p, id));
    } catch (Exception failure) {
      ui.error(p, failure);
    }
  }

  void copyMob(Player p, String from) {
    if (!access(p)) return;
    ui.prompt(
        p,
        "Neue eindeutige Mob-ID (a-z, 0-9, _ und -)",
        id -> {
          if (!access(p)) return;
          var original = ui.plugin.mobs().mobConfigById(from);
          if (original == null) throw new IllegalArgumentException("Vorlage fehlt");
          var yaml = new YamlConfiguration();
          yaml.loadFromString(original.saveToString());
          yaml.set("mob-id", id);
          create(p, id, yaml.saveToString(), false);
        },
        () -> ui.mob(p, from));
  }

  void newMob(Player p) {
    if (!access(p)) return;
    ui.prompt(
        p,
        "Neue eindeutige Mob-ID (a-z, 0-9, _ und -)",
        id -> {
          if (!access(p)) return;
          var yaml = new YamlConfiguration();
          yaml.set("mob-id", id);
          yaml.set("base-type", "ZOMBIE");
          yaml.set("name", "&a" + id);
          yaml.set("stats.health.max", 20);
          yaml.set("stats.health.current", 20);
          yaml.set("xp", 0);
          yaml.set("drops", List.of());
          create(p, id, yaml.saveToString(), false);
        },
        () -> ui.mobs(p, "", 0, null));
  }

  private void create(Player p, String id, String source, boolean override) throws Exception {
    if (!access(p)) return;
    if (!id.matches("[a-z0-9_-]{1,80}")) throw new IllegalArgumentException("Ungueltige Mob-ID");
    if (!override && ui.plugin.mobs().mobExists(id))
      throw new IllegalArgumentException("Diese Mob-ID ist bereits geladen");
    try (var all = Files.walk(data.resolve("mobs"), 9)) {
      for (Path file :
          all.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".yml")).toList())
        if (!Files.isSymbolicLink(file)
            && id.equalsIgnoreCase(AtomicYaml.read(file).getString("mob-id", "")))
          throw new IllegalArgumentException("Diese Mob-ID ist bereits gespeichert");
    }
    Path folder = data.resolve("mobs/admin");
    if (Files.isSymbolicLink(data.resolve("mobs")) || Files.isSymbolicLink(folder))
      throw new IllegalArgumentException("Symbolischer Mobordner wird abgelehnt");
    Files.createDirectories(folder);
    Path target = folder.resolve(id + ".yml");
    // CREATE_NEW also rejects concurrent creations and never overwrites existing definitions.
    Files.writeString(target, source, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    open(p, data.relativize(target).toString());
    p.sendMessage("§aMobdatei angelegt. Erst nach Neustart/Hard-Reload geladen.");
  }
}
