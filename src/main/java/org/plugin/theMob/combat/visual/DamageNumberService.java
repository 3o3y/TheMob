package org.plugin.theMob.combat.visual;

import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Chooses display entities where available and marker armor stands on Minecraft 1.18/1.19. */
public final class DamageNumberService {
  private DamageNumberService() {}

  public static void spawn(Plugin plugin, Entity target, double damage, boolean crit) {
    if (valid(plugin, target)) show(plugin, target, format(damage, crit), crit, false);
  }

  public static void spawnHeal(Plugin plugin, Entity target, double amount) {
    if (valid(plugin, target) && amount / 2.0 >= .25)
      show(plugin, target, "§a+" + Math.round(amount / 2.0) + "♥", false, true);
  }

  private static boolean valid(Plugin plugin, Entity target) {
    return plugin != null && plugin.isEnabled() && target != null && target.isValid();
  }

  private static void show(Plugin plugin, Entity target, String label, boolean crit, boolean heal) {
    if (org.plugin.theMob.compat.Displays.available()) {
      org.plugin.theMob.compat.ModernDamageNumbers.spawn(plugin, target, label, crit, heal);
      return;
    }
    var location =
        target
            .getLocation()
            .add(0, heal ? target.getHeight() + .8 : Math.max(.8, target.getHeight() * .85), 0);
    ArmorStand actor = (ArmorStand) target.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
    actor.setVisible(false);
    actor.setMarker(true);
    actor.setGravity(false);
    actor.setSmall(true);
    actor.setInvulnerable(true);
    actor.setPersistent(false);
    actor.setCustomName(label);
    actor.setCustomNameVisible(true);
    actor
        .getPersistentDataContainer()
        .set(
            new org.bukkit.NamespacedKey(plugin, "temporary_visual"),
            PersistentDataType.BYTE,
            (byte) 1);
    animate(plugin, actor, heal);
  }

  public static void animate(Plugin plugin, Entity actor, boolean heal) {
    new BukkitRunnable() {
      int ticks;

      public void run() {
        if (actor.isValid() && ticks++ < (heal ? 19 : 16))
          actor.teleport(actor.getLocation().add(0, heal ? .04 : .045, 0));
        else {
          actor.remove();
          cancel();
        }
      }
    }.runTaskTimer(plugin, 1, 1);
  }

  private static String format(double damage, boolean crit) {
    String value =
        damage >= 100 ? String.valueOf(Math.round(damage)) : String.format("%.1f", damage);
    return crit ? "§6✧§e" + value + "§6✧" : "§c" + value;
  }
}
