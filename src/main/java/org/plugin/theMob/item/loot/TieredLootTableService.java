package org.plugin.theMob.item.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;

public final class TieredLootTableService {
  private final List<TieredLootTable> tables = new ArrayList<>();
  private final Random random = new Random();

  public void register(TieredLootTable table) {
    this.tables.add(table);
  }

  public Material roll(String tier) {
    for (TieredLootTable table : this.tables) {
      if (table.tier().equalsIgnoreCase(tier) && this.random.nextDouble() <= table.chance()) {
        return table.material();
      }
    }

    return null;
  }
}
