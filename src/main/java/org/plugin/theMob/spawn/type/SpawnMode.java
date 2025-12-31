package org.plugin.theMob.spawn.type;

public enum SpawnMode {
    ONETIME,
    ENDLESS;

    public static SpawnMode fromString(String s) {
        if (s == null) return ENDLESS;
        return s.equalsIgnoreCase("onetime") ? ONETIME : ENDLESS;
    }
}
