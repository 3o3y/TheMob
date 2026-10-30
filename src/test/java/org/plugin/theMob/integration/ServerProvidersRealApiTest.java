package org.plugin.theMob.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

/**
 * Real provider API classes, isolated from production; no external databases or servers started.
 */
class ServerProvidersRealApiTest {
  ServerMock server;
  TheMob plugin;
  Player player;
  LivingEntity mob;
  URLClassLoader loader;
  ServerPluginAdapters adapters;
  final Map<String, Plugin> providers = new HashMap<>();

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("world");
    world.getBlockAt(0, 63, 0).setType(Material.STONE);
    plugin = MockBukkit.load(TheMob.class);
    player = server.addPlayer();
    mob = plugin.api().spawn("pig_lvl_1", new Location(world, .5, 64, .5)).orElseThrow();
    var urls = new ArrayList<java.net.URL>();
    for (String name :
        List.of("luckpermsApi", "auraSkillsApi", "advancedCratesJar", "economyShopJar"))
      urls.add(Path.of(System.getProperty("provider.test." + name)).toUri().toURL());
    loader = new URLClassLoader(urls.toArray(java.net.URL[]::new), getClass().getClassLoader());
    adapters = new ServerPluginAdapters(plugin, plugin.integrations());
  }

  @AfterEach
  void close() throws Exception {
    try {
      if (adapters != null) adapters.close();
      MockBukkit.unmock();
    } finally {
      if (loader != null) loader.close();
    }
  }

  Class<?> type(String name) throws Exception {
    return Class.forName(name, true, loader);
  }

  Object proxy(String name, InvocationHandler handler) throws Exception {
    return Proxy.newProxyInstance(
        loader,
        new Class<?>[] {type(name)},
        (self, method, args) -> {
          if (method.getDeclaringClass() == Object.class)
            return switch (method.getName()) {
              case "hashCode" -> System.identityHashCode(self);
              case "equals" -> self == args[0];
              default -> name;
            };
          return handler.invoke(self, method, args);
        });
  }

  Plugin provider(String id) throws Exception {
    Plugin p =
        (Plugin)
            Proxy.newProxyInstance(
                loader,
                new Class<?>[] {Plugin.class},
                (self, m, a) ->
                    switch (m.getName()) {
                      case "isEnabled" -> true;
                      case "getName" -> id;
                      case "hashCode" -> System.identityHashCode(self);
                      case "equals" -> self == a[0];
                      default -> null;
                    });
    field("providers", adapters, Map.class).put(id, p);
    providers.put(id, p);
    var target = IntegrationCatalog.find(id).orElseThrow();
    plugin.integrations().state(target.name(), IntegrationStatus.State.ACTIVE, "native API test");
    return p;
  }

  @SuppressWarnings("unchecked")
  <T> T field(String name, Object target, Class<T> expected) throws Exception {
    var f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return (T) f.get(target);
  }

  void initialize(String id) throws Exception {
    var m = ServerPluginAdapters.class.getDeclaredMethod("initialize", String.class, Plugin.class);
    m.setAccessible(true);
    m.invoke(adapters, id, provider(id));
  }

  void registerStatic(String owner, String method, String api, Object value) throws Exception {
    var m = type(owner).getDeclaredMethod(method, type(api));
    m.setAccessible(true);
    m.invoke(null, value);
  }

  Object emptyApiReturn(Method method) {
    if (method.getReturnType() == boolean.class) return false;
    if (method.getReturnType() == int.class) return 0;
    if (method.getReturnType() == double.class) return 0.0;
    return null;
  }

  TheMobDeathEvent death() {
    return new TheMobDeathEvent(mob, plugin.api().mob(mob).orElseThrow(), player);
  }

  @Test
  void luckPerms55RegistersNativeCalculatorAndUnregistersWithoutWorldAccessInCallback()
      throws Exception {
    var registered = new ArrayList<Object>();
    var removed = new ArrayList<Object>();
    Object empty =
        proxy("net.luckperms.api.context.ImmutableContextSet", (s, m, a) -> emptyApiReturn(m));
    Object factory =
        proxy(
            "net.luckperms.api.context.ContextSetFactory",
            (s, m, a) -> m.getName().equals("immutableEmpty") ? empty : null);
    Object manager =
        proxy(
            "net.luckperms.api.context.ContextManager",
            (s, m, a) ->
                switch (m.getName()) {
                  case "registerCalculator" -> {
                    registered.add(a[0]);
                    yield null;
                  }
                  case "unregisterCalculator" -> {
                    removed.add(a[0]);
                    yield null;
                  }
                  case "getContextSetFactory" -> factory;
                  default -> emptyApiReturn(m);
                });
    Object api =
        proxy(
            "net.luckperms.api.LuckPerms",
            (s, m, a) -> m.getName().equals("getContextManager") ? manager : emptyApiReturn(m));
    registerStatic(
        "net.luckperms.api.LuckPermsProvider", "register", "net.luckperms.api.LuckPerms", api);
    initialize("luckperms");
    assertEquals(1, registered.size());
    field("contexts", adapters, Map.class).put(player.getUniqueId(), "mine_1");
    var values = new ArrayList<String>();
    Object consumer =
        proxy(
            "net.luckperms.api.context.ContextConsumer",
            (s, m, a) -> {
              if (m.getName().equals("accept")) values.add(a[0] + "=" + a[1]);
              return null;
            });
    Class<?> calculator = type("net.luckperms.api.context.ContextCalculator");
    calculator
        .getMethod("calculate", Object.class, type("net.luckperms.api.context.ContextConsumer"))
        .invoke(registered.getFirst(), player, consumer);
    assertEquals(List.of("themob:dungeon=mine_1"), values);
    assertSame(
        empty, calculator.getMethod("estimatePotentialContexts").invoke(registered.getFirst()));
    adapters.close();
    assertEquals(registered, removed);
  }

  @Test
  void auraSkills224AwardsOnlyExplicitBonusThroughNativeUserApi() throws Exception {
    var calls = new ArrayList<List<Object>>();
    Object user =
        proxy(
            "dev.aurelium.auraskills.api.user.SkillsUser",
            (s, m, a) -> {
              if (m.getName().equals("addSkillXp")) calls.add(List.of(a));
              return emptyApiReturn(m);
            });
    Object api =
        proxy(
            "dev.aurelium.auraskills.api.AuraSkillsApi",
            (s, m, a) -> m.getName().equals("getUser") ? user : emptyApiReturn(m));
    registerStatic(
        "dev.aurelium.auraskills.api.AuraSkillsProvider",
        "register",
        "dev.aurelium.auraskills.api.AuraSkillsApi",
        api);
    initialize("auraskills");
    adapters.kill(death());
    assertTrue(calls.isEmpty());
    plugin.mobs().mobConfigById("pig_lvl_1").set("integrations.auraskills.bonus-xp", 2.5);
    adapters.kill(death());
    assertEquals(1, calls.size());
    assertEquals("FIGHTING", ((Enum<?>) calls.getFirst().getFirst()).name());
    assertEquals(2.5, calls.getFirst().get(1));
    plugin.mobs().mobConfigById("pig_lvl_1").set("integrations.auraskills.bonus-xp", Double.NaN);
    adapters.kill(death());
    assertEquals(1, calls.size());
  }

  @Test
  void advancedCrates474PublicFactoryDescriptorsMatchExactly() throws Exception {
    Class<?> main = Class.forName("me.PM2.AdvancedCrates.MonthlyCrate", false, loader),
        handler = type("me.PM2.AdvancedCrates.cratesHandler.CrateHandler"),
        crate = type("me.PM2.AdvancedCrates.cratesHandler.Crate"),
        builder = type("me.PM2.AdvancedCrates.rewards.SaveableItemBuilder");
    var lookup = MethodHandles.publicLookup();
    assertNotNull(lookup.findStatic(main, "getCratesHandler", MethodType.methodType(handler)));
    assertNotNull(
        lookup.findVirtual(handler, "getCrate", MethodType.methodType(crate, String.class)));
    assertNotNull(lookup.findVirtual(crate, "getKeyBuilder", MethodType.methodType(builder)));
    assertNotNull(
        lookup.findVirtual(builder, "getItemStack", MethodType.methodType(ItemStack.class)));
  }

  @Test
  void advancedCratesNativeKeyBuilderRetainsExactMetadata() throws Exception {
    var key = new ItemStack(Material.TRIPWIRE_HOOK);
    var meta = key.getItemMeta();
    meta.setDisplayName("§6Native key");
    meta.setCustomModelData(235);
    key.setItemMeta(meta);
    Class<?> builder = type("me.PM2.AdvancedCrates.rewards.SaveableItemBuilder");
    Object nativeBuilder = builder.getConstructor(ItemStack.class).newInstance(key);
    assertEquals(
        key,
        NarrowApi.call(builder, nativeBuilder, "getItemStack", ItemStack.class, new Class<?>[0]));
  }

  Object shell(Class<?> type) throws Exception {
    Class<?> u = Class.forName("sun.misc.Unsafe");
    var f = u.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return u.getMethod("allocateInstance", Class.class).invoke(f.get(null), type);
  }

  void set(Object target, String name, Object value) throws Exception {
    var f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  Event transaction(String typeName, ItemStack template) throws Exception {
    Class<?> main = type("me.gypopo.economyshopgui.EconomyShopGUI");
    MethodHandles.privateLookupIn(main, MethodHandles.lookup())
        .findStaticVarHandle(main, "instance", main)
        .set(shell(main));
    // Native enum descriptions read Lang during static initialization. Supply only the local
    // translation cache, without starting the real plugin/database or replacing its event classes.
    Class<?> lang = type("me.gypopo.economyshopgui.files.Lang");
    Object translations = shell(type("me.gypopo.economyshopgui.files.Lang$LangCache"));
    var messages = new HashMap<String, Object>();
    for (Object value : (Object[]) lang.getMethod("values").invoke(null)) {
      String key = (String) NarrowApi.call(lang, value, "getKey", String.class, new Class<?>[0]);
      messages.put(
          key,
          proxy(
              "me.gypopo.economyshopgui.files.Translatable",
              (s, m, a) -> m.getName().equals("getLegacy") ? key : emptyApiReturn(m)));
    }
    set(translations, "cache", messages);
    var lookup = MethodHandles.privateLookupIn(lang, MethodHandles.lookup());
    lookup.findStaticVarHandle(lang, "defaultLang", Locale.class).set(Locale.ENGLISH);
    ((Map) lookup.findStaticVarHandle(lang, "langCache", Map.class).get())
        .put(Locale.ENGLISH, translations);
    Class<?> shopType = type("me.gypopo.economyshopgui.objects.ShopItem");
    Object shop = shell(shopType);
    // Populate the real native item field; getItemToGive() itself remains production provider code.
    var field = shopType.getDeclaredField("itemToGive");
    field.setAccessible(true);
    field.set(shop, template);
    Class<?> action = type("me.gypopo.economyshopgui.util.Transaction$Type");
    Object kind = Enum.valueOf((Class) action, "API_" + typeName);
    return (Event)
        type("me.gypopo.economyshopgui.api.events.PreTransactionEvent")
            .getConstructor(Map.class, Map.class, Player.class, int.class, action)
            .newInstance(Map.of(shop, 1), Map.of(), player, 1, kind);
  }

  void shop(Event event) throws Exception {
    var m = ServerPluginAdapters.class.getDeclaredMethod("shop", Event.class);
    m.setAccessible(true);
    m.invoke(adapters, event);
  }

  @Test
  void economyShop651BlocksSellingSpecialGearAtVanillaPrice() throws Exception {
    provider("economyshopgui");
    var item = plugin.api().createItem("epic_sword_5").orElseThrow();
    player.getInventory().addItem(item);
    Event event = transaction("SELL", new ItemStack(item.getType()));
    shop(event);
    assertTrue(((Cancellable) event).isCancelled());
    assertTrue(plugin.integrations().active("EconomyShopGUI"));
  }

  @Test
  void economyShop651AllowsExactCustomTemplateAndOrdinaryItems() throws Exception {
    provider("economyshopgui");
    var item = plugin.api().createItem("epic_sword_5").orElseThrow();
    player.getInventory().addItem(item);
    Event exact = transaction("SELL", item);
    shop(exact);
    assertFalse(((Cancellable) exact).isCancelled());
    player.getInventory().clear();
    player.getInventory().addItem(new ItemStack(item.getType()));
    Event ordinary = transaction("SELL", new ItemStack(item.getType()));
    shop(ordinary);
    assertFalse(((Cancellable) ordinary).isCancelled());
    assertTrue(plugin.integrations().active("EconomyShopGUI"));
  }

  @Test
  void economyShop651BuyEventsRemainUntouched() throws Exception {
    provider("economyshopgui");
    player.getInventory().addItem(plugin.api().createItem("epic_sword_5").orElseThrow());
    Event event = transaction("BUY", new ItemStack(Material.DIAMOND_SWORD));
    shop(event);
    assertFalse(((Cancellable) event).isCancelled());
  }

  @Test
  void economyShopIncompatibleGuardKeepsBlockingUntilRestart() throws Exception {
    provider("economyshopgui");
    plugin.integrations().state("EconomyShopGUI", IntegrationStatus.State.INCOMPATIBLE, "fixture");
    for (int i = 0; i < 2; i++) {
      Event event = transaction("SELL", new ItemStack(Material.STONE));
      shop(event);
      assertTrue(((Cancellable) event).isCancelled());
    }
  }
}
