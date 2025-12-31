package org.plugin.theMob.visual;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.util.SkullUtil;

public final class MobVisualService {

    private static final double VIEW_DISTANCE_SQ = 24 * 24;

    public static void attachVisual(
            Plugin plugin,
            LivingEntity mob,
            FileConfiguration cfg,
            KeyRegistry keys
    ) {
        if (mob == null || cfg == null) return;

        String typeRaw = cfg.getString("visual.helmet.type", "").trim();
        if (typeRaw.isEmpty()) return;

        Material mat;
        try {
            mat = Material.valueOf(typeRaw.toUpperCase());
        } catch (Exception e) {
            return;
        }

        boolean isHead = mat == Material.PLAYER_HEAD;
        String texture = cfg.getString("visual.helmet.texture", "");

        if (isHead && (texture == null || texture.isBlank())) return;

        new BukkitRunnable() {

            Entity visual;
            float yaw;
            boolean active;
            int tick;

            @Override
            public void run() {

                // ❌ Mob endgültig tot → Task beenden
                if (mob.isDead()) {
                    if (visual != null) visual.remove();
                    cancel();
                    return;
                }

                // ⚠ Chunk unloaded → Mob temporär invalid → warten, NICHT canceln
                if (!mob.isValid()) {
                    if (visual != null) {
                        visual.remove();
                        visual = null;
                        active = false;
                    }
                    return;
                }

                if ((tick++ % 5) == 0) {
                    boolean inRange = mob.getWorld().getPlayers().stream()
                            .anyMatch(p ->
                                    p.getWorld() == mob.getWorld()
                                            && p.getLocation().distanceSquared(mob.getLocation()) <= VIEW_DISTANCE_SQ
                            );

                    if (inRange && !active) {
                        active = true;

                        if (!isHead) {
                            ItemDisplay d = (ItemDisplay) mob.getWorld().spawnEntity(
                                    mob.getLocation(),
                                    EntityType.ITEM_DISPLAY
                            );
                            d.setItemStack(new ItemStack(mat));
                            d.getPersistentDataContainer().set(
                                    keys.VISUAL_HEAD,
                                    PersistentDataType.INTEGER,
                                    1
                            );
                            visual = d;
                        } else {
                            ArmorStand as = (ArmorStand) mob.getWorld().spawnEntity(
                                    mob.getLocation(),
                                    EntityType.ARMOR_STAND
                            );
                            as.setInvisible(true);
                            as.setMarker(true);
                            as.setGravity(false);
                            as.setSmall(true);
                            as.setHelmet(SkullUtil.fromBase64(texture));
                            as.getPersistentDataContainer().set(
                                    keys.VISUAL_HEAD,
                                    PersistentDataType.INTEGER,
                                    1
                            );
                            visual = as;
                        }
                    }

                    if (!inRange && active) {
                        active = false;
                        if (visual != null) {
                            visual.remove();
                            visual = null;
                        }
                    }
                }

                if (!active || visual == null) return;

                double y = mob.getHeight() + 0.5;
                Location t = mob.getLocation().clone().add(0, y, 0);

                yaw = (yaw + 4f) % 360f;
                t.setYaw(yaw);

                visual.teleport(t);
            }

        }.runTaskTimer(plugin, 1L, 1L);
    }
}
