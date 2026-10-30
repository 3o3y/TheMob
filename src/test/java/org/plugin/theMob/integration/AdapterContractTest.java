package org.plugin.theMob.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

/** Local contract fixtures, not live tests of the third-party plugins. */
class AdapterContractTest {
  ServerMock server;
  TheMob plugin;
  LivingEntity mob;
  Player killer;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    plugin = MockBukkit.load(TheMob.class);
    server.getScheduler().performTicks(1);
    mob = plugin.api().spawn("pig_lvl_1", new Location(world, .5, 64, .5)).orElseThrow();
    killer = server.addPlayer();
  }

  @AfterEach
  void teardown() {
    MockBukkit.unmock();
  }

  @Test
  void dynamicEventsWithSharedHandlerListsAreDispatchedOnlyToTheirOwnAdapter() throws Exception {
    var provider = MockBukkit.createMockPlugin("ContractProvider");
    var payments = new java.util.concurrent.atomic.AtomicInteger();
    var experience = new java.util.concurrent.atomic.AtomicInteger();
    Reflect.call(
        plugin.integrations(),
        "bind",
        provider,
        Payment.class.getName(),
        EventPriority.NORMAL,
        (java.util.function.Consumer<Event>) event -> payments.incrementAndGet());
    Reflect.call(
        plugin.integrations(),
        "bind",
        provider,
        Experience.class.getName(),
        EventPriority.NORMAL,
        (java.util.function.Consumer<Event>) event -> experience.incrementAndGet());
    server.getPluginManager().callEvent(new Payment(mob));
    assertEquals(1, payments.get());
    assertEquals(0, experience.get());
    server.getPluginManager().callEvent(new Experience());
    assertEquals(1, payments.get());
    assertEquals(1, experience.get());
  }

  public static class Experience extends Event {
    public HandlerList getHandlers() {
      return Payment.HANDLERS;
    }

    public static HandlerList getHandlerList() {
      return Payment.HANDLERS;
    }
  }

  @SuppressWarnings("unchecked")
  void activeJobs() throws Exception {
    var field = IntegrationManager.class.getDeclaredField("states");
    field.setAccessible(true);
    ((Map<String, IntegrationStatus>) field.get(plugin.integrations()))
        .put(
            "Jobs",
            new IntegrationStatus("Jobs", IntegrationStatus.State.ACTIVE, "Contract fixture"));
  }

  @Test
  void jobsScalesNativeMoneyPointsAndExperienceExactlyOnce() throws Exception {
    activeJobs();
    var config = plugin.mobs().mobConfigOf(mob);
    config.set("integrations.jobs.money-multiplier", 2);
    config.set("integrations.jobs.points-multiplier", 3);
    config.set("integrations.jobs.experience-multiplier", 4);
    var payment = new Payment(mob);
    Reflect.call(plugin.integrations(), "jobsMoney", payment);
    assertEquals(20, payment.money);
    assertEquals(15, payment.points);
    assertEquals(2, payment.exp);
    Reflect.call(plugin.integrations(), "jobsExperience", payment);
    assertEquals(8, payment.exp);
    assertFalse(payment.cancelled);
  }

  @Test
  void jobsDefaultsPreservePayoutAndMinionsAreBlocked() throws Exception {
    activeJobs();
    var payment = new Payment(mob);
    Reflect.call(plugin.integrations(), "jobsMoney", payment);
    assertEquals(10, payment.money);
    assertEquals(5, payment.points);
    mob.getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "minion"), PersistentDataType.BYTE, (byte) 1);
    Reflect.call(plugin.integrations(), "jobsMoney", payment);
    assertTrue(payment.cancelled);
  }

  @Test
  void jobsIgnoresVanillaMobsAndClampsInvalidConfiguration() throws Exception {
    activeJobs();
    var vanilla = (LivingEntity) mob.getWorld().spawnEntity(mob.getLocation(), EntityType.ZOMBIE);
    var ignored = new Payment(vanilla);
    Reflect.call(plugin.integrations(), "jobsMoney", ignored);
    assertEquals(10, ignored.money);
    plugin.mobs().mobConfigOf(mob).set("integrations.jobs.money-multiplier", Double.NaN);
    plugin.mobs().mobConfigOf(mob).set("integrations.jobs.points-multiplier", -20);
    var payment = new Payment(mob);
    Reflect.call(plugin.integrations(), "jobsMoney", payment);
    assertEquals(10, payment.money);
    assertEquals(0, payment.points);
  }

  @Test
  void questFiltersSupportListsWildcardsAndCase() {
    assertTrue(QuestsHook.matches("*", "pig_lvl_1"));
    assertTrue(QuestsHook.matches("other, PIG_LVL_1", "pig_lvl_1"));
    assertFalse(QuestsHook.matches("pig_lvl_10", "pig_lvl_1"));
  }

  @Test
  void questsUpdatesAllMatchingActiveQuestsAndRemovesOwnObjective() throws Exception {
    var provider = mock(Plugin.class, withSettings().extraInterfaces(QuestAccess.class));
    var access = (QuestAccess) provider;
    var list = new ArrayList<Object>();
    var quester = new Quester();
    quester.active.put("first", 0);
    quester.active.put("second", 0);
    when(access.getCustomObjectives()).thenReturn(list);
    when(access.getQuester(killer.getUniqueId())).thenReturn(quester);
    Objective.counts.clear();
    Objective.filter = "pig_lvl_1";
    Objective.bossOnly = false;
    try (var hook = new QuestsHook(provider, Objective.class)) {
      assertEquals(1, list.size());
      assertEquals("TheMob", Reflect.call(list.getFirst(), "getModuleName"));
      hook.kill(new TheMobDeathEvent(mob, plugin.api().mob(mob).orElseThrow(), killer));
      assertEquals(Map.of("first", 1, "second", 1), Objective.counts);
      Objective.bossOnly = true;
      hook.kill(new TheMobDeathEvent(mob, plugin.api().mob(mob).orElseThrow(), killer));
      assertEquals(Map.of("first", 1, "second", 1), Objective.counts);
    }
    assertTrue(list.isEmpty());
  }

  @Test
  void battlePassBindsProtectedApiAndPassesMobRootAndBossType() throws Exception {
    var provider = mock(Plugin.class, withSettings().extraInterfaces(PassAccess.class));
    var registry = new RegistryFixture();
    when(((PassAccess) provider).getActionRegistry()).thenReturn(registry);
    var hook = new BattlePassHook(plugin, provider, ExternalContainer.class);
    // Isolate the basic action contract from the independently tested configurable profiles.
    var profiles = IntegrationManager.class.getDeclaredField("battlePassObjectives");
    profiles.setAccessible(true);
    profiles.set(plugin.integrations(), new BattlePassObjectives(List.of(), List.of()));
    assertNotNull(registry.factory);
    var container = (ExternalContainer) registry.factory.apply(plugin);
    assertEquals("themob", container.namespace);
    hook.kill(new TheMobDeathEvent(mob, plugin.api().mob(mob).orElseThrow(), killer));
    assertEquals(List.of("kill:pig_lvl_1:1"), container.executed);
    var snapshot =
        new MobSnapshot(
            mob.getUniqueId(),
            "zombie_boss_template",
            "Boss",
            true,
            false,
            "phase2",
            SpawnReason.API);
    hook.kill(new TheMobDeathEvent(mob, snapshot, killer));
    assertEquals(
        List.of(
            "kill:pig_lvl_1:1", "kill:zombie_boss_template:1", "boss_kill:zombie_boss_template:1"),
        container.executed);
    hook.kill(new TheMobDeathEvent(mob, snapshot, null));
    assertEquals(3, container.executed.size());
  }

  public static final class Payment extends Event implements Cancellable {
    static final HandlerList HANDLERS = new HandlerList();
    LivingEntity entity;
    double money = 10, points = 5, exp = 2;
    boolean cancelled;

    Payment(LivingEntity entity) {
      this.entity = entity;
    }

    public LivingEntity getLivingEntity() {
      return entity;
    }

    public double getAmount() {
      return money;
    }

    public void setAmount(double value) {
      money = value;
    }

    public double getPoints() {
      return points;
    }

    public void setPoints(double value) {
      points = value;
    }

    public double getExp() {
      return exp;
    }

    public void setExp(double value) {
      exp = value;
    }

    public boolean isCancelled() {
      return cancelled;
    }

    public void setCancelled(boolean value) {
      cancelled = value;
    }

    public HandlerList getHandlers() {
      return HANDLERS;
    }

    public static HandlerList getHandlerList() {
      return HANDLERS;
    }
  }

  public interface QuestAccess {
    Collection<Object> getCustomObjectives();

    Quester getQuester(UUID id);
  }

  public static class Quester {
    final Map<Object, Object> active = new LinkedHashMap<>();

    public Map<Object, Object> getCurrentQuests() {
      return active;
    }
  }

  public static class Objective {
    static Map<String, Integer> counts = new HashMap<>();
    static String filter;
    static boolean bossOnly;

    public Objective() {}

    public String getModuleName() {
      return "base";
    }

    public void setName(String v) {}

    public void setAuthor(String v) {}

    public void setItem(String v, short s) {}

    public void setShowCount(boolean v) {}

    public void setCountPrompt(String v) {}

    public void setDisplay(String v) {}

    public void addStringPrompt(String k, String d, String value) {}

    public Map<String, Object> getDataForPlayer(UUID id, Objective o, Object quest) {
      return Map.of("Mob ID", filter, "Boss only", String.valueOf(bossOnly));
    }

    public void incrementObjective(UUID id, Objective o, Object quest, int amount) {
      counts.merge((String) quest, amount, Integer::sum);
    }
  }

  public interface PassAccess {
    RegistryFixture getActionRegistry();
  }

  public static class RegistryFixture {
    Function<JavaPlugin, ?> factory;

    public void quest(Function<JavaPlugin, ?> factory) {
      this.factory = factory;
    }
  }

  public abstract static class ExternalContainer {
    final String namespace;
    final List<String> executed = new ArrayList<>();

    protected ExternalContainer(JavaPlugin plugin, String namespace) {
      this.namespace = namespace;
    }

    protected Builder executionBuilder(String type) {
      return new Builder(this, type);
    }
  }

  public static class Builder {
    ExternalContainer owner;
    String type, root;
    Player player;
    int count;

    Builder(ExternalContainer owner, String type) {
      this.owner = owner;
      this.type = type;
    }

    public Builder player(Player v) {
      player = v;
      return this;
    }

    public Builder root(String v) {
      root = v;
      return this;
    }

    public Builder progressSingle() {
      count = 1;
      return this;
    }

    public Builder subRoot(String key, String value) {
      return this;
    }

    public Builder subRoot(String key, java.util.function.Predicate<Object> value) {
      return this;
    }

    public void buildAndExecute() {
      assertNotNull(player);
      owner.executed.add(type + ":" + root + ":" + count);
    }
  }
}
