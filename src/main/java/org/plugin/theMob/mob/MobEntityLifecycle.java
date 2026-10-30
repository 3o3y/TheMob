package org.plugin.theMob.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.visual.MobVisualService;

/** Extends the existing hard-reset policy to entities in previously unloaded chunks. */
public final class MobEntityLifecycle implements Listener {
  private final TheMob plugin;
  private final MobManager mobs;

  public MobEntityLifecycle(TheMob plugin, MobManager mobs) {
    this.plugin = plugin;
    this.mobs = mobs;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onLoad(EntitiesLoadEvent event) {
    for (var entity : event.getEntities()) {
      var pdc = entity.getPersistentDataContainer();
      if (pdc.has(plugin.keys().VISUAL_HEAD, PersistentDataType.INTEGER)
          || pdc.has(new NamespacedKey(plugin, "temporary_visual"), PersistentDataType.BYTE)) {
        entity.remove();
        continue;
      }
      if (!(entity instanceof LivingEntity living)) continue;
      boolean owned =
          mobs.isCustomMob(living)
              || pdc.has(new NamespacedKey(plugin, "minion"), PersistentDataType.BYTE);
      if (!owned) continue;
      String session =
          pdc.get(new NamespacedKey(plugin, "spawn_session"), PersistentDataType.STRING);
      if (!plugin.runtimeSession().equals(session)) {
        entity.remove();
        continue;
      }
      var auto = mobs.getAutoSpawnManager();
      if (auto != null && !auto.currentEpoch(living)) {
        mobs.removeWithoutRewards(living);
        continue;
      }
      mobs.restoreLoaded(living);
      var cfg = mobs.mobConfigOf(living);
      if (cfg != null && cfg.contains("visual.helmet.type"))
        MobVisualService.attachVisual(plugin, living, cfg, plugin.keys());
    }
  }

  @EventHandler
  public void onUnload(EntitiesUnloadEvent event) {
    for (var entity : event.getEntities()) {
      if (entity instanceof LivingEntity living
          && mobs.isBoss(living)
          && plugin.bossPhases() != null) plugin.bossPhases().onBossDeath(living);
      mobs.untrack(entity.getUniqueId());
    }
  }
}
