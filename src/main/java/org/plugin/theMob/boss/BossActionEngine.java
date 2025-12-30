package org.plugin.theMob.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BossActionEngine implements Listener {

    private final TheMob plugin;
    private final Random rnd = new Random();

    // Players currently under "boss weather station" effects
    private final Set<UUID> affectedPlayers = ConcurrentHashMap.newKeySet();

    // 🔴 ACTIVE WEATHER STATION (one active boss arena at a time)
    private LivingEntity activeBoss;
    private double activeRadius = 0.0;
    private String activeWeather; // CLEAR / RAIN / THUNDER
    private String activeTime;    // DAY / NIGHT

    // ✅ Boss snapshots so phase buffs don't stack forever + we can restore base values
    private final Map<UUID, BossSnapshot> bossSnapshots = new ConcurrentHashMap<>();

    // Optional failsafe tick (re-apply / reset) so it works even without movement
    private BukkitRunnable stationTask;

    public BossActionEngine(TheMob plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        stationTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickWeatherStation();
                cleanupSnapshots();
            }
        };
        stationTask.runTaskTimer(plugin, 20L, 20L); // every 1s
    }

    // =====================================================
    // GETTERS
    // =====================================================
    public LivingEntity getActiveBoss() {
        return activeBoss;
    }

    public double getActiveBossRadius() {
        return activeRadius;
    }

    // =====================================================
    // PHASE ENTER
    // =====================================================
    public void onPhaseEnter(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || !boss.isValid()) return;

        // Ensure snapshot exists (prevents stacking attributes across phases)
        ensureSnapshot(boss);

        ConfigurationSection cfg = phase.cfg();
        if (cfg == null) return;

        applyAttributes(boss, cfg.getConfigurationSection("buffs"));
        applyAbilities(boss, cfg.getConfigurationSection("abilities"));
        applyEffects(boss, cfg.getConfigurationSection("effects"));
        applyPhysics(boss, cfg.getConfigurationSection("physics"));

        // Boss = weather station (updates station config per phase)
        applyWorldStationConfig(boss, cfg.getConfigurationSection("world"));

        ConfigurationSection onEnter = cfg.getConfigurationSection("on-enter");
        if (onEnter != null) runOnEnterEffects(boss, onEnter);

        ConfigurationSection actions = cfg.getConfigurationSection("actions");
        if (actions != null) {
            ConfigurationSection summon = actions.getConfigurationSection("summon-minions");
            if (summon != null && summon.getBoolean("enabled")) {
                runSummonMinionsOnce(boss, phase, summon);
            }
        }
    }

    // =====================================================
    // PHASE LEAVE
    // =====================================================
    public void onPhaseLeave(LivingEntity boss, BossPhase phase) {
        if (boss == null || !boss.isValid()) return;

        // ✅ WICHTIG:
        // Phase leave darf den Boss NICHT "zum Vanilla Mob" resetten.
        // Sonst sieht es so aus, als würde er seinen Boss-Status verlieren.
        //
        // Wir machen hier absichtlich KEIN:
        // - resetAttributes()
        // - boss.setAI(true) / gravity true / silent false etc.
        // - clearWeatherStation()
        //
        // Cleanup passiert nur:
        // - beim nächsten onPhaseEnter (überschreibt die Werte sauber)
        // - bei onBossDeath()/shutdown() (harte Rücksetzung der Spieler-Wetter/Time)

        // Optional: Wenn du willst, kannst du hier "phasenspezifische" Effekte entfernen,
        // aber nur gezielt (nicht alles pauschal).
        // Beispiel: remove fire resistance wenn Phase es gesetzt hat und du es nicht carry-over willst.
        // -> aktuell lassen wir Effekte bewusst bestehen, bis nächste Phase sie überschreibt.
    }

    // =====================================================
    // BOSS DEATH / DESPAWN
    // =====================================================
    public void onBossDeath(LivingEntity boss) {
        // PhaseController ruft das bei Boss-Tod / cleanup auf
        clearWeatherStation();

        if (boss != null) {
            bossSnapshots.remove(boss.getUniqueId());
        }
    }

    // =====================================================
    // WEATHER STATION CONFIG (player-local only)
    // =====================================================
    private void applyWorldStationConfig(LivingEntity boss, ConfigurationSection cfg) {
        if (boss == null || !boss.isValid()) return;

        if (cfg == null) {
            // Phase hat keinen world-block: Station deaktivieren
            // (Spieler werden zurückgesetzt)
            clearWeatherStation();
            return;
        }

        this.activeBoss = boss;
        this.activeRadius = Math.max(0.0, cfg.getDouble("radius", 32.0));
        this.activeWeather = cfg.getString("weather");
        this.activeTime = cfg.getString("time");

        // Apply to players currently inside / reset outside
        for (Player p : boss.getWorld().getPlayers()) {
            if (isInsideStation(p)) {
                applyToPlayer(p);
            } else {
                if (affectedPlayers.contains(p.getUniqueId())) resetPlayer(p);
            }
        }
    }

    private boolean isInsideStation(Player p) {
        if (p == null) return false;
        LivingEntity boss = activeBoss;
        if (boss == null || !boss.isValid()) return false;
        if (!Objects.equals(p.getWorld(), boss.getWorld())) return false;

        double r = activeRadius;
        if (r <= 0) return false;

        return p.getLocation().distanceSquared(boss.getLocation()) <= (r * r);
    }

    private void applyToPlayer(Player p) {
        if (p == null) return;

        if (activeWeather != null) {
            String w = activeWeather.trim().toUpperCase(Locale.ROOT);
            switch (w) {
                case "CLEAR" -> p.setPlayerWeather(WeatherType.CLEAR);
                case "RAIN", "THUNDER" -> p.setPlayerWeather(WeatherType.DOWNFALL);
                default -> { /* ignore */ }
            }
        }

        if (activeTime != null) {
            String t = activeTime.trim().toUpperCase(Locale.ROOT);
            switch (t) {
                case "DAY" -> p.setPlayerTime(1000L, false);
                case "NIGHT" -> p.setPlayerTime(13000L, false);
                default -> { /* ignore */ }
            }
        }

        affectedPlayers.add(p.getUniqueId());
    }

    private void resetPlayer(Player p) {
        if (p == null) return;
        p.resetPlayerWeather();
        p.resetPlayerTime();
        affectedPlayers.remove(p.getUniqueId());
    }

    private void resetAllPlayers() {
        for (UUID id : new HashSet<>(affectedPlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) resetPlayer(p);
        }
        affectedPlayers.clear();
    }

    private void clearWeatherStation() {
        resetAllPlayers();
        activeBoss = null;
        activeRadius = 0.0;
        activeWeather = null;
        activeTime = null;
    }

    // =====================================================
    // FAILSAFE TICK
    // =====================================================
    private void tickWeatherStation() {
        LivingEntity boss = activeBoss;
        if (boss == null) return;

        if (!boss.isValid()) {
            clearWeatherStation();
            return;
        }

        World w = boss.getWorld();
        for (Player p : w.getPlayers()) {
            UUID id = p.getUniqueId();
            boolean inside = isInsideStation(p);

            if (inside) {
                if (!affectedPlayers.contains(id)) applyToPlayer(p);
            } else {
                if (affectedPlayers.contains(id)) resetPlayer(p);
            }
        }

        for (UUID id : new HashSet<>(affectedPlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) affectedPlayers.remove(id);
        }
    }

    // =====================================================
    // EVENTS
    // =====================================================
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        resetPlayer(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        resetPlayer(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (isInsideStation(p)) applyToPlayer(p);
        else resetPlayer(p);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (activeBoss == null) return;

        // Avoid heavy checks when player hasn't changed block
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        boolean inside = isInsideStation(p);
        if (inside) {
            if (!affectedPlayers.contains(id)) applyToPlayer(p);
        } else {
            if (affectedPlayers.contains(id)) resetPlayer(p);
        }
    }

    // =====================================================
    // SNAPSHOT (prevents stacking & keeps boss "bossy")
    // =====================================================
    private void ensureSnapshot(LivingEntity boss) {
        UUID id = boss.getUniqueId();
        bossSnapshots.computeIfAbsent(id, k -> BossSnapshot.capture(boss));
    }

    private void cleanupSnapshots() {
        // Remove snapshots for entities that no longer exist
        for (UUID id : new HashSet<>(bossSnapshots.keySet())) {
            LivingEntity le = findLivingEntity(id);
            if (le == null || !le.isValid()) {
                bossSnapshots.remove(id);
            }
        }
    }

    private LivingEntity findLivingEntity(UUID uuid) {
        for (World w : Bukkit.getWorlds()) {
            org.bukkit.entity.Entity e = w.getEntity(uuid);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    // =====================================================
    // ATTRIBUTES / EFFECTS
    // =====================================================
    private void applyAttributes(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        BossSnapshot snap = bossSnapshots.get(boss.getUniqueId());
        if (snap == null) {
            // fallback (should not happen)
            applyAttributeAdditive(boss, Attribute.MOVEMENT_SPEED, cfg.getDouble("movement-speed", 0));
            applyAttributeAdditive(boss, Attribute.ATTACK_DAMAGE, cfg.getDouble("damage", 0));
            applyAttributeAdditive(boss, Attribute.ARMOR, cfg.getDouble("armor", 0));
            applyAttributeAdditive(boss, Attribute.ARMOR_TOUGHNESS, cfg.getDouble("armor-toughness", 0));
            applyAttributeAdditive(boss, Attribute.KNOCKBACK_RESISTANCE, cfg.getDouble("knockback-resistance", 0));
            return;
        }

        // ✅ Set relative to original base values (NO stacking)
        setAttribute(boss, Attribute.MOVEMENT_SPEED, snap.base(Attribute.MOVEMENT_SPEED) + cfg.getDouble("movement-speed", 0));
        setAttribute(boss, Attribute.ATTACK_DAMAGE, snap.base(Attribute.ATTACK_DAMAGE) + cfg.getDouble("damage", 0));
        setAttribute(boss, Attribute.ARMOR, snap.base(Attribute.ARMOR) + cfg.getDouble("armor", 0));
        setAttribute(boss, Attribute.ARMOR_TOUGHNESS, snap.base(Attribute.ARMOR_TOUGHNESS) + cfg.getDouble("armor-toughness", 0));
        setAttribute(boss, Attribute.KNOCKBACK_RESISTANCE, snap.base(Attribute.KNOCKBACK_RESISTANCE) + cfg.getDouble("knockback-resistance", 0));
    }

    private void applyAttributeAdditive(LivingEntity e, Attribute attr, double add) {
        if (add == 0) return;
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(inst.getBaseValue() + add);
    }

    private void setAttribute(LivingEntity e, Attribute attr, double value) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private void applyAbilities(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        // ✅ WICHTIG: wir setzen hier nur, was YAML sagt.
        // Kein globaler Reset bei Phase-Wechsel.
        boss.setAI(!cfg.getBoolean("no-ai", false));
        boss.setSilent(cfg.getBoolean("silent", false));
        boss.setInvulnerable(cfg.getBoolean("invulnerable", false));
        boss.setGlowing(cfg.getBoolean("glowing", false));
        boss.setInvisible(cfg.getBoolean("invisibility", false));
        boss.setGravity(cfg.getBoolean("gravity", true));
        boss.setPersistent(true);
    }

    private void applyEffects(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        if (cfg.getBoolean("fire-resistance", false)) {
            boss.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE,
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false
            ));
        }
    }

    private void clearPotionEffects(LivingEntity e) {
        e.getActivePotionEffects().forEach(pe -> e.removePotionEffect(pe.getType()));
    }

    private void applyPhysics(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg != null) boss.setGravity(cfg.getBoolean("gravity", true));
    }

    // =====================================================
    // MINIONS
    // =====================================================
    private void runSummonMinionsOnce(
            LivingEntity boss,
            BossPhase phase,
            ConfigurationSection cfg
    ) {
        // 🔒 Prevent re-spawn for this phase
        NamespacedKey key = new NamespacedKey(plugin, "minions_" + phase.id());
        if (boss.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) {
            return;
        }
        boss.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);

        int amount = cfg.getInt("amount", 1);
        double radius = cfg.getDouble("radius", 5.0);

        // cooldown == lifetime (seconds)
        int lifetimeSeconds = cfg.getInt("cooldown", 15);

        // ✅ SAFE enum parsing
        String typeRaw = cfg.getString("type", "ZOMBIE");
        EntityType type;
        try {
            type = EntityType.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(
                    "[TheMob] Invalid minion type: " + typeRaw + " – fallback to ZOMBIE"
            );
            type = EntityType.ZOMBIE;
        }

        // ✅ OPTIONAL NAME
        String name = cfg.getString("name");

        for (int i = 0; i < amount; i++) {
            Location loc = boss.getLocation().clone().add(
                    (rnd.nextDouble() * 2 - 1) * radius,
                    0,
                    (rnd.nextDouble() * 2 - 1) * radius
            );

            LivingEntity minion = (LivingEntity) boss.getWorld().spawnEntity(loc, type);
            minion.setPersistent(true);
            minion.setRemoveWhenFarAway(false);

            // ✅ Name from YAML
            if (name != null && !name.isEmpty()) {
                minion.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
                minion.setCustomNameVisible(true);
            }

            // ❌ Prevent drops
            minion.getPersistentDataContainer().set(
                    plugin.keys().NO_DROPS,
                    PersistentDataType.INTEGER,
                    1
            );

            // ⏱ Lifetime = cooldown
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (minion.isValid()) {
                        minion.remove();
                    }
                }
            }.runTaskLater(plugin, lifetimeSeconds * 20L);
        }
    }


    // =====================================================
    // ON-ENTER EFFECTS
    // =====================================================
    private void runOnEnterEffects(LivingEntity boss, ConfigurationSection onEnter) {
        ConfigurationSection effects = onEnter.getConfigurationSection("effects");
        if (effects == null) return;

        Location loc = boss.getLocation().clone().add(0, 1, 0);

        ConfigurationSection particles = effects.getConfigurationSection("particles");
        if (particles != null) {
            try {
                Particle type = Particle.valueOf(particles.getString("type", "FLAME").toUpperCase(Locale.ROOT));
                boss.getWorld().spawnParticle(type, loc,
                        particles.getInt("amount", 20),
                        particles.getDouble("radius", 1),
                        particles.getDouble("height", 1),
                        particles.getDouble("radius", 1),
                        0.02
                );
            } catch (Exception ignored) {}
        }

        ConfigurationSection sound = effects.getConfigurationSection("sound");
        if (sound != null) {
            try {
                boss.getWorld().playSound(
                        boss.getLocation(),
                        Sound.valueOf(sound.getString("type")),
                        (float) sound.getDouble("volume", 1),
                        (float) sound.getDouble("pitch", 1)
                );
            } catch (Exception ignored) {}
        }
    }

    // =====================================================
    // HARD RESET (reload / restart safe)
    // =====================================================
    public void shutdown() {
        clearWeatherStation();

        if (stationTask != null) {
            stationTask.cancel();
            stationTask = null;
        }

        bossSnapshots.clear();
        activeBoss = null;
    }

    // =====================================================
    // SNAPSHOT RECORD
    // =====================================================
    private record BossSnapshot(
            Map<Attribute, Double> bases
    ) {

        static BossSnapshot capture(LivingEntity e) {
            Map<Attribute, Double> map = new HashMap<>();

            for (Attribute a : Attribute.values()) {
                AttributeInstance inst = e.getAttribute(a);
                if (inst != null) {
                    map.put(a, inst.getBaseValue());
                }
            }
            return new BossSnapshot(map);
        }

        double base(Attribute a) {
            return bases.getOrDefault(a, 0.0);
        }
    }

}
