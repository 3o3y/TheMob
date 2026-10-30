package org.plugin.theMob.hud.compass.betterhud;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import kr.toxicity.hud.api.compass.Compass;
import kr.toxicity.hud.api.component.WidthComponent;
import kr.toxicity.hud.api.configuration.HudComponentSupplier;
import kr.toxicity.hud.api.configuration.HudObject;

/** Decorates an already enabled compass; BetterHud still owns its yaw, geometry and update task. */
final class RainbowCompass implements HudComponentSupplier<Compass> {
  private final HudComponentSupplier<?> original;
  private final DoubleSupplier seconds;
  private final BooleanSupplier active;
  private final BooleanSupplier visible;

  private RainbowCompass(
      HudComponentSupplier<?> original,
      DoubleSupplier seconds,
      BooleanSupplier active,
      BooleanSupplier visible) {
    this.original = original;
    this.seconds = seconds;
    this.active = active;
    this.visible = visible;
  }

  static void install(
      Map<HudObject.Identifier, HudComponentSupplier<?>> objects,
      DoubleSupplier seconds,
      BooleanSupplier active) {
    install(objects, seconds, active, () -> true);
  }

  static void install(
      Map<HudObject.Identifier, HudComponentSupplier<?>> objects,
      DoubleSupplier seconds,
      BooleanSupplier active,
      BooleanSupplier visible) {
    install(objects, seconds, active, visible, "default_compass");
  }

  static void install(
      Map<HudObject.Identifier, HudComponentSupplier<?>> objects,
      DoubleSupplier seconds,
      BooleanSupplier active,
      BooleanSupplier visible,
      String compassName) {
    objects.replaceAll(
        (identifier, supplier) ->
            supplier instanceof RainbowCompass
                    || !(supplier.parent() instanceof Compass)
                    || !supplier.parent().getName().equals(compassName)
                ? supplier
                : new RainbowCompass(supplier, seconds, active, visible));
  }

  static void restore(Map<HudObject.Identifier, HudComponentSupplier<?>> objects) {
    objects.replaceAll(
        (identifier, supplier) ->
            supplier instanceof RainbowCompass rainbow ? rainbow.original : supplier);
  }

  @Override
  public Compass parent() {
    return (Compass) original.parent();
  }

  @Override
  public List<WidthComponent> get() {
    if (!active.getAsBoolean()) {
      return original.get();
    }
    if (!visible.getAsBoolean()) {
      return List.of();
    }
    List<WidthComponent> components = original.get();
    double frameTime = seconds.getAsDouble();
    return components.stream()
        .map(component -> CompassPalette.paint(component, frameTime))
        .toList();
  }
}
