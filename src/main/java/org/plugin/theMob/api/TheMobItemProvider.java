package org.plugin.theMob.api;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/** Optional Bukkit service for namespaced loot/reward items. Never shade this interface. */
public interface TheMobItemProvider {
  /** Unique lowercase namespace, e.g. myitems. The native themob namespace is reserved. */
  String id();

  /** Server-thread factory; empty for unknown IDs. Caller always clones before delivery. */
  Optional<ItemStack> create(String itemId);
}
