package org.plugin.theMob.compat;

import org.bukkit.Bukkit;

public final class Displays {
  private Displays() {}

  private static final boolean AVAILABLE = present();

  private static boolean present() {
    try {
      Class.forName("org.bukkit.entity.TextDisplay", false, Bukkit.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException older) {
      return false;
    }
  }

  public static boolean available() {
    return AVAILABLE;
  }
}
