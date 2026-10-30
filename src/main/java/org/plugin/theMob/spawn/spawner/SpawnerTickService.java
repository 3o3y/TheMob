package org.plugin.theMob.spawn.spawner;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.TileState;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.KeyRegistry;

public final class SpawnerTickService implements Listener {
  private record Position(UUID world, int x, int y, int z) {}

  private final Set<Position> positions = new HashSet<>();
  private final TheMob plugin;
  private final SpawnerService service;
  private final KeyRegistry keys;

  public SpawnerTickService(TheMob plugin, SpawnerService service, KeyRegistry keys) {
    this.plugin = plugin;
    this.service = service;
    this.keys = keys;
  }

  public void start() {
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
    for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) index(chunk);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
  }

  private void index(Chunk chunk) {
    for (var state : chunk.getTileEntities())
      if (state instanceof TileState tile
          && tile.getPersistentDataContainer().has(keys.THEMOB_SPAWNER, PersistentDataType.BYTE))
        positions.add(
            new Position(chunk.getWorld().getUID(), tile.getX(), tile.getY(), tile.getZ()));
  }

  @EventHandler
  public void onLoad(ChunkLoadEvent e) {
    index(e.getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onUse(PlayerInteractEvent e) {
    var block = e.getClickedBlock();
    if (block == null || block.getType() != Material.SPAWNER) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (block.getChunk().isLoaded()) index(block.getChunk());
            });
  }

  // The egg linker cancels its event; indexing must therefore also see cancelled interactions.
  @EventHandler(priority = EventPriority.MONITOR)
  public void onLinked(PlayerInteractEvent e) {
    var block = e.getClickedBlock();
    if (block == null || block.getType() != Material.SPAWNER) return;
    if (block.getState() instanceof TileState tile
        && tile.getPersistentDataContainer().has(keys.THEMOB_SPAWNER, PersistentDataType.BYTE))
      positions.add(
          new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
  }

  private void tick() {
    var iterator = positions.iterator();
    while (iterator.hasNext()) {
      var pos = iterator.next();
      World world = Bukkit.getWorld(pos.world());
      if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
        iterator.remove();
        continue;
      }
      if (!(world.getBlockAt(pos.x(), pos.y(), pos.z()).getState() instanceof TileState tile)
          || !tile.getPersistentDataContainer().has(keys.THEMOB_SPAWNER, PersistentDataType.BYTE)) {
        iterator.remove();
        continue;
      }
      service.trySpawn(tile);
    }
  }
}
