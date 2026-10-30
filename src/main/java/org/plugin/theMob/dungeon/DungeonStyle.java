package org.plugin.theMob.dungeon;

import java.util.Locale;

public enum DungeonStyle {
  CRYPT("krypta"),
  TOWER("turm"),
  MINE("mine"),
  WORLD_TREE("weltenbaum"),
  GOD_TOWER("gottesturm"),
  MAZE("labyrinth");

  private final String label;

  DungeonStyle(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static DungeonStyle parse(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "krypta", "crypt" -> CRYPT;
      case "turm", "tower" -> TOWER;
      case "mine", "minenschacht" -> MINE;
      case "weltenbaum", "tree" -> WORLD_TREE;
      case "gottesturm", "godtower" -> GOD_TOWER;
      case "labyrinth", "maze" -> MAZE;
      default ->
          throw new IllegalArgumentException(
              "Stile: random, krypta, turm, mine, weltenbaum, gottesturm, labyrinth");
    };
  }
}
