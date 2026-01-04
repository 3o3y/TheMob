package org.plugin.theMob.combat;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatDebugService {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player p) {
        return p != null && enabled.contains(p.getUniqueId());
    }

    public void setEnabled(Player p, boolean on) {
        if (p == null) return;
        if (on) enabled.add(p.getUniqueId());
        else enabled.remove(p.getUniqueId());
    }

    public void send(Player p, DamageResult r) {
        if (p == null || r == null) return;

        p.sendMessage(color("&8&m--------------------------------"));
        p.sendMessage(color("&e⚔ Combat Debug"));
        for (Map.Entry<String, String> e : r.debug().entrySet()) {
            p.sendMessage(color("&7" + e.getKey() + ": &f" + e.getValue()));
        }
        p.sendMessage(color("&aFinal Damage: &f" + trim(r.finalDamage())));
        if (r.lifestealAmount() > 0) {
            p.sendMessage(color("&dLifesteal: &f+" + trim(r.lifestealAmount())));
        }
        p.sendMessage(color("&8&m--------------------------------"));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String trim(double d) {
        return (d % 1 == 0) ? String.valueOf((int) d) : String.valueOf(Math.round(d * 100.0) / 100.0);
    }
}
