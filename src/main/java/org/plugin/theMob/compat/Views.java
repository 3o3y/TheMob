package org.plugin.theMob.compat;

import org.bukkit.inventory.*;

/**
 * InventoryView changed from a class to an interface; reflection avoids an invoke-kind mismatch.
 */
public final class Views {
  private Views() {}

  private static final java.lang.reflect.Method TOP = topMethod();

  private static java.lang.reflect.Method topMethod() {
    try {
      return InventoryView.class.getMethod("getTopInventory");
    } catch (NoSuchMethodException failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  public static Inventory top(InventoryView view) {
    if (view == null) return null;
    try {
      return (Inventory) TOP.invoke(view);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("InventoryView API", failure);
    }
  }
}
