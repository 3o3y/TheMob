package org.plugin.theMob.hud.compass;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/** Persistent master switch and UUID preferences, independent of currency accounts. */
public final class CompassPreferences {
  public enum Action {
    ON,
    OFF,
    TOGGLE;

    boolean apply(boolean current) {
      return switch (this) {
        case ON -> true;
        case OFF -> false;
        case TOGGLE -> !current;
      };
    }
  }

  private record Snapshot(boolean serverEnabled, Map<UUID, Boolean> players) {}

  private final Path file;
  private volatile Snapshot snapshot;

  public CompassPreferences(Path file) throws IOException {
    this.file = file.toAbsolutePath();
    snapshot = load();
  }

  public boolean serverEnabled() {
    return snapshot.serverEnabled();
  }

  public boolean personalEnabled(UUID player) {
    return snapshot.players().getOrDefault(player, true);
  }

  public boolean visible(UUID player) {
    Snapshot current = snapshot;
    return current.serverEnabled() && current.players().getOrDefault(player, true);
  }

  public synchronized boolean changeServer(Action action) throws IOException {
    boolean enabled = action.apply(snapshot.serverEnabled());
    if (enabled != snapshot.serverEnabled()) {
      commit(new Snapshot(enabled, snapshot.players()));
    }
    return enabled;
  }

  public synchronized boolean changePlayer(UUID player, Action action) throws IOException {
    Objects.requireNonNull(player);
    boolean enabled = action.apply(personalEnabled(player));
    if (enabled != personalEnabled(player)) {
      Map<UUID, Boolean> players = new HashMap<>(snapshot.players());
      players.put(player, enabled);
      commit(new Snapshot(snapshot.serverEnabled(), Map.copyOf(players)));
    }
    return enabled;
  }

  private Snapshot load() throws IOException {
    if (Files.notExists(file)) {
      return new Snapshot(true, Map.of());
    }
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      properties.load(reader);
      if (!"1".equals(properties.getProperty("format"))) {
        throw new IllegalArgumentException("Unbekanntes Kompass-Dateiformat.");
      }
      boolean server = parseBoolean(properties.getProperty("server-enabled"));
      Map<UUID, Boolean> players = new HashMap<>();
      for (String key : properties.stringPropertyNames()) {
        if (key.equals("format") || key.equals("server-enabled")) {
          continue;
        }
        if (!key.startsWith("player.")) {
          throw new IllegalArgumentException("Unbekanntes Kompassfeld: " + key);
        }
        UUID id = UUID.fromString(key.substring("player.".length()));
        players.put(id, parseBoolean(properties.getProperty(key)));
      }
      return new Snapshot(server, Map.copyOf(players));
    } catch (IllegalArgumentException exception) {
      throw new IOException(
          "Kompass-Einstellungen ungueltig; Datei wird nicht ueberschrieben: " + file, exception);
    }
  }

  private static boolean parseBoolean(String value) {
    if (!("true".equals(value) || "false".equals(value))) {
      throw new IllegalArgumentException("Kompass-Schalter muss true oder false sein.");
    }
    return Boolean.parseBoolean(value);
  }

  private void commit(Snapshot next) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("format", "1");
    properties.setProperty("server-enabled", Boolean.toString(next.serverEnabled()));
    next.players()
        .forEach((id, enabled) -> properties.setProperty("player." + id, enabled.toString()));
    StringWriter writer = new StringWriter();
    properties.store(writer, "TheMob: Kompass. Mit /kompass verwalten.");
    byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
    Files.createDirectories(file.getParent());
    Path temporary = Files.createTempFile(file.getParent(), "compass-", ".tmp");
    try {
      try (FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      snapshot = next;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
