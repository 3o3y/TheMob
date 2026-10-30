package org.plugin.theMob.combat;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class CombatDebugService {
  private static final long COOLDOWN_MS = 150L;
  private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Long> lastSend = new ConcurrentHashMap<>();

  public boolean isEnabled(Player p) {
    return p != null && this.enabled.contains(p.getUniqueId());
  }

  public void setEnabled(Player p, boolean on) {
    if (p != null) {
      UUID id = p.getUniqueId();
      if (on) {
        this.enabled.add(id);
      } else {
        this.enabled.remove(id);
        this.lastSend.remove(id);
      }
    }
  }

  public void send(Player p, DamageResult r) {
    if (p != null && r != null) {
      if (this.isEnabled(p)) {
        long now = System.currentTimeMillis();
        long last = this.lastSend.getOrDefault(p.getUniqueId(), 0L);
        if (now - last >= 150L) {
          this.lastSend.put(p.getUniqueId(), now);
          p.sendMessage(this.color("&8&m--------------------------------"));
          p.sendMessage(this.color("&e⚔ Combat Debug"));

          for (Entry<String, String> e : r.debug().entrySet()) {
            p.sendMessage(this.color("&7" + e.getKey() + ": &f" + e.getValue()));
          }

          p.sendMessage(this.color("&aFinal Damage: &f" + this.trim(r.finalDamage())));
          if (r.lifestealAmount() > 0.0) {
            p.sendMessage(this.color("&dLifesteal: &f+" + this.trim(r.lifestealAmount())));
          }

          p.sendMessage(this.color("&8&m--------------------------------"));
        }
      }
    }
  }

  public void clearAll() {
    this.enabled.clear();
    this.lastSend.clear();
  }

  private String color(String s) {
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  private String trim(double d) {
    return d % 1.0 == 0.0 ? String.valueOf((int) d) : String.format("%.2f", d);
  }
}
