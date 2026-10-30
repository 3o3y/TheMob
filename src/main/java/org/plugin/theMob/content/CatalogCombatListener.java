package org.plugin.theMob.content;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;

/** Native ranged attacks receive the same level curve; scripted boss attacks are already scaled. */
public final class CatalogCombatListener implements Listener {
  private final TheMob plugin;
  private final NamespacedKey skill;

  public CatalogCombatListener(TheMob plugin) {
    this.plugin = plugin;
    skill = new NamespacedKey(plugin, "catalog_skill");
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void damage(EntityDamageByEntityEvent event) {
    if (event.isCancelled()
        || !(event.getDamager() instanceof Projectile projectile)
        || !(projectile.getShooter() instanceof LivingEntity shooter)
        || projectile.getPersistentDataContainer().has(skill, PersistentDataType.BYTE)) return;
    var config = plugin.mobs().mobConfigOf(shooter);
    if (config == null || !config.getBoolean("catalog-generated", false)) return;
    event.setDamage(
        event.getDamage() * MobCatalog.number(config, "projectile-damage-multiplier", 1, .1, 10));
  }
}
