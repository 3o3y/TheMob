package org.plugin.theMob.integration;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import org.bukkit.plugin.Plugin;

/** Optional plugins are linked only after their presence and API contracts are checked. */
final class Reflect {
  private Reflect() {}

  static Class<?> type(Plugin plugin, String name) throws ClassNotFoundException {
    return Class.forName(name, true, plugin.getClass().getClassLoader());
  }

  static Object call(Object target, String name, Object... args)
      throws ReflectiveOperationException {
    Class<?> type = target instanceof Class<?> c ? c : target.getClass();
    for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
      for (Method method : cursor.getDeclaredMethods()) {
        if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
        Class<?>[] params = method.getParameterTypes();
        boolean matches = true;
        for (int i = 0; i < args.length; i++)
          if (args[i] != null && !box(params[i]).isInstance(args[i])) matches = false;
        if (!matches) continue;
        if (!method.trySetAccessible()) continue;
        return method.invoke(target instanceof Class<?> ? null : target, args);
      }
    // Includes default methods declared on public API interfaces.
    for (Method method : type.getMethods()) {
      if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
      boolean matches = true;
      for (int i = 0; i < args.length; i++)
        if (args[i] != null && !box(method.getParameterTypes()[i]).isInstance(args[i]))
          matches = false;
      if (matches) return method.invoke(target instanceof Class<?> ? null : target, args);
    }
    throw new NoSuchMethodException(type.getName() + "." + name + "/" + args.length);
  }

  private static Class<?> box(Class<?> type) {
    if (!type.isPrimitive()) return type;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == double.class) return Double.class;
    if (type == boolean.class) return Boolean.class;
    if (type == short.class) return Short.class;
    if (type == float.class) return Float.class;
    return type;
  }

  static Class<?> pluginClass(Plugin plugin, String simpleName) throws Exception {
    Path location =
        Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
    try (var jar = new JarFile(location.toFile())) {
      for (var entry :
          jar.stream().filter(e -> e.getName().endsWith("/" + simpleName + ".class")).toList())
        return type(plugin, entry.getName().replace('/', '.').replace(".class", ""));
    }
    throw new ClassNotFoundException(simpleName + " in " + location.getFileName());
  }
}
