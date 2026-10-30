package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.io.File;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.invoke.MethodType;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Links the actual production bytecode to each checksum-verified official Bukkit API. */
class CompatibilityMatrixTest {
  record Source(String name, ClassModel model) {}

  @Test
  void linksAgainstEveryStableMinecraftApiFrom118To263() throws Exception {
    var apis =
        JsonParser.parseString(Files.readString(Path.of(System.getProperty("compat.api.manifest"))))
            .getAsJsonArray();
    Path classes =
        Path.of(TheMob.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var sources = new ArrayList<Source>();
    try (var files = Files.walk(classes)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList())
        sources.add(
            new Source(
                classes.relativize(file).toString().replace('\\', '/'),
                ClassFile.of().parse(Files.readAllBytes(file))));
    }
    var dependencies = new ArrayList<URL>();
    for (String file : System.getProperty("compat.compile.classpath").split(File.pathSeparator))
      if (!Path.of(file).getFileName().toString().startsWith("spigot-api-"))
        dependencies.add(Path.of(file).toUri().toURL());
    var all = new TreeSet<String>();
    var report = new ArrayList<String>();
    for (var entry : apis) {
      var api = entry.getAsJsonObject();
      String version = api.get("version").getAsString();
      Path jar = Path.of(api.get("file").getAsString());
      assertEquals(
          api.get("sha256").getAsString(),
          HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
      var urls = new ArrayList<URL>();
      urls.add(jar.toUri().toURL());
      urls.addAll(dependencies);
      var errors = new TreeSet<String>();
      var checked = new HashSet<String>();
      try (var loader =
          new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
        for (Source source : sources) {
          // These implementations are reached only through Displays.available(); older APIs
          // deliberately use the ordinary armor-stand implementations instead.
          if (source.name().startsWith("org/plugin/theMob/compat/Modern")) {
            try {
              loader.loadClass("org.bukkit.entity.TextDisplay");
            } catch (ClassNotFoundException unavailable) {
              continue;
            }
          }
          boolean switchTable =
              source.model().fields().stream()
                  .anyMatch(f -> f.fieldName().stringValue().startsWith("$SwitchMap"));
          for (var constant : source.model().constantPool()) {
            if (constant instanceof ClassEntry entryType) {
              String className = entryType.asInternalName();
              if (className.startsWith("org/bukkit/"))
                try {
                  loader.loadClass(className.replace('/', '.'));
                } catch (ClassNotFoundException missing) {
                  errors.add(className + " | " + source.name() + " | missing class");
                }
            }
            if (!(constant instanceof MemberRefEntry member)) continue;
            String owner = member.owner().asInternalName(),
                name = member.nameAndType().name().stringValue(),
                desc = member.nameAndType().type().stringValue();
            if (!owner.startsWith("org/bukkit/")) continue;
            String reference = owner + "." + name + desc;
            if (!checked.add(reference + (switchTable ? "|switch" : ""))) continue;
            try {
              Class<?> type = loader.loadClass(owner.replace('/', '.'));
              if (member instanceof FieldRefEntry) {
                try {
                  if (!type.getField(name).getType().descriptorString().equals(desc))
                    throw new NoSuchFieldException(reference);
                } catch (NoSuchFieldException failure) {
                  if (!switchTable) throw failure;
                }
              } else if (name.equals("<init>")) {
                if (Arrays.stream(type.getDeclaredConstructors())
                    .noneMatch(
                        c ->
                            MethodType.methodType(void.class, c.getParameterTypes())
                                .descriptorString()
                                .equals(desc))) throw new NoSuchMethodException(reference);
              } else {
                if (member instanceof InterfaceMethodRefEntry && !type.isInterface()
                    || member instanceof MethodRefEntry && type.isInterface())
                  throw new IncompatibleClassChangeError("class/interface call");
                if (!method(type, name, desc, new HashSet<>()))
                  throw new NoSuchMethodException(reference);
              }
            } catch (ReflectiveOperationException | LinkageError failure) {
              errors.add(
                  reference + " | " + source.name() + " | " + failure.getClass().getSimpleName());
            }
          }
        }
      }
      report.add(version + ": " + checked.size() + " references, " + errors.size() + " unresolved");
      report.addAll(errors);
      all.addAll(errors);
    }
    Path output = Path.of("build/reports/compatibility/api-matrix.txt");
    Files.createDirectories(output.getParent());
    Files.write(output, report);
    System.out.println(
        "API versions checked: " + apis.size() + "; distinct unresolved references: " + all.size());
    assertTrue(
        all.isEmpty(),
        String.join("\n", all.stream().limit(40).toList())
            + "\nFull report: "
            + output.toAbsolutePath());
  }

  boolean method(Class<?> type, String name, String descriptor, Set<Class<?>> seen) {
    if (type == null || !seen.add(type)) return false;
    for (var method : type.getDeclaredMethods())
      if (method.getName().equals(name)
          && MethodType.methodType(method.getReturnType(), method.getParameterTypes())
              .descriptorString()
              .equals(descriptor)) return true;
    if (method(type.getSuperclass(), name, descriptor, seen)) return true;
    for (var parent : type.getInterfaces()) if (method(parent, name, descriptor, seen)) return true;
    return type.isInterface() && method(Object.class, name, descriptor, seen);
  }
}
