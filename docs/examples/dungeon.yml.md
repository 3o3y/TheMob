# dungeon.yml — configuration example

This is a reference file from TheMob 1.19.0. Keep your tuned production configuration when upgrading. Use materials and entity types supported by your Minecraft version.

```yaml
# Physische, leere Dungeons. Erzeugen ausschliesslich mit themob.dungeon.admin/themob.admin.
enabled: true
# Am Admin-Standort; Eingang am Standort, Anlage nach Norden.
# Im 113x133-Baufeld werden Bloecke ab Fundament bis zur Weltobergrenze entfernt/ersetzt.
auto-enter-creator: true
build:
  # Hauptthread: maximal ein Chunk pro Tick; kein WorldEdit und keine Extra-Abhaengigkeit.
  blocks-per-tick: 1536
  milliseconds-per-tick: 3.0
# Schutz ist immer aktiv. Admin-Baumodus explizit: /mob dungeon edit ID
# Routen aller Bauarten bilden mindestens 500 Bloecke begehbares Wegenetz.
```
