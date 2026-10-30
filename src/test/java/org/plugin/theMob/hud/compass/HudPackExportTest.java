package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudPackExportTest {
  @TempDir Path directory;

  @Test
  void exportsTheClientFilesAtZipRootWithAnAccurateDownloadHash() throws Exception {
    byte[] meta =
        "{\"pack\":{\"min_format\":[97,1],\"max_format\":[97,1],\"description\":\"Test\"}}"
            .getBytes(StandardCharsets.UTF_8);
    HudPackExport.Result result =
        HudPackExport.write(
            Map.of(
                "pack.mcmeta",
                meta,
                "assets/test/font/currency.json",
                "{}".getBytes(StandardCharsets.UTF_8)),
            directory);
    try (ZipFile zip = new ZipFile(result.zip().toFile())) {
      assertArrayEquals(meta, zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes());
      assertNotNull(zip.getEntry("assets/test/font/currency.json"));
    }
    String actualHash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(result.zip())));
    assertEquals(actualHash, result.sha1());
    assertEquals(
        actualHash, Files.readString(directory.resolve("TheMob-Compass-26.3.zip.sha1")).strip());
  }

  @Test
  void identicalContentsKeepTheSameHashRegardlessOfMapOrder() throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put("pack.mcmeta", new byte[] {1});
    files.put("assets/b.png", new byte[] {2});
    String firstHash = HudPackExport.write(files, directory).sha1();
    Map<String, byte[]> reversed = new LinkedHashMap<>();
    reversed.put("assets/b.png", new byte[] {2});
    reversed.put("pack.mcmeta", new byte[] {1});
    assertEquals(firstHash, HudPackExport.write(reversed, directory).sha1());
    reversed.put("assets/b.png", new byte[] {3});
    assertNotEquals(firstHash, HudPackExport.write(reversed, directory).sha1());
  }

  @Test
  void invalidBuildsCannotReplaceThePreviousDownload() throws IOException {
    HudPackExport.Result original =
        HudPackExport.write(Map.of("pack.mcmeta", new byte[] {1}), directory);
    byte[] previousZip = Files.readAllBytes(original.zip());
    assertThrows(
        IOException.class,
        () -> HudPackExport.write(Map.of("not-a-pack.txt", new byte[0]), directory));
    assertThrows(
        IOException.class,
        () ->
            HudPackExport.write(
                Map.of("pack.mcmeta", new byte[] {1}, "../outside", new byte[0]), directory));
    assertArrayEquals(previousZip, Files.readAllBytes(original.zip()));
  }
}
