package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompassPreferencesTest {
  @TempDir Path directory;
  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();

  private CompassPreferences preferences() throws IOException {
    return new CompassPreferences(directory.resolve("compass.properties"));
  }

  @Test
  void defaultIsVisibleAndPersonalSwitchAffectsOnlyOneUuid() throws IOException {
    CompassPreferences preferences = preferences();
    assertTrue(preferences.serverEnabled());
    assertTrue(preferences.visible(alice));
    preferences.changePlayer(alice, CompassPreferences.Action.OFF);
    assertFalse(preferences.visible(alice));
    assertTrue(preferences.visible(bob));
  }

  @Test
  void globalOffCoversExistingAndNewPlayersAndRestoresTheirChoices() throws IOException {
    CompassPreferences preferences = preferences();
    preferences.changePlayer(alice, CompassPreferences.Action.OFF);
    preferences.changeServer(CompassPreferences.Action.OFF);
    assertFalse(preferences.visible(alice));
    assertFalse(preferences.visible(bob));
    assertFalse(preferences.visible(UUID.randomUUID()));
    assertTrue(preferences.personalEnabled(bob));
    preferences.changeServer(CompassPreferences.Action.ON);
    assertFalse(preferences.visible(alice));
    assertTrue(preferences.visible(bob));
  }

  @Test
  void playerCannotOverrideServerOffButCanSavePreferenceForLater() throws IOException {
    CompassPreferences preferences = preferences();
    preferences.changeServer(CompassPreferences.Action.OFF);
    preferences.changePlayer(alice, CompassPreferences.Action.OFF);
    preferences.changePlayer(alice, CompassPreferences.Action.ON);
    assertTrue(preferences.personalEnabled(alice));
    assertFalse(preferences.visible(alice));
    preferences.changeServer(CompassPreferences.Action.ON);
    assertTrue(preferences.visible(alice));
  }

  @Test
  void playerAndServerChoicesSurviveRestartAndDoNotTouchAccounts() throws IOException {
    Path accounts = directory.resolve("accounts.properties");
    Files.writeString(accounts, "existing currency data");
    CompassPreferences preferences = preferences();
    preferences.changePlayer(alice, CompassPreferences.Action.OFF);
    preferences.changeServer(CompassPreferences.Action.OFF);
    CompassPreferences restarted = preferences();
    assertFalse(restarted.serverEnabled());
    assertFalse(restarted.personalEnabled(alice));
    assertTrue(restarted.personalEnabled(bob));
    restarted.changeServer(CompassPreferences.Action.ON);
    assertFalse(restarted.visible(alice));
    assertTrue(restarted.visible(bob));
    assertEquals("existing currency data", Files.readString(accounts));
  }

  @Test
  void repeatedOnAndOffAreIdempotentWhileToggleUsesPersonalChoice() throws IOException {
    CompassPreferences preferences = preferences();
    preferences.changePlayer(alice, CompassPreferences.Action.OFF);
    preferences.changePlayer(alice, CompassPreferences.Action.OFF);
    assertFalse(preferences.personalEnabled(alice));
    preferences.changeServer(CompassPreferences.Action.OFF);
    assertTrue(preferences.changePlayer(alice, CompassPreferences.Action.TOGGLE));
    assertFalse(preferences.visible(alice));
    assertFalse(preferences.changePlayer(alice, CompassPreferences.Action.TOGGLE));
    assertTrue(preferences.changeServer(CompassPreferences.Action.TOGGLE));
  }

  @Test
  void failedSaveDoesNotPublishAnUnsavedSetting() throws IOException {
    Path blocked = directory.resolve("blocked");
    CompassPreferences preferences = new CompassPreferences(blocked.resolve("compass.properties"));
    Files.writeString(blocked, "not a directory");
    assertThrows(IOException.class, () -> preferences.changeServer(CompassPreferences.Action.OFF));
    assertThrows(
        IOException.class, () -> preferences.changePlayer(alice, CompassPreferences.Action.OFF));
    assertTrue(preferences.visible(alice));
    assertTrue(preferences.serverEnabled());
  }

  @Test
  void invalidFileIsReportedAndKeptIntact() throws IOException {
    Path file = directory.resolve("compass.properties");
    String invalid = "format=1\nserver-enabled=maybe\n";
    Files.writeString(file, invalid);
    assertThrows(IOException.class, this::preferences);
    assertEquals(invalid, Files.readString(file));
  }
}
