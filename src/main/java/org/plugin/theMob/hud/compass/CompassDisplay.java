package org.plugin.theMob.hud.compass;

import java.util.UUID;
import org.bukkit.command.CommandSender;

/** Keeps optional BetterHud and Adventure classes outside the standalone plugin lifecycle. */
public interface CompassDisplay extends AutoCloseable {
  void refresh(UUID player);

  void forget(UUID player);

  void reload(CommandSender sender);

  boolean available();

  void close();
}
