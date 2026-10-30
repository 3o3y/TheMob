package org.plugin.theMob.integration;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Minimal Java-17 class writer for late-bound provider subclasses; no bundled provider/ASM. */
final class ApiSubclass extends ClassLoader {
  private ApiSubclass(ClassLoader parent) {
    super(parent);
  }

  static Object container(Class<?> base, JavaPlugin plugin, String namespace) throws Exception {
    if (!Modifier.isAbstract(base.getModifiers())) {
      var constructor = base.getDeclaredConstructor(JavaPlugin.class, String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(plugin, namespace);
    }
    String name = "org.plugin.theMob.generated.BattlePassContainer";
    byte[] bytes = subclass(name, base.getName(), true);
    Class<?> generated =
        new ApiSubclass(base.getClassLoader()).defineClass(name, bytes, 0, bytes.length);
    return generated.getConstructor(JavaPlugin.class, String.class).newInstance(plugin, namespace);
  }

  static Object objective(Class<?> base) throws Exception {
    String name = "org.plugin.theMob.generated.QuestsObjective";
    byte[] bytes = subclass(name, base.getName(), false);
    Class<?> generated =
        new ApiSubclass(base.getClassLoader()).defineClass(name, bytes, 0, bytes.length);
    return generated.getConstructor().newInstance();
  }

  private record Method(int name, int descriptor, int stack, int locals, byte[] code) {}

  private static byte[] subclass(String name, String parent, boolean arguments) throws IOException {
    Pool pool = new Pool();
    int own = pool.type(name.replace('.', '/')), supertype = pool.type(parent.replace('.', '/'));
    int codeName = pool.utf("Code"), constructor = pool.utf("<init>");
    String descriptor =
        arguments ? "(Lorg/bukkit/plugin/java/JavaPlugin;Ljava/lang/String;)V" : "()V";
    int desc = pool.utf(descriptor),
        call = pool.method(parent.replace('.', '/'), "<init>", descriptor);
    byte[] init =
        arguments
            ? new byte[] {
              0x2a, 0x2b, 0x2c, (byte) 0xb7, (byte) (call >> 8), (byte) call, (byte) 0xb1
            }
            : new byte[] {0x2a, (byte) 0xb7, (byte) (call >> 8), (byte) call, (byte) 0xb1};
    var methods = new ArrayList<Method>();
    methods.add(new Method(constructor, desc, arguments ? 3 : 1, arguments ? 3 : 1, init));
    if (!arguments) {
      int value = pool.string("TheMob");
      methods.add(
          new Method(
              pool.utf("getModuleName"),
              pool.utf("()Ljava/lang/String;"),
              1,
              1,
              new byte[] {0x13, (byte) (value >> 8), (byte) value, (byte) 0xb0}));
    }
    var bytes = new ByteArrayOutputStream();
    var out = new DataOutputStream(bytes);
    out.writeInt(0xcafebabe);
    out.writeShort(0);
    out.writeShort(61);
    pool.write(out);
    out.writeShort(0x0021);
    out.writeShort(own);
    out.writeShort(supertype);
    out.writeShort(0);
    out.writeShort(0);
    out.writeShort(methods.size());
    for (Method method : methods) {
      out.writeShort(1);
      out.writeShort(method.name());
      out.writeShort(method.descriptor());
      out.writeShort(1);
      out.writeShort(codeName);
      out.writeInt(12 + method.code().length);
      out.writeShort(method.stack());
      out.writeShort(method.locals());
      out.writeInt(method.code().length);
      out.write(method.code());
      out.writeShort(0);
      out.writeShort(0);
    }
    out.writeShort(0);
    out.flush();
    return bytes.toByteArray();
  }

  private static final class Pool {
    private final List<byte[]> values = new ArrayList<>();
    private final Map<String, Integer> indices = new HashMap<>();

    int utf(String value) throws IOException {
      Integer old = indices.get("u:" + value);
      if (old != null) return old;
      var bytes = new ByteArrayOutputStream();
      var out = new DataOutputStream(bytes);
      out.writeByte(1);
      out.writeUTF(value);
      values.add(bytes.toByteArray());
      int index = values.size();
      indices.put("u:" + value, index);
      return index;
    }

    int pair(String key, int tag, int a, int b) throws IOException {
      Integer old = indices.get(key);
      if (old != null) return old;
      var bytes = new ByteArrayOutputStream();
      var out = new DataOutputStream(bytes);
      out.writeByte(tag);
      out.writeShort(a);
      if (b != 0) out.writeShort(b);
      values.add(bytes.toByteArray());
      int index = values.size();
      indices.put(key, index);
      return index;
    }

    int type(String name) throws IOException {
      return pair("c:" + name, 7, utf(name), 0);
    }

    int string(String value) throws IOException {
      return pair("s:" + value, 8, utf(value), 0);
    }

    int method(String owner, String name, String descriptor) throws IOException {
      return pair(
          "m:" + owner + name + descriptor,
          10,
          type(owner),
          pair("n:" + name + descriptor, 12, utf(name), utf(descriptor)));
    }

    void write(DataOutputStream out) throws IOException {
      out.writeShort(values.size() + 1);
      for (byte[] value : values) out.write(value);
    }
  }
}
