package org.plugin.theMob.hud.compass.betterhud;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import kr.toxicity.hud.api.component.WidthComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/** Recolors the existing BetterHud ribbon without changing its glyphs, spacing or resources. */
final class CompassPalette {
  static final int GOLD = 0xFFD700;
  static final double CYCLE_SECONDS = 4.0;

  // BetterHud 2.2.0-449 CircleCompass uses these two separate codepoint ranges.
  // Each ribbon cell is: horizontal spacer, bitmap glyph, horizontal spacer.
  // Pointer icons use a different structure and deliberately keep their original colors.
  private static final int GLYPH_START = 0xC0000;
  private static final int GLYPH_END = 0xC8000;
  private static final int SPACE_ORIGIN = 0xD0000;

  private CompassPalette() {}

  static WidthComponent paint(WidthComponent source, double seconds) {
    TextComponent original = source.component().build();
    int columns = countCells(original);
    if (columns == 0) {
      return source;
    }
    Component painted = paintTree(original, new Cursor(columns, seconds));
    return new WidthComponent(((TextComponent) painted).toBuilder(), source.width());
  }

  static int rgb(int column, int columns, double seconds) {
    double phase = (double) column / Math.max(1, columns - 1) - seconds / CYCLE_SECONDS;
    float hue = (float) (phase - Math.floor(phase));
    // A gentle highlight travels with the colors; even the darker part stays readable.
    float brightness = (float) (0.92 + 0.08 * Math.cos(2 * Math.PI * phase));
    return Color.HSBtoRGB(hue, 0.85F, brightness) & 0xFFFFFF;
  }

  private static int countCells(Component component) {
    int count = isCell(component) ? 1 : 0;
    for (Component child : component.children()) {
      count += countCells(child);
    }
    return count;
  }

  private static Component paintTree(Component component, Cursor cursor) {
    Component result = component;
    if (isCell(component)) {
      result =
          component.color(TextColor.color(rgb(cursor.column++, cursor.columns, cursor.seconds)));
    }
    if (!component.children().isEmpty()) {
      List<Component> children = new ArrayList<>(component.children().size());
      for (Component child : component.children()) {
        children.add(paintTree(child, cursor));
      }
      result = result.children(children);
    }
    return result;
  }

  private static boolean isCell(Component component) {
    if (!(component instanceof TextComponent text)) {
      return false;
    }
    String content = text.content();
    if (content.codePointCount(0, content.length()) != 3) {
      return false;
    }
    int first = content.codePointAt(0);
    int middle = content.codePointAt(content.offsetByCodePoints(0, 1));
    int last = content.codePointAt(content.offsetByCodePoints(0, 2));
    return Math.abs(first - SPACE_ORIGIN) <= 8192
        && Math.abs(last - SPACE_ORIGIN) <= 8192
        && middle >= GLYPH_START
        && middle < GLYPH_END;
  }

  private static final class Cursor {
    private final int columns;
    private final double seconds;
    private int column;

    private Cursor(int columns, double seconds) {
      this.columns = columns;
      this.seconds = seconds;
    }
  }
}
