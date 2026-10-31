package org.plugin.theMob.admin;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.plugin.theMob.core.AtomicYaml;

/** An isolated editable tree. No live configuration changes until an explicit reload/restart. */
public final class AdminDocument {
  private final Path file;
  private final Path base;
  private byte[] expected;
  private final Map<String, Object> root;
  private boolean dirty;

  public AdminDocument(Path dataRoot, String relative) throws Exception {
    base = dataRoot.toRealPath();
    Path candidate = base.resolve(relative).normalize();
    if (base.equals(candidate.getParent())
        && Set.of("stats.yml", "skills.yml").contains(candidate.getFileName().toString()))
      throw new IllegalArgumentException(
          "Diese fruehere Spieler-/Skillkonfiguration wird nicht mehr verwendet");
    if (!candidate.startsWith(base) || !relative.endsWith(".yml"))
      throw new IllegalArgumentException("Ungueltige Konfigurationsdatei");
    for (Path cursor = candidate; !cursor.equals(base); cursor = cursor.getParent())
      if (Files.isSymbolicLink(cursor))
        throw new IllegalArgumentException("Symbolische Links sind nicht editierbar");
    if (!candidate.toRealPath().startsWith(base) || Files.size(candidate) > 2_000_000)
      throw new IllegalArgumentException("Datei zu gross oder ausserhalb TheMob");
    file = candidate;
    byte[] bytes = Files.readAllBytes(file);
    var yaml = AtomicYaml.read(file);
    root = castMap(copy(yaml));
    validate(root, 0, new int[1]);
    expected = digest(bytes);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  private static Object copy(Object value) {
    if (value instanceof ConfigurationSection section) return copy(section.getValues(false));
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((key, child) -> result.put(Objects.requireNonNull(key).toString(), copy(child)));
      return result;
    }
    if (value instanceof List<?> list)
      return new ArrayList<>(list.stream().map(AdminDocument::copy).toList());
    if (value == null
        || value instanceof String
        || value instanceof Boolean
        || value instanceof Number) return value;
    throw new IllegalArgumentException(
        "Nicht unterstuetzter YAML-Typ: " + value.getClass().getSimpleName());
  }

  private static void validate(Object value, int depth, int[] count) {
    if (depth > 32 || ++count[0] > 40_000)
      throw new IllegalArgumentException("Konfigurationsbaum zu gross/tief");
    if (value instanceof Map<?, ?> map)
      for (var entry : map.entrySet()) {
        String key = entry.getKey().toString();
        if (key.isBlank() || key.length() > 160 || key.chars().anyMatch(Character::isISOControl))
          throw new IllegalArgumentException("Ungueltiger Schluessel");
        validate(entry.getValue(), depth + 1, count);
      }
    else if (value instanceof List<?> list)
      for (Object child : list) validate(child, depth + 1, count);
    else if (value instanceof Number n && !Double.isFinite(n.doubleValue()))
      throw new IllegalArgumentException("Zahl muss endlich sein");
    else if (value instanceof String s && s.length() > 32768)
      throw new IllegalArgumentException("Text zu lang");
  }

  private static byte[] digest(byte[] bytes) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(bytes);
  }

  public String name() {
    return file.getFileName().toString();
  }

  public boolean dirty() {
    return dirty;
  }

  public void defaults(ConfigurationSection defaults) {
    if (defaults != null && merge(root, castMap(copy(defaults)))) dirty = true;
  }

  @SuppressWarnings("unchecked")
  private static boolean merge(Map<String, Object> target, Map<String, Object> defaults) {
    boolean changed = false;
    for (var entry : defaults.entrySet()) {
      if (!target.containsKey(entry.getKey())) {
        target.put(entry.getKey(), copy(entry.getValue()));
        changed = true;
      } else if (target.get(entry.getKey()) instanceof Map<?, ?> existing
          && entry.getValue() instanceof Map<?, ?> template)
        changed |= merge((Map<String, Object>) existing, (Map<String, Object>) template);
    }
    return changed;
  }

  public Object get(List<Object> path) {
    Object node = root;
    for (Object part : path)
      node =
          part instanceof Integer index
              ? ((List<?>) node).get(index)
              : ((Map<?, ?>) node).get(part);
    return node;
  }

  @SuppressWarnings("unchecked")
  public void set(List<Object> path, Object value) {
    if (path.isEmpty()) throw new IllegalArgumentException("Wurzel kann nicht ersetzt werden");
    Object parent = get(path.subList(0, path.size() - 1)), key = path.get(path.size() - 1);
    Object copied = copy(value);
    validate(copied, 0, new int[1]);
    if (key instanceof Integer index) ((List<Object>) parent).set(index, copied);
    else ((Map<String, Object>) parent).put(key.toString(), copied);
    dirty = true;
  }

  @SuppressWarnings("unchecked")
  public void delete(List<Object> path) {
    if (path.isEmpty()) throw new IllegalArgumentException("Wurzel kann nicht geloescht werden");
    Object parent = get(path.subList(0, path.size() - 1)), key = path.get(path.size() - 1);
    if (key instanceof Integer index) ((List<Object>) parent).remove(index.intValue());
    else ((Map<String, Object>) parent).remove(key.toString());
    dirty = true;
  }

  @SuppressWarnings("unchecked")
  public void add(List<Object> path, String key, Object value) {
    Object node = get(path);
    if (node instanceof List<?> list) ((List<Object>) list).add(copy(value));
    else if (node instanceof Map<?, ?> map) {
      if (key == null
          || key.isBlank()
          || key.contains(".")
          || key.length() > 160
          || key.chars().anyMatch(Character::isISOControl)
          || map.containsKey(key))
        throw new IllegalArgumentException("Schluessel ungueltig oder bereits vorhanden");
      ((Map<String, Object>) map).put(key, copy(value));
    } else throw new IllegalArgumentException("Kein Abschnitt/keine Liste");
    dirty = true;
  }

  public static Object scalar(Object before, String input) {
    if (input.length() > 32768 || input.chars().anyMatch(c -> c == '\n' || c == '\r' || c == 0))
      throw new IllegalArgumentException("Ungueltiger Text");
    if (before instanceof Boolean) {
      return switch (input.toLowerCase(Locale.ROOT)) {
        case "true", "an", "ja" -> true;
        case "false", "aus", "nein" -> false;
        default -> throw new IllegalArgumentException("true/false erwartet");
      };
    }
    if (before instanceof Integer || before instanceof Short || before instanceof Byte)
      return Integer.parseInt(input.strip());
    if (before instanceof Long) return Long.parseLong(input.strip());
    if (before instanceof Number) {
      double value = Double.parseDouble(input.strip().replace(',', '.'));
      if (!Double.isFinite(value)) throw new IllegalArgumentException("Zahl muss endlich sein");
      return value;
    }
    return input;
  }

  public void save() throws Exception {
    for (Path cursor = file; !cursor.equals(base); cursor = cursor.getParent())
      if (Files.isSymbolicLink(cursor))
        throw new IllegalStateException("Symbolischer Link wurde nachtraeglich angelegt");
    if (!file.toRealPath().startsWith(base))
      throw new IllegalStateException("Datei ausserhalb TheMob");
    if (Files.isSymbolicLink(file) || !Arrays.equals(expected, digest(Files.readAllBytes(file))))
      throw new IllegalStateException(
          "Datei wurde inzwischen geaendert. Neu oeffnen, damit fremde Aenderungen erhalten"
              + " bleiben.");
    validate(root, 0, new int[1]);
    var yaml = AtomicYaml.read(file);
    for (String key : new ArrayList<>(yaml.getKeys(false)))
      if (!root.containsKey(key)) yaml.set(key, null);
    root.forEach(yaml::set);
    // Parse the complete candidate before touching the disk.
    var check = new YamlConfiguration();
    check.loadFromString(yaml.saveToString());
    validateSemantics(check);
    AtomicYaml.replace(file, yaml, file.getFileName() + ".admin.bak");
    expected = digest(Files.readAllBytes(file));
    dirty = false;
  }

  private void validateSemantics(YamlConfiguration config) {
    if (file.getParent().getFileName().toString().equals("mobs") || config.contains("mob-id")) {
      String type = config.getString("base-type", config.getString("type", ""));
      org.bukkit.entity.EntityType entity =
          org.bukkit.entity.EntityType.valueOf(type.toUpperCase(Locale.ROOT));
      if (!entity.isAlive() || !entity.isSpawnable())
        throw new IllegalArgumentException("Kein lebender Mobtyp");
      double hp = config.getDouble("stats.health.max", 0);
      if (!Double.isFinite(hp) || hp <= 0 || hp > 1_000_000)
        throw new IllegalArgumentException("Maximales Leben muss 1..1.000.000 sein");
      String id = config.getString("mob-id", "");
      if (!id.isEmpty() && !id.matches("[a-z0-9_:-]{1,80}"))
        throw new IllegalArgumentException("Ungueltige Mob-ID");
      var phases = config.getConfigurationSection("phases");
      if (phases != null)
        for (String phase : phases.getKeys(false)) {
          String range = phases.getString(phase + ".hp-range", "");
          if (!range.matches("[0-9]+(?:\\.[0-9]+)?\\s*-\\s*[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("Phase " + phase + ": hp-range muss min-max sein");
          String[] bounds = range.split("-");
          double a = Double.parseDouble(bounds[0].strip()),
              b = Double.parseDouble(bounds[1].strip());
          if (Math.min(a, b) < 0 || Math.max(a, b) > 100 || a == b)
            throw new IllegalArgumentException("Phase " + phase + ": HP-Bereich ausserhalb 0..100");
        }
    }
    if (file.getFileName().toString().equals("abilities.yml"))
      for (var entry : config.getValues(true).entrySet())
        if (!(entry.getValue() instanceof ConfigurationSection)
            && !(entry.getValue() instanceof Boolean))
          throw new IllegalArgumentException("Faehigkeitsschalter muessen true/false sein");
  }
}
