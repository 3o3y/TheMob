package org.plugin.theMob.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class AdminDocumentTest {
  @TempDir Path root;
  Path file;

  @BeforeEach
  void setup() throws Exception {
    file = root.resolve("settings.yml");
    Files.writeString(
        file,
        "# keep me\n"
            + "unknown: future-value\n"
            + "flag: true\n"
            + "number: 3\n"
            + "nested:\n"
            + "  drops:\n"
            + "    - item: DIAMOND\n"
            + "      chance: 0.5\n");
  }

  @Test
  void retiredPlayerFilesCannotBeEditedEvenThroughNormalizedAliases() throws Exception {
    for (String name : List.of("stats.yml", "skills.yml")) {
      Files.writeString(root.resolve(name), "enabled: true\n");
      assertThrows(IllegalArgumentException.class, () -> new AdminDocument(root, name));
      assertThrows(IllegalArgumentException.class, () -> new AdminDocument(root, "./" + name));
      assertEquals("enabled: true\n", Files.readString(root.resolve(name)));
    }
  }

  @Test
  void descendingBossPhaseRangesRemainEditableWithoutChangingMechanics() throws Exception {
    Path mob = root.resolve("boss.yml");
    Files.writeString(
        mob,
        "mob-id: king\n"
            + "base-type: ZOMBIE\n"
            + "stats:\n"
            + "  health:\n"
            + "    max: 100\n"
            + "phases:\n"
            + "  guard:\n"
            + "    hp-range: 100-65\n");
    var doc = new AdminDocument(root, "boss.yml");
    doc.set(List.of("name"), "King");
    doc.save();
    assertEquals(
        "100-65",
        YamlConfiguration.loadConfiguration(mob.toFile()).getString("phases.guard.hp-range"));
  }

  @Test
  void nestedMapsAndDropListsAreSavedWithExactPreviousFileBackup() throws Exception {
    byte[] before = Files.readAllBytes(file);
    var doc = new AdminDocument(root, "settings.yml");
    doc.set(List.of("nested", "drops", 0, "chance"), .25);
    doc.add(
        List.of("nested", "drops"),
        null,
        new LinkedHashMap<>(Map.of("item", "GOLD_INGOT", "chance", .1)));
    doc.set(List.of("flag"), false);
    doc.save();
    var yaml = YamlConfiguration.loadConfiguration(file.toFile());
    assertFalse(yaml.getBoolean("flag"));
    assertEquals("future-value", yaml.getString("unknown"));
    assertEquals(2, yaml.getMapList("nested.drops").size());
    assertEquals(.25, yaml.getMapList("nested.drops").getFirst().get("chance"));
    assertArrayEquals(before, Files.readAllBytes(root.resolve("settings.yml.admin.bak")));
    assertFalse(doc.dirty());
  }

  @Test
  void anotherAdminOrCommandCannotBeSilentlyOverwritten() throws Exception {
    var first = new AdminDocument(root, "settings.yml");
    var second = new AdminDocument(root, "settings.yml");
    first.set(List.of("number"), 5);
    first.save();
    byte[] newer = Files.readAllBytes(file);
    second.set(List.of("number"), 7);
    assertThrows(IllegalStateException.class, second::save);
    assertArrayEquals(newer, Files.readAllBytes(file));
  }

  @Test
  void defaultsAppearOnlyInDraftAndUnknownSettingsSurvive() throws Exception {
    byte[] before = Files.readAllBytes(file);
    var doc = new AdminDocument(root, "settings.yml");
    var defaults = new YamlConfiguration();
    defaults.set("number", 99);
    defaults.set("nested.new-setting", true);
    doc.defaults(defaults);
    assertEquals(3, doc.get(List.of("number")));
    assertEquals(true, doc.get(List.of("nested", "new-setting")));
    assertTrue(doc.dirty());
    assertArrayEquals(before, Files.readAllBytes(file));
    doc.save();
    assertEquals(
        "future-value", YamlConfiguration.loadConfiguration(file.toFile()).getString("unknown"));
  }

  @Test
  void scalarInputCannotInjectYamlAndPreservesNumberTypes() {
    assertEquals(4, AdminDocument.scalar(1, "4"));
    assertEquals(1.25, AdminDocument.scalar(1.0, "1,25"));
    assertEquals(true, AdminDocument.scalar(false, "an"));
    assertThrows(NumberFormatException.class, () -> AdminDocument.scalar(1, "1.5"));
    assertThrows(IllegalArgumentException.class, () -> AdminDocument.scalar(1.0, "NaN"));
    assertThrows(
        IllegalArgumentException.class, () -> AdminDocument.scalar("old", "x\nadmin: true"));
    assertEquals("!!java/object:example", AdminDocument.scalar("old", "!!java/object:example"));
  }

  @Test
  void traversalAndNonYamlFilesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new AdminDocument(root, "../outside.yml"));
    assertThrows(IllegalArgumentException.class, () -> new AdminDocument(root, "settings.txt"));
  }

  @Test
  void malformedFilesAndNonFiniteCandidatesRemainUntouched() throws Exception {
    Files.writeString(file, "broken: [\n");
    byte[] broken = Files.readAllBytes(file);
    assertThrows(Exception.class, () -> new AdminDocument(root, "settings.yml"));
    assertArrayEquals(broken, Files.readAllBytes(file));
    Files.writeString(file, "number: 1\n");
    var doc = new AdminDocument(root, "settings.yml");
    assertThrows(
        IllegalArgumentException.class, () -> doc.set(List.of("number"), Double.POSITIVE_INFINITY));
    assertEquals(1, doc.get(List.of("number")));
  }

  @Test
  void skillsBooleanAndMobHealthValidationHappenBeforeReplacement() throws Exception {
    Path skill = root.resolve("abilities.yml");
    Files.writeString(skill, "enabled: true\n");
    var doc = new AdminDocument(root, "abilities.yml");
    doc.set(List.of("enabled"), "false");
    assertThrows(IllegalArgumentException.class, doc::save);
    assertEquals("enabled: true\n", Files.readString(skill));
    Path mobs = Files.createDirectory(root.resolve("mobs"));
    Path mob = mobs.resolve("test.yml");
    Files.writeString(mob, "mob-id: test\nbase-type: ZOMBIE\nstats:\n  health:\n    max: 20\n");
    var mobDoc = new AdminDocument(root, "mobs/test.yml");
    mobDoc.set(List.of("stats", "health", "max"), -1);
    byte[] original = Files.readAllBytes(mob);
    assertThrows(IllegalArgumentException.class, mobDoc::save);
    assertArrayEquals(original, Files.readAllBytes(mob));
  }

  @Test
  void deleteAndAppendHandleListIndicesWithoutStringPathConfusion() throws Exception {
    var doc = new AdminDocument(root, "settings.yml");
    doc.delete(List.of("nested", "drops", 0));
    doc.add(List.of("nested", "drops"), null, Map.of("a.b", "literal dotted map key"));
    doc.save();
    var yaml = YamlConfiguration.loadConfiguration(file.toFile());
    assertEquals("literal dotted map key", yaml.getMapList("nested.drops").getFirst().get("a.b"));
    assertThrows(IllegalArgumentException.class, () -> doc.add(List.of(), "bad.key", true));
  }
}
