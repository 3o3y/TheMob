package org.plugin.theMob.hud.compass.betterhud;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import kr.toxicity.hud.api.component.WidthComponent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class CompassPaletteTest {
  @Test
  void directionsKeepMovingThroughTheRainbowWhileGoldIsReservedForSeparateArrow() {
    for (int heading = 0; heading < 8; heading++) {
      WidthComponent source = ribbon(heading, false);
      for (double seconds : List.of(0.0, 0.2, 1.0, 2.5, 4.0, 120.0)) {
        List<TextComponent> cells =
            cells(CompassPalette.paint(source, seconds).component().build());
        assertNotEquals(CompassPalette.GOLD, cells.get(9).color().value());
        assertEquals(cell(heading + 9).content(), cells.get(9).content());
      }
    }
  }

  @Test
  void rainbowAndHighlightTravelFromLeftToRightAndRepeatSeamlessly() {
    // A color at column 2 reaches column 3 one step later, even while the player stands still.
    double stepTime = CompassPalette.CYCLE_SECONDS / 18;
    int before = CompassPalette.rgb(2, 19, 0.0);
    assertEquals(before, CompassPalette.rgb(3, 19, stepTime));
    assertNotEquals(before, CompassPalette.rgb(2, 19, stepTime));
    assertEquals(before, CompassPalette.rgb(2, 19, CompassPalette.CYCLE_SECONDS));
    assertEquals(before, CompassPalette.rgb(2, 19, 1_000 * CompassPalette.CYCLE_SECONDS));
  }

  @Test
  void fullSpectrumIsVisibleInOneFrame() {
    int red = CompassPalette.rgb(0, 19, 0);
    int green = CompassPalette.rgb(6, 19, 0);
    int blue = CompassPalette.rgb(12, 19, 0);
    assertTrue(channel(red, 16) > channel(red, 8) * 3);
    assertTrue(channel(green, 8) > channel(green, 0) * 3);
    assertTrue(channel(blue, 0) > channel(blue, 16) * 3);
  }

  @Test
  void keepsGlyphsSpacingFontStyleAndWidthWithoutMutatingCachedRendererOutput() {
    WidthComponent original = ribbon(0, false);
    TextComponent snapshot = original.component().build();
    WidthComponent result = CompassPalette.paint(original, 0.5);
    TextComponent painted = result.component().build();

    assertEquals(original.width(), result.width());
    assertEquals(snapshot, original.component().build());
    assertEquals(snapshot.font(), painted.font());
    assertEquals(
        snapshot.decoration(TextDecoration.ITALIC), painted.decoration(TextDecoration.ITALIC));
    assertEquals(contents(snapshot), contents(painted));
    assertEquals(19, cells(painted).size());
    assertNotEquals(painted, CompassPalette.paint(original, 1.5).component().build());
  }

  @Test
  void pointersKeepTheirColorAndDoNotShiftTheRainbow() {
    TextComponent result = CompassPalette.paint(ribbon(0, true), 0.5).component().build();
    assertEquals(CompassPalette.rgb(9, 19, 0.5), cells(result).get(9).color().value());
    Component pointer = result.children().getLast();
    assertEquals(NamedTextColor.RED, pointer.color());
  }

  @Test
  void leavesEmptyOrUnrecognizedComponentsAlone() {
    WidthComponent empty = new WidthComponent(Component.text(), 0);
    WidthComponent plain = new WidthComponent(Component.text("Normaler Text").toBuilder(), 70);
    assertSame(empty, CompassPalette.paint(empty, 1));
    assertSame(plain, CompassPalette.paint(plain, 1));
  }

  static WidthComponent ribbon(int heading, boolean pointer) {
    TextComponent.Builder root =
        Component.text()
            .font(Key.key("betterhud:compass_default_compass"))
            .decoration(TextDecoration.ITALIC, false);
    // Same three-codepoint cell structure as the bundled CircleCompass; nested like
    // WidthComponent.plus.
    TextComponent.Builder row = Component.text();
    for (int column = 0; column < 19; column++) {
      row.append(cell(heading + column));
    }
    root.append(Component.text(codepoint(0xD0000 - 180)).append(row));
    if (pointer) {
      root.append(Component.text(codepoint(0xC0100) + codepoint(0xD0000 - 1), NamedTextColor.RED));
    }
    return new WidthComponent(root, 380);
  }

  private static TextComponent cell(int glyph) {
    return Component.text(
        codepoint(0xD0000 + 2) + codepoint(0xC0000 + glyph) + codepoint(0xD0000 + 1));
  }

  private static String codepoint(int codepoint) {
    return new String(Character.toChars(codepoint));
  }

  private static List<TextComponent> cells(Component tree) {
    List<TextComponent> result = new ArrayList<>();
    if (tree instanceof TextComponent text
        && text.content().codePointCount(0, text.content().length()) == 3) {
      result.add(text);
    }
    for (Component child : tree.children()) {
      result.addAll(cells(child));
    }
    return result;
  }

  private static String contents(Component component) {
    StringBuilder result = new StringBuilder();
    if (component instanceof TextComponent text) {
      result.append(text.content());
    }
    for (Component child : component.children()) {
      result.append(contents(child));
    }
    return result.toString();
  }

  private static int channel(int rgb, int shift) {
    return (rgb >> shift) & 255;
  }
}
