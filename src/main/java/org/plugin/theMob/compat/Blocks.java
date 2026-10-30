package org.plugin.theMob.compat;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;

/** Geometry is unchanged; an unavailable decorative tuff-brick palette uses stone bricks. */
public final class Blocks {
  private Blocks() {}

  public static BlockData data(String specification) {
    if (specification.equals("minecraft:tuff_bricks")
        && Material.getMaterial("TUFF_BRICKS") == null)
      return Bukkit.createBlockData("minecraft:stone_bricks");
    return Bukkit.createBlockData(specification);
  }
}
