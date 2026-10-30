package org.plugin.theMob.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobPackInstallerTest {
  @TempDir Path temp;

  Path archive(String... pairs) throws IOException {
    Path zip = temp.resolve("pack.zip");
    try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
      for (int i = 0; i < pairs.length; i += 2) {
        output.putNextEntry(new ZipEntry(pairs[i]));
        output.write(pairs[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
      }
    }
    return zip;
  }

  @Test
  void installsNestedYamlAndPreservesExistingFiles() throws Exception {
    Path mobs = Files.createDirectories(temp.resolve("mobs"));
    Files.writeString(mobs.resolve("existing.yml"), "original");
    assertEquals(
        1,
        MobPackInstaller.install(
            archive("mobs/existing.yml", "replace", "mobs/sub/new.yml", "new"), mobs));
    assertEquals("original", Files.readString(mobs.resolve("existing.yml")));
    assertEquals("new", Files.readString(mobs.resolve("sub/new.yml")));
  }

  @Test
  void traversalRejectsEntirePackageWithoutPartialFiles() throws Exception {
    Path mobs = temp.resolve("mobs");
    assertThrows(
        IOException.class,
        () ->
            MobPackInstaller.install(
                archive("good.yml", "ok", "mobs/../../escape.yml", "bad"), mobs));
    assertFalse(Files.exists(mobs.resolve("good.yml")));
    assertFalse(Files.exists(temp.resolve("escape.yml")));
  }

  @Test
  void rejectsWindowsAndUnixAbsolutePaths() {
    for (String path :
        new String[] {"C:/outside.yml", "/outside.yml", "..\\escape.yml", "mobs/../escape.yml"})
      assertThrows(IOException.class, () -> MobPackInstaller.resolveEntry(temp, path));
  }

  @Test
  void oversizedEntryRollsBack() throws Exception {
    Path mobs = temp.resolve("mobs");
    assertThrows(
        IOException.class,
        () ->
            MobPackInstaller.install(archive("large.yml", "x".repeat(2 * 1024 * 1024 + 1)), mobs));
    assertFalse(Files.exists(mobs.resolve("large.yml")));
  }

  @Test
  void directoryEntriesAndNestedMobsFoldersArePreserved() throws Exception {
    Path archive = temp.resolve("directory.zip");
    try (var out = new ZipOutputStream(Files.newOutputStream(archive))) {
      out.putNextEntry(new ZipEntry("mobs/"));
      out.closeEntry();
      out.putNextEntry(new ZipEntry("mobs/sub/mobs/nested.yml"));
      out.write("nested".getBytes());
      out.closeEntry();
    }
    Path mobs = temp.resolve("mobs");
    assertEquals(1, MobPackInstaller.install(archive, mobs));
    assertTrue(Files.exists(mobs.resolve("sub/mobs/nested.yml")));
  }
}
