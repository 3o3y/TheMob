package org.plugin.theMob.hud.compass.betterhud;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kr.toxicity.hud.api.compass.Compass;
import kr.toxicity.hud.api.component.WidthComponent;
import kr.toxicity.hud.api.configuration.HudComponentSupplier;
import kr.toxicity.hud.api.configuration.HudObject;
import kr.toxicity.hud.api.configuration.HudObjectType;
import kr.toxicity.hud.api.player.HudPlayer;
import org.junit.jupiter.api.Test;
import org.plugin.theMob.hud.compass.CompassPreferences;

class RainbowCompassTest {
  @Test
  void independentTheMobCompassSurvivesRemovalOfOldCoinOverlayCompass() {
    Compass own = new TestCompass("themob_compass");
    Compass old = new TestCompass("default_compass");
    Map<HudObject.Identifier, HudComponentSupplier<?>> objects = new HashMap<>();
    objects.put(own.identifier(), own.indicate(null));
    objects.put(old.identifier(), old.indicate(null));
    RainbowCompass.install(objects, () -> 1, () -> true, () -> true, "themob_compass");
    objects
        .values()
        .removeIf(
            supplier ->
                supplier.parent().getName().equals("default_compass")
                    || supplier.parent().getName().equals("coinoverlay_compass_marker"));
    assertEquals(1, objects.size());
    assertInstanceOf(RainbowCompass.class, objects.get(own.identifier()));
    assertFalse(objects.get(own.identifier()).get().isEmpty());
  }

  @Test
  void decoratesOnlyExistingDefaultCompassAndRepeatedRefreshDoesNotNest() {
    Compass standard = new TestCompass("default_compass");
    Compass other = new TestCompass("admin_compass");
    HudComponentSupplier<?> original = standard.indicate(null);
    HudComponentSupplier<?> unrelated = other.indicate(null);
    Map<HudObject.Identifier, HudComponentSupplier<?>> objects = new HashMap<>();
    objects.put(standard.identifier(), original);
    objects.put(other.identifier(), unrelated);

    RainbowCompass.install(objects, () -> 0, () -> true);
    HudComponentSupplier<?> decorated = objects.get(standard.identifier());
    assertInstanceOf(RainbowCompass.class, decorated);
    assertSame(standard, decorated.parent());
    assertSame(unrelated, objects.get(other.identifier()));
    RainbowCompass.install(objects, () -> 1, () -> true);
    assertSame(decorated, objects.get(standard.identifier()));
    RainbowCompass.restore(objects);
    assertSame(original, objects.get(standard.identifier()));
    assertSame(unrelated, objects.get(other.identifier()));

    objects.clear();
    RainbowCompass.install(objects, () -> 2, () -> true);
    assertTrue(objects.isEmpty(), "A compass disabled by the player must not be added again.");
  }

  @Test
  void animatesAtRenderTimeAndFallsBackImmediatelyOnPluginShutdown() {
    Compass compass = new TestCompass("default_compass");
    HudComponentSupplier<?> original = compass.indicate(null);
    AtomicReference<Double> time = new AtomicReference<>(0.0);
    AtomicBoolean active = new AtomicBoolean(true);
    Map<HudObject.Identifier, HudComponentSupplier<?>> objects = new HashMap<>();
    objects.put(compass.identifier(), original);
    RainbowCompass.install(objects, time::get, active::get);
    HudComponentSupplier<?> animated = objects.get(compass.identifier());
    List<WidthComponent> first = animated.get();
    time.set(1.0);
    assertNotEquals(first, animated.get());
    active.set(false);
    assertEquals(original.get(), animated.get());
  }

  @Test
  void personalAndGlobalSwitchesHideWholeCompassAndRemainEffectiveAfterRendererReload()
      throws Exception {
    java.nio.file.Path folder = java.nio.file.Files.createTempDirectory("compass-visibility-test-");
    try {
      CompassPreferences preferences = new CompassPreferences(folder.resolve("compass.properties"));
      java.util.UUID alice = java.util.UUID.randomUUID();
      java.util.UUID bob = java.util.UUID.randomUUID();
      Compass compass = new TestCompass("default_compass");
      Map<HudObject.Identifier, HudComponentSupplier<?>> aliceObjects = new HashMap<>();
      Map<HudObject.Identifier, HudComponentSupplier<?>> bobObjects = new HashMap<>();
      aliceObjects.put(compass.identifier(), compass.indicate(null));
      bobObjects.put(compass.identifier(), compass.indicate(null));
      RainbowCompass.install(aliceObjects, () -> 1, () -> true, () -> preferences.visible(alice));
      RainbowCompass.install(bobObjects, () -> 1, () -> true, () -> preferences.visible(bob));
      preferences.changePlayer(alice, CompassPreferences.Action.OFF);
      assertTrue(aliceObjects.get(compass.identifier()).get().isEmpty());
      assertFalse(bobObjects.get(compass.identifier()).get().isEmpty());
      preferences.changeServer(CompassPreferences.Action.OFF);
      assertTrue(bobObjects.get(compass.identifier()).get().isEmpty());
      bobObjects.put(
          compass.identifier(), compass.indicate(null)); // BetterHud reload creates a new supplier.
      RainbowCompass.install(bobObjects, () -> 2, () -> true, () -> preferences.visible(bob));
      assertTrue(bobObjects.get(compass.identifier()).get().isEmpty());
      preferences.changeServer(CompassPreferences.Action.ON);
      assertFalse(bobObjects.get(compass.identifier()).get().isEmpty());
      assertTrue(aliceObjects.get(compass.identifier()).get().isEmpty());
    } finally {
      java.nio.file.Files.deleteIfExists(folder.resolve("compass.properties"));
      java.nio.file.Files.deleteIfExists(folder);
    }
  }

  private record TestCompass(String name) implements Compass {
    private static final List<WidthComponent> COMPONENTS =
        List.of(CompassPaletteTest.ribbon(0, true));

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isDefault() {
      return true;
    }

    @Override
    public HudObjectType<?> getType() {
      return HudObjectType.COMPASS;
    }

    @Override
    public long tick() {
      return 1;
    }

    @Override
    public HudComponentSupplier<Compass> indicate(HudPlayer player) {
      return HudComponentSupplier.of(this, () -> COMPONENTS);
    }
  }
}
