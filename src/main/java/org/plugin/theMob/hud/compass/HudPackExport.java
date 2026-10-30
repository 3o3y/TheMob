package org.plugin.theMob.hud.compass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exports a stable, directly hostable client ZIP, separate from the plugin installation bundle. */
public final class HudPackExport {
  public record Result(Path zip, String sha1) {}

  private HudPackExport() {}

  public static Result write(Map<String, byte[]> files, Path folder) throws IOException {
    if (!files.containsKey("pack.mcmeta")) {
      throw new IOException("BetterHud-Ergebnis enthaelt keine pack.mcmeta.");
    }
    // Validate the whole map before creating or replacing the previous export.
    for (Map.Entry<String, byte[]> file : files.entrySet()) {
      String name = file.getKey();
      if (name.isBlank()
          || name.startsWith("/")
          || name.contains("\\")
          || name.contains(":")
          || java.util.Arrays.asList(name.split("/", -1)).contains("..")
          || file.getValue() == null) {
        throw new IOException("Ungueltiger Pfad im Ressourcenpaket: " + name);
      }
    }
    Files.createDirectories(folder);
    Path temporary = Files.createTempFile(folder, "hud-pack-", ".tmp");
    Path zipFile = folder.resolve("TheMob-Compass-26.3.zip");
    try {
      try (ZipOutputStream zip =
          new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
        for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet()) {
          ZipEntry entry = new ZipEntry(file.getKey());
          entry.setTime(0L);
          zip.putNextEntry(entry);
          zip.write(file.getValue());
          zip.closeEntry();
        }
      }
      String hash;
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (var input = Files.newInputStream(temporary)) {
          byte[] buffer = new byte[8192];
          int size;
          while ((size = input.read(buffer)) != -1) {
            digest.update(buffer, 0, size);
          }
        }
        hash = HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException("SHA-1 fehlt in der Java-Laufzeit.", exception);
      }
      Files.move(temporary, zipFile, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(
          folder.resolve("TheMob-Compass-26.3.zip.sha1"), hash + "\n", StandardCharsets.UTF_8);
      return new Result(zipFile, hash);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
