package org.plugin.theMob.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Strict read and atomic replacement with a byte-for-byte previous-file backup. */
public final class AtomicYaml {
  private AtomicYaml() {}

  public static YamlConfiguration read(Path file) throws Exception {
    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file))
      throw new IOException("Keine regulaere Konfigurationsdatei: " + file.getFileName());
    var yaml = new YamlConfiguration();
    org.plugin.theMob.compat.YamlComments.enable(yaml);
    yaml.load(file.toFile());
    return yaml;
  }

  public static void replace(Path file, YamlConfiguration yaml, String backupName)
      throws IOException {
    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file))
      throw new IOException("Konfiguration ist keine regulaere Datei");
    Path backup = file.resolveSibling(backupName);
    if (Files.isSymbolicLink(backup)) throw new IOException("Backup ist ein symbolischer Link");
    Path temporary = Files.createTempFile(file.getParent(), "themob-config-", ".tmp");
    try {
      Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
      Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
