package org.plugin.theMob.item;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;

public final class ItemBuilderFromConfig {
  private final Plugin plugin;

  public ItemBuilderFromConfig(Plugin plugin) {
    this.plugin = plugin;
  }

  public ItemStack build(Map<?, ?> cfg) {
    if (cfg != null
        && cfg.get("catalog-item") instanceof String id
        && plugin instanceof org.plugin.theMob.TheMob owner
        && owner.gear() != null) {
      var item = owner.gear().item(id);
      if (item != null)
        item.setAmount(
            org.plugin.theMob.compat.Numbers.clamp(
                parseInt(cfg.get("amount"), 1), 1, item.getMaxStackSize()));
      return item;
    }
    if (cfg == null) {
      return null;
    } else {
      String type = String.valueOf(cfg.get("type"));
      if ("BASE64".equalsIgnoreCase(type)) {
        return this.fromBase64(String.valueOf(cfg.get("value")));
      } else if ("HEAD_TEXTURE".equalsIgnoreCase(type)) {
        return this.skullFromTexture(String.valueOf(cfg.get("value")));
      } else {
        String matKey =
            cfg.containsKey("material")
                ? String.valueOf(cfg.get("material"))
                : String.valueOf(cfg.get("item"));
        if (matKey != null && !matKey.isBlank()) {
          Material mat;
          try {
            mat = Material.valueOf(matKey.toUpperCase(Locale.ROOT));
          } catch (Exception var7) {
            return null;
          }

          ItemStack item =
              new ItemStack(
                  mat,
                  org.plugin.theMob.compat.Numbers.clamp(
                      this.parseInt(cfg.get("amount"), 1), 1, Math.max(1, mat.getMaxStackSize())));
          ItemMeta meta = item.getItemMeta();
          if (meta == null) {
            return item;
          } else {
            this.applyMeta(item, meta, cfg);
            item.setItemMeta(meta);
            return item;
          }
        } else {
          return null;
        }
      }
    }
  }

  private void applyMeta(ItemStack item, ItemMeta meta, Map<?, ?> cfg) {
    List<String> lore = new ArrayList<>();
    Object name = cfg.get("name");
    if (name instanceof String s) {
      meta.setDisplayName(this.cc(s));
    } else if (name instanceof List<?> list && !list.isEmpty()) {
      meta.setDisplayName(this.cc(list.get(0).toString()));
      for (int i = 1; i < list.size(); i++) lore.add(this.cc(list.get(i).toString()));
    }
    if (cfg.get("lore") instanceof List<?> lines) {
      for (Object line : lines) lore.add(this.cc(String.valueOf(line)));
    }
    if (!lore.isEmpty()) meta.setLore(lore);

    if (cfg.get("enchants") instanceof Map<?, ?> map) {
      for (Entry<?, ?> e : map.entrySet()) {
        Enchantment ench =
            Enchantment.getByKey(
                NamespacedKey.minecraft(e.getKey().toString().toLowerCase(Locale.ROOT)));
        if (ench != null) {
          meta.addEnchant(ench, this.parseInt(e.getValue(), 1), true);
        }
      }
    }

    if (cfg.get("stats") instanceof Map<?, ?> map) {
      for (Entry<?, ?> ex : map.entrySet()) {
        this.setDouble(meta, this.normalize(ex.getKey().toString()), ex.getValue());
      }
    }

    if (meta instanceof LeatherArmorMeta leather && cfg.containsKey("color")) {
      Color color = this.parseColor(cfg.get("color"));
      if (color != null) {
        leather.setColor(color);
        item.setItemMeta(leather);
      }
    }

    meta.addItemFlags(new ItemFlag[] {ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS});
  }

  private Color parseColor(Object o) {
    if (o instanceof String s) {
      if (s.startsWith("#")) {
        try {
          return Color.fromRGB(Integer.parseInt(s.substring(1), 16));
        } catch (Exception var6) {
        }
      }

      String var9 = s.toUpperCase(Locale.ROOT);

      return switch (var9) {
        case "BLUE" -> Color.BLUE;
        case "RED" -> Color.RED;
        case "GREEN" -> Color.GREEN;
        case "BLACK" -> Color.BLACK;
        case "WHITE" -> Color.WHITE;
        case "PURPLE" -> Color.PURPLE;
        default -> null;
      };
    } else {
      if (o instanceof Map<?, ?> map) {
        try {
          int r = Integer.parseInt(map.get("r").toString());
          int g = Integer.parseInt(map.get("g").toString());
          int b = Integer.parseInt(map.get("b").toString());
          return Color.fromRGB(r, g, b);
        } catch (Exception var7) {
        }
      }

      return null;
    }
  }

  public ItemStack fromBase64(String base64) {
    if (base64 != null && !base64.isBlank()) {
      try {
        if (base64.length() > 3_000_000) return null;
        byte[] bytes = Base64.getDecoder().decode(base64);

        try {
          // Optional legacy Paper NBT format; standard Bukkit streams work on both servers.
          ItemStack it =
              (ItemStack)
                  ItemStack.class
                      .getMethod("deserializeBytes", byte[].class)
                      .invoke(null, (Object) bytes);
          if (it != null && !it.getType().isAir()) {
            return it;
          }
        } catch (Throwable var8) {
        }

        BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes));
        in.setObjectInputFilter(
            info -> {
              if (info.depth() > 32
                  || info.references() > 10000
                  || info.streamBytes() > 2_000_000
                  || info.arrayLength() > 10000) return java.io.ObjectInputFilter.Status.REJECTED;
              Class<?> type = info.serialClass();
              if (type == null) return java.io.ObjectInputFilter.Status.UNDECIDED;
              while (type.isArray()) type = type.getComponentType();
              String name = type.getName();
              return type.isPrimitive()
                      || name.startsWith("java.lang.")
                      || name.startsWith("java.util.")
                      || name.equals("com.google.common.collect.ImmutableMap$SerializedForm")
                      || name.equals("com.google.common.collect.ImmutableList$SerializedForm")
                      || name.equals("com.google.common.collect.ImmutableSet$SerializedForm")
                      || com.google.common.collect.ImmutableMap.class.isAssignableFrom(type)
                      || com.google.common.collect.ImmutableCollection.class.isAssignableFrom(type)
                      || name.equals("org.bukkit.util.io.Wrapper")
                      || ItemStack.class.isAssignableFrom(type)
                  ? java.io.ObjectInputFilter.Status.ALLOWED
                  : java.io.ObjectInputFilter.Status.REJECTED;
            });

        label63:
        {
          ItemStack var13;
          label62:
          {
            try {
              Object obj = in.readObject();
              if (obj instanceof ItemStack it) {
                var13 = it;
                break label62;
              }

              if (!(obj instanceof ItemStack[] arr) || arr.length <= 0) {
                break label63;
              }

              var13 = arr[0];
            } catch (Throwable var9) {
              try {
                in.close();
              } catch (Throwable var7) {
                var9.addSuppressed(var7);
              }

              throw var9;
            }

            in.close();
            return var13;
          }

          in.close();
          return var13;
        }

        in.close();
      } catch (Throwable var10) {
      }

      return null;
    } else {
      return null;
    }
  }

  public ItemStack skullFromTexture(String base64Texture) {
    return org.plugin.theMob.util.SkullUtil.fromBase64(base64Texture);
  }

  private void setDouble(ItemMeta meta, String key, Object val) {
    try {
      meta.getPersistentDataContainer()
          .set(
              new NamespacedKey(this.plugin, key),
              PersistentDataType.DOUBLE,
              Double.parseDouble(val.toString()));
    } catch (Throwable var5) {
    }
  }

  private int parseInt(Object o, int def) {
    try {
      return Integer.parseInt(String.valueOf(o));
    } catch (Throwable var4) {
      return def;
    }
  }

  private String cc(String s) {
    return s == null ? "" : s.replace('&', '§');
  }

  private String normalize(String s) {
    return s == null ? "" : s.toLowerCase(Locale.ROOT).replace("-", "_").trim();
  }
}
