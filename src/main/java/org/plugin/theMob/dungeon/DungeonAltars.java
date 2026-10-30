package org.plugin.theMob.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.dungeon.*;

/** Physical altar upgrades and a source-bound menu. No banner or command spoofing. */
final class DungeonAltars implements Listener, AutoCloseable {
  private record Anchor(UUID world, long chunk) {}

  private record Placement(AltarLayout.Pos pos, BlockData data, boolean oldCenter) {}

  private record Group(int x, int z, List<Placement> blocks) {}

  private static final class Job {
    final DungeonRecord record;
    final List<Group> groups;
    final AltarLayout.Pos previous;
    final int previousVersion;
    boolean scanning = true;
    int group, cursor;

    Job(DungeonRecord record, List<Group> groups, AltarLayout.Pos previous, int previousVersion) {
      this.record = record;
      this.groups = groups;
      this.previous = previous;
      this.previousVersion = previousVersion;
    }
  }

  static final class View implements InventoryHolder {
    final DungeonAltars controller;
    final UUID player;
    final String dungeon;
    final int page;
    final DungeonTravelProvider provider;
    final Map<Integer, String> targets = new HashMap<>();
    Inventory inventory;

    View(
        DungeonAltars controller,
        Player player,
        String dungeon,
        int page,
        DungeonTravelProvider provider) {
      this.controller = controller;
      this.player = player.getUniqueId();
      this.dungeon = dungeon;
      this.page = page;
      this.provider = provider;
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }

  private final TheMob plugin;
  private final DungeonService service;
  private final ArrayDeque<Job> jobs = new ArrayDeque<>();
  private final Set<String> queued = new HashSet<>();
  private final Set<String> blocked = new HashSet<>();
  private final Map<String, Anchor> anchors = new HashMap<>();
  private final Map<Anchor, Set<String>> chunks = new HashMap<>();
  private BukkitTask task;
  private Chunk held;
  private boolean closed;

  DungeonAltars(TheMob plugin, DungeonService service) {
    this.plugin = plugin;
    this.service = service;
    Bukkit.getPluginManager().registerEvents(this, plugin);
    service.records().forEach(this::register);
  }

  private static long chunkKey(int x, int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
  }

  void register(DungeonRecord r) {
    Anchor previous = anchors.remove(r.id);
    if (previous != null) {
      var ids = chunks.get(previous);
      if (ids != null) {
        ids.remove(r.id);
        if (ids.isEmpty()) chunks.remove(previous);
      }
    }
    if (r.altar != null) {
      var key =
          new Anchor(r.worldId, chunkKey((r.x() + r.altar.x()) >> 4, (r.z() + r.altar.z()) >> 4));
      anchors.put(r.id, key);
      chunks.computeIfAbsent(key, k -> new HashSet<>()).add(r.id);
    }
  }

  boolean near(Player p, String id) {
    if (p == null || id == null || !service.available()) return false;
    var r = service.get(id);
    if (closed
        || r == null
        || r.state != DungeonRecord.State.READY
        || r.altar == null
        || r.altarVersion != AltarLayout.VERSION
        || !p.isOnline()
        || p.isDead()
        || p.getGameMode() == GameMode.SPECTATOR
        || !p.hasPermission("themob.dungeon.altar")
        || !p.getWorld().getUID().equals(r.worldId)
        || service.recordAt(p.getLocation()) != r) return false;
    int x = r.x() + r.altar.x(), y = r.baseY + r.altar.y(), z = r.z() + r.altar.z();
    return p.getLocation().distanceSquared(new Location(p.getWorld(), x + .5, y + .5, z + .5)) <= 64
        && p.getWorld().isChunkLoaded(x >> 4, z >> 4)
        && p.getWorld().getBlockAt(x, y, z).getType() == Material.ENCHANTING_TABLE;
  }

  void ensure(DungeonRecord r) {
    if (r != null
        && !blocked.contains(r.id)
        && r.state == DungeonRecord.State.READY
        && r.altarVersion != AltarLayout.VERSION) queue(r, null, false);
  }

  boolean queue(DungeonRecord r, AltarLayout.Pos requested, boolean repair) {
    if (closed || r.state != DungeonRecord.State.READY || queued.contains(r.id)) return false;
    if (!repair && r.altarVersion == AltarLayout.VERSION) return true;
    AltarLayout.Pos selected =
        requested != null
            ? requested
            : r.altar != null ? r.altar : AltarLayout.choose(r.rooms).orElse(null);
    World world = Bukkit.getWorld(r.worldId);
    if (selected == null
        || world == null
        || selected.x() < 2
        || selected.z() < 2
        || selected.x() + 2 >= DungeonPlan.WIDTH
        || selected.z() + 2 >= DungeonPlan.DEPTH
        || r.baseY + selected.y() - 1 < world.getMinHeight()
        || r.baseY + selected.y() + 3 >= world.getMaxHeight()) return false;
    var old = r.altar;
    int oldVersion = r.altarVersion;
    r.altar = selected;
    r.altarVersion = 0;
    if (!service.saveAltarState()) {
      r.altar = old;
      r.altarVersion = oldVersion;
      return false;
    }
    Map<AltarLayout.Pos, String> layout = AltarLayout.blocks(selected);
    List<Placement> placements = new ArrayList<>();
    layout.forEach(
        (pos, data) -> placements.add(new Placement(pos, Bukkit.createBlockData(data), false)));
    if (old != null && !old.equals(selected) && !layout.containsKey(old))
      placements.add(
          new Placement(old, Bukkit.createBlockData(Material.POLISHED_BLACKSTONE_BRICKS), true));
    Map<Long, List<Placement>> groups = new LinkedHashMap<>();
    for (var placement : placements) {
      int cx = (r.x() + placement.pos.x()) >> 4, cz = (r.z() + placement.pos.z()) >> 4;
      groups.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(placement);
    }
    List<Group> parts =
        groups.entrySet().stream()
            .map(e -> new Group((int) (e.getKey() >> 32), (int) (long) e.getKey(), e.getValue()))
            .toList();
    jobs.add(new Job(r, parts, old, oldVersion));
    blocked.remove(r.id);
    queued.add(r.id);
    register(r);
    if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    return true;
  }

  private void tick() {
    if (jobs.isEmpty()) {
      stopTask();
      return;
    }
    Job job = jobs.peek();
    DungeonRecord r = job.record;
    try {
      World world = Bukkit.getWorld(r.worldId);
      if (world == null || service.get(r.id) != r || r.state != DungeonRecord.State.READY)
        throw new IllegalStateException("Dungeon derzeit nicht verfuegbar");
      Group part = job.groups.get(job.group);
      if (held == null) {
        held = world.getChunkAt(part.x, part.z);
        held.addPluginChunkTicket(plugin);
      }
      long start = System.nanoTime();
      int count = 0;
      while (job.cursor < part.blocks.size()
          && count < 128
          && (count == 0 || System.nanoTime() - start < 3_000_000)) {
        var placement = part.blocks.get(job.cursor++);
        var p = placement.pos;
        Block block = held.getBlock((r.x() + p.x()) & 15, r.baseY + p.y(), (r.z() + p.z()) & 15);
        if (!placement.oldCenter
            && (block.getType() == Material.SPAWNER
                || block.getState() instanceof InventoryHolder)) {
          release();
          r.altar = job.previous;
          r.altarVersion = job.previousVersion;
          service.saveAltarState();
          queued.remove(r.id);
          jobs.remove();
          register(r);
          blocked.add(r.id);
          plugin
              .getLogger()
              .warning(
                  "Ausgangsaltar "
                      + r.id
                      + ": Platz durch Behaelter/Spawner belegt. /mob dungeon altar "
                      + r.id
                      + " here an freiem Platz verwenden.");
          if (jobs.isEmpty()) stopTask();
          return;
        }
        if (!job.scanning && (!placement.oldCenter || block.getType() == Material.ENCHANTING_TABLE))
          block.setBlockData(placement.data, false);
        count++;
      }
      if (job.cursor == part.blocks.size()) {
        release();
        job.cursor = 0;
        if (++job.group == job.groups.size()) {
          if (job.scanning) {
            job.scanning = false;
            job.group = 0;
            return;
          }
          r.altarVersion = AltarLayout.VERSION;
          if (!service.saveAltarState()) r.altarVersion = 0;
          queued.remove(r.id);
          jobs.remove();
          register(r);
        }
      }
    } catch (RuntimeException failure) {
      release();
      queued.remove(r.id);
      jobs.remove();
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.WARNING,
              "Ausgangsaltar " + r.id + " konnte nicht gebaut werden.",
              failure);
    }
    if (jobs.isEmpty()) stopTask();
  }

  private void release() {
    if (held != null) {
      held.removePluginChunkTicket(plugin);
      held = null;
    }
  }

  private void stopTask() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    release();
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void interact(PlayerInteractEvent e) {
    Block block = e.getClickedBlock();
    if (block == null
        || (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            && e.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK)) return;
    var r = service.recordAt(block.getLocation());
    if (r == null
        || r.altar == null
        || block.getX() != r.x() + r.altar.x()
        || block.getY() != r.baseY + r.altar.y()
        || block.getZ() != r.z() + r.altar.z()) return;
    if (e.getPlayer().isSneaking() && service.canEdit(e.getPlayer(), block)) return;
    e.setCancelled(true);
    if (e.getHand() == EquipmentSlot.OFF_HAND) return;
    open(e.getPlayer(), r.id, 0);
  }

  void open(Player player, String id, int requestedPage) {
    if (!near(player, id)) {
      player.sendMessage("§cGehe naeher an den fertigen Ausgangsaltar heran.");
      return;
    }
    DungeonTravelProvider provider = Bukkit.getServicesManager().load(DungeonTravelProvider.class);
    List<DungeonTravelTarget> targets = List.of();
    if (provider != null)
      try {
        targets = List.copyOf(provider.destinations(player, id));
      } catch (RuntimeException | LinkageError failure) {
        plugin
            .getLogger()
            .log(java.util.logging.Level.WARNING, "Altar-Reiseziele nicht verfuegbar.", failure);
        provider = null;
      }
    int pages = Math.max(1, (targets.size() + 44) / 45),
        page = org.plugin.theMob.compat.Numbers.clamp(requestedPage, 0, pages - 1);
    var view = new View(this, player, id, page, provider);
    view.inventory = Bukkit.createInventory(view, 54, "Ausgangsaltar: " + id);
    for (int slot = 0; slot < 45 && page * 45 + slot < targets.size(); slot++) {
      var target = targets.get(page * 45 + slot);
      view.targets.put(slot, target.id());
      view.inventory.setItem(slot, icon(target.icon(), target.name(), target.lore()));
    }
    if (page > 0) view.inventory.setItem(45, icon(Material.ARROW, "Vorherige Seite", List.of()));
    view.inventory.setItem(
        46,
        icon(
            Material.OAK_DOOR,
            "Zum Dungeon-Eingang",
            List.of("Zum Eingang dieser Anlage reisen.")));
    view.inventory.setItem(
        47,
        icon(
            Material.COMPASS,
            "Zum Eintrittsort zurueck",
            List.of("Dungeon verlassen und zum vorherigen Ort zurueckkehren.")));
    view.inventory.setItem(
        49,
        icon(
            Material.ENCHANTING_TABLE,
            "Reiseziel waehlen",
            List.of(
                "Seite " + (page + 1) + " / " + pages,
                targets.isEmpty()
                    ? "Keine weiteren Reiseziele verfuegbar."
                    : "Home, Knotenpunkte und Stadtzentrum.",
                "Der Altar ist auch vor dem Bosskampf nutzbar.")));
    view.inventory.setItem(50, icon(Material.CLOCK, "Ziele aktualisieren", List.of()));
    view.inventory.setItem(52, icon(Material.BARRIER, "Schliessen", List.of()));
    if (page + 1 < pages)
      view.inventory.setItem(53, icon(Material.ARROW, "Naechste Seite", List.of()));
    player.openInventory(view.inventory);
  }

  private ItemStack icon(Material material, String name, List<String> lore) {
    if (!material.isItem()) material = Material.BARRIER;
    ItemStack item = new ItemStack(material);
    var meta = item.getItemMeta();
    meta.setDisplayName("§6" + name);
    meta.setLore(lore.stream().map(line -> "§7" + line).toList());
    item.setItemMeta(meta);
    return item;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void click(InventoryClickEvent e) {
    if (!(org.plugin.theMob.compat.Views.top(e.getView()).getHolder() instanceof View view)
        || view.controller != this) return;
    e.setCancelled(true);
    if (!(e.getWhoClicked() instanceof Player player) || !view.player.equals(player.getUniqueId()))
      return;
    int slot = e.getRawSlot();
    if (slot < 0 || slot >= 54) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (closed
                  || !player.isOnline()
                  || org.plugin.theMob.compat.Views.top(player.getOpenInventory())
                      != view.inventory) return;
              if (!near(player, view.dungeon)) {
                player.closeInventory();
                player.sendMessage("§cDer Ausgangsaltar ist nicht mehr in Reichweite.");
                return;
              }
              if (slot == 45 || slot == 53) {
                open(player, view.dungeon, view.page + (slot == 45 ? -1 : 1));
                return;
              }
              if (slot == 50) {
                open(player, view.dungeon, view.page);
                return;
              }
              if (slot == 52) {
                player.closeInventory();
                return;
              }
              if (slot == 46) {
                player.closeInventory();
                service.enter(player, view.dungeon);
                return;
              }
              if (slot == 47) {
                player.closeInventory();
                service.leave(player);
                return;
              }
              String target = view.targets.get(slot);
              if (target == null) return;
              if (view.provider == null
                  || view.provider
                      != Bukkit.getServicesManager().load(DungeonTravelProvider.class)) {
                player.sendMessage("§eDie Reiseziele haben sich geaendert.");
                open(player, view.dungeon, view.page);
                return;
              }
              try {
                if (!view.provider.travel(player, view.dungeon, target)
                    && near(player, view.dungeon)) open(player, view.dungeon, view.page);
              } catch (RuntimeException | LinkageError failure) {
                player.sendMessage(
                    "§c"
                        + (failure.getMessage() == null
                            ? "Dieses Reiseziel ist nicht mehr verfuegbar."
                            : failure.getMessage()));
                if (near(player, view.dungeon)) open(player, view.dungeon, view.page);
              }
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drag(InventoryDragEvent e) {
    if (org.plugin.theMob.compat.Views.top(e.getView()).getHolder() instanceof View view
        && view.controller == this) e.setCancelled(true);
  }

  @EventHandler
  public void joined(PlayerJoinEvent e) {
    ensure(service.recordAt(e.getPlayer().getLocation()));
  }

  @EventHandler
  public void loaded(ChunkLoadEvent e) {
    var ids =
        chunks.get(
            new Anchor(e.getWorld().getUID(), chunkKey(e.getChunk().getX(), e.getChunk().getZ())));
    if (ids == null) return;
    for (String id : List.copyOf(ids)) {
      var r = service.get(id);
      if (r == null
          || blocked.contains(id)
          || r.state != DungeonRecord.State.READY
          || r.altarVersion != AltarLayout.VERSION) continue;
      if (e.getChunk()
              .getBlock(
                  (r.x() + r.altar.x()) & 15, r.baseY + r.altar.y(), (r.z() + r.altar.z()) & 15)
              .getType()
          != Material.ENCHANTING_TABLE) queue(r, null, true);
    }
  }

  @Override
  public void close() {
    closed = true;
    stopTask();
    jobs.clear();
    queued.clear();
    blocked.clear();
    for (Player p : Bukkit.getOnlinePlayers()) {
      var top = org.plugin.theMob.compat.Views.top(p.getOpenInventory());
      if (top != null && top.getHolder() instanceof View v && v.controller == this)
        p.closeInventory();
    }
    HandlerList.unregisterAll(this);
  }
}
