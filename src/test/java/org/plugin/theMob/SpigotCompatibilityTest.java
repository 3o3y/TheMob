package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks actual method descriptors in isolation from MockBukkit's Paper API. */
class SpigotCompatibilityTest {
  @Test
  void allBukkitBytecodeReferencesLinkAgainstSpigotWithoutPaper() throws Exception {
    var urls = new ArrayList<URL>();
    for (String entry : System.getProperty("spigot.compat.classpath").split(File.pathSeparator))
      urls.add(Path.of(entry).toUri().toURL());
    var errors = new ArrayList<String>();
    var checked = new HashSet<String>();
    Path classes =
        Path.of(TheMob.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (var loader =
            new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
        var files = Files.walk(classes)) {
      assertThrows(
          ClassNotFoundException.class, () -> loader.loadClass("io.papermc.paper.ServerBuildInfo"));
      for (Path path : files.filter(p -> p.toString().endsWith(".class")).toList()) {
        for (var entry : ClassFile.of().parse(Files.readAllBytes(path)).constantPool()) {
          if (entry instanceof ClassEntry type) {
            String name = type.asInternalName();
            assertFalse(
                name.startsWith("io/papermc/")
                    || name.startsWith("com/destroystokyo/")
                    || (name.startsWith("net/kyori/")
                        && !path.toString().replace('\\', '/').contains("/hud/compass/betterhud/")),
                path + ": " + name);
          }
          if (!(entry instanceof MemberRefEntry member)) continue;
          String owner = member.owner().asInternalName();
          if (!owner.startsWith("org/bukkit/")) continue;
          String name = member.name().stringValue();
          String descriptor = member.type().stringValue();
          String reference = owner + "." + name + descriptor;
          if (!checked.add(reference)) continue;
          try {
            Class<?> type = Class.forName(owner.replace('/', '.'), false, loader);
            boolean found;
            if (member instanceof FieldRefEntry) {
              found = type.getField(name).getType().descriptorString().equals(descriptor);
            } else if (name.equals("<init>")) {
              found =
                  Arrays.stream(type.getDeclaredConstructors())
                      .anyMatch(
                          c ->
                              MethodType.methodType(void.class, c.getParameterTypes())
                                  .descriptorString()
                                  .equals(descriptor));
            } else {
              found = hasMethod(type, name, descriptor, new HashSet<>());
            }
            if (!found) errors.add(reference);
          } catch (ReflectiveOperationException | LinkageError failure) {
            errors.add(reference + ": " + failure);
          }
        }
      }
    }
    assertTrue(checked.size() > 200, "API reference check did not run");
    assertEquals(java.util.List.of(), errors);
    System.out.println("Spigot API references resolved without Paper: " + checked.size());
  }

  private boolean hasMethod(Class<?> type, String name, String descriptor, Set<Class<?>> seen) {
    if (type == null || !seen.add(type)) return false;
    for (var method : type.getDeclaredMethods())
      if (method.getName().equals(name)
          && MethodType.methodType(method.getReturnType(), method.getParameterTypes())
              .descriptorString()
              .equals(descriptor)) return true;
    if (hasMethod(type.getSuperclass(), name, descriptor, seen)) return true;
    for (Class<?> parent : type.getInterfaces())
      if (hasMethod(parent, name, descriptor, seen)) return true;
    if (type.isInterface() && hasMethod(Object.class, name, descriptor, seen)) return true;
    return false;
  }
}
