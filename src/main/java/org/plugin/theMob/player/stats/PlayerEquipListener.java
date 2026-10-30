package org.plugin.theMob.player.stats;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Own additive modifiers preserve vanilla equipment and other plugins' modifiers. */
public final class PlayerEquipListener implements Listener {
  private final Plugin plugin;
  private final PlayerStatCache cache;
  private final Map<UUID, Map<String, Double>> previous = new HashMap<>();
  private final NamespacedKey healthCredit;

  public PlayerEquipListener(Plugin plugin, PlayerStatCache cache) {
    this.plugin = plugin;
    this.cache = cache;
    this.healthCredit = new NamespacedKey(plugin, "equipment_health_credit");
    Bukkit.getOnlinePlayers().forEach(this::attach);
    Bukkit.getScheduler()
        .runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 1, 10);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onJoin(PlayerJoinEvent e) {
    attach(e.getPlayer());
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent e) {
    Player player = e.getPlayer();
    player.getPersistentDataContainer().remove(healthCredit);
    previous.remove(player.getUniqueId());
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline() || player.isDead()) return;
              refresh(player);
            });
  }

  @EventHandler
  public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
    e.getEntity().getPersistentDataContainer().remove(healthCredit);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    remove(e.getPlayer());
  }

  private void refresh(Player player) {
    if (!player.isOnline() || player.isDead()) return;
    Map<String, Double> stats = cache.get(player);
    if (stats.equals(previous.get(player.getUniqueId()))) return;
    double health = player.getHealth();
    apply(
        player,
        org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH"),
        "bonus_health",
        stats);
    apply(player, org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR"), "armor", stats);
    apply(
        player,
        org.plugin.theMob.compat.BukkitTypes.attribute("MOVEMENT_SPEED"),
        "movement_speed",
        stats);
    apply(
        player,
        org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_SPEED"),
        "attack_speed",
        stats);
    apply(
        player,
        org.plugin.theMob.compat.BukkitTypes.attribute("KNOCKBACK_RESISTANCE"),
        "knockback_resistance",
        stats);
    apply(player, org.plugin.theMob.compat.BukkitTypes.attribute("LUCK"), "luck", stats);
    previous.put(player.getUniqueId(), Map.copyOf(stats));
    var max = org.plugin.theMob.compat.BukkitTypes.instance(player, "MAX_HEALTH");
    if (max != null) player.setHealth(Math.min(health, max.getValue()));
  }

  private void attach(Player player) {
    previous.remove(player.getUniqueId());
    cache.invalidate(player);
    refresh(player);
    if (player.isDead()) {
      player.getPersistentDataContainer().remove(healthCredit);
      return;
    }
    Double credit =
        player.getPersistentDataContainer().get(healthCredit, PersistentDataType.DOUBLE);
    player.getPersistentDataContainer().remove(healthCredit);
    var max = org.plugin.theMob.compat.BukkitTypes.instance(player, "MAX_HEALTH");
    if (credit != null && Double.isFinite(credit) && credit > 0 && max != null)
      player.setHealth(Math.min(max.getValue(), player.getHealth() + credit));
  }

  private void apply(Player player, Attribute attribute, String name, Map<String, Double> stats) {
    var instance = player.getAttribute(attribute);
    if (instance == null) return;
    var key = new NamespacedKey(plugin, "player_" + name);
    for (var modifier : new ArrayList<>(instance.getModifiers()))
      if (org.plugin.theMob.compat.Modifiers.identity(modifier).equals(key.toString()))
        instance.removeModifier(modifier);
    double value = stats.getOrDefault(name, 0.0);
    if (!Double.isFinite(value)) return;
    if (name.equals("bonus_health"))
      value = org.plugin.theMob.compat.Numbers.clamp(value, 0, 10000);
    if (name.equals("movement_speed")) value = org.plugin.theMob.compat.Numbers.clamp(value, 0, .6);
    if (name.equals("knockback_resistance"))
      value = org.plugin.theMob.compat.Numbers.clamp(value, 0, 1);
    if (value != 0) instance.addModifier(org.plugin.theMob.compat.Modifiers.additive(key, value));
  }

  private void remove(Player player) {
    double health = player.isDead() ? 0 : player.getHealth();
    for (var attribute : org.bukkit.Registry.ATTRIBUTE) {
      var instance = player.getAttribute(attribute);
      if (instance != null)
        for (var modifier : new ArrayList<>(instance.getModifiers()))
          if (org.plugin
              .theMob
              .compat
              .Modifiers
              .identity(modifier)
              .startsWith(plugin.getName().toLowerCase(Locale.ROOT) + ":player_"))
            instance.removeModifier(modifier);
    }
    var max = org.plugin.theMob.compat.BukkitTypes.instance(player, "MAX_HEALTH");
    if (!player.isDead() && max != null) {
      double retained = Math.min(health, max.getValue());
      double lost = Math.max(0, health - retained);
      Double old = player.getPersistentDataContainer().get(healthCredit, PersistentDataType.DOUBLE);
      double pending = old != null && Double.isFinite(old) ? Math.max(0, old) : 0;
      if (pending + lost > 0)
        player
            .getPersistentDataContainer()
            .set(healthCredit, PersistentDataType.DOUBLE, pending + lost);
      player.setHealth(retained);
    } else player.getPersistentDataContainer().remove(healthCredit);
    cache.invalidate(player);
    previous.remove(player.getUniqueId());
  }

  public void shutdown() {
    Bukkit.getOnlinePlayers().forEach(this::remove);
    previous.clear();
  }
}
