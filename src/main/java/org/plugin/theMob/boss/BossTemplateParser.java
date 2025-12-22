package org.plugin.theMob.boss;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class BossTemplateParser {

    private BossTemplateParser() {}
    public static BossTemplate tryParse(String mobId, FileConfiguration cfg) {
        if (cfg == null) return null;
        ConfigurationSection phasesSec = cfg.getConfigurationSection("phases");
        if (phasesSec == null || phasesSec.getKeys(false).isEmpty()) {
            return null;
        }
        BossTemplate template = new BossTemplate(mobId.toLowerCase());
        for (String phaseId : phasesSec.getKeys(false)) {
            ConfigurationSection p = phasesSec.getConfigurationSection(phaseId);
            if (p == null) continue;
            String range = p.getString("hp-range");
            if (range == null || !range.contains("-")) continue;
            String[] parts = range.split("-");
            if (parts.length != 2) continue;
            double max = parse(parts[0], 100.0);
            double min = parse(parts[1], 0.0);
            if (min > max) {
                double tmp = min; min = max; max = tmp;
            }
            String title = p.getString("title", phaseId);
            template.addPhase(new BossPhase(
                    phaseId,
                    min,
                    max,
                    title,
                    p
            ));
        }
        return template.hasPhases() ? template : null;
    }
    private static double parse(String s, double def) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return def; }
    }
}
