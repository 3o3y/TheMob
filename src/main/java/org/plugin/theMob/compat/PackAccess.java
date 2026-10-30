package org.plugin.theMob.compat;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** One optional client pack on old clients; UUID-aware offers on Bukkit 1.20.3+. */
public final class PackAccess {
  private PackAccess() {}

  public record Data(UUID id, String url, String hash, String prompt) {}

  public static Object nativePack() {
    try {
      return Server.class.getMethod("getServerResourcePack").invoke(Bukkit.getServer());
    } catch (NoSuchMethodException legacy) {
      try {
        String url = (String) Server.class.getMethod("getResourcePack").invoke(Bukkit.getServer());
        if (url == null || url.isBlank()) return null;
        String hash =
            (String) Server.class.getMethod("getResourcePackHash").invoke(Bukkit.getServer());
        return new Data(
            UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)), url, hash, "");
      } catch (NoSuchMethodException unsupported) {
        return null;
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Native resource pack", failure);
      }
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Native resource pack", failure);
    }
  }

  public static Data inspect(Object pack) {
    if (pack == null) return null;
    if (pack instanceof Data data) return data;
    try {
      Class<?> type =
          Class.forName("org.bukkit.packs.ResourcePack", false, Server.class.getClassLoader());
      return new Data(
          (UUID) type.getMethod("getId").invoke(pack),
          (String) type.getMethod("getUrl").invoke(pack),
          (String) type.getMethod("getHash").invoke(pack),
          (String) type.getMethod("getPrompt").invoke(pack));
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Resource pack API", failure);
    }
  }

  public static UUID id(PlayerResourcePackStatusEvent event) {
    try {
      return (UUID) PlayerResourcePackStatusEvent.class.getMethod("getID").invoke(event);
    } catch (NoSuchMethodException older) {
      return null;
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Pack status", failure);
    }
  }

  public static void send(
      Player player, UUID id, String url, byte[] hash, String prompt, boolean required) {
    try {
      try {
        Player.class
            .getMethod(
                "addResourcePack",
                UUID.class,
                String.class,
                byte[].class,
                String.class,
                boolean.class)
            .invoke(player, id, url, hash, prompt, required);
      } catch (NoSuchMethodException older) {
        Player.class
            .getMethod("setResourcePack", String.class, byte[].class, String.class, boolean.class)
            .invoke(player, url, hash, prompt, required);
      }
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof IllegalArgumentException invalid) throw invalid;
      throw new IllegalStateException("Client pack offer", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Client pack API", failure);
    }
  }
}
