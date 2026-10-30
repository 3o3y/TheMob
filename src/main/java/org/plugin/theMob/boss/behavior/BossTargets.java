package org.plugin.theMob.boss.behavior;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.plugin.theMob.TheMob;

public final class BossTargets {
  private BossTargets() {}

  public static boolean combatValid(LivingEntity entity) {
    return entity instanceof Player player
        ? valid(player)
        : entity != null
            && entity.isValid()
            && !entity.isDead()
            && !entity.isInvulnerable()
            && org.plugin.theMob.core.CosmeticEntities.isPet(entity);
  }

  public static LivingEntity combatResolve(java.util.UUID id) {
    if (id == null) return null;
    Player player = Bukkit.getPlayer(id);
    if (player != null) return player;
    return Bukkit.getEntity(id) instanceof LivingEntity living ? living : null;
  }

  public static java.util.List<LivingEntity> petsNear(Location at, double radius) {
    if (at.getWorld() == null) return java.util.List.of();
    return at.getWorld().getNearbyEntities(at, radius, radius, radius).stream()
        .filter(
            e ->
                e instanceof LivingEntity living
                    && !(living instanceof Player)
                    && combatValid(living))
        .map(e -> (LivingEntity) e)
        .filter(e -> e.getLocation().distanceSquared(at) <= radius * radius)
        .toList();
  }

  public static java.util.List<LivingEntity> combatNearby(
      TheMob plugin, Location at, double radius) {
    var targets = new java.util.ArrayList<LivingEntity>(plugin.activity().nearby(at, radius, true));
    targets.addAll(petsNear(at, radius));
    return targets;
  }

  public static LivingEntity combatNearest(LivingEntity boss, double radius) {
    LivingEntity best = nearest(boss, radius);
    double distance =
        best == null ? radius * radius : best.getLocation().distanceSquared(boss.getLocation());
    for (var pet : petsNear(boss.getLocation(), radius)) {
      double candidate = pet.getLocation().distanceSquared(boss.getLocation());
      if (candidate < distance) {
        best = pet;
        distance = candidate;
      }
    }
    return best;
  }

  public static Player nearest(LivingEntity boss, double radius) {
    var plugin = Bukkit.getPluginManager().getPlugin("TheMob");
    if (plugin instanceof TheMob owner && owner.activity() != null)
      return owner.activity().nearest(boss, radius);
    Player best = null;
    double range = radius * radius;
    Location at = boss.getLocation();
    for (Player player : boss.getWorld().getPlayers()) {
      if (!valid(player)) continue;
      double distance = player.getLocation().distanceSquared(at);
      if (distance < range) {
        best = player;
        range = distance;
      }
    }
    return best;
  }

  public static boolean valid(Player player) {
    return player != null
        && player.isOnline()
        && !player.isDead()
        && !player.hasMetadata("NPC")
        && player.getMetadata("vanished").stream()
            .noneMatch(org.bukkit.metadata.MetadataValue::asBoolean)
        && player.getGameMode() != GameMode.CREATIVE
        && player.getGameMode() != GameMode.SPECTATOR;
  }
}
