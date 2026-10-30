# Minecraft-Kompatibilität 1.18–26.3

**TheMob 1.19.0** ist eine gemeinsame JAR mit `api-version: 1.18` und Java-17-Bytecode für Spigot/Paper. Das vollständige Projekt samt modernen Testwerkzeugen benötigt zum Bauen JDK 25. Folia wird nicht unterstützt; TheMob verwendet den normalen Bukkit-Serverthread.

| Minecraft | Empfohlene Server-Java-Version | TheMob |
|---|---|---|
| 1.18–1.19.4 | Java 17 | Gemeinsame JAR |
| 1.20–1.21.11 | Java 21 | Gemeinsame JAR |
| 26.1–26.3 | Java 25 | Gemeinsame JAR |

Diese Java-Zuordnung folgt den [Paper-Empfehlungen](https://docs.papermc.io/paper/getting-started/). Die Serverdistribution legt ihre Mindestlaufzeit selbst fest; die Java-17-Ausgabe des Plugins senkt diese Serveranforderung nicht. [Bukkit-Pluginmetadaten](https://docs.papermc.io/paper/dev/plugin-yml/).

## Versionsabhängige Inhalte

- Der Level-1–5-Katalog verwendet die tatsächlich verfügbaren EntityTypes und Materialien. Warden, Mace, Spears und andere spätere Inhalte existieren auf 1.18 nicht und werden dort nicht erzeugt. Alte eigene YAMLs mit unbekannten Vanilla-Typen müssen versionsgerecht konfiguriert sein.
- Vor 1.19.4 werden Item-/Schadensanzeigen mit kurzlebigen, markierten ArmorStands dargestellt; moderne Server verwenden Display-Entities. Diese Anzeigen zählen nicht als Kampfziele oder Radar-Mobs.
- `SCALE` wirkt nur auf Servern, die dieses Attribut anbieten. Die übrigen HP-/Schadens-/Phasenregeln bleiben erhalten.
- Dungeon-Geometrie und Schutzregeln bleiben gleich. Nicht verfügbare Tuffziegel werden durch Steinziegel ersetzt.
- Moderne und historische Attributnamen werden aufgelöst. Eigene Ausrüstungsmodifikatoren benutzen auf alten APIs stabile UUIDs; neue APIs erhalten NamespacedKeys. Ein Rejoin verbraucht das gespeicherte HP-Guthaben nur einmal.
- Ressourcenpaketangebote verwenden auf alten APIs ein einzelnes Paket, auf neueren die UUID-basierte API. Das gemeinsame CoinOverlay-Paket und BetterHud müssen zur jeweiligen Client-/Serverversion passen. Menü und Textkompass benötigen kein Paket.
- Optionale Anbieter brauchen jeweils eine zur Minecraft-/Java-Version passende Ausgabe. Die Unterstützung von 20 Schnittstellen bedeutet nicht, dass die aktuelle Ausgabe jedes Anbieters auf jedem alten Minecraft läuft. Inkompatible optionale HUD-Anbieter lassen den Kern auf Textdarstellung zurückfallen.
- Das Plugin konvertiert keine Minecraft-Welten oder fremden Itemdaten zwischen Serverversionen.

## Automatisierte Belege und Grenzen

`CompatibilityMatrixTest` löst die Klassen-, Feld-, Methoden- und Konstruktorverweise des tatsächlichen Java-17-Produktionscodes gegen 32 SHA-256-geprüfte offizielle Spigot-APIs auf. Klassen-/Interfacewechsel werden mitgeprüft. Display-Helfer werden auf alten APIs nur hinter dem geprüften Verfügbarkeitszweig ausgelassen. Alte und neue Enum-Switchtabellen dürfen unbenutzte neuere Konstanten enthalten; ungeschützte Feldaufrufe nicht.

Zusätzlich laufen echte Plugin-Lifecycles in MockBukkit 1.18.2 und 26.3. Die 1.18.2-Fixture ergänzt explizit dokumentierte Lücken der damaligen Testbibliothek; sie verändert keine Produktionspfade. Es wird kein übersprungener Test als Erfolg gewertet. [Aktuelle Zahlen](VALIDATION.md), [reproduzierbare API-Matrix](compatibility/README.md).

API-Verknüpfung und simulierte Serverabläufe sind keine Liveprüfung jeder Minecraft-Ausgabe. Native KI, Flug-/Wasserverhalten, Netzwerk/Clientdarstellung, Fremdplugins und große Dungeonbauten benötigen einen echten Testserver. Eine TPS-Steigerung wurde nicht gemessen.
