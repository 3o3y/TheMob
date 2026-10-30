package org.plugin.theMob.combat;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class MobMultiplierService {
  private final NamespacedKey mobIdKeyPlugin;
  private final NamespacedKey mobIdKeyLegacy;
  private final NamespacedKey mobIdKeyTheMobNs;

  public MobMultiplierService(Plugin plugin) {
    this.mobIdKeyPlugin = new NamespacedKey(plugin, "mob_id");
    this.mobIdKeyLegacy = new NamespacedKey(plugin, "themob_mob_id");
    this.mobIdKeyTheMobNs = new NamespacedKey("themob", "mob_id");
  }

  public String resolveMobId(LivingEntity e) {
    if (e == null) {
      return null;
    } else {
      PersistentDataContainer pdc = e.getPersistentDataContainer();
      String id = this.readString(pdc, this.mobIdKeyPlugin);
      if (id == null) {
        id = this.readString(pdc, this.mobIdKeyLegacy);
      }

      if (id == null) {
        id = this.readString(pdc, this.mobIdKeyTheMobNs);
      }

      if (id == null) {
        return null;
      } else {
        id = id.trim();
        return id.isEmpty() ? null : id.toLowerCase(Locale.ROOT);
      }
    }
  }

  public double multiplierFor(
      LivingEntity target, ConfigurationSection combatCfg, boolean requireTheMobId) {
    if (target != null && combatCfg != null) {
      String mobId = this.resolveMobId(target);
      if (requireTheMobId && mobId == null) {
        return 1.0;
      } else {
        ConfigurationSection mobSection = combatCfg.getConfigurationSection("mob_multipliers");
        if (mobSection != null && mobId != null) {
          double v = mobSection.getDouble(mobId, Double.NaN);
          if (!Double.isNaN(v)) {
            return this.clamp(v);
          }
        }

        ConfigurationSection typeSection =
            combatCfg.getConfigurationSection("entitytype_multipliers");
        if (typeSection != null) {
          String type = target.getType().name().toLowerCase(Locale.ROOT);
          double v = typeSection.getDouble(type, Double.NaN);
          if (!Double.isNaN(v)) {
            return this.clamp(v);
          }
        }

        return 1.0;
      }
    } else {
      return 1.0;
    }
  }

  public double multiplierFor(LivingEntity target, ConfigurationSection combatCfg) {
    return this.multiplierFor(target, combatCfg, false);
  }

  private String readString(PersistentDataContainer pdc, NamespacedKey key) {
    if (pdc == null || key == null) {
      return null;
    } else {
      return !pdc.has(key, PersistentDataType.STRING)
          ? null
          : (String) pdc.get(key, PersistentDataType.STRING);
    }
  }

  private double clamp(double v) {
    if (v < 0.05) {
      return 0.05;
    } else {
      return v > 50.0 ? 50.0 : v;
    }
  }
}
