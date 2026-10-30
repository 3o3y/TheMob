package org.plugin.theMob.hud.compass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** Identical CoinOverlay assets/layout, with independent TheMob identifiers. */
public final class CompassFiles {
  public static final List<String> FILES =
      List.of(
          "assets/themob/compass-arrow.png",
          "images/themob-compass.yml",
          "layouts/themob-compass.yml",
          "huds/themob-compass.yml",
          "compasses/themob-compass.yml",
          "assets/compass/n.png",
          "assets/compass/s.png",
          "assets/compass/e.png",
          "assets/compass/w.png",
          "assets/compass/ne.png",
          "assets/compass/nw.png",
          "assets/compass/se.png",
          "assets/compass/sw.png",
          "assets/compass/chain.png",
          "assets/compass/point.png");

  private CompassFiles() {}

  public static boolean install(Path folder, Function<String, InputStream> resources)
      throws IOException {
    Path root = folder.toAbsolutePath().normalize();
    boolean changed = false;
    for (String file : FILES) {
      Path target = root.resolve(file).normalize();
      if (!target.startsWith(root)) throw new IOException("Ungueltiger Kompass-Pfad: " + file);
      if (Files.exists(target)) continue;
      try (InputStream source = resources.apply("hud/" + file)) {
        if (source == null) throw new IOException("Kompass-Ressource fehlt: " + file);
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
        changed = true;
      }
    }
    return changed;
  }
}
