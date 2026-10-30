package org.plugin.theMob.hud.compass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reserves a top header in BetterHud and positions only TheMob's compass inside it. */
public final class CompassHeaderLayout {
  public static final int COMPASS_Y = 8;
  public static final int ARROW_Y = 18;
  public static final int HEADER_LINES = 2;
  public static final String SHADER = "betterhud_26_3/assets/minecraft/shaders/core/text.vsh";
  private static final Pattern OFFSET =
      Pattern.compile("(?m)^(#define[ \\t]+DEFAULT_OFFSET[ \\t]+)(-?\\d+)(?=[ \\t]*\\r?$)");
  private static final Pattern MARKER =
      Pattern.compile("(?m)^// TheMob header correction: (\\d+)\\r?\\n");

  private CompassHeaderLayout() {}

  public static void reserve(
      Path root, java.util.function.Function<String, java.io.InputStream> defaults)
      throws IOException {
    Path file = root.resolve("config.yml");
    if (Files.notExists(file))
      try (var input = defaults.apply("config.yml")) {
        if (input != null) {
          Files.createDirectories(root);
          Files.copy(input, file);
        }
      }
    reserve(root);
  }

  /** Called in onLoad, before BetterHud enables and clients create their carrier bossbars. */
  public static void reserve(Path root) throws IOException {
    Path file = root.resolve("config.yml");
    if (!Files.isRegularFile(file)) return;
    var config = YamlConfiguration.loadConfiguration(file.toFile());
    int lines = Math.max(HEADER_LINES, config.getInt("bossbar-line", 1));
    if (config.getInt("bossbar-line", 1) == lines
        && config.getInt("bossbar-resource-pack-line", 0) == lines
        && !config.getBoolean("merge-boss-bar", true)) return;
    config.set("bossbar-line", lines);
    config.set("bossbar-resource-pack-line", lines);
    config.set("merge-boss-bar", false);
    save(file, config);
  }

  /** Runs after CoinOverlay's older migration and before BetterHud reads the files. */
  public static void position(Path root) throws IOException {
    reserve(root);
    Path file = root.resolve("compasses/themob-compass.yml");
    if (Files.isRegularFile(file)) {
      var config = YamlConfiguration.loadConfiguration(file.toFile());
      if (config.isConfigurationSection("themob_compass")
          && (config.getInt("themob_compass.pixel.y") != COMPASS_Y
              || config.getDouble("themob_compass.gui.y") != 0)) {
        config.set("themob_compass.pixel.y", COMPASS_Y);
        config.set("themob_compass.gui.y", 0);
        save(file, config);
      }
    }
    file = root.resolve("huds/themob-compass.yml");
    if (!Files.isRegularFile(file)) return;
    var config = YamlConfiguration.loadConfiguration(file.toFile());
    var layouts = config.getConfigurationSection("themob_compass_marker.layouts");
    boolean changed = false;
    if (layouts != null)
      for (String key : layouts.getKeys(false)) {
        var layout = layouts.getConfigurationSection(key);
        if (layout == null || !"themob_compass_marker_layout".equals(layout.getString("name")))
          continue;
        if (layout.getInt("pixel.y") != ARROW_Y || layout.getDouble("gui.y") != 0) {
          layout.set("pixel.y", ARROW_Y);
          layout.set("gui.y", 0);
          changed = true;
        }
      }
    if (changed) save(file, config);
  }

  /** 26.3 uses 19 GUI pixels per row; BetterHud 449's origin calculation uses 17. */
  public static byte[] correctShader(byte[] bytes, int lines) throws IOException {
    if (lines < HEADER_LINES)
      throw new IOException("Kompass-Kopfbereich benoetigt zwei BetterHud-Zeilen.");
    String text = new String(bytes, StandardCharsets.UTF_8);
    var marker = MARKER.matcher(text);
    int previous = 0;
    if (marker.find()) {
      previous = Integer.parseInt(marker.group(1));
      if (marker.find()) throw new IOException("Mehrdeutige TheMob-Kopfbereichskorrektur.");
      text = MARKER.matcher(text).replaceFirst("");
    }
    var match = OFFSET.matcher(text);
    if (!match.find()) throw new IOException("DEFAULT_OFFSET fehlt im Minecraft-26.3-Shader.");
    int old = Integer.parseInt(match.group(2));
    if (match.find())
      throw new IOException("Mehrdeutiger DEFAULT_OFFSET im Minecraft-26.3-Shader.");
    int correction = 2 * (lines - 1);
    int next = Math.addExact(Math.subtractExact(old, previous), correction);
    String corrected =
        OFFSET
            .matcher(text)
            .replaceFirst(m -> java.util.regex.Matcher.quoteReplacement(m.group(1) + next));
    int insertion = corrected.indexOf('\n') + 1;
    corrected =
        corrected.substring(0, insertion)
            + "// TheMob header correction: "
            + correction
            + "\n"
            + corrected.substring(insertion);
    return corrected.getBytes(StandardCharsets.UTF_8);
  }

  public static void correctPack(Map<String, byte[]> pack, int lines) throws IOException {
    byte[] shader = pack.get(SHADER);
    if (shader == null)
      throw new IOException("Minecraft-26.3-Shader fuer den Kompass-Kopfbereich fehlt.");
    pack.put(SHADER, correctShader(shader, lines));
  }

  private static void save(Path file, YamlConfiguration config) throws IOException {
    Path backup = file.resolveSibling(file.getFileName() + ".before-themob-1.10.2");
    if (Files.notExists(backup)) Files.copy(file, backup);
    Path temporary = Files.createTempFile(file.getParent(), "themob-header-", ".tmp");
    try {
      Files.writeString(temporary, config.saveToString(), StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
