package org.plugin.theMob.boss;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class BossTemplateParser {
  private BossTemplateParser() {}

  public static BossTemplate tryParse(String mobId, FileConfiguration cfg) {
    if (mobId != null && cfg != null) {
      ConfigurationSection phasesSec = cfg.getConfigurationSection("phases");
      if (phasesSec != null && !phasesSec.getKeys(false).isEmpty()) {
        int arenaRadiusChunks = 1;
        ConfigurationSection arenaSec = cfg.getConfigurationSection("arena");
        if (arenaSec != null) {
          arenaRadiusChunks = arenaSec.getInt("radius-chunks", 1);
        }

        BossTemplate template = new BossTemplate(mobId.toLowerCase(Locale.ROOT), arenaRadiusChunks);

        for (String phaseId : phasesSec.getKeys(false)) {
          ConfigurationSection p = phasesSec.getConfigurationSection(phaseId);
          if (p != null) {
            String range = p.getString("hp-range");
            if (range != null) {
              String[] parts = range.split("-");
              if (parts.length == 2) {
                double max = parse(parts[0], 100.0);
                double min = parse(parts[1], 0.0);
                if (min > max) {
                  double tmp = min;
                  min = max;
                  max = tmp;
                }

                String title = p.getString("title", phaseId);
                template.addPhase(new BossPhase(phaseId, min, max, title, p), p);
              }
            }
          }
        }

        return template.hasPhases() ? template : null;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  private static double parse(String s, double def) {
    if (s == null) {
      return def;
    } else {
      try {
        return Double.parseDouble(s.trim());
      } catch (NumberFormatException var4) {
        return def;
      }
    }
  }
}
