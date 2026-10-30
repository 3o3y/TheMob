package org.plugin.theMob.integration;

import java.lang.invoke.*;
import java.util.*;

/** Resolves one exact descriptor without enumerating unrelated optional provider types. */
final class NarrowApi {
  static Object call(
      Class<?> owner,
      Object receiver,
      String name,
      Class<?> result,
      Class<?>[] parameters,
      Object... args)
      throws Exception {
    try {
      MethodType signature = MethodType.methodType(result, parameters);
      MethodHandle method =
          receiver == null
              ? MethodHandles.publicLookup().findStatic(owner, name, signature)
              : MethodHandles.publicLookup().findVirtual(owner, name, signature);
      var values = new ArrayList<Object>();
      if (receiver != null) values.add(receiver);
      Collections.addAll(values, args);
      return method.invokeWithArguments(values);
    } catch (Exception | LinkageError failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new ReflectiveOperationException(owner.getName() + "." + name, failure);
    }
  }

  private NarrowApi() {}
}
