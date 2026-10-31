package org.plugin.theMob.admin;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.AdminMenuExtension;

/** Resource-pack-free administration. All callbacks run on the server thread as the real player. */
public final class AdminMenus implements Listener, AutoCloseable {
  final TheMob plugin;
  private final UUID generation = UUID.randomUUID();
  private final Map<UUID, Session> sessions = new HashMap<>();
  private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
  private final org.bukkit.scheduler.BukkitTask cleanup;
  private boolean closed;

  static final class Session {
    Inventory inventory;
    AdminDocument document;
    UUID prompt;
    long touched = System.nanoTime();
  }

  @FunctionalInterface
  interface Input {
    void accept(String text) throws Exception;
  }

  private record Prompt(UUID id, long expires, Input action, Runnable cancel) {}

  record Entry(Material icon, String name, List<String> lore, Consumer<Player> action) {}

  final AdminConfigMenus configs;

  public AdminMenus(TheMob plugin) {
    this.plugin = plugin;
    configs = new AdminConfigMenus(this);
    Bukkit.getPluginManager().registerEvents(this, plugin);
    cleanup =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  long now = System.nanoTime();
                  prompts.entrySet().removeIf(e -> e.getValue().expires() < now);
                  sessions
                      .entrySet()
                      .removeIf(
                          e ->
                              Bukkit.getPlayer(e.getKey()) == null
                                  || now - e.getValue().touched > 600_000_000_000L
                                      && !viewing(Bukkit.getPlayer(e.getKey())));
                },
                200,
                200);
  }

  public static boolean mayOpen(Player p) {
    return p.hasPermission("themob.admin") || p.hasPermission("themob.admin.menu");
  }

  private static boolean viewing(Player p) {
    return p != null
        && p.getOpenInventory() != null
        && org.plugin.theMob.compat.Views.top(p.getOpenInventory()) != null
        && org.plugin.theMob.compat.Views.top(p.getOpenInventory()).getHolder()
            instanceof AdminMenuHolder;
  }

  boolean allowed(Player p, String permission) {
    return p.isOnline()
        && mayOpen(p)
        && (p.hasPermission("themob.admin") || p.hasPermission(permission));
  }

  Session session(Player p) {
    return sessions.computeIfAbsent(p.getUniqueId(), id -> new Session());
  }

  public boolean open(Player p) {
    if (closed || !mayOpen(p)) {
      p.sendMessage("§cDafuer fehlt dir themob.admin.menu.");
      return false;
    }
    prompts.remove(p.getUniqueId());
    session(p).prompt = null;
    var h = screen(p, "TheMob | Administration", null);
    button(
        h,
        10,
        Material.ZOMBIE_HEAD,
        "Mobs und Bosse",
        List.of("Spawnen, Eier, Definitionen und Bossphasen"),
        v -> mobs(v, "", 0, null));
    button(
        h,
        12,
        Material.DIAMOND_SWORD,
        "Items und Drops",
        List.of("160 Katalogitems, Beute und Fremdanbieter"),
        v -> items(v, "", 0));
    button(
        h,
        14,
        Material.SPAWNER,
        "Spawns und Wellen",
        List.of("Alle Spawnarten, Positionen und Limits"),
        this::spawns);
    button(
        h,
        16,
        Material.END_STONE_BRICKS,
        "Dungeons",
        List.of("Bauen, betreten, Endboss und Ausgangsaltar"),
        v -> dungeons(v, 0));
    button(
        h,
        28,
        Material.BLAZE_POWDER,
        "Boss- und Itemfaehigkeiten",
        List.of("Globale und einzelne Schalter", "Gespeicherte Schalter gelten nach Neustart"),
        v -> abilities(v, 0));
    button(
        h,
        30,
        Material.COMPARATOR,
        "Alle Einstellungen",
        List.of(
            "Konfigurationen inkl. Listen und Abschnitten",
            "Entwurf, Pruefung und Sicherung vor Speichern"),
        v -> configs.files(v, 0));
    button(
        h,
        32,
        Material.IRON_BARS,
        "Plugin-Anbindungen",
        List.of("Verbindungen, Einrichtung und API-Erweiterungen"),
        v -> integrations(v, 0));
    button(
        h,
        34,
        Material.REPEATER,
        "Diagnose und Wartung",
        List.of("Auslastung, Limits und bestaetigte Aktionen"),
        this::maintenance);
    button(
        h,
        40,
        Material.BOOK,
        "Befehle bleiben erhalten",
        List.of(
            "Alle bisherigen Unterbefehle sind weiter nutzbar",
            "Dieses Menue verwendet dieselben Dienste/Rechte"),
        v -> execute(v, "help"));
    if (session(p).document != null)
      button(
          h,
          4,
          Material.WRITABLE_BOOK,
          "Offener Einstellungsentwurf",
          List.of(
              session(p).document.name(),
              session(p).document.dirty() ? "Ungespeicherte Aenderungen" : "Noch keine Aenderung"),
          v -> configs.tree(v, List.of(), 0));
    show(p, h);
    return true;
  }

  AdminMenuHolder screen(Player p, String title, Runnable back) {
    if (closed || !mayOpen(p)) throw new IllegalStateException("Kein Menuezutritt");
    var holder = new AdminMenuHolder(p.getUniqueId(), generation);
    holder.bind(
        Bukkit.createInventory(holder, 54, title.length() > 64 ? title.substring(0, 64) : title));
    if (back != null) button(holder, 45, Material.ARROW, "Zurueck", List.of(), v -> back.run());
    button(holder, 49, Material.COMPASS, "Hauptmenue", List.of(), this::open);
    button(
        holder,
        53,
        Material.BARRIER,
        "Schliessen",
        List.of("Ungespeicherte Entwuerfe bleiben 10 Minuten erhalten"),
        Player::closeInventory);
    return holder;
  }

  void button(
      AdminMenuHolder h,
      int slot,
      Material icon,
      String name,
      List<String> lore,
      Consumer<Player> action) {
    var item = new ItemStack(icon == null || icon.isAir() ? Material.PAPER : icon);
    var meta = item.getItemMeta();
    meta.setDisplayName("§e" + clean(name, 140));
    meta.setLore(lore.stream().map(line -> "§7" + clean(line, 240)).toList());
    item.setItemMeta(meta);
    h.getInventory().setItem(slot, item);
    if (action != null) h.actions.put(slot, action);
  }

  private static String clean(String s, int limit) {
    if (s == null) return "";
    s = s.replaceAll("[\\p{Cntrl}]", " ");
    return s.length() > limit ? s.substring(0, limit) + "…" : s;
  }

  void show(Player p, AdminMenuHolder h) {
    if (closed || !mayOpen(p)) return;
    var s = session(p);
    s.touched = System.nanoTime();
    s.inventory = h.getInventory();
    p.openInventory(h.getInventory());
  }

  void page(
      Player p,
      String title,
      List<Entry> entries,
      int requested,
      Runnable back,
      Consumer<Integer> navigate) {
    int pages = Math.max(1, (entries.size() + 44) / 45),
        number = org.plugin.theMob.compat.Numbers.clamp(requested, 0, pages - 1);
    var h = screen(p, title + " " + (number + 1) + "/" + pages, back);
    for (int i = number * 45; i < Math.min(entries.size(), (number + 1) * 45); i++) {
      var e = entries.get(i);
      button(h, i - number * 45, e.icon, e.name, e.lore, e.action);
    }
    if (number > 0)
      button(h, 46, Material.ARROW, "Vorige Seite", List.of(), v -> navigate.accept(number - 1));
    if (number + 1 < pages)
      button(h, 52, Material.ARROW, "Naechste Seite", List.of(), v -> navigate.accept(number + 1));
    show(p, h);
  }

  void confirm(
      Player p,
      String title,
      List<String> consequences,
      String permission,
      Runnable action,
      Runnable back) {
    var h = screen(p, "Bestaetigen | " + title, back);
    button(h, 13, Material.BOOK, title, consequences, null);
    button(
        h,
        29,
        Material.LIME_CONCRETE,
        "Bestaetigen",
        List.of("Fuehrt die oben beschriebene Aktion aus"),
        v -> {
          if (!allowed(v, permission)) {
            denied(v);
            return;
          }
          action.run();
        });
    button(h, 33, Material.RED_CONCRETE, "Abbrechen", List.of(), v -> back.run());
    show(p, h);
  }

  void prompt(Player p, String question, Input action, Runnable cancel) {
    if (!mayOpen(p)) {
      denied(p);
      return;
    }
    UUID token = UUID.randomUUID();
    session(p).prompt = token;
    prompts.put(
        p.getUniqueId(), new Prompt(token, System.nanoTime() + 120_000_000_000L, action, cancel));
    p.closeInventory();
    p.sendMessage("§6TheMob: §f" + question);
    p.sendMessage("§7Antwort in den Chat; 'abbrechen' zum Zurueckgehen. Zeit: 2 Minuten.");
  }

  void denied(Player p) {
    p.sendMessage("§cDafuer fehlt dir die passende TheMob-Berechtigung.");
  }

  void error(Player p, Exception error) {
    p.sendMessage("§c" + clean(error.getMessage(), 220));
  }

  void execute(Player p, String... args) {
    if (closed || !mayOpen(p)) {
      denied(p);
      return;
    }
    Command command = plugin.getCommand("mob");
    plugin.getCommand("mob").getExecutor().onCommand(p, command, "mob", args);
  }

  void mobs(Player p, String filter, int page, Consumer<String> choose) {
    var entries = new ArrayList<Entry>();
    entries.add(
        new Entry(
            Material.OAK_SIGN,
            "Suchen",
            List.of("Nach ID oder Namen filtern"),
            v ->
                prompt(
                    v,
                    "Mob-ID oder Teil des Namens (leer = alle)",
                    text -> mobs(v, text, 0, choose),
                    () -> mobs(v, filter, page, choose))));
    if (choose == null)
      entries.add(
          new Entry(
              Material.WRITABLE_BOOK,
              "Neuen Mob anlegen",
              List.of("Vorlage erstellen und vollstaendig bearbeiten"),
              configs::newMob));
    for (var def :
        plugin.api().definitions().stream()
            .sorted(Comparator.comparing(org.plugin.theMob.api.MobDefinition::id))
            .filter(
                d ->
                    filter.isBlank()
                        || (d.id() + d.displayName())
                            .toLowerCase(Locale.ROOT)
                            .contains(filter.toLowerCase(Locale.ROOT)))
            .toList())
      entries.add(
          new Entry(
              def.boss() ? Material.WITHER_SKELETON_SKULL : Material.ZOMBIE_HEAD,
              def.id(),
              List.of(
                  def.displayName(), def.entityType().name(), def.boss() ? "Boss" : "Normaler Mob"),
              v -> {
                if (choose != null) choose.accept(def.id());
                else mob(v, def.id());
              }));
    page(p, "Mobkatalog", entries, page, () -> open(p), n -> mobs(p, filter, n, choose));
  }

  void mob(Player p, String id) {
    if (plugin.api().definitions().stream().noneMatch(d -> d.id().equals(id))) {
      p.sendMessage("§cMob ist nicht mehr verfuegbar.");
      open(p);
      return;
    }
    var h = screen(p, "Mob | " + id, () -> mobs(p, "", 0, null));
    button(
        h,
        10,
        Material.ZOMBIE_HEAD,
        "Hier spawnen",
        List.of("Normale Budgets, Cooldowns und Schutzpruefungen"),
        v -> {
          execute(v, "spawn", id);
          mob(v, id);
        });
    button(
        h,
        12,
        Material.ZOMBIE_SPAWN_EGG,
        "Spawnei erhalten",
        List.of("Ein Ei fuer diesen TheMob-Mob"),
        v -> {
          if (!allowed(v, "themob.command.egg")) {
            denied(v);
            return;
          }
          give(
              v,
              new org.plugin.theMob.spawn.egg.SpawnEggItemFactory(plugin.keys())
                  .createEgg(id, "§aTheMob " + id, List.of("§7Mob-ID: " + id)));
        });
    button(
        h,
        14,
        Material.WRITABLE_BOOK,
        "Definition bearbeiten",
        List.of(
            "Werte, Ausruestung, Drops und Bossphasen",
            "Katalogmobs erhalten eine eigene Override-Datei"),
        v -> configs.mob(v, id));
    button(
        h,
        16,
        Material.SPAWNER,
        "Spawnpunkt einrichten",
        List.of("Assistent fuer alle bisherigen Spawnarten"),
        v -> spawnType(v, id));
    button(
        h,
        28,
        Material.BOOK,
        "Mobkopie erstellen",
        List.of("Neue eindeutige ID, Original bleibt erhalten"),
        v -> configs.copyMob(v, id));
    button(
        h,
        30,
        Material.COMPARATOR,
        "Jobs / Economy / Skill-XP",
        List.of(
            "Mob-Konfiguration: integrations", "Auszahlungen bleiben standardmaessig unveraendert"),
        v -> configs.mob(v, id));
    button(
        h,
        32,
        Material.SPAWNER,
        "TheMob-Blockspawner erhalten",
        List.of("Ein Spawner; mit dem passenden Mob-Ei bestuecken"),
        v -> {
          if (v.getInventory().firstEmpty() < 0) {
            v.sendMessage("§cInventar voll.");
            return;
          }
          execute(v, "spawner", "1");
        });
    show(p, h);
  }

  void give(Player p, ItemStack item) {
    if (item == null || item.getType().isAir()) {
      p.sendMessage("§cItem nicht verfuegbar.");
      return;
    }
    if (p.getInventory().firstEmpty() < 0) {
      p.sendMessage("§cInventar voll. Es wurde nichts ausgegeben.");
      return;
    }
    var leftover = p.getInventory().addItem(item.clone());
    if (!leftover.isEmpty()) p.sendMessage("§cItem konnte nicht vollstaendig ausgegeben werden.");
  }

  void items(Player p, String filter, int number) {
    var entries = new ArrayList<Entry>();
    entries.add(
        new Entry(
            Material.OAK_SIGN,
            "Suchen",
            List.of("Nach Item-ID filtern"),
            v ->
                prompt(
                    v,
                    "Teil der Item-ID",
                    text -> items(v, text, 0),
                    () -> items(v, filter, number))));
    entries.add(
        new Entry(
            Material.CHEST,
            "Droptabellen / Fremditems",
            List.of("Mob auswaehlen und drops bearbeiten"),
            v -> mobs(v, "", 0, id -> configs.mob(v, id))));
    entries.add(
        new Entry(
            Material.ENDER_CHEST,
            "Fremditem testen",
            List.of(
                "Installierte Provider: " + String.join(", ", plugin.api().itemProviders()),
                "Nur Admin: ein natives Item erhalten"),
            v -> {
              if (!allowed(v, "themob.admin")) {
                denied(v);
                return;
              }
              prompt(
                  v,
                  "Itemreferenz, z.B. mmoitems:SWORD/ID oder advancedcrates:KISTE",
                  ref -> {
                    if (allowed(v, "themob.admin"))
                      give(v, plugin.api().createItem(ref).orElse(null));
                    items(v, filter, number);
                  },
                  () -> items(v, filter, number));
            }));
    for (String id :
        plugin.gear().ids().stream()
            .sorted()
            .filter(id -> id.contains(filter.toLowerCase(Locale.ROOT)))
            .toList()) {
      ItemStack item = plugin.gear().item(id);
      entries.add(
          new Entry(
              item.getType(),
              id,
              List.of("Klicken: ein Item erhalten", "Ausruestungswerte: equipment.yml"),
              v -> {
                if (!allowed(v, "themob.admin")) {
                  denied(v);
                  return;
                }
                give(v, plugin.gear().item(id));
              }));
    }
    page(p, "Itemkatalog", entries, number, () -> open(p), n -> items(p, filter, n));
  }

  void abilities(Player p, int number) {
    var entries = new ArrayList<Entry>();
    var saved =
        YamlConfiguration.loadConfiguration(
            new java.io.File(plugin.getDataFolder(), "abilities.yml"));
    for (String id : org.plugin.theMob.core.CombatAbilities.targets()) {
      String key =
          id.equals("all")
              ? "enabled"
              : id.equals("bosses") || id.equals("items") ? id + ".enabled" : id;
      boolean configured = saved.getBoolean(key, true);
      entries.add(
          new Entry(
              configured ? Material.LIME_DYE : Material.GRAY_DYE,
              id,
              List.of(
                  "Laufend wirksam: " + plugin.abilities().enabled(id),
                  "Gespeichert: " + configured,
                  "Klick: fuer naechsten Neustart umschalten"),
              v -> {
                execute(v, "abilities", id, configured ? "aus" : "an");
                abilities(v, number);
              }));
    }
    page(p, "Faehigkeitsschalter", entries, number, () -> open(p), n -> abilities(p, n));
  }

  void integrations(Player p, int number) {
    var entries = new ArrayList<Entry>();
    for (var status : plugin.api().integrations())
      entries.add(
          new Entry(
              status.state() == org.plugin.theMob.api.IntegrationStatus.State.ACTIVE
                  ? Material.LIME_DYE
                  : Material.GRAY_DYE,
              status.plugin(),
              List.of(
                  status.state().name(), status.detail(), "Klicken: Einrichtung und Einstellungen"),
              v -> provider(v, status.plugin())));
    for (var registration :
        Bukkit.getServicesManager().getRegistrations(AdminMenuExtension.class).stream()
            .limit(64)
            .toList()) {
      if (!registration.getPlugin().isEnabled()) continue;
      try {
        var extension = registration.getProvider();
        if (!p.hasPermission(extension.permission())) continue;
        entries.add(
            new Entry(
                extension.icon(),
                extension.title(),
                List.of("API-Erweiterung: " + registration.getPlugin().getName()),
                v -> {
                  if (!registration.getPlugin().isEnabled()
                      || !Bukkit.getServicesManager()
                          .getRegistrations(AdminMenuExtension.class)
                          .contains(registration)
                      || !v.hasPermission(extension.permission())) {
                    denied(v);
                    return;
                  }
                  v.closeInventory();
                  extension.open(v);
                }));
      } catch (Exception | LinkageError failure) {
        plugin
            .getLogger()
            .warning(
                "Admin-Erweiterung konnte nicht angezeigt werden: "
                    + registration.getPlugin().getName());
      }
    }
    page(p, "Anbindungen / API", entries, number, () -> open(p), n -> integrations(p, n));
  }

  void provider(Player p, String name) {
    var h = screen(p, "Anbindung | " + name, () -> integrations(p, 0));
    var status =
        plugin.api().integrations().stream()
            .filter(s -> s.plugin().equals(name))
            .findFirst()
            .orElse(null);
    button(
        h,
        13,
        Material.BOOK,
        name,
        status == null
            ? List.of("Nicht verfuegbar")
            : List.of(status.state().name(), status.detail()),
        null);
    button(
        h,
        29,
        Material.LIME_DYE,
        "Aktivierung vormerken",
        List.of("Gespeichert fuer vollstaendigen Serverneustart"),
        v -> {
          execute(v, "connect", name, "enable");
          provider(v, name);
        });
    button(
        h,
        33,
        Material.GRAY_DYE,
        "Deaktivierung vormerken",
        List.of("Gespeichert fuer vollstaendigen Serverneustart"),
        v -> {
          execute(v, "connect", name, "disable");
          provider(v, name);
        });
    button(
        h,
        40,
        Material.COMPARATOR,
        "TheMob-Anbindung konfigurieren",
        List.of("config.yml > integrations", "Keine fremden Plugin-Dateien werden veraendert"),
        v -> configs.open(v, "config.yml"));
    if (name.equals("BattlePass"))
      button(
          h,
          31,
          Material.WRITABLE_BOOK,
          "Aufgabenprofile bearbeiten",
          List.of("Killgruppen: battlepass.yml", "Aktive Wochen verwaltet BattlePass selbst"),
          v -> configs.open(v, "battlepass.yml"));
    show(p, h);
  }

  void spawns(Player p) {
    var h = screen(p, "Spawns und Wellen", () -> open(p));
    button(
        h,
        11,
        Material.SPAWNER,
        "Neuer Spawn",
        List.of("Mob und Spawnart waehlen", "Nutzen dieselben Dienste wie die Befehle"),
        v -> mobs(v, "", 0, id -> spawnType(v, id)));
    button(
        h,
        15,
        Material.WRITABLE_BOOK,
        "Alle Spawnpunkte bearbeiten",
        List.of(
            "Auch Welt, Radien, Nachrichten und aktiv/inaktiv",
            "Datei: auto_spawn.yml; Anwenden nach Speichern"),
        v -> configs.open(v, "auto_spawn.yml"));
    button(
        h,
        22,
        Material.BOOKSHELF,
        "Laufende Spawnpunkte",
        List.of(
            "Alle Arten und temporaere ONETIME-Punkte", "Details ansehen und bestaetigt entfernen"),
        v -> spawnList(v, 0));
    button(
        h,
        31,
        Material.COMPARATOR,
        "Spawnlimits und Hot-/Cold-Zonen",
        List.of("Datei: config.yml"),
        v -> configs.open(v, "config.yml"));
    show(p, h);
  }

  void spawnList(Player p, int number) {
    var entries = new ArrayList<Entry>();
    for (var point : plugin.spawns().points())
      entries.add(
          new Entry(
              Material.SPAWNER,
              point.mobId(),
              List.of(
                  point.type().name(),
                  point.spawnId(),
                  "Intervall: " + point.intervalSeconds() + " s | Limit: " + point.maxSpawns(),
                  "Modus: " + point.mode() + " | Aktiv: " + point.enabled(),
                  "Klicken: Details"),
              v -> {
                var h = screen(v, "Spawnpunkt | " + point.mobId(), () -> spawnList(v, number));
                button(
                    h,
                    13,
                    Material.BOOK,
                    point.mobId(),
                    List.of(
                        point.spawnId(),
                        point.type().name(),
                        "Welt: " + Objects.toString(point.worldName(), "Spieler"),
                        "Zielspieler: " + Objects.toString(point.playerName(), "-"),
                        "Radius: " + point.minRadius() + ".." + point.maxRadius(),
                        "Abstand: " + point.minDistance() + ".." + point.maxDistance()),
                    null);
                button(
                    h,
                    29,
                    Material.WRITABLE_BOOK,
                    "Datei bearbeiten",
                    List.of(
                        "Gespeicherte ENDLESS-Punkte in auto_spawn.yml",
                        "ONETIME-Punkte sind nur temporaer"),
                    viewer -> configs.open(viewer, "auto_spawn.yml"));
                button(
                    h,
                    33,
                    Material.REDSTONE_BLOCK,
                    "Spawnpunkt entfernen",
                    List.of("Wird sofort beendet und gespeichert"),
                    viewer ->
                        confirm(
                            viewer,
                            "Spawnpunkt entfernen",
                            List.of(
                                point.spawnId(),
                                "Bereits gespawnte Mobs werden dadurch nicht getoetet"),
                            "themob.spawn.delete",
                            () -> {
                              boolean removed = plugin.spawns().deleteBySpawnId(point.spawnId());
                              viewer.sendMessage(
                                  removed
                                      ? "§aSpawnpunkt entfernt."
                                      : "§eSpawnpunkt nicht mehr vorhanden.");
                              spawnList(viewer, number);
                            },
                            () -> spawnList(viewer, number)));
                show(v, h);
              }));
    page(p, "Laufende Spawnpunkte", entries, number, () -> spawns(p), n -> spawnList(p, n));
  }

  void spawnType(Player p, String id) {
    var h = screen(p, "Spawnart | " + id, () -> mob(p, id));
    for (var entry :
        Map.of(10, "autospawn", 12, "randomradius", 14, "followplayer", 16, "randomworld")
            .entrySet())
      button(
          h,
          entry.getKey(),
          Material.SPAWNER,
          entry.getValue(),
          List.of("Assistent mit geprueften Zahlen"),
          v -> {
            if (!allowed(v, "themob.spawn.set")) {
              denied(v);
              return;
            }
            var values = new ArrayList<String>();
            if (entry.getValue().equals("followplayer"))
              prompt(
                  v,
                  "Name des Zielspielers",
                  text -> {
                    values.add(text);
                    spawnQuestions(v, id, entry.getValue(), values, 0);
                  },
                  () -> spawnType(v, id));
            else spawnQuestions(v, id, entry.getValue(), values, 0);
          });
    show(p, h);
  }

  void spawnQuestions(Player p, String id, String type, List<String> values, int step) {
    String[] questions =
        type.equals("randomradius")
            ? new String[] {
              "Intervall in Sekunden (1..86400)",
              "Maximal gleichzeitig (1..1000)",
              "Minimalradius (0..512)",
              "Maximalradius (0..512)"
            }
            : type.equals("followplayer")
                ? new String[] {
                  "Intervall in Sekunden (1..86400)",
                  "Maximal gleichzeitig (1..1000)",
                  "Mindestabstand (0..512)",
                  "Maximalabstand (0..512)"
                }
                : type.equals("randomworld")
                    ? new String[] {
                      "Intervall in Sekunden (1..86400)", "Maximal gleichzeitig (1..1000)"
                    }
                    : new String[] {
                      "Intervall in Sekunden (1..86400)", "Maximal gleichzeitig (1..1000)"
                    };
    if (step < questions.length) {
      prompt(
          p,
          questions[step],
          text -> {
            int n = Integer.parseInt(text);
            int min = step < 2 ? 1 : 0, max = step == 0 ? 86400 : step == 1 ? 1000 : 512;
            if (n < min || n > max)
              throw new IllegalArgumentException("Zahl ausserhalb des erlaubten Bereichs");
            values.add(Integer.toString(n));
            spawnQuestions(p, id, type, values, step + 1);
          },
          () -> spawnType(p, id));
      return;
    }
    if (questions.length == 4) {
      int offset = type.equals("followplayer") ? 1 : 0;
      if (Integer.parseInt(values.get(offset + 2)) > Integer.parseInt(values.get(offset + 3)))
        throw new IllegalArgumentException(
            "Minimalabstand darf nicht groesser als Maximalabstand sein");
    }
    Location location = p.getLocation().clone();
    confirm(
        p,
        "Spawnpunkt anlegen",
        List.of(
            id,
            "Art: " + type,
            "Welt/Ort: "
                + location.getWorld().getName()
                + " "
                + location.getBlockX()
                + ", "
                + location.getBlockZ(),
            "Wird sofort gespeichert und aktiviert"),
        "themob.spawn.set",
        () -> {
          if (!p.getWorld().equals(location.getWorld())
              || p.getLocation().distanceSquared(location) > 4) {
            p.sendMessage("§cPosition geaendert; Assistent erneut oeffnen.");
            spawns(p);
            return;
          }
          var args = new ArrayList<String>(List.of("set", type));
          if (type.equals("followplayer")) args.add(values.get(0));
          args.add(id);
          int offset = type.equals("followplayer") ? 1 : 0;
          args.add(values.get(offset));
          args.add(values.get(offset + 1));
          if (type.equals("randomradius")) {
            args.add(values.get(2));
            args.add(values.get(3));
          }
          if (type.equals("followplayer")) {
            args.add("endless");
            args.add(values.get(3));
            args.add(values.get(4));
            args.add("");
          }
          if (type.equals("randomworld")) {
            args.add("endless");
            args.add("60");
            args.add("");
          }
          execute(p, args.toArray(String[]::new));
          spawns(p);
        },
        () -> spawnType(p, id));
  }

  void dungeons(Player p, int number) {
    var entries = new ArrayList<Entry>();
    entries.add(
        new Entry(
            Material.END_STONE_BRICKS,
            "Dungeon erzeugen",
            List.of("Vorschau und Bestaetigung vor Terrainveraenderung"),
            this::dungeonStyles));
    entries.add(
        new Entry(
            Material.REPEATER,
            "Baustatus",
            List.of(plugin.dungeons().status()),
            v -> dungeons(v, number)));
    entries.add(
        new Entry(
            Material.REDSTONE_BLOCK,
            "Aktiven Bau abbrechen",
            List.of("Bestaetigung erforderlich; fertige Bloecke bleiben"),
            v ->
                confirm(
                    v,
                    "Bau abbrechen",
                    List.of(
                        plugin.dungeons().status(),
                        "Teilbauten bleiben geschuetzt und koennen fortgesetzt werden"),
                    "themob.dungeon.admin",
                    () -> {
                      execute(v, "dungeon", "cancel");
                      dungeons(v, number);
                    },
                    () -> dungeons(v, number))));
    for (var record : plugin.dungeons().records())
      entries.add(
          new Entry(
              Material.STONE_BRICKS,
              record.id,
              List.of(
                  record.style.label(),
                  record.state.name(),
                  record.levels + " Ebenen",
                  record.pathLength + " Bloecke Weg"),
              v -> dungeon(v, record.id)));
    page(p, "Dungeons", entries, number, () -> open(p), n -> dungeons(p, n));
  }

  void dungeonStyles(Player p) {
    var entries = new ArrayList<Entry>();
    for (String style :
        List.of("random", "krypta", "turm", "mine", "weltenbaum", "gottesturm", "labyrinth"))
      entries.add(
          new Entry(
              Material.STONE_BRICKS,
              style,
              List.of("Grosse Anlage; mindestens 500 Bloecke Wegenetz"),
              v -> {
                Location at = v.getLocation().clone();
                confirm(
                    v,
                    "Dungeon: " + style,
                    List.of(
                        "Welt: " + at.getWorld().getName(),
                        "Anker: " + at.getBlockX() + ", " + at.getBlockZ(),
                        "Vorhandenes Terrain im Baufeld wird entfernt!",
                        "Nur bei freiem, freigegebenem Baufeld bestaetigen"),
                    "themob.dungeon.admin",
                    () -> {
                      if (!v.getWorld().equals(at.getWorld())
                          || v.getLocation().distanceSquared(at) > 4) {
                        v.sendMessage("§cPosition geaendert. Vorschau erneut oeffnen.");
                        dungeonStyles(v);
                        return;
                      }
                      execute(v, "dungeon", style);
                      dungeons(v, 0);
                    },
                    () -> dungeonStyles(v));
              }));
    page(p, "Dungeonbau waehlen", entries, 0, () -> dungeons(p, 0), n -> {});
  }

  void dungeon(Player p, String id) {
    var record = plugin.dungeons().get(id);
    if (record == null) {
      dungeons(p, 0);
      return;
    }
    var h = screen(p, "Dungeon | " + id, () -> dungeons(p, 0));
    button(
        h,
        4,
        Material.BOOK,
        record.style.label(),
        List.of(
            record.state.name(),
            record.levels + " Ebenen",
            record.pathLength + " Bloecke Wegenetz",
            "Endboss: " + record.completionBossId),
        null);
    button(
        h,
        10,
        Material.ENDER_PEARL,
        "Betreten",
        List.of(),
        v -> {
          v.closeInventory();
          execute(v, "dungeon", "enter", id);
        });
    button(
        h,
        12,
        Material.OAK_DOOR,
        "Verlassen",
        List.of(),
        v -> {
          v.closeInventory();
          execute(v, "dungeon", "leave");
        });
    button(
        h,
        14,
        Material.DIAMOND_PICKAXE,
        "Baumodus an/aus",
        List.of("Nur Admin; bestehende Schutzregeln gelten"),
        v -> {
          execute(v, "dungeon", "edit", id);
          dungeon(v, id);
        });
    button(
        h,
        16,
        Material.BARRIER,
        "Baumodus aus",
        List.of(),
        v -> {
          execute(v, "dungeon", "edit", "off");
          dungeon(v, id);
        });
    button(
        h,
        28,
        Material.ZOMBIE_HEAD,
        "Endboss festlegen",
        List.of("Boss-Mob-ID waehlen; spawnt selbst keinen Boss"),
        v ->
            mobs(
                v,
                "",
                0,
                mob -> {
                  execute(v, "dungeon", "boss", id, mob);
                  dungeon(v, id);
                }));
    button(
        h,
        30,
        Material.RED_DYE,
        "Endbosswertung ausschalten",
        List.of(),
        v -> {
          execute(v, "dungeon", "boss", id, "off");
          dungeon(v, id);
        });
    button(
        h,
        32,
        Material.ENCHANTING_TABLE,
        "Ausgangsaltar reparieren",
        List.of("Standardposition im Bossraum"),
        v -> {
          execute(v, "dungeon", "altar", id);
          dungeon(v, id);
        });
    button(
        h,
        34,
        Material.BEACON,
        "Altar an aktueller Position",
        List.of("Bestehende Bloecke in der Altarform werden geaendert"),
        v ->
            confirm(
                v,
                "Altar versetzen",
                List.of(id, "Aktuelle Position im Dungeon verwenden"),
                "themob.dungeon.admin",
                () -> {
                  execute(v, "dungeon", "altar", id, "here");
                  dungeon(v, id);
                },
                () -> dungeon(v, id)));
    button(
        h,
        40,
        Material.REPEATER,
        "Bau fortsetzen",
        List.of("Nur fuer unterbrochene/abgebrochene Anlagen"),
        v ->
            confirm(
                v,
                "Dungeonbau fortsetzen",
                List.of(id, "Terrain im restlichen Baufeld wird wieder veraendert"),
                "themob.dungeon.admin",
                () -> {
                  execute(v, "dungeon", "resume", id);
                  dungeon(v, id);
                },
                () -> dungeon(v, id)));
    show(p, h);
  }

  void maintenance(Player p) {
    var h = screen(p, "Diagnose und Wartung", () -> open(p));
    button(
        h,
        10,
        Material.CLOCK,
        "Leistung",
        List.of(
            String.format(Locale.ROOT, "TPS: %.2f", plugin.automation().tps().tps1m()),
            "Geladene Mobs: " + plugin.mobs().getAllLivingMobs().size(),
            "Cold-Zone: " + plugin.activity().coldCount()),
        v -> maintenance(v));
    button(
        h,
        12,
        Material.COMPARATOR,
        "Limits / Hot-Cold konfigurieren",
        List.of("config.yml bearbeiten"),
        v -> configs.open(v, "config.yml"));
    button(
        h,
        14,
        Material.WRITABLE_BOOK,
        "Kompass und HUD",
        List.of(
            "config.yml: navigation-hud / mob-radar",
            "CoinOverlay besitzt weiterhin das gemeinsame Paket"),
        v -> configs.open(v, "config.yml"));
    button(
        h,
        16,
        Material.BOOK,
        "Ereignisse / API",
        List.of(
            "Mob-, Phasen-, Schaden-, Beute- und Dungeonereignisse",
            "Bukkit ServicesManager; API-JAR nicht installieren"),
        v -> integrations(v, 0));
    button(
        h,
        20,
        Material.REDSTONE_TORCH,
        "Debuganzeige umschalten",
        List.of("Verwendet den bestehenden Debug-Befehl"),
        v -> {
          execute(v, "debug");
          maintenance(v);
        });
    button(
        h,
        22,
        Material.MAP,
        "Eigenes Navigations-HUD an/aus",
        List.of("Aendert nur deine eigene HUD-Anzeige"),
        v -> {
          execute(v, "toggle", "hud");
          maintenance(v);
        });
    button(
        h,
        24,
        Material.PLAYER_HEAD,
        "Kopftextur auslesen",
        List.of("Kopf in der Haupthand halten", "Verwendet /mob get base64"),
        v -> {
          execute(v, "get", "base64");
          maintenance(v);
        });
    button(
        h,
        29,
        Material.REDSTONE_BLOCK,
        "Aktive Mobs entfernen",
        List.of("Keine Kills, Beute oder Aufgabenfortschritte"),
        v ->
            confirm(
                v,
                "Alle TheMob-Mobs entfernen",
                List.of(
                    "Alle aktuell verwalteten Mobs werden entfernt",
                    "Diese Aktion erzeugt keine Belohnungen"),
                "themob.killall",
                () -> {
                  execute(v, "killall");
                  maintenance(v);
                },
                () -> maintenance(v)));
    button(
        h,
        33,
        Material.REPEATER,
        "Mob-/Konfigurationsdaten neu laden",
        List.of(
            "Entfernt aktive Mobs und beendet aktuelle Systeme",
            "Integrationen und Faehigkeiten benoetigen vollstaendigen Neustart"),
        v ->
            confirm(
                v,
                "Hard-Reload",
                List.of(
                    "Aktive TheMob-Mobs werden entfernt",
                    "Ungespeicherte Menueentwuerfe werden verworfen!",
                    "Mobdateien, Spawns und Konfiguration werden neu geladen",
                    "Faehigkeiten und Anbindungen bleiben bis zum Neustart vorgemerkt"),
                "themob.reload",
                () -> {
                  v.closeInventory();
                  execute(v, "reload");
                  if (plugin.adminMenus() != null) plugin.adminMenus().open(v);
                },
                () -> maintenance(v)));
    show(p, h);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void click(InventoryClickEvent event) {
    if (org.plugin.theMob.compat.Views.top(event.getView()) == null
        || !(org.plugin.theMob.compat.Views.top(event.getView()).getHolder()
            instanceof AdminMenuHolder h)) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player p)
        || closed
        || !generation.equals(h.generation)
        || !p.getUniqueId().equals(h.owner)
        || !mayOpen(p)) return;
    var s = sessions.get(p.getUniqueId());
    if (s == null
        || s.inventory != h.getInventory()
        || event.getRawSlot() < 0
        || event.getRawSlot() >= 54
        || event.getClick() != ClickType.LEFT) return;
    var action = h.actions.get(event.getRawSlot());
    if (action == null) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (closed
                  || !allowed(p, "themob.admin.menu")
                  || org.plugin.theMob.compat.Views.top(p.getOpenInventory()) != h.getInventory()
                  || session(p).inventory != h.getInventory()) return;
              session(p).touched = System.nanoTime();
              try {
                action.accept(p);
              } catch (Exception failure) {
                error(p, failure);
              }
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drag(InventoryDragEvent event) {
    if (org.plugin.theMob.compat.Views.top(event.getView()) != null
        && org.plugin.theMob.compat.Views.top(event.getView()).getHolder()
            instanceof AdminMenuHolder) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void chat(AsyncPlayerChatEvent event) {
    Prompt pending = prompts.remove(event.getPlayer().getUniqueId());
    if (pending == null) return;
    event.setCancelled(true);
    String text = event.getMessage();
    Player player = event.getPlayer();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (closed
                  || !mayOpen(player)
                  || !player.isOnline()
                  || pending.expires() < System.nanoTime()
                  || !pending.id().equals(session(player).prompt)) return;
              session(player).prompt = null;
              try {
                if (text.equalsIgnoreCase("abbrechen")) pending.cancel().run();
                else pending.action().accept(text);
              } catch (Exception failure) {
                error(player, failure);
                pending.cancel().run();
              }
            });
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    prompts.remove(event.getPlayer().getUniqueId());
    sessions.remove(event.getPlayer().getUniqueId());
  }

  @Override
  public void close() {
    closed = true;
    cleanup.cancel();
    HandlerList.unregisterAll(this);
    prompts.clear();
    for (Player p : Bukkit.getOnlinePlayers()) if (viewing(p)) p.closeInventory();
    sessions.clear();
  }
}
