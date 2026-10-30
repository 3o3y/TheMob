package org.plugin.theMob.hud;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.plugin.theMob.mob.MobManager;

public final class MobRadarResolver {
  private final MobManager mobs;
  private final boolean enabled, ignoreBats, ignoreVillagers, ignoreNamed;
  private final double radius;
  private int horizontal = 1, vertical = 1;
  private final Map<RadarType, Integer> priorities = new EnumMap<>(RadarType.class);

  private record ChunkKey(UUID world, int x, int z) {}

  private final Map<ChunkKey, List<LivingEntity>> frame = new HashMap<>();
  private boolean caching;

  public void beginFrame() {
    frame.clear();
    caching = true;
  }

  public void endFrame() {
    caching = false;
    frame.clear();
  }

  private List<LivingEntity> candidates(World world, int x, int z) {
    if (!world.isChunkLoaded(x, z)) return List.of();
    ChunkKey key = new ChunkKey(world.getUID(), x, z);
    if (caching && frame.containsKey(key)) return frame.get(key);
    var entities =
        Arrays.stream(world.getChunkAt(x, z).getEntities())
            .filter(entity -> entity instanceof LivingEntity && !(entity instanceof Player))
            .map(entity -> (LivingEntity) entity)
            .toList();
    if (caching) frame.put(key, entities);
    return entities;
  }

  public MobRadarResolver(
      MobManager mobs,
      boolean enabled,
      double radius,
      boolean ignoreBats,
      boolean ignoreVillagers,
      boolean ignoreNamed) {
    this.mobs = mobs;
    this.enabled = enabled;
    this.radius =
        Double.isFinite(radius) ? org.plugin.theMob.compat.Numbers.clamp(radius, 1, 128) : 20;
    this.ignoreBats = ignoreBats;
    this.ignoreVillagers = ignoreVillagers;
    this.ignoreNamed = ignoreNamed;
    for (var type : RadarType.values()) priorities.put(type, type.prio);
  }

  public void configure(ConfigurationSection config) {
    if (config == null) return;
    horizontal =
        org.plugin.theMob.compat.Numbers.clamp(config.getInt("scan-chunks.horizontal", 1), 0, 8);
    vertical =
        org.plugin.theMob.compat.Numbers.clamp(config.getInt("scan-chunks.vertical", 1), 0, 8);
    priorities.put(RadarType.PLUGIN, config.getInt("priority.plugin-mobs", 1));
    priorities.put(RadarType.AGGRESSIVE, config.getInt("priority.aggressive", 2));
    priorities.put(RadarType.NEUTRAL, config.getInt("priority.neutral", 3));
    priorities.put(RadarType.PASSIVE, config.getInt("priority.passive", 4));
  }

  public double getRadius() {
    return radius;
  }

  public RadarTarget find(Player player) {
    if (!enabled) return null;
    World world = player.getWorld();
    Location location = player.getLocation();
    int cx = location.getBlockX() >> 4, cz = location.getBlockZ() >> 4;
    RadarTarget best = null;
    for (int dx = -horizontal; dx <= horizontal; dx++)
      for (int dz = -horizontal; dz <= horizontal; dz++) {
        // Calling getChunkAt first would synchronously load every scanned chunk.
        for (LivingEntity living : candidates(world, cx + dx, cz + dz)) {
          if (!living.isValid()
              || living.isDead()
              || org.plugin.theMob.core.CosmeticEntities.isVisual(living)) continue;
          if (ignoreNamed && living.getCustomName() != null
              || ignoreVillagers && living instanceof Villager
              || ignoreBats && living instanceof Bat) continue;
          if (Math.abs(living.getLocation().getY() - location.getY()) > (vertical + 1) * 16)
            continue;
          double distance = location.distanceSquared(living.getLocation());
          if (distance > radius * radius) continue;
          RadarTarget candidate = classify(living, Math.sqrt(distance));
          if (candidate == null) continue;
          if (best == null
              || priorities.get(candidate.type()) < priorities.get(best.type())
              || priorities.get(candidate.type()).equals(priorities.get(best.type()))
                  && candidate.distance() < best.distance()) best = candidate;
        }
      }
    return best;
  }

  private RadarTarget classify(LivingEntity entity, double distance) {
    if (mobs.isCustomMob(entity)) return new RadarTarget(entity, RadarType.PLUGIN, distance);
    if (entity instanceof Enderman || entity instanceof Bee || entity instanceof IronGolem)
      return new RadarTarget(entity, RadarType.NEUTRAL, distance);
    if (entity instanceof Monster || entity instanceof Slime || entity instanceof Phantom)
      return new RadarTarget(entity, RadarType.AGGRESSIVE, distance);
    return entity instanceof Animals ? new RadarTarget(entity, RadarType.PASSIVE, distance) : null;
  }

  public record RadarTarget(LivingEntity entity, RadarType type, double distance) {}

  public enum RadarType {
    PLUGIN(1),
    AGGRESSIVE(2),
    NEUTRAL(3),
    PASSIVE(4);
    public final int prio;

    RadarType(int prio) {
      this.prio = prio;
    }
  }
}
