package org.plugin.theMob.integration;

import java.util.*;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.TheMobItemProvider;

/** Namespaced native factories; no payment, delivery or progress is implied by item creation. */
public final class ProviderItems {
  private final TheMob plugin;
  private final Map<String, Function<String, ItemStack>> factories = new LinkedHashMap<>();
  private final Map<ItemStack, String> identities = new LinkedHashMap<>();

  private record Created(String id, ItemStack prototype) {}

  private final Map<ItemStack, Created> instances = new IdentityHashMap<>();

  ProviderItems(TheMob plugin) {
    this.plugin = plugin;
  }

  void add(String id, Function<String, ItemStack> factory) {
    factories.put(id, factory);
  }

  public Collection<String> ids() {
    var ids = new TreeSet<String>();
    factories.keySet().stream()
        .filter(
            id ->
                IntegrationCatalog.find(id)
                    .map(t -> plugin.integrations().active(t.name()))
                    .orElse(true))
        .forEach(ids::add);
    ids.add("themob");
    for (var r : Bukkit.getServicesManager().getRegistrations(TheMobItemProvider.class))
      if (r.getPlugin().isEnabled())
        try {
          if (valid(r.getProvider().id())) ids.add(r.getProvider().id());
        } catch (Exception | LinkageError ignored) {
        }
    return List.copyOf(ids);
  }

  private static boolean valid(String id) {
    return id != null && id.matches("[a-z][a-z0-9_-]{0,39}") && !id.equals("themob");
  }

  public Optional<String> identity(ItemStack item) {
    if (item == null || item.getType().isAir()) return Optional.empty();
    Created known = instances.get(item);
    if (known != null && known.prototype().isSimilar(item)) return Optional.of(known.id());
    ItemStack key = item.clone();
    key.setAmount(1);
    return Optional.ofNullable(identities.get(key));
  }

  public Optional<ItemStack> create(String reference) {
    if (reference == null || reference.length() > 220) return Optional.empty();
    int split = reference.indexOf(':');
    String provider = split < 0 ? "themob" : reference.substring(0, split).toLowerCase(Locale.ROOT),
        id = split < 0 ? reference : reference.substring(split + 1);
    if (id.isBlank() || !id.matches("[A-Za-z0-9_./:-]{1,180}")) return Optional.empty();
    ItemStack item = null;
    try {
      if (provider.equals("themob")) item = plugin.gear() == null ? null : plugin.gear().item(id);
      else if (factories.containsKey(provider)) item = factories.get(provider).apply(id);
      else
        for (var registration :
            Bukkit.getServicesManager().getRegistrations(TheMobItemProvider.class).stream()
                .sorted(
                    Comparator.comparingInt(
                            (org.bukkit.plugin.RegisteredServiceProvider<TheMobItemProvider> r) ->
                                r.getPriority().ordinal())
                        .reversed())
                .toList()) {
          if (!registration.getPlugin().isEnabled()) continue;
          var external = registration.getProvider();
          if (valid(external.id()) && provider.equals(external.id())) {
            item = external.create(id).orElse(null);
            break;
          }
        }
      if (item == null || item.getType().isAir() || item.getAmount() < 1) return Optional.empty();
      item = item.clone();
      if (!provider.equals("themob")) {
        ItemStack key = item.clone();
        key.setAmount(1);
        if (identities.size() >= 2048 && !identities.containsKey(key))
          identities.remove(identities.keySet().iterator().next());
        identities.put(key, provider + ":" + id);
        if (instances.size() >= 2048) instances.remove(instances.keySet().iterator().next());
        instances.put(item, new Created(provider + ":" + id, key));
      }
      return Optional.of(item);
    } catch (Exception | LinkageError failure) {
      plugin
          .getLogger()
          .warning(
              "Fremditem nicht erzeugt: " + provider + " / " + failure.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
