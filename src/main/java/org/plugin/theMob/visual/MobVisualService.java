package org.plugin.theMob.visual;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.util.SkullUtil;

public final class MobVisualService {
  private static final double VIEW_DISTANCE_SQ = 576.0;

  public static void attachVisual(
      Plugin plugin, final LivingEntity mob, FileConfiguration cfg, final KeyRegistry keys) {
    if (mob != null && cfg != null) {
      String typeRaw = cfg.getString("visual.helmet.type", "").trim();
      if (!typeRaw.isEmpty()) {
        final Material mat;
        try {
          mat = Material.valueOf(typeRaw.toUpperCase());
        } catch (Exception var8) {
          return;
        }

        final boolean isHead = mat == Material.PLAYER_HEAD;
        final String texture = cfg.getString("visual.helmet.texture", "");
        if (!isHead || texture != null && !texture.isBlank()) {
          (new BukkitRunnable() {
                Entity visual;
                boolean active;
                int tick;

                public void run() {
                  if (mob.isDead()) {
                    if (this.visual != null) {
                      this.visual.remove();
                    }

                    this.cancel();
                  } else if (!mob.isValid()) {
                    this.cancel();
                    if (this.visual != null) {
                      this.visual.remove();
                      this.visual = null;
                      this.active = false;
                    }
                  } else {
                    if (this.tick++ % 5 == 0) {
                      boolean inRange =
                          mob.getWorld().getPlayers().stream()
                              .anyMatch(
                                  p ->
                                      p.getWorld() == mob.getWorld()
                                          && p.getLocation().distanceSquared(mob.getLocation())
                                              <= 576.0);
                      if (inRange && !this.active) {
                        this.active = true;
                        if (!isHead && org.plugin.theMob.compat.Displays.available()) {
                          this.visual =
                              org.plugin.theMob.compat.ModernItemVisual.spawn(mob, mat, keys);
                        } else {
                          ArmorStand as =
                              (ArmorStand)
                                  mob.getWorld()
                                      .spawnEntity(mob.getLocation(), EntityType.ARMOR_STAND);
                          as.setInvisible(true);
                          as.setMarker(true);
                          as.setGravity(false);
                          as.setSmall(true);
                          as.setHelmet(isHead ? SkullUtil.fromBase64(texture) : new ItemStack(mat));
                          as.getPersistentDataContainer()
                              .set(keys.VISUAL_HEAD, PersistentDataType.INTEGER, 1);
                          this.visual = as;
                        }
                      }

                      if (!inRange && this.active) {
                        this.active = false;
                        if (this.visual != null) {
                          this.visual.remove();
                          this.visual = null;
                        }
                      }
                    }

                    if (this.active && this.visual != null) {
                      double y = mob.getHeight() + 0.5;
                      Location t = mob.getLocation().clone().add(0.0, y, 0.0);
                      t.setYaw(mob.getLocation().getYaw());
                      t.setPitch(0.0F);
                      this.visual.teleport(t);
                    }
                  }
                }
              })
              .runTaskTimer(plugin, 1L, 1L);
        }
      }
    }
  }
}
