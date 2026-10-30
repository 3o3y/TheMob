package org.plugin.theMob.spawn.type;

public enum SpawnMode {
  ONETIME,
  ENDLESS;

  public static SpawnMode fromString(String s) {
    if (s == null) {
      return ENDLESS;
    } else {
      return s.equalsIgnoreCase("onetime") ? ONETIME : ENDLESS;
    }
  }
}
