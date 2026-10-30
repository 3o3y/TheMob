package org.plugin.theMob.compat;

import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlComments {
  private YamlComments() {}

  public static void enable(YamlConfiguration yaml) {
    try {
      yaml.options()
          .getClass()
          .getMethod("parseComments", boolean.class)
          .invoke(yaml.options(), true);
    } catch (NoSuchMethodException legacy) {
    } // Bukkit 1.18 does not expose comment parsing.
    catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("YAML comments", failure);
    }
  }
}
