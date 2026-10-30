package org.plugin.theMob.dungeon;

import java.io.IOException;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.plugin.theMob.TheMob;

public final class DungeonService implements Listener, AutoCloseable {
  private final TheMob plugin;
  private final NamespacedKey returnKey;
  private final Map<UUID, String> editors = new HashMap<>();
  private final Map<UUID, Map<Long, List<DungeonRecord>>> chunks = new HashMap<>();
  private final ExecutorService planner =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "TheMob-DungeonPlanner");
            thread.setDaemon(true);
            return thread;
          });
  private final Random random = new SecureRandom();
  private DungeonStore store;
  private String error;
  private int blocksPerTick = 1536;
  private long tickNanos = 3_000_000;
  private boolean enabled = true, autoEnter = true;
  private Future<DungeonPlan> pending;
  private DungeonBuilder builder;
  private DungeonRecord current;
  private UUID requester;
  private Location waiting;
  private BukkitTask task;
  private int ticks;
  private final DungeonAltars altars;

  public DungeonService(TheMob plugin) {
    this.plugin = plugin;
    returnKey = new NamespacedKey(plugin, "dungeon_return");
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
    var protection = new DungeonProtection(this);
    plugin.getServer().getPluginManager().registerEvents(protection, plugin);
    protection.optional(plugin);
    Path data = plugin.getDataFolder().toPath().resolve("dungeons.yml");
    try {
      Path config = plugin.getDataFolder().toPath().resolve("dungeon.yml");
      if (!Files.exists(config)) plugin.saveResource("dungeon.yml", false);
      if (!Files.exists(plugin.getDataFolder().toPath().resolve("battlepass-dungeons.yml")))
        plugin.saveResource("battlepass-dungeons.yml", false);
      var yaml = new YamlConfiguration();
      yaml.load(config.toFile());
      enabled = yaml.getBoolean("enabled", true);
      autoEnter = yaml.getBoolean("auto-enter-creator", true);
      blocksPerTick =
          org.plugin.theMob.compat.Numbers.clamp(
              yaml.getInt("build.blocks-per-tick", 1536), 64, 8192);
      double millis = yaml.getDouble("build.milliseconds-per-tick", 3);
      tickNanos =
          Double.isFinite(millis)
              ? (long) (org.plugin.theMob.compat.Numbers.clamp(millis, 0.5, 10) * 1_000_000)
              : 3_000_000;
      store = new DungeonStore(data);
      for (var record : store.records()) index(record);
      if (!store.records().isEmpty()) store.save();
    } catch (Exception failure) {
      fail("Dungeon-Initialisierung", failure);
      try {
        store = new DungeonStore(data.resolveSibling("dungeons.yml.bak"));
        for (var r : store.records()) index(r);
      } catch (Exception ignored) {
      }
    }
    altars = new DungeonAltars(plugin, this);
    for (Player player : Bukkit.getOnlinePlayers()) altars.ensure(recordAt(player.getLocation()));
  }

  boolean available() {
    return error == null;
  }

  DungeonRecord recordAt(Location l) {
    return l == null || l.getWorld() == null
        ? null
        : at(l.getWorld(), l.getBlockX(), l.getBlockZ());
  }

  boolean saveAltarState() {
    if (store == null || error != null) return false;
    try {
      store.save();
      return true;
    } catch (IOException failure) {
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Ausgangsaltar konnte nicht gespeichert werden.",
              failure);
      return false;
    }
  }

  public boolean atExitAltar(Player player, String id) {
    return available() && altars.near(player, id);
  }

  public void altar(CommandSender sender, String id, boolean here) {
    if (!admin(sender)) {
      sender.sendMessage("§cNur Admins duerfen Altaere setzen.");
      return;
    }
    var r = get(id);
    if (r == null || r.state != DungeonRecord.State.READY) {
      sender.sendMessage("§cDungeon unbekannt oder nicht fertig.");
      return;
    }
    AltarLayout.Pos position = null;
    if (here) {
      if (!(sender instanceof Player p) || !p.getWorld().getUID().equals(r.worldId)) {
        sender.sendMessage("§cStehe im Dungeon, um den Altar hier zu setzen.");
        return;
      }
      var l = p.getLocation();
      position =
          new AltarLayout.Pos(
              l.getBlockX() - r.x(), l.getBlockY() - r.baseY, l.getBlockZ() - r.z());
    }
    sender.sendMessage(
        altars.queue(r, position, true)
            ? "§aAusgangsaltar fuer " + r.id + " wird aufgebaut."
            : "§cAltar konnte nicht beauftragt werden: Bau aktiv, Datenfehler oder Platz ausserhalb"
                + " des Dungeons.");
  }

  public boolean admin(CommandSender sender) {
    return sender.hasPermission("themob.admin") || sender.hasPermission("themob.dungeon.admin");
  }

  public Collection<DungeonRecord> records() {
    return store == null ? List.of() : store.records();
  }

  public DungeonRecord get(String id) {
    return store == null ? null : store.get(id);
  }

  public boolean busy() {
    return current != null;
  }

  public String status() {
    if (error != null) return "§cNicht verfuegbar: " + error;
    if (current == null) return "§aKein Bau aktiv. " + records().size() + " reservierte Anlagen.";
    return "§e"
        + current.id
        + " / "
        + current.style.label()
        + ": "
        + (builder == null ? "Planung" : builder.progress() + " % gebaut");
  }

  private static long key(int x, int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
  }

  private void index(DungeonRecord record) {
    var map = chunks.computeIfAbsent(record.worldId, k -> new HashMap<>());
    for (int z = record.z() >> 4; z <= (record.z() + DungeonPlan.DEPTH - 1) >> 4; z++)
      for (int x = record.x() >> 4; x <= (record.x() + DungeonPlan.WIDTH - 1) >> 4; x++)
        map.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(record);
  }

  private DungeonRecord at(World world, int x, int z) {
    var map = chunks.get(world.getUID());
    if (map == null) return null;
    for (var r : map.getOrDefault(key(x >> 4, z >> 4), List.of())) if (r.contains(x, z)) return r;
    return null;
  }

  public boolean protectedBlock(Block b) {
    return at(b.getWorld(), b.getX(), b.getZ()) != null;
  }

  public Collection<org.plugin.theMob.api.DungeonRegion> intersecting(
      UUID world, double x, double z, double bx, double bz) {
    Set<DungeonRecord> result = new LinkedHashSet<>();
    var map = chunks.get(world);
    if (map == null) return List.of();
    if (Math.abs(x) > 30_000_000
        || Math.abs(z) > 30_000_000
        || Math.abs(bx) > 30_000_000
        || Math.abs(bz) > 30_000_000
        || (bx - x + 32) * (bz - z + 32) > 4096 * 256) {
      for (var r : records()) if (r.worldId.equals(world)) result.add(r);
    } else
      for (int cz = ((int) Math.floor(z)) >> 4; cz <= ((int) Math.floor(bz)) >> 4; cz++)
        for (int cx = ((int) Math.floor(x)) >> 4; cx <= ((int) Math.floor(bx)) >> 4; cx++)
          result.addAll(map.getOrDefault(key(cx, cz), List.of()));
    return result.stream()
        .filter(
            r ->
                r.x() <= bx
                    && r.x() + DungeonPlan.WIDTH >= x
                    && r.z() <= bz
                    && r.z() + DungeonPlan.DEPTH >= z)
        .map(this::snapshot)
        .toList();
  }

  public void setBoss(CommandSender sender, String id, String boss) {
    if (!admin(sender)) {
      sender.sendMessage("§cNur Admins duerfen Endbosse festlegen.");
      return;
    }
    var r = get(id);
    if (r == null) {
      sender.sendMessage("§cDungeon unbekannt.");
      return;
    }
    boss = boss.toLowerCase(Locale.ROOT);
    var definition = plugin.api().definition(boss);
    if (!boss.equals("off") && (definition == null || !definition.boss())) {
      sender.sendMessage("§cBitte eine gueltige TheMob-Boss-ID angeben.");
      return;
    }
    String previous = r.completionBossId;
    r.completionBossId = boss.equals("off") ? "" : boss;
    try {
      store.save();
      sender.sendMessage(
          "§aEndboss fuer "
              + r.id
              + ": "
              + (r.completionBossId.isEmpty() ? "aus" : r.completionBossId)
              + ". Boss selbst platzieren.");
    } catch (IOException e) {
      r.completionBossId = previous;
      sender.sendMessage("§cEndboss konnte nicht gespeichert werden.");
    }
  }

  private boolean eligible(Player p, DungeonRecord r) {
    return p != null
        && p.isOnline()
        && !p.isDead()
        && p.getGameMode() != GameMode.SPECTATOR
        && !p.hasMetadata("NPC")
        && p.getWorld().getUID().equals(r.worldId)
        && r.contains(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
  }

  public boolean complete(String id, UUID run, Collection<UUID> participants, String boss) {
    var r = get(id);
    if (r == null
        || r.state != DungeonRecord.State.READY
        || run == null
        || participants == null
        || error != null
        || r.completedRuns.contains(run)) return false;
    var players =
        participants.stream()
            .filter(Objects::nonNull)
            .distinct()
            .map(Bukkit::getPlayer)
            .filter(p -> eligible(p, r))
            .toList();
    if (players.isEmpty()) return false;
    var previous = new LinkedHashSet<>(r.completedRuns);
    r.completedRuns.add(run);
    while (r.completedRuns.size() > 256) r.completedRuns.remove(r.completedRuns.iterator().next());
    try {
      store.save();
    } catch (IOException failure) {
      r.completedRuns.clear();
      r.completedRuns.addAll(previous);
      fail("Dungeon-Abschluss", failure);
      return false;
    }
    Bukkit.getPluginManager()
        .callEvent(
            new org.plugin.theMob.api.dungeon.DungeonCompletedEvent(
                snapshot(r), run, players, boss, r.style.label(), r.worldName, r.levels));
    players.forEach(p -> p.sendMessage("§6Dungeon " + r.id + " bezwungen!"));
    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void bossDefeated(org.plugin.theMob.api.event.TheMobDeathEvent e) {
    if (!e.getMob().boss()
        || e.getMob().minion()
        || e.getKiller() == null
        || e.getEntity().hasMetadata("NPC")) return;
    var l = e.getEntity().getLocation();
    var r = at(l.getWorld(), l.getBlockX(), l.getBlockZ());
    if (r == null
        || !r.completionBossId.equals(e.getMob().definitionId())
        || !eligible(e.getKiller(), r)) return;
    complete(
        r.id,
        e.getMob().entityId(),
        e.getEntity().getWorld().getPlayers().stream().map(Player::getUniqueId).toList(),
        r.completionBossId);
  }

  private org.plugin.theMob.api.DungeonRegion snapshot(DungeonRecord record) {
    return new org.plugin.theMob.api.DungeonRegion(
        record.id,
        record.worldId,
        record.x(),
        record.z(),
        record.x() + DungeonPlan.WIDTH,
        record.z() + DungeonPlan.DEPTH,
        error == null && record.state == DungeonRecord.State.READY);
  }

  public Collection<org.plugin.theMob.api.DungeonRegion> regions() {
    return records().stream().map(this::snapshot).toList();
  }

  public Optional<org.plugin.theMob.api.DungeonRegion> regionAt(Location location) {
    if (location == null
        || location.getWorld() == null
        || !Double.isFinite(location.getX())
        || !Double.isFinite(location.getZ())) return Optional.empty();
    return Optional.ofNullable(at(location.getWorld(), location.getBlockX(), location.getBlockZ()))
        .map(this::snapshot);
  }

  public boolean canEdit(Player p, Block b) {
    if (error != null || !admin(p)) return false;
    var r = at(b.getWorld(), b.getX(), b.getZ());
    return r != null
        && r.state != DungeonRecord.State.BUILDING
        && r.state != DungeonRecord.State.PLANNING
        && r.id.equals(editors.get(p.getUniqueId()));
  }

  public void create(CommandSender sender, DungeonStyle style) {
    if (!admin(sender)) {
      sender.sendMessage("§cNur Admins duerfen Dungeons erzeugen.");
      return;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage("§cDungeon am gewuenschten Bauort als Spieler erzeugen.");
      return;
    }
    if (error != null || store == null || !enabled) {
      sender.sendMessage(
          "§cDungeon-Erzeugung nicht verfuegbar. "
              + (error == null ? "Siehe dungeon.yml." : error));
      return;
    }
    if (busy()) {
      sender.sendMessage("§eEs wird bereits ein Dungeon gebaut. /mob dungeon status");
      return;
    }
    if (style == null) style = DungeonStyle.values()[random.nextInt(DungeonStyle.values().length)];
    World world = player.getWorld();
    int x = player.getLocation().getBlockX() - 56, z = player.getLocation().getBlockZ() - 123;
    for (var r : records())
      if (r.worldId.equals(world.getUID())
          && x < r.x() + DungeonPlan.WIDTH
          && x + DungeonPlan.WIDTH > r.x()
          && z < r.z() + DungeonPlan.DEPTH
          && z + DungeonPlan.DEPTH > r.z()) {
        sender.sendMessage("§cBaufeld ueberschneidet den geschuetzten Dungeon " + r.id + ".");
        return;
      }
    if (Math.abs((long) x) > 29_999_000
        || Math.abs((long) z) > 29_999_000
        || !world.getWorldBorder().isInside(new Location(world, x, player.getLocation().getY(), z))
        || !world
            .getWorldBorder()
            .isInside(
                new Location(
                    world,
                    x + DungeonPlan.WIDTH,
                    player.getLocation().getY(),
                    z + DungeonPlan.DEPTH))) {
      sender.sendMessage("§cDungeon passt nicht in die Weltgrenze.");
      return;
    }
    var preCreate =
        new org.plugin.theMob.api.event.TheMobDungeonPreCreateEvent(
            player,
            new org.plugin.theMob.api.DungeonRegion(
                "pending",
                world.getUID(),
                x,
                z,
                x + DungeonPlan.WIDTH,
                z + DungeonPlan.DEPTH,
                false),
            style.label());
    Bukkit.getPluginManager().callEvent(preCreate);
    if (preCreate.isCancelled()) {
      sender.sendMessage("§cDungeonbau durch eine Schutz-/API-Pruefung abgelehnt.");
      return;
    }
    try {
      Location safe = safeOutside(world, x, z);
      current =
          store.reserve(
              style, random.nextLong(), world, x, player.getLocation().getBlockY() - 9, z);
      index(current);
      waiting = safe;
      requester = player.getUniqueId();
      DungeonStyle selected = style;
      long seed = current.seed;
      pending = planner.submit(() -> DungeonPlanner.generate(selected, seed));
      task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
      sender.sendMessage(
          "§6[TheMob] " + current.id + ": " + style.label() + " wird nach Norden gebaut.");
      sender.sendMessage(
          "§eBaufeld: X "
              + x
              + ".."
              + (x + 112)
              + ", Z "
              + z
              + ".."
              + (z + 132)
              + ". Vorhandene Bloecke werden ohne Drops ersetzt.");
      sender.sendMessage(
          "§7Mindestens 500 Bloecke Wegenetz. /mob dungeon status | Abbrechen: /mob dungeon"
              + " cancel");
    } catch (Exception failure) {
      if (current != null) {
        current.state = DungeonRecord.State.FAILED;
        persist();
      }
      cleanup();
      sender.sendMessage("§cDungeon konnte nicht gestartet werden: " + failure.getMessage());
      plugin.getLogger().log(java.util.logging.Level.SEVERE, "Dungeon start failed", failure);
    }
  }

  private Location safeOutside(World world, int x, int z) throws IOException {
    int sx = x + 56, sz = z + DungeonPlan.DEPTH + 5;
    if (at(world, sx, sz) != null)
      throw new IOException("Wartebereich liegt in einem anderen Dungeon.");
    if (!world.getWorldBorder().isInside(new Location(world, sx, 64, sz)))
      throw new IOException("Kein Wartebereich innerhalb der Weltgrenze.");
    int y = world.getHighestBlockYAt(sx, sz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
    if (y <= world.getMinHeight() + 1 || y + 2 >= world.getMaxHeight())
      throw new IOException("Kein sicherer Boden im Wartebereich.");
    Material ground = world.getBlockAt(sx, y - 1, sz).getType();
    if (!ground.isSolid()
        || java.util.Set.of(
                Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE)
            .contains(ground)
        || !world.getBlockAt(sx, y, sz).isPassable()
        || !world.getBlockAt(sx, y + 1, sz).isPassable())
      throw new IOException("Wartebereich unsicher; bitte einen anderen Bauort waehlen.");
    return new Location(world, sx + 0.5, y, sz + 0.5, 180, 0);
  }

  private void tick() {
    try {
      if (builder == null) {
        if (!pending.isDone()) return;
        DungeonPlan plan = pending.get();
        pending = null;
        World world = Bukkit.getWorld(current.worldId);
        if (world == null) throw new IOException("Bauwelt nicht geladen");
        current.apply(plan);
        // Validate height before displacing anyone or changing a block.
        builder = new DungeonBuilder(plugin, world, current, plan, waiting);
        current.state = DungeonRecord.State.BUILDING;
        store.save();
        for (Player player : world.getPlayers())
          if (current.contains(
              player.getLocation().getBlockX(), player.getLocation().getBlockZ())) {
            player
                .getPersistentDataContainer()
                .set(returnKey, PersistentDataType.STRING, encode(waiting));
            if (!player.teleport(waiting))
              throw new IOException("Spieler konnte nicht aus dem Baufeld versetzt werden");
            player.sendMessage("§eDungeon-Bau: Du wartest ausserhalb des Baufelds.");
          }
      }
      if (builder.tick(blocksPerTick, tickNanos)) {
        current.state = DungeonRecord.State.READY;
        current.altarVersion = AltarLayout.VERSION;
        store.save();
        altars.register(current);
        var r = current;
        Player player = requester == null ? null : Bukkit.getPlayer(requester);
        plugin
            .getLogger()
            .info(
                "Dungeon "
                    + r.id
                    + " fertig: "
                    + r.style.label()
                    + ", "
                    + r.levels
                    + " Ebenen, "
                    + r.pathLength
                    + " Bloecke Wegenetz.");
        cleanup();
        if (player != null) {
          player.sendMessage(
              "§aDungeon "
                  + r.id
                  + " fertig: "
                  + r.levels
                  + " Ebenen, "
                  + r.pathLength
                  + " Bloecke Wegenetz.");
          player.sendMessage(
              "§eGruppe: /mob dungeon enter " + r.id + " | Baumodus: /mob dungeon edit " + r.id);
          if (autoEnter) enter(player, r.id);
        }
      } else if (++ticks % 200 == 0 && requester != null) {
        Player p = Bukkit.getPlayer(requester);
        if (p != null) p.sendMessage("§7[TheMob] " + status());
      }
    } catch (Exception failure) {
      if (current != null) {
        current.state = DungeonRecord.State.FAILED;
        persist();
      }
      Player p = requester == null ? null : Bukkit.getPlayer(requester);
      if (p != null) p.sendMessage("§cDungeon-Bau abgebrochen: " + failure.getMessage());
      cleanup();
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Dungeon-Bau fehlgeschlagen; Teilbau bleibt geschuetzt.",
              failure);
    }
  }

  public void edit(Player player, String id) {
    if (!admin(player)) {
      player.sendMessage("§cNur Admins duerfen den Baumodus verwenden.");
      return;
    }
    if (id.equalsIgnoreCase("off")) {
      editors.remove(player.getUniqueId());
      player.sendMessage("§aDungeon-Baumodus aus.");
      return;
    }
    var r = get(id);
    if (r == null
        || r.state == DungeonRecord.State.BUILDING
        || r.state == DungeonRecord.State.PLANNING) {
      player.sendMessage("§cDungeon unbekannt oder Bau aktiv.");
      return;
    }
    editors.put(player.getUniqueId(), r.id);
    player.sendMessage("§eBaumodus fuer " + r.id + " aktiv. Beenden: /mob dungeon edit off");
  }

  public void cancel(CommandSender sender) {
    if (!admin(sender)) {
      sender.sendMessage("§cNur Admins duerfen einen Bau abbrechen.");
      return;
    }
    if (current == null) {
      sender.sendMessage("§eKein Bau aktiv.");
      return;
    }
    String id = current.id;
    current.state = DungeonRecord.State.CANCELLED;
    persist();
    cleanup();
    sender.sendMessage(
        "§eBau "
            + id
            + " abgebrochen. Teilbau bleibt geschuetzt; Baumodus: /mob dungeon edit "
            + id);
  }

  public void resume(CommandSender sender, String id) {
    if (!admin(sender)) {
      sender.sendMessage("§cNur Admins duerfen Dungeons weiterbauen.");
      return;
    }
    var r = get(id);
    if (error != null || !enabled || busy() || r == null || r.state == DungeonRecord.State.READY) {
      sender.sendMessage(
          "§cNur unterbrochene/abgebrochene Bauten koennen ohne anderen aktiven Bau fortgesetzt"
              + " werden.");
      return;
    }
    World world = Bukkit.getWorld(r.worldId);
    if (world == null) {
      sender.sendMessage("§cBauwelt derzeit nicht geladen.");
      return;
    }
    var preResume =
        new org.plugin.theMob.api.event.TheMobDungeonPreCreateEvent(
            sender,
            new org.plugin.theMob.api.DungeonRegion(
                r.id,
                world.getUID(),
                r.x(),
                r.z(),
                r.x() + DungeonPlan.WIDTH,
                r.z() + DungeonPlan.DEPTH,
                false),
            r.style.label());
    Bukkit.getPluginManager().callEvent(preResume);
    if (preResume.isCancelled()) {
      sender.sendMessage("§cWeiterbau durch eine Schutz-/API-Pruefung abgelehnt.");
      return;
    }
    try {
      waiting = safeOutside(world, r.x(), r.z());
      current = r;
      requester = sender instanceof Player p ? p.getUniqueId() : null;
      r.state = DungeonRecord.State.PLANNING;
      store.save();
      pending = planner.submit(() -> DungeonPlanner.generate(r.style, r.seed));
      task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
      sender.sendMessage(
          "§e" + r.id + " wird aus demselben Bauplan neu aufgebaut. Der Teilbau wird ersetzt.");
    } catch (Exception failure) {
      if (current != null) {
        current.state = DungeonRecord.State.FAILED;
        persist();
      }
      cleanup();
      sender.sendMessage("§cWeiterbau nicht moeglich: " + failure.getMessage());
    }
  }

  public void enter(Player player, String id) {
    if (!player.hasPermission("themob.dungeon.enter") && !admin(player)) {
      player.sendMessage("§cKeine Dungeon-Zutrittsrechte.");
      return;
    }
    var r = get(id);
    if (r == null || r.state != DungeonRecord.State.READY || error != null) {
      player.sendMessage("§cDungeon nicht fertig oder unbekannt.");
      return;
    }
    World world = Bukkit.getWorld(r.worldId);
    if (world == null) {
      player.sendMessage("§cDungeon-Welt derzeit nicht geladen.");
      return;
    }
    boolean remember =
        at(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ())
            == null;
    if (remember)
      player
          .getPersistentDataContainer()
          .set(returnKey, PersistentDataType.STRING, encode(player.getLocation()));
    if (!player.teleport(new Location(world, r.x() + 56.5, r.baseY + 9, r.z() + 123.5, 180, 0))) {
      if (remember) player.getPersistentDataContainer().remove(returnKey);
      player.sendMessage("§cTeleport wurde blockiert.");
    } else {
      player.sendMessage("§a" + r.id + " | Verlassen: /mob dungeon leave");
      altars.ensure(r);
    }
  }

  public void leave(Player player) {
    Location back = returnLocation(player);
    if (back != null && player.teleport(back)) {
      player.getPersistentDataContainer().remove(returnKey);
      editors.remove(player.getUniqueId());
    } else player.sendMessage("§cRueckkehr derzeit blockiert.");
  }

  private String encode(Location l) {
    return l.getWorld().getUID()
        + ";"
        + l.getX()
        + ";"
        + l.getY()
        + ";"
        + l.getZ()
        + ";"
        + l.getYaw()
        + ";"
        + l.getPitch();
  }

  private Location returnLocation(Player p) {
    String raw = p.getPersistentDataContainer().get(returnKey, PersistentDataType.STRING);
    if (raw != null)
      try {
        String[] v = raw.split(";");
        World world = Bukkit.getWorld(UUID.fromString(v[0]));
        double x = Double.parseDouble(v[1]),
            y = Double.parseDouble(v[2]),
            z = Double.parseDouble(v[3]);
        if (world != null
            && Double.isFinite(x)
            && Double.isFinite(y)
            && Double.isFinite(z)
            && at(world, (int) Math.floor(x), (int) Math.floor(z)) == null)
          return new Location(world, x, y, z, Float.parseFloat(v[4]), Float.parseFloat(v[5]));
      } catch (RuntimeException ignored) {
      }
    var r = at(p.getWorld(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
    if (r != null)
      try {
        return safeOutside(p.getWorld(), r.x(), r.z());
      } catch (IOException ignored) {
      }
    return null;
  }

  private void guardMove(PlayerMoveEvent e) {
    if (e.getTo() == null) return;
    var r = at(e.getTo().getWorld(), e.getTo().getBlockX(), e.getTo().getBlockZ());
    if (r != null
        && (r.state == DungeonRecord.State.PLANNING || r.state == DungeonRecord.State.BUILDING)) {
      if (waiting != null && current == r) e.setTo(waiting);
      else e.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void move(PlayerMoveEvent e) {
    if (e.getTo() != null
        && e.getFrom().getBlockX() == e.getTo().getBlockX()
        && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
    guardMove(e);
  }

  @EventHandler(ignoreCancelled = true)
  public void teleport(PlayerTeleportEvent e) {
    guardMove(e);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    editors.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void changed(PlayerChangedWorldEvent e) {
    editors.remove(e.getPlayer().getUniqueId());
    if (at(
            e.getPlayer().getWorld(),
            e.getPlayer().getLocation().getBlockX(),
            e.getPlayer().getLocation().getBlockZ())
        == null) e.getPlayer().getPersistentDataContainer().remove(returnKey);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void exited(PlayerMoveEvent e) {
    forgetReturnOnExit(e);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void exitedTeleport(PlayerTeleportEvent e) {
    forgetReturnOnExit(e);
  }

  private void forgetReturnOnExit(PlayerMoveEvent e) {
    if (e.getTo() == null) return;
    if (e.getFrom().getBlockX() == e.getTo().getBlockX()
        && e.getFrom().getBlockZ() == e.getTo().getBlockZ()
        && e.getFrom().getWorld().equals(e.getTo().getWorld())) return;
    if (at(e.getFrom().getWorld(), e.getFrom().getBlockX(), e.getFrom().getBlockZ()) != null
        && at(e.getTo().getWorld(), e.getTo().getBlockX(), e.getTo().getBlockZ()) == null) {
      editors.remove(e.getPlayer().getUniqueId());
      e.getPlayer().getPersistentDataContainer().remove(returnKey);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void respawn(PlayerRespawnEvent e) {
    var r =
        at(
            e.getRespawnLocation().getWorld(),
            e.getRespawnLocation().getBlockX(),
            e.getRespawnLocation().getBlockZ());
    if (r != null && r.state != DungeonRecord.State.READY) {
      Location back = returnLocation(e.getPlayer());
      if (back != null) e.setRespawnLocation(back);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void natural(CreatureSpawnEvent e) {
    var l = e.getLocation();
    if (at(l.getWorld(), l.getBlockX(), l.getBlockZ()) == null) return;
    switch (e.getSpawnReason()) {
      case NATURAL, CHUNK_GEN, REINFORCEMENTS, VILLAGE_DEFENSE, VILLAGE_INVASION, PATROL, RAID ->
          e.setCancelled(true);
      default -> {}
    }
  }

  private void persist() {
    try {
      store.save();
    } catch (IOException e) {
      fail("Dungeon-Speicherung", e);
    }
  }

  private void fail(String where, Exception e) {
    error = e.getMessage();
    plugin.getLogger().log(java.util.logging.Level.SEVERE, where + ": " + error, e);
  }

  private void cleanup() {
    if (pending != null) {
      pending.cancel(true);
      pending = null;
    }
    if (builder != null) {
      builder.close();
      builder = null;
    }
    if (task != null) {
      task.cancel();
      task = null;
    }
    current = null;
    requester = null;
    waiting = null;
    ticks = 0;
  }

  @Override
  public void close() {
    altars.close();
    if (current != null) {
      current.state = DungeonRecord.State.INTERRUPTED;
      persist();
    }
    cleanup();
    planner.shutdownNow();
    editors.clear();
    HandlerList.unregisterAll(this);
  }
}
