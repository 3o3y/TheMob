package org.plugin.theMob.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ItemBuilderFromConfig {

    private final Plugin plugin;

    public ItemBuilderFromConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    // =====================================================
    // MAIN ENTRY
    // =====================================================
    public ItemStack build(Map<?, ?> cfg) {
        if (cfg == null) return null;

        String type = String.valueOf(cfg.get("type"));
        if ("BASE64".equalsIgnoreCase(type)) {
            return fromBase64(String.valueOf(cfg.get("value")));
        }

        if ("HEAD_TEXTURE".equalsIgnoreCase(type)) {
            return skullFromTexture(String.valueOf(cfg.get("value")));
        }

        String matKey = cfg.containsKey("material")
                ? String.valueOf(cfg.get("material"))
                : String.valueOf(cfg.get("item"));

        if (matKey == null || matKey.isBlank()) return null;

        Material mat;
        try {
            mat = Material.valueOf(matKey.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }

        ItemStack item = new ItemStack(mat, parseInt(cfg.get("amount"), 1));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        applyMeta(item, meta, cfg);
        item.setItemMeta(meta);
        return item;
    }

    // =====================================================
    // META
    // =====================================================
    private void applyMeta(ItemStack item, ItemMeta meta, Map<?, ?> cfg) {

        // ---------- NAME ----------
        List<Component> lore = new ArrayList<>();

        Object name = cfg.get("name");
        if (name instanceof String s) {
            meta.displayName(Component.text(cc(s)));
        } else if (name instanceof List<?> list && !list.isEmpty()) {
            meta.displayName(Component.text(cc(list.get(0).toString())));
            for (int i = 1; i < list.size(); i++) {
                lore.add(Component.text(cc(list.get(i).toString())));
            }
        }

        // ---------- LORE ----------
        Object loreObj = cfg.get("lore");
        if (loreObj instanceof List<?> list) {
            for (Object o : list) {
                lore.add(Component.text(cc(o.toString())));
            }
        }
        if (!lore.isEmpty()) meta.lore(lore);

        // ---------- ENCHANTS ----------
        Object enchants = cfg.get("enchants");
        if (enchants instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                Enchantment ench = Enchantment.getByKey(
                        NamespacedKey.minecraft(e.getKey().toString().toLowerCase(Locale.ROOT))
                );
                if (ench != null) {
                    meta.addEnchant(ench, parseInt(e.getValue(), 1), true);
                }
            }
        }

        // ---------- CUSTOM STATS (PDC) ----------
        Object stats = cfg.get("stats");
        if (stats instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                setDouble(meta, normalize(e.getKey().toString()), e.getValue());
            }
        }

        // ---------- LEATHER COLOR ----------
        if (meta instanceof LeatherArmorMeta leather && cfg.containsKey("color")) {
            Color color = parseColor(cfg.get("color"));
            if (color != null) {
                leather.setColor(color);
                item.setItemMeta(leather); // safety write-back
            }
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    }

    // =====================================================
    // COLOR PARSER
    // =====================================================
    private Color parseColor(Object o) {
        if (o instanceof String s) {

            // HEX: "#3366ff"
            if (s.startsWith("#")) {
                try {
                    return Color.fromRGB(Integer.parseInt(s.substring(1), 16));
                } catch (Exception ignored) {}
            }

            return switch (s.toUpperCase(Locale.ROOT)) {
                case "BLUE" -> Color.BLUE;
                case "RED" -> Color.RED;
                case "GREEN" -> Color.GREEN;
                case "BLACK" -> Color.BLACK;
                case "WHITE" -> Color.WHITE;
                case "PURPLE" -> Color.PURPLE;
                default -> null;
            };
        }

        if (o instanceof Map<?, ?> map) {
            try {
                int r = Integer.parseInt(map.get("r").toString());
                int g = Integer.parseInt(map.get("g").toString());
                int b = Integer.parseInt(map.get("b").toString());
                return Color.fromRGB(r, g, b);
            } catch (Exception ignored) {}
        }
        return null;
    }

    // =====================================================
    // BASE64 ITEM
    // =====================================================
    public ItemStack fromBase64(String base64) {
        if (base64 == null || base64.isBlank()) return null;

        try {
            byte[] bytes = Base64.getDecoder().decode(base64);

            try {
                ItemStack it = ItemStack.deserializeBytes(bytes);
                if (it != null && !it.getType().isAir()) return it;
            } catch (Throwable ignored) {}

            try (BukkitObjectInputStream in =
                         new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {

                Object obj = in.readObject();
                if (obj instanceof ItemStack it) return it;
                if (obj instanceof ItemStack[] arr && arr.length > 0) return arr[0];
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // =====================================================
    // HEAD TEXTURE
    // =====================================================
    public ItemStack skullFromTexture(String base64Texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        try {
            String json = new String(
                    Base64.getDecoder().decode(base64Texture),
                    StandardCharsets.UTF_8
            );

            int start = json.indexOf("\"url\":\"");
            if (start == -1) return head;
            start += 7;
            int end = json.indexOf('"', start);
            if (end == -1) return head;

            String urlStr = json.substring(start, end);

            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(urlStr));
            profile.setTextures(textures);

            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        } catch (Throwable ignored) {}

        return head;
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void setDouble(ItemMeta meta, String key, Object val) {
        try {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, key),
                    PersistentDataType.DOUBLE,
                    Double.parseDouble(val.toString())
            );
        } catch (Throwable ignored) {}
    }

    private int parseInt(Object o, int def) {
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Throwable e) { return def; }
    }

    private String cc(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace("-", "_").trim();
    }
}
