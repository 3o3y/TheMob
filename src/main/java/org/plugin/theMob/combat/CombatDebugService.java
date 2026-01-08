package org.plugin.theMob.combat;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatDebugService {

    private static final long COOLDOWN_MS = 150; // anti-spam

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastSend = new ConcurrentHashMap<>();

    // =====================================================
    // STATE
    // =====================================================

    public boolean isEnabled(Player p) {
        return p != null && enabled.contains(p.getUniqueId());
    }

    public void setEnabled(Player p, boolean on) {
        if (p == null) return;

        UUID id = p.getUniqueId();
        if (on) {
            enabled.add(id);
        } else {
            enabled.remove(id);
            lastSend.remove(id);
        }
    }

    // =====================================================
    // SEND
    // =====================================================

    public void send(Player p, DamageResult r) {
        if (p == null || r == null) return;
        if (!isEnabled(p)) return;

        long now = System.currentTimeMillis();
        long last = lastSend.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < COOLDOWN_MS) return;
        lastSend.put(p.getUniqueId(), now);

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

    // =====================================================
    // CLEANUP (RELOAD / QUIT)
    // =====================================================

    public void clearAll() {
        enabled.clear();
        lastSend.clear();
    }

    // =====================================================
    // UTIL
    // =====================================================

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String trim(double d) {
        if (d % 1 == 0) return String.valueOf((int) d);
        return String.format("%.2f", d);
    }
}
