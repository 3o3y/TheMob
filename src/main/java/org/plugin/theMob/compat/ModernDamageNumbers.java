package org.plugin.theMob.compat;

import org.bukkit.Color;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.plugin.theMob.combat.visual.DamageNumberService;

/** Loaded only when TextDisplay exists (Minecraft 1.19.4+). */
public final class ModernDamageNumbers {
  private ModernDamageNumbers() {}

  public static void spawn(Plugin plugin, Entity target, String label, boolean crit, boolean heal) {
    var location =
        target
            .getLocation()
            .add(0, heal ? target.getHeight() + .8 : Math.max(.8, target.getHeight() * .85), 0);
    TextDisplay actor =
        (TextDisplay) target.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
    actor
        .getPersistentDataContainer()
        .set(
            new org.bukkit.NamespacedKey(plugin, "temporary_visual"),
            PersistentDataType.BYTE,
            (byte) 1);
    actor.setPersistent(false);
    actor.setText(label);
    actor.setBillboard(Display.Billboard.CENTER);
    actor.setShadowed(true);
    actor.setSeeThrough(true);
    actor.setLineWidth(80);
    actor.setTextOpacity((byte) -1);
    if (!heal) actor.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    float scale = heal ? 1.6f : crit ? 2.4f : 1.8f;
    actor.setTransformation(
        new Transformation(
            new Vector3f(),
            new Quaternionf(),
            new Vector3f(scale, scale, scale),
            new Quaternionf()));
    if (crit) actor.setGlowColorOverride(Color.YELLOW);
    DamageNumberService.animate(plugin, actor, heal);
  }
}
