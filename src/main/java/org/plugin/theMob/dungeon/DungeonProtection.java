package org.plugin.theMob.dungeon;

import java.util.List;
import java.util.function.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;

/** Protect the entire reserved footprint at every height, including during construction. */
final class DungeonProtection implements Listener {
  private final Predicate<Block> protectedBlock;
  private final BiPredicate<Player, Block> editable;

  DungeonProtection(DungeonService service) {
    this(service::protectedBlock, service::canEdit);
  }

  DungeonProtection(Predicate<Block> protectedBlock, BiPredicate<Player, Block> editable) {
    this.protectedBlock = protectedBlock;
    this.editable = editable;
  }

  private boolean blocked(Player player, Block block) {
    return protectedBlock.test(block) && !editable.test(player, block);
  }

  private boolean any(List<Block> blocks) {
    return blocks.stream().anyMatch(protectedBlock);
  }

  private boolean moved(List<Block> blocks, org.bukkit.block.BlockFace face) {
    return blocks.stream()
        .anyMatch(b -> protectedBlock.test(b) || protectedBlock.test(b.getRelative(face)));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent e) {
    if (blocked(e.getPlayer(), e.getBlock())) {
      e.setCancelled(true);
      e.getPlayer().sendMessage("§cDieser Dungeon ist geschuetzt.");
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void place(BlockPlaceEvent e) {
    if (blocked(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    if (e instanceof BlockMultiPlaceEvent multi
        && multi.getReplacedBlockStates().stream()
            .anyMatch(s -> blocked(e.getPlayer(), s.getBlock()))) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void explode(EntityExplodeEvent e) {
    e.blockList().removeIf(protectedBlock);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void explode(BlockExplodeEvent e) {
    e.blockList().removeIf(protectedBlock);
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void entity(EntityChangeBlockEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void extend(BlockPistonExtendEvent e) {
    if (protectedBlock.test(e.getBlock())
        || moved(e.getBlocks(), e.getDirection())
        || protectedBlock.test(e.getBlock().getRelative(e.getDirection()))) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void retract(BlockPistonRetractEvent e) {
    if (protectedBlock.test(e.getBlock())
        || moved(e.getBlocks(), e.getDirection())
        || moved(e.getBlocks(), e.getDirection().getOppositeFace())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void flow(BlockFromToEvent e) {
    if (protectedBlock.test(e.getBlock()) || protectedBlock.test(e.getToBlock()))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void bucket(PlayerBucketEmptyEvent e) {
    if (blocked(e.getPlayer(), e.getBlock())
        || blocked(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void bucket(PlayerBucketFillEvent e) {
    if (blocked(e.getPlayer(), e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void burn(BlockBurnEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void ignite(BlockIgniteEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void spread(BlockSpreadEvent e) {
    if (protectedBlock.test(e.getBlock()) || protectedBlock.test(e.getSource()))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fade(BlockFadeEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void form(BlockFormEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void grow(BlockGrowEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void decay(LeavesDecayEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void moisture(MoistureChangeEvent e) {
    if (protectedBlock.test(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fertilize(BlockFertilizeEvent e) {
    if (e.getBlocks().stream().anyMatch(s -> protectedBlock.test(s.getBlock())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void structure(StructureGrowEvent e) {
    if (e.getBlocks().stream().anyMatch(s -> protectedBlock.test(s.getBlock())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void portal(PortalCreateEvent e) {
    if (e.getBlocks().stream().anyMatch(s -> protectedBlock.test(s.getBlock())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void sponge(SpongeAbsorbEvent e) {
    if (protectedBlock.test(e.getBlock())
        || e.getBlocks().stream().anyMatch(s -> protectedBlock.test(s.getBlock())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void interact(PlayerInteractEvent e) {
    Block block = e.getClickedBlock();
    if (block == null || !protectedBlock.test(block)) return;
    if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL
        && block.getType() == Material.FARMLAND) e.setCancelled(true);
    else if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
        && !editable.test(e.getPlayer(), block)) {
      // Prevent axes/shovels/hoes stripping logs, scraping copper and changing ground blocks.
      Material item = e.getMaterial();
      String name = item.name();
      if (name.endsWith("_AXE")
          || name.endsWith("_HOE")
          || name.endsWith("_SHOVEL")
          || item == Material.HONEYCOMB
          || item == Material.FLINT_AND_STEEL
          || item == Material.FIRE_CHARGE
          || (block.getType() == Material.SPAWNER
              && (item == Material.EGG || name.endsWith("_SPAWN_EGG")))
          || block.getType().name().endsWith("_BED")
          || block.getType() == Material.RESPAWN_ANCHOR) e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void physical(EntityInteractEvent e) {
    if (e.getBlock().getType() == Material.FARMLAND && protectedBlock.test(e.getBlock()))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void beforeInteract(PlayerInteractEvent e) {
    interact(e);
  }

  void optional(org.bukkit.plugin.Plugin plugin) {
    org.plugin.theMob.compat.OptionalEvents.bind(
        plugin,
        this,
        "org.bukkit.event.block.TNTPrimeEvent",
        EventPriority.HIGHEST,
        true,
        event -> prime((BlockEvent) event));
  }

  public void prime(BlockEvent e) {
    if (protectedBlock.test(e.getBlock()) && e instanceof Cancellable cancellable)
      cancellable.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void dispense(BlockDispenseEvent e) {
    String name = e.getItem().getType().name();
    if (!(name.endsWith("BUCKET") || name.equals("FLINT_AND_STEEL") || name.equals("BONE_MEAL")))
      return;
    Block target = e.getBlock();
    if (target.getBlockData() instanceof org.bukkit.block.data.Directional directional)
      target = target.getRelative(directional.getFacing());
    if (protectedBlock.test(e.getBlock()) || protectedBlock.test(target)) e.setCancelled(true);
  }
}
