package org.plugin.theMob.util;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class SkullUtil {
  private SkullUtil() {}

  public static ItemStack fromBase64(String base64) {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    if (!(head.getItemMeta() instanceof SkullMeta meta)) return head;
    URL skin = skinUrl(base64);
    if (skin == null) return head;
    UUID id = UUID.nameUUIDFromBytes(skin.toExternalForm().getBytes(StandardCharsets.UTF_8));
    try {
      Class<?> profileType = Class.forName("org.bukkit.profile.PlayerProfile");
      Class<?> texturesType = Class.forName("org.bukkit.profile.PlayerTextures");
      Object profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class).invoke(null, id);
      Object textures = profileType.getMethod("getTextures").invoke(profile);
      texturesType.getMethod("setSkin", URL.class).invoke(textures, skin);
      profileType.getMethod("setTextures", texturesType).invoke(profile, textures);
      SkullMeta.class.getMethod("setOwnerProfile", profileType).invoke(meta, profile);
    } catch (ClassNotFoundException | NoSuchMethodException legacy) {
      try {
        Class<?> profileType = Class.forName("com.mojang.authlib.GameProfile"),
            propertyType = Class.forName("com.mojang.authlib.properties.Property");
        Object profile =
            profileType.getConstructor(UUID.class, String.class).newInstance(id, "TheMobHead");
        Object properties = profileType.getMethod("getProperties").invoke(profile);
        Object property =
            propertyType.getConstructor(String.class, String.class).newInstance("textures", base64);
        properties
            .getClass()
            .getMethod("put", Object.class, Object.class)
            .invoke(properties, "textures", property);
        var field = meta.getClass().getDeclaredField("profile");
        if (!field.trySetAccessible()) return head;
        field.set(meta, profile);
      } catch (ReflectiveOperationException unsupported) {
        return head;
      }
    } catch (ReflectiveOperationException failure) {
      return head;
    }
    head.setItemMeta(meta);
    return head;
  }

  static URL skinUrl(String base64) {
    if (base64 == null || base64.isBlank() || base64.length() > 65536) return null;
    try {
      String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
      String url =
          JsonParser.parseString(json)
              .getAsJsonObject()
              .getAsJsonObject("textures")
              .getAsJsonObject("SKIN")
              .get("url")
              .getAsString();
      URI uri = URI.create(url);
      if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())) return null;
      return uri.toURL();
    } catch (Exception invalidTexture) {
      return null;
    }
  }
}
