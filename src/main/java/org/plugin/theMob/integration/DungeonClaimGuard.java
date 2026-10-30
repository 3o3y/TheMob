package org.plugin.theMob.integration;

import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.TheMobDungeonPreCreateEvent;

/** Queries complete footprints before any reservation or terrain mutation; never loads chunks. */
final class DungeonClaimGuard implements Listener {
  private final TheMob plugin;
  private final IntegrationManager integrations;
  private final Set<String> protectedProviders;
  private final boolean protectPk;

  DungeonClaimGuard(TheMob plugin, IntegrationManager integrations) {
    this.plugin = plugin;
    this.integrations = integrations;
    protectPk = plugin.getConfig().getBoolean("integrations.pkplugin.protect-dungeons", true);
    protectedProviders =
        Set.copyOf(
            List.of("worldguard", "griefprevention", "towny", "lands").stream()
                .filter(
                    id ->
                        plugin.getConfig().getBoolean("integrations." + id + ".enabled", true)
                            && plugin
                                .getConfig()
                                .getBoolean("integrations." + id + ".protect-dungeons", true))
                .toList());
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void beforeCreate(TheMobDungeonPreCreateEvent event) {
    for (String id : List.of("worldguard", "griefprevention", "towny", "lands")) {
      var target = IntegrationCatalog.find(id).orElseThrow();
      if (!protectedProviders.contains(id)) continue;
      Plugin provider =
          target.pluginNames().stream()
              .map(Bukkit.getPluginManager()::getPlugin)
              .filter(Objects::nonNull)
              .filter(Plugin::isEnabled)
              .findFirst()
              .orElse(null);
      if (provider == null) continue;
      if (integrations.statuses().stream()
          .anyMatch(
              s ->
                  s.plugin().equals(target.name())
                      && s.state() == IntegrationStatus.State.INCOMPATIBLE)) {
        reject(event, target.name() + "-Anbindung nicht kompatibel; Schutzpruefung nicht umgehen");
        return;
      }
      try {
        if (blocked(id, provider, event.getRegion())) {
          reject(event, "Baufeld ueberschneidet ein geschuetztes Gebiet von " + target.name());
          return;
        }
      } catch (Exception | LinkageError failure) {
        integrations.failed(target.name(), failure);
        reject(event, "Schutzpruefung von " + target.name() + " fehlgeschlagen. Bau blockiert.");
        return;
      }
    }
    Plugin pk = Bukkit.getPluginManager().getPlugin("PKPlugin");
    if (protectPk && pk != null && pk.isEnabled())
      try {
        Object territories = Reflect.call(pk, "territories");
        for (Object marker : (Collection<?>) Reflect.call(territories, "all")) {
          UUID world = (UUID) Reflect.call(marker, "world");
          double x = ((Number) Reflect.call(marker, "centerX")).doubleValue(),
              z = ((Number) Reflect.call(marker, "centerZ")).doubleValue(),
              radius = ((Number) Reflect.call(marker, "radius")).doubleValue();
          if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(radius) || radius < 0)
            throw new IllegalStateException("Ungueltige PK-Markierung");
          if (circleOverlaps(event.getRegion(), world, x, z, radius)) {
            reject(event, "Baufeld ueberschneidet ein PK-Bannergebiet");
            return;
          }
        }
      } catch (Exception | LinkageError failure) {
        plugin
            .getLogger()
            .warning("PK-Dungeonbaupruefung fehlgeschlagen: " + failure.getClass().getSimpleName());
        reject(event, "PK-Schutzpruefung fehlgeschlagen. Bau blockiert.");
      }
  }

  static boolean circleOverlaps(
      DungeonRegion region, UUID world, double x, double z, double radius) {
    double dx = x - org.plugin.theMob.compat.Numbers.clamp(x, region.minX(), region.maxX()),
        dz = z - org.plugin.theMob.compat.Numbers.clamp(z, region.minZ(), region.maxZ());
    return world.equals(region.worldId()) && dx * dx + dz * dz <= radius * radius + 1.0e-8;
  }

  private void reject(TheMobDungeonPreCreateEvent event, String reason) {
    event.setCancelled(true);
    event.getSender().sendMessage("§c" + reason);
  }

  static boolean overlaps(DungeonRegion region, Location first, Location second) {
    return first != null
        && second != null
        && first.getWorld() != null
        && first.getWorld().getUID().equals(region.worldId())
        && region.minX() <= Math.max(first.getBlockX(), second.getBlockX())
        && region.maxX() > Math.min(first.getBlockX(), second.getBlockX())
        && region.minZ() <= Math.max(first.getBlockZ(), second.getBlockZ())
        && region.maxZ() > Math.min(first.getBlockZ(), second.getBlockZ());
  }

  boolean blocked(String id, Plugin provider, DungeonRegion region) throws Exception {
    World world = Objects.requireNonNull(Bukkit.getWorld(region.worldId()));
    switch (id) {
      case "griefprevention" -> {
        Object store = provider.getClass().getField("dataStore").get(provider);
        Collection<?> claims;
        try {
          var getter = store.getClass().getMethod("getClaims", int.class, int.class);
          var candidates = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
          for (int x = region.minX() >> 4; x <= (region.maxX() - 1) >> 4; x++)
            for (int z = region.minZ() >> 4; z <= (region.maxZ() - 1) >> 4; z++)
              candidates.addAll((Collection<?>) getter.invoke(store, x, z));
          claims = candidates;
        } catch (NoSuchMethodException legacy) {
          claims = (Collection<?>) Reflect.call(store, "getClaims");
        }
        for (Object claim : claims)
          if (overlaps(
              region,
              (Location) Reflect.call(claim, "getLesserBoundaryCorner"),
              (Location) Reflect.call(claim, "getGreaterBoundaryCorner"))) return true;
      }
      case "towny" -> {
        Object api =
            Reflect.call(
                Reflect.type(provider, "com.palmergames.bukkit.towny.TownyAPI"), "getInstance");
        int size =
            ((Number)
                    Reflect.call(
                        Reflect.type(provider, "com.palmergames.bukkit.towny.TownySettings"),
                        "getTownBlockSize"))
                .intValue();
        if (size < 1 || size > 1024) throw new IllegalStateException("Ungueltige Townblockgroesse");
        for (int x = Math.floorDiv(region.minX(), size);
            x <= Math.floorDiv(region.maxX() - 1, size);
            x++)
          for (int z = Math.floorDiv(region.minZ(), size);
              z <= Math.floorDiv(region.maxZ() - 1, size);
              z++)
            if (Reflect.call(
                    api,
                    "getTownBlock",
                    new Location(world, x * size, world.getMinHeight(), z * size))
                != null) return true;
      }
      case "lands" -> {
        Object api =
            Reflect.call(
                Reflect.type(provider, "me.angeschossen.lands.api.LandsIntegration"), "of", plugin);
        for (int x = region.minX() >> 4; x <= (region.maxX() - 1) >> 4; x++)
          for (int z = region.minZ() >> 4; z <= (region.maxZ() - 1) >> 4; z++)
            if (Reflect.call(api, "getLandByUnloadedChunk", world, x, z) != null) return true;
      }
      case "worldguard" -> {
        Object api =
            Reflect.call(Reflect.type(provider, "com.sk89q.worldguard.WorldGuard"), "getInstance");
        Object container = Reflect.call(Reflect.call(api, "getPlatform"), "getRegionContainer");
        Object wrapped =
            Reflect.call(
                Reflect.type(provider, "com.sk89q.worldedit.bukkit.BukkitAdapter"), "adapt", world);
        Object manager = Reflect.call(container, "get", wrapped);
        if (manager == null) return false;
        Class<?> vector = Reflect.type(provider, "com.sk89q.worldedit.math.BlockVector3");
        Object min = Reflect.call(vector, "at", region.minX(), world.getMinHeight(), region.minZ());
        Object max =
            Reflect.call(
                vector, "at", region.maxX() - 1, world.getMaxHeight() - 1, region.maxZ() - 1);
        Object query =
            Reflect.type(provider, "com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion")
                .getConstructor(String.class, vector, vector)
                .newInstance("themob_build_query", min, max);
        Object result = Reflect.call(manager, "getApplicableRegions", query);
        for (Object protectedRegion : (Collection<?>) Reflect.call(result, "getRegions"))
          if (!String.valueOf(Reflect.call(protectedRegion, "getId")).equals("__global__"))
            return true;
      }
      default -> throw new IllegalArgumentException(id);
    }
    return false;
  }
}
