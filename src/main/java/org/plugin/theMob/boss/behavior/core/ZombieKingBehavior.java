package org.plugin.theMob.boss.behavior.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;
import org.plugin.theMob.boss.behavior.BossTargets;

/** Four grounded tactics; 10-tick decisions and a shared 2-tick movement pass. */
public final class ZombieKingBehavior implements BossBehavior, Listener {
  public static final String METADATA = "themob_zombie_king";

  private enum Stage {
    ROYAL,
    HUNT,
    FRENZY,
    DESPERATE
  }

  private enum Motion {
    IDLE,
    WINDUP,
    LUNGE,
    WAVE,
    RECOVER,
    RETREAT
  }

  private enum Attack {
    CLEAVE,
    LUNGE,
    STOMP
  }

  private record Settings(
      Stage stage,
      long windup,
      long recovery,
      long cooldown,
      double damage,
      double range,
      double leash) {
    static Settings read(ConfigurationSection cfg) {
      Stage stage;
      try {
        stage =
            Stage.valueOf(cfg.getString("behavior.zombie-stage", "royal").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException invalid) {
        stage = Stage.ROYAL;
      }
      return new Settings(
          stage,
          cfg.getLong("behavior.windup-ticks", 30),
          cfg.getLong("behavior.recovery-ticks", 30),
          cfg.getLong("behavior.cooldown-ticks", 100),
          number(cfg, "attack-damage", 4, 0, 40),
          number(cfg, "range", 25, 4, 64),
          number(cfg, "leash-radius", 48, 8, 128));
    }
  }

  private static final class Attempt {
    final UUID target;
    EntityDamageByEntityEvent event;

    Attempt(LivingEntity target) {
      this.target = target.getUniqueId();
    }
  }

  private static final class State {
    Settings settings;
    LivingEntity owner;
    Location home, origin, last;
    Vector direction;
    UUID target;
    Motion motion = Motion.IDLE;
    Attack attack;
    long until, due, started, nextMotion;
    int cycle;
    double waveRadius;
    boolean ai, aware, suspended;
    Attempt attempt;
    final Set<UUID> hit = new HashSet<>();
  }

  private final TheMob plugin;
  private final Map<UUID, State> states = new HashMap<>();
  private final Map<UUID, State> moving = new HashMap<>();

  public ZombieKingBehavior(TheMob plugin) {
    this.plugin = plugin;
  }

  @Override
  public String id() {
    return "zombie_king";
  }

  @Override
  public void onEnter(LivingEntity boss, BossPhase phase) {
    if (phase == null || phase.cfg() == null) return;
    State state = new State();
    state.owner = boss;
    state.settings = Settings.read(phase.cfg());
    state.home = home(boss);
    state.ai = boss.hasAI();
    state.aware = !(boss instanceof Mob mob) || mob.isAware();
    state.due = now() + 40;
    states.put(boss.getUniqueId(), state);
    boss.setMetadata(METADATA, new FixedMetadataValue(plugin, true));
    boss.setAI(true);
    aware(boss, true);
    String intro = phase.cfg().getString("behavior.intro", "");
    if (!intro.isBlank())
      for (Player player :
          plugin.activity().nearby(boss.getLocation(), state.settings.range(), true))
        player
            .spigot()
            .sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', intro)));
    boss.getWorld()
        .playSound(
            boss.getLocation(),
            Sound.ENTITY_ZOMBIE_AMBIENT,
            1f,
            state.settings.stage() == Stage.DESPERATE ? .55f : .7f);
  }

  @Override
  public void onExit(LivingEntity boss, BossPhase phase) {
    State state = states.remove(boss.getUniqueId());
    moving.remove(boss.getUniqueId());
    boss.removeMetadata(METADATA, plugin);
    if (state != null && boss.isValid() && !boss.isDead()) {
      halt(boss);
      if (boss instanceof Mob mob) mob.setTarget(null);
      aware(boss, state.aware);
      boss.setAI(state.ai);
    }
  }

  @Override
  public void onSuspend(LivingEntity boss, BossPhase phase) {
    State state = states.get(boss.getUniqueId());
    if (state == null || state.suspended) return;
    reset(state, now() + 40);
    moving.remove(boss.getUniqueId());
    state.suspended = true;
    halt(boss); // Do not re-enable the AI that the cold-zone service just suspended.
  }

  /** Only moving attacks enter this pass; idle/cold bosses incur no movement work. */
  public void tickMovement() {
    if (moving.isEmpty()) return;
    for (State state : moving.values().toArray(State[]::new)) {
      if (!live(state.owner, state)) {
        moving.remove(state.owner.getUniqueId());
        if (states.get(state.owner.getUniqueId()) == state) {
          if (!state.owner.isValid() || state.owner.isDead()) onExit(state.owner, null);
          else onSuspend(state.owner, null);
        }
      } else tick(state.owner, null);
    }
  }

  @Override
  public void tick(LivingEntity boss, BossPhase phase) {
    State state = states.get(boss.getUniqueId());
    if (state == null || !boss.isValid() || boss.isDead()) return;
    if (!plugin.activity().active(boss)) {
      onSuspend(boss, phase);
      return;
    }
    long now = now();
    if (state.suspended) {
      state.suspended = false;
      reset(state, now + 40);
      moving.remove(boss.getUniqueId());
      aware(boss, true);
    }
    Location at = boss.getLocation();
    if (!inside(state, at)) {
      reset(state, now + 40);
      moving.remove(boss.getUniqueId());
      aware(boss, false);
      if (boss instanceof Mob mob) mob.setTarget(null);
      if (at.getWorld().equals(state.home.getWorld()))
        move(boss, state, state.home.toVector().subtract(at.toVector()), .18, false);
      return;
    }
    LivingEntity target = BossTargets.combatResolve(state.target);
    if (!validTarget(boss, state, target))
      target = BossTargets.combatNearest(boss, state.settings.range());
    if (!validTarget(boss, state, target)) {
      reset(state, now + 40);
      moving.remove(boss.getUniqueId());
      halt(boss);
      aware(boss, true);
      if (boss instanceof Mob mob) mob.setTarget(null);
      return;
    }
    // Once a warning starts, do not transfer the attack to a new player.
    if (state.motion != Motion.IDLE
        && state.target != null
        && !state.target.equals(target.getUniqueId())) {
      state.target = target.getUniqueId();
      recover(boss, state, now);
      return;
    }
    state.target = target.getUniqueId();
    switch (state.motion) {
      case WINDUP -> {
        halt(boss);
        if (!boss.isOnGround()
            || at.distanceSquared(state.origin) > 2.25
            || !boss.hasLineOfSight(target)) {
          recover(boss, state, now);
        } else if (now >= state.until) release(boss, state, now);
      }
      case LUNGE -> {
        if (now >= state.nextMotion) {
          state.nextMotion = now + 2;
          lunge(boss, state, now);
        }
      }
      case WAVE -> wave(boss, state, now);
      case RECOVER -> {
        halt(boss);
        if (now >= state.until) {
          state.motion = Motion.IDLE;
          aware(boss, true);
        }
      }
      case RETREAT -> {
        if (now < state.nextMotion) return;
        state.nextMotion = now + 2;
        Vector away = at.toVector().subtract(target.getLocation().toVector());
        if (now >= state.until
            || horizontalSquared(away) >= 49
            || !move(boss, state, away, .18, true)) {
          windup(boss, target, state, Attack.CLEAVE, now);
        }
      }
      case IDLE -> {
        aware(boss, true);
        if (boss instanceof Mob mob && mob.getTarget() != target) mob.setTarget(target);
        if (now < state.due || !boss.hasLineOfSight(target) || !boss.isOnGround()) return;
        double distance =
            horizontalSquared(target.getLocation().toVector().subtract(at.toVector()));
        if (Math.abs(target.getLocation().getY() - at.getY()) > 1.5) return;
        Attack attack = choose(state, distance);
        if (attack == null) return;
        if (state.settings.stage() == Stage.DESPERATE
            && plugin.abilities().enabled("bosses.zombie-retreat")) {
          state.motion = Motion.RETREAT;
          moving.put(boss.getUniqueId(), state);
          state.nextMotion = now;
          state.until = now + 40;
          aware(boss, false);
          if (boss instanceof Mob mob) mob.setTarget(null);
          boss.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_HURT, .9f, .5f);
        } else windup(boss, target, state, attack, now);
      }
    }
  }

  private Attack choose(State state, double distance) {
    Attack chosen =
        switch (state.settings.stage()) {
          case ROYAL -> distance <= 25 ? Attack.CLEAVE : null;
          case HUNT ->
              distance >= 16 && distance <= 100
                  ? Attack.LUNGE
                  : distance < 16 ? Attack.CLEAVE : null;
          case FRENZY ->
              state.cycle % 3 == 0 && distance <= 49
                  ? Attack.STOMP
                  : distance >= 16 && distance <= 100
                      ? Attack.LUNGE
                      : distance < 16 ? Attack.CLEAVE : null;
          case DESPERATE -> distance <= 49 ? Attack.CLEAVE : null;
        };
    if (chosen != null
        && !plugin.abilities().enabled("bosses.zombie-" + chosen.name().toLowerCase(Locale.ROOT))) {
      state.cycle++;
      state.due = now() + 20;
      return null;
    }
    return chosen;
  }

  private void windup(
      LivingEntity boss, LivingEntity target, State state, Attack attack, long now) {
    moving.remove(boss.getUniqueId());
    state.motion = Motion.WINDUP;
    state.attack = attack;
    state.origin = boss.getLocation().clone();
    state.direction = target.getLocation().toVector().subtract(state.origin.toVector()).setY(0);
    if (state.direction.lengthSquared() < .001)
      state.direction = state.origin.getDirection().setY(0);
    if (state.direction.lengthSquared() < .001) state.direction = new Vector(0, 0, 1);
    state.direction.normalize();
    state.until = now + org.plugin.theMob.compat.Numbers.clamp(state.settings.windup(), 20, 100);
    state.hit.clear();
    state.cycle++;
    aware(boss, false);
    if (boss instanceof Mob mob) mob.setTarget(null);
    halt(boss);
    boss.setRotation(
        (float) Math.toDegrees(Math.atan2(-state.direction.getX(), state.direction.getZ())), 0);
    warning(boss, state);
  }

  private void warning(LivingEntity boss, State state) {
    boss.getWorld()
        .playSound(
            state.origin,
            Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
            .8f,
            state.attack == Attack.STOMP ? .5f : .8f);
    if (state.attack == Attack.STOMP)
      ring(state.origin, 6, org.plugin.theMob.compat.BukkitTypes.particle("SMOKE"));
    else if (state.attack == Attack.LUNGE) {
      for (int i = 1; i <= 6; i++)
        particle(
            state.origin.clone().add(state.direction.clone().multiply(i)),
            org.plugin.theMob.compat.BukkitTypes.particle("SMOKE"));
    } else {
      double angle = Math.atan2(state.direction.getZ(), state.direction.getX());
      for (int i = 0; i <= 8; i++) {
        double theta = angle - Math.PI / 3 + i * Math.PI / 12;
        particle(
            state.origin.clone().add(Math.cos(theta) * 4, .1, Math.sin(theta) * 4),
            org.plugin.theMob.compat.BukkitTypes.particle("SMOKE"));
      }
    }
  }

  private void release(LivingEntity boss, State state, long now) {
    boss.swingMainHand();
    if (state.attack == Attack.CLEAVE) {
      boss.getWorld().playSound(state.origin, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, .8f, .65f);
      particle(
          state.origin.clone().add(state.direction.clone().multiply(2)).add(0, 1, 0),
          org.plugin.theMob.compat.BukkitTypes.particle("SWEEP_ATTACK"));
      for (LivingEntity player : BossTargets.combatNearby(plugin, state.origin, 5)) {
        Vector offset = player.getLocation().toVector().subtract(state.origin.toVector());
        double horizontal = horizontalSquared(offset);
        if (horizontal <= 16
            && Math.abs(offset.getY()) <= 1.5
            && (horizontal < .01 || offset.setY(0).normalize().dot(state.direction) >= .5))
          hit(boss, player, state, state.origin, .45);
      }
      recover(boss, state, now);
    } else {
      state.started = now;
      state.last = boss.getLocation().clone();
      state.waveRadius = 0;
      state.motion = state.attack == Attack.LUNGE ? Motion.LUNGE : Motion.WAVE;
      if (state.motion == Motion.LUNGE) {
        moving.put(boss.getUniqueId(), state);
        state.nextMotion = now + 2;
        lunge(boss, state, now);
      } else {
        boss.getWorld().playSound(state.origin, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, .5f);
        wave(boss, state, now);
      }
    }
  }

  private void lunge(LivingEntity boss, State state, long now) {
    Location at = boss.getLocation();
    if (!boss.isOnGround()
        || at.distanceSquared(state.last) > 25
        || at.distanceSquared(state.origin) > 49) {
      recover(boss, state, now);
      return;
    }
    // Test only the distance actually travelled, never the projected future path.
    for (LivingEntity player : BossTargets.combatNearby(plugin, at, 6)) {
      if (nearSegment(player.getLocation(), state.last, at, 1.3))
        hit(boss, player, state, state.origin, .6);
    }
    if (!live(boss, state)) return;
    state.last = at.clone();
    if (now - state.started >= 20 || !move(boss, state, state.direction, .30, true))
      recover(boss, state, now);
  }

  private void wave(LivingEntity boss, State state, long now) {
    halt(boss);
    if (boss.getLocation().distanceSquared(state.origin) > 2.25) {
      recover(boss, state, now);
      return;
    }
    double radius = Math.min(6, 2 * (1 + (now - state.started) / 10));
    ring(state.origin, radius, org.plugin.theMob.compat.BukkitTypes.particle("POOF"));
    for (LivingEntity player : BossTargets.combatNearby(plugin, state.origin, 7)) {
      Vector offset = player.getLocation().toVector().subtract(state.origin.toVector());
      double distance = Math.sqrt(horizontalSquared(offset));
      if ((player.isOnGround() || org.plugin.theMob.core.CosmeticEntities.isPet(player))
          && Math.abs(offset.getY()) <= .75
          && distance <= radius
          && (state.waveRadius == 0 || distance > state.waveRadius))
        hit(boss, player, state, state.origin, .45);
    }
    state.waveRadius = radius;
    if (radius >= 6) recover(boss, state, now);
  }

  private void hit(
      LivingEntity boss, LivingEntity player, State state, Location from, double knockback) {
    if (!live(boss, state)
        || !BossTargets.combatValid(player)
        || !inside(state, player.getLocation())
        || state.hit.contains(player.getUniqueId())
        || !boss.hasLineOfSight(player)) return;
    state.hit.add(player.getUniqueId());
    Attempt attempt = new Attempt(player);
    state.attempt = attempt;
    try {
      player.damage(state.settings.damage(), boss);
    } finally {
      state.attempt = null;
    }
    // Protection plugins and shields can reject a hit; then also reject its knockback.
    if (!live(boss, state)
        || attempt.event == null
        || attempt.event.isCancelled()
        || attempt.event.getFinalDamage() <= 0
        || !BossTargets.combatValid(player)
        || player.isDead()
        || !inside(state, player.getLocation())) return;
    Vector away = player.getLocation().toVector().subtract(from.toVector()).setY(0);
    if (away.lengthSquared() > .001)
      player.setVelocity(away.normalize().multiply(knockback).setY(.22));
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void gateNativeMelee(EntityDamageByEntityEvent event) {
    State state = states.get(event.getDamager().getUniqueId());
    if (state != null
        && state.motion != Motion.IDLE
        && (state.attempt == null || !state.attempt.target.equals(event.getEntity().getUniqueId())))
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void observeScriptedHit(EntityDamageByEntityEvent event) {
    State state = states.get(event.getDamager().getUniqueId());
    if (state != null
        && state.attempt != null
        && state.attempt.target.equals(event.getEntity().getUniqueId()))
      state.attempt.event = event;
  }

  private void recover(LivingEntity boss, State state, long now) {
    if (!live(boss, state)) return;
    moving.remove(boss.getUniqueId());
    state.motion = Motion.RECOVER;
    state.until = now + org.plugin.theMob.compat.Numbers.clamp(state.settings.recovery(), 20, 100);
    state.due =
        state.until + org.plugin.theMob.compat.Numbers.clamp(state.settings.cooldown(), 40, 1200);
    state.hit.clear();
    halt(boss);
    aware(boss, false);
  }

  private static void reset(State state, long due) {
    state.motion = Motion.IDLE;
    state.target = null;
    state.origin = null;
    state.hit.clear();
    state.due = due;
  }

  private boolean live(LivingEntity boss, State state) {
    return states.get(boss.getUniqueId()) == state
        && boss.isValid()
        && !boss.isDead()
        && plugin.activity().active(boss);
  }

  private boolean validTarget(LivingEntity boss, State state, LivingEntity target) {
    return BossTargets.combatValid(target)
        && inside(state, target.getLocation())
        && boss.getWorld().equals(target.getWorld())
        && boss.getLocation().distanceSquared(target.getLocation())
            <= state.settings.range() * state.settings.range();
  }

  private static boolean inside(State state, Location at) {
    return state.home.getWorld().equals(at.getWorld())
        && state.home.distanceSquared(at) <= state.settings.leash() * state.settings.leash();
  }

  private boolean move(
      LivingEntity boss, State state, Vector direction, double speed, boolean leash) {
    if (!live(boss, state)) return false;
    direction = direction.clone().setY(0);
    if (direction.lengthSquared() < .001 || !boss.isOnGround()) {
      halt(boss);
      return false;
    }
    direction.normalize();
    Vector velocity = direction.clone().multiply(speed);
    Location at = boss.getLocation();
    // Check the entire next 2-tick step, with the actual scaled body, without loading chunks.
    for (double distance = .25; distance <= speed * 2 + .25; distance += .25) {
      Location next = at.clone().add(direction.clone().multiply(distance));
      if (leash && !inside(state, next) || !clearBody(boss, next)) {
        halt(boss);
        return false;
      }
    }
    velocity.setY(Math.min(0, boss.getVelocity().getY()));
    boss.setVelocity(velocity);
    return true;
  }

  private static boolean clearBody(LivingEntity boss, Location at) {
    World world = at.getWorld();
    double width = boss.getWidth(), actualHeight = boss.getHeight();
    if (!Double.isFinite(width) || !Double.isFinite(actualHeight) || width > 4 || actualHeight > 6)
      return false;
    double half = Math.max(width / 2, .3);
    double height = Math.max(actualHeight, 1.8);
    int minX = (int) Math.floor(at.getX() - half), maxX = (int) Math.floor(at.getX() + half);
    int minZ = (int) Math.floor(at.getZ() - half), maxZ = (int) Math.floor(at.getZ() + half);
    int floor = (int) Math.floor(at.getY() - .05), top = (int) Math.floor(at.getY() + height);
    if (floor < world.getMinHeight() || top >= world.getMaxHeight()) return false;
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        var ground = world.getBlockAt(x, floor, z);
        String material = ground.getType().name();
        if (!ground.getType().isSolid()
            || material.contains("MAGMA")
            || material.contains("CAMPFIRE")) return false;
        for (int y = at.getBlockY(); y <= top; y++) {
          var block = world.getBlockAt(x, y, z);
          if (!block.isPassable() || block.isLiquid() || block.getType().name().contains("FIRE"))
            return false;
        }
      }
    return true;
  }

  private static boolean nearSegment(Location player, Location from, Location to, double radius) {
    if (Math.abs(player.getY() - to.getY()) > 1.5) return false;
    Vector segment = to.toVector().subtract(from.toVector()).setY(0);
    Vector offset = player.toVector().subtract(from.toVector()).setY(0);
    double fraction =
        segment.lengthSquared() < .001
            ? 0
            : org.plugin.theMob.compat.Numbers.clamp(
                offset.dot(segment) / segment.lengthSquared(), 0, 1);
    return offset.subtract(segment.multiply(fraction)).lengthSquared() <= radius * radius;
  }

  private Location home(LivingEntity boss) {
    Location home = boss.getLocation().clone();
    var pdc = boss.getPersistentDataContainer();
    for (String axis : new String[] {"x", "y", "z"}) {
      var key = new NamespacedKey(plugin, "boss_home_" + axis);
      double coordinate =
          switch (axis) {
            case "x" -> home.getX();
            case "y" -> home.getY();
            default -> home.getZ();
          };
      Double old = pdc.get(key, PersistentDataType.DOUBLE);
      if (old == null || !Double.isFinite(old)) pdc.set(key, PersistentDataType.DOUBLE, coordinate);
      else
        switch (axis) {
          case "x" -> home.setX(old);
          case "y" -> home.setY(old);
          default -> home.setZ(old);
        }
    }
    return home;
  }

  private static double number(
      ConfigurationSection cfg, String key, double fallback, double min, double max) {
    double value = cfg.getDouble("behavior." + key, fallback);
    return Double.isFinite(value)
        ? org.plugin.theMob.compat.Numbers.clamp(value, min, max)
        : fallback;
  }

  private long now() {
    return plugin.automation().tps().currentTick();
  }

  private static double horizontalSquared(Vector vector) {
    return vector.getX() * vector.getX() + vector.getZ() * vector.getZ();
  }

  private static void aware(LivingEntity boss, boolean aware) {
    if (boss instanceof Mob mob) mob.setAware(aware);
  }

  private static void halt(LivingEntity boss) {
    boss.setVelocity(new Vector(0, Math.min(0, boss.getVelocity().getY()), 0));
  }

  private static void particle(Location at, Particle particle) {
    if (at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4))
      at.getWorld().spawnParticle(particle, at.clone().add(0, .2, 0), 2, .08, .08, .08, 0);
  }

  private static void ring(Location at, double radius, Particle particle) {
    for (int i = 0; i < 16; i++) {
      double angle = i * Math.PI / 8;
      particle(at.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), particle);
    }
  }
}
