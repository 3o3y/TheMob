package org.plugin.theMob.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Bounded, staged imports. Existing user files are never overwritten. */
public final class MobPackInstaller {
  private static final int MAX_FILES = 256,
      MAX_FILE_BYTES = 2 * 1024 * 1024,
      MAX_TOTAL_BYTES = 16 * 1024 * 1024;

  private MobPackInstaller() {}

  public static Path resolveEntry(Path root, String entry) throws IOException {
    String name = entry.replace('\\', '/');
    if (name.startsWith("/")
        || name.contains(":")
        || Arrays.stream(name.split("/")).anyMatch(p -> p.equals("..") || p.equals(".")))
      throw new IOException("Unsafe package path: " + entry);
    int index = name.indexOf("mobs/");
    if (index >= 0) name = name.substring(index + 5);
    if (name.isBlank() || name.startsWith("."))
      throw new IOException("Invalid package path: " + entry);
    Path base = root.toAbsolutePath().normalize(), result = base.resolve(name).normalize();
    if (!result.startsWith(base) || result.equals(base))
      throw new IOException("Package path escapes mobs directory");
    for (Path current = result;
        current != null && current.startsWith(base);
        current = current.getParent())
      if (Files.isSymbolicLink(current))
        throw new IOException("Symbolic links are not valid import targets");
    return result;
  }

  public static int install(Path archive, Path destination) throws IOException {
    Files.createDirectories(destination);
    Path base = destination.toRealPath();
    Path stage = Files.createTempDirectory(base, ".pack-staging-");
    List<Path> created = new ArrayList<>();
    List<Path> relativePaths = new ArrayList<>();
    int total = 0, entries = 0;
    try {
      try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
        java.util.zip.ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          if (++entries > MAX_FILES) throw new IOException("Too many package entries");
          if (entry.isDirectory()) {
            resolveEntry(base, entry.getName() + "directory-marker.yml");
            continue;
          }
          Path target = resolveEntry(base, entry.getName());
          byte[] bytes = zip.readNBytes(MAX_FILE_BYTES + 1);
          total += bytes.length;
          if (bytes.length > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES)
            throw new IOException("Package exceeds extraction limits");
          if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) continue;
          Path relative = base.relativize(target);
          if (relativePaths.contains(relative))
            throw new IOException("Duplicate package target: " + relative);
          relativePaths.add(relative);
          Path staged = stage.resolve(relative);
          Files.createDirectories(staged.getParent());
          Files.write(staged, bytes, StandardOpenOption.CREATE_NEW);
        }
      }
      for (Path relative : relativePaths) {
        Path target = resolveEntry(base, "mobs/" + relative.toString());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
        Files.createDirectories(target.getParent());
        Files.move(stage.resolve(relative), target);
        created.add(target);
      }
      return created.size();
    } catch (IOException failure) {
      for (Path path : created) Files.deleteIfExists(path);
      throw failure;
    } finally {
      try (var paths = Files.walk(stage)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }
}
