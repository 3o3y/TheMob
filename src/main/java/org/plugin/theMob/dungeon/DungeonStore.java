package org.plugin.theMob.dungeon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Atomic metadata writes; reserved plots are never reused, even after cancellation or restart. */
final class DungeonStore {
  private final Path file;
  private final Map<String, DungeonRecord> records = new LinkedHashMap<>();
  private int nextSlot;

  DungeonStore(Path file) throws Exception {
    this.file = file;
    if (!Files.exists(file)) return;
    var yaml = new YamlConfiguration();
    yaml.load(file.toFile());
    if (yaml.getInt("format") != 1) throw new IOException("Unbekanntes Dungeon-Datenformat");
    nextSlot = yaml.getInt("next-slot");
    if (nextSlot < 0 || nextSlot > 100_000) throw new IOException("Ungueltige naechste Dungeon-ID");
    var section = yaml.getConfigurationSection("dungeons");
    if (section != null)
      for (String id : section.getKeys(false)) {
        String root = "dungeons." + id + ".";
        var record =
            new DungeonRecord(
                yaml.getInt(root + "slot"),
                DungeonStyle.valueOf(yaml.getString(root + "style", "")),
                yaml.getLong(root + "seed"),
                DungeonRecord.State.valueOf(yaml.getString(root + "state", "")));
        if (!id.equals(record.id) || records.values().stream().anyMatch(r -> r.slot == record.slot))
          throw new IOException("Doppelte/ungueltige Dungeon-ID: " + id);
        if (record.state == DungeonRecord.State.PLANNING
            || record.state == DungeonRecord.State.BUILDING)
          record.state = DungeonRecord.State.INTERRUPTED;
        record.levels = yaml.getInt(root + "levels");
        record.worldId = UUID.fromString(yaml.getString(root + "world-id", ""));
        record.worldName = yaml.getString(root + "world-name", "");
        record.originX = yaml.getInt(root + "x");
        record.baseY = yaml.getInt(root + "base-y");
        record.originZ = yaml.getInt(root + "z");
        record.height = yaml.getInt(root + "height");
        record.blocks = yaml.getInt(root + "blocks");
        record.pathLength = yaml.getInt(root + "path-length");
        record.completionBossId = yaml.getString(root + "completion-boss", "");
        if (yaml.contains(root + "exit-altar.x")
            && yaml.contains(root + "exit-altar.y")
            && yaml.contains(root + "exit-altar.z")) {
          record.altar =
              new AltarLayout.Pos(
                  yaml.getInt(root + "exit-altar.x"),
                  yaml.getInt(root + "exit-altar.y"),
                  yaml.getInt(root + "exit-altar.z"));
          record.altarVersion = yaml.getInt(root + "exit-altar.version");
        }
        for (String run : yaml.getStringList(root + "completed-runs"))
          record.completedRuns.add(UUID.fromString(run));
        while (record.completedRuns.size() > 256)
          record.completedRuns.remove(record.completedRuns.iterator().next());
        List<DungeonPlan.Room> rooms = new ArrayList<>();
        for (Map<?, ?> room : yaml.getMapList(root + "rooms"))
          rooms.add(
              new DungeonPlan.Room(
                  ((Number) room.get("level")).intValue(),
                  ((Number) room.get("x")).intValue(),
                  ((Number) room.get("y")).intValue(),
                  ((Number) room.get("z")).intValue(),
                  String.valueOf(room.get("name"))));
        record.rooms = List.copyOf(rooms);
        records.put(record.id, record);
        nextSlot = Math.max(nextSlot, record.slot + 1);
      }
  }

  Collection<DungeonRecord> records() {
    return Collections.unmodifiableCollection(records.values());
  }

  DungeonRecord get(String id) {
    return records.get(id.toLowerCase(Locale.ROOT));
  }

  DungeonRecord reserve(DungeonStyle style, long seed, org.bukkit.World world, int x, int y, int z)
      throws IOException {
    var record = new DungeonRecord(nextSlot++, style, seed, DungeonRecord.State.PLANNING);
    record.locate(world, x, y, z);
    records.put(record.id, record);
    save();
    return record;
  }

  void save() throws IOException {
    var yaml = new YamlConfiguration();
    yaml.set("format", 1);
    yaml.set("next-slot", nextSlot);
    for (var record : records.values()) {
      String root = "dungeons." + record.id + ".";
      yaml.set(root + "slot", record.slot);
      yaml.set(root + "style", record.style.name());
      yaml.set(root + "seed", record.seed);
      yaml.set(root + "world-id", record.worldId.toString());
      yaml.set(root + "world-name", record.worldName);
      yaml.set(root + "x", record.x());
      yaml.set(root + "base-y", record.baseY);
      yaml.set(root + "z", record.z());
      yaml.set(root + "state", record.state.name());
      yaml.set(root + "levels", record.levels);
      yaml.set(root + "height", record.height);
      yaml.set(root + "blocks", record.blocks);
      yaml.set(root + "path-length", record.pathLength);
      yaml.set(root + "completion-boss", record.completionBossId);
      if (record.altar != null) {
        yaml.set(root + "exit-altar.x", record.altar.x());
        yaml.set(root + "exit-altar.y", record.altar.y());
        yaml.set(root + "exit-altar.z", record.altar.z());
        yaml.set(root + "exit-altar.version", record.altarVersion);
      }
      yaml.set(root + "completed-runs", record.completedRuns.stream().map(UUID::toString).toList());
      yaml.set(
          root + "rooms",
          record.rooms.stream()
              .map(
                  r ->
                      Map.of(
                          "level", r.level(), "x", r.x(), "y", r.y(), "z", r.z(), "name", r.name()))
              .toList());
    }
    Files.createDirectories(file.getParent());
    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(temp, yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
    if (Files.exists(file))
      Files.copy(
          file,
          file.resolveSibling(file.getFileName() + ".bak"),
          StandardCopyOption.REPLACE_EXISTING);
    try {
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
