# Performance und Hot-/Cold-Zonen ab 1.12.0

Die Überarbeitung reduziert wiederkehrende weltweite Suchen, begrenzt die Arbeit pro Aktualisierung und pausiert TheMob-KI ohne aktive Spieler. Die Anzahl der verfügbaren Mobdefinitionen erzeugt dabei keine laufenden Mob-Ticks: Erst ein tatsächlich gespawnter Mob benötigt Laufzeit.

## Aktivität

`performance.activity` in config.yml; fehlende Werte in einer bestehenden Datei verwenden diese Standards:

```yaml
performance:
  arena-ticket-radius-chunks: 2
  nameplates-per-cycle: 128
  activity:
    enabled: true
    hot-radius: 64
    cold-radius: 96
    cold-grace-seconds: 10
    combat-keepalive-seconds: 20
    entities-per-cycle: 64
```

Ein bereits aktiver Mob wird außerhalb von 96 Blöcken nach zehn Sekunden kalt. Ein kalter Mob wacht erst wieder innerhalb von 64 Blöcken auf. Diese getrennten Grenzen verhindern ein ständiges Umschalten am Rand. Echter, nicht abgebrochener Schaden hält beteiligte TheMob-Mobs für 20 Sekunden aktiv und weckt sie sofort. Danach beginnt bei fehlenden Spielern erneut die Kaltwartezeit. Zuschauer und NPCs halten keine Zonen aktiv; Kreativspieler können Zonen aktiv halten, werden aber von Bossen nicht als Kampfziel gewählt.

Der Spielerindex wird einmal pro Sekunde aktualisiert. Die KI-Verwaltung verarbeitet standardmäßig maximal 64 Einträge alle zwei Ticks. Bei 500 geladenen Mobs benötigt ein vollständiger Durchlauf acht Zyklen; zusammen mit dem Spielerindex ergibt sich im Normalfall eine Reaktion innerhalb von ungefähr zwei Sekunden. Der Server-Takt und Änderungen am Entitybestand können diese Zeit beeinflussen. Kampfereignisse wecken unmittelbar.

Beim Kaltstellen werden die bisherigen AI-/Aware-Werte gespeichert. Aufwachen stellt diese Werte wieder her; ein bereits absichtlich deaktivierter AI-Modus wird nicht eingeschaltet. Die Werte werden am Mob gespeichert und über einen Chunkwechsel erhalten. Bei kalten Bossen pausieren die eigenen Verhaltensaktionen und Minionwellen; begonnene Spezialangriffe werden verworfen und beim Aufwachen nicht als Nachholserie abgefeuert.

Manuelle Mobs werden durch die Aktivitätsverwaltung nicht automatisch gelöscht. Feste Auto-Spawn-Arenen behalten die bisherige 60-Sekunden-Kaltfrist: Danach werden ihre geladenen Mobs administrativ entfernt. Eine Generation am Spawnpunkt verhindert, dass beim späteren Laden alter Chunks bereits bereinigte Exemplare zurückkehren. Es entstehen keine Killereignisse, Questfortschritte oder Drops. Beim Entladen von Bossen werden Laufzeitverhalten und Minions aufgeräumt; gültige Bosse erhalten beim Laden ihre zu ihrem Gesundheitsstand passende Phase zurück.

## Weniger wiederkehrende Arbeit

- Geladene TheMob-Mobs werden bei Spawn, Tod und Chunk-Lifecycle registriert. `getAllLivingMobs()` und `api.activeMobs()` durchsuchen keine Welten mehr.
- Bossziele und Namensschilder nutzen einen gemeinsamen räumlichen Spielerindex mit 64-Block-Zellen. Tote Spieler, Zuschauer, Kreative und NPCs werden für Kampfziele herausgefiltert.
- Namensschilder verarbeiten maximal 128 Einträge pro Fünf-Tick-Zyklus. Die doppelte eigene Block-Sichtprüfung wurde entfernt; die native Sichtprüfung bleibt erhalten.
- Das Radar liest die Entityliste eines Chunks pro HUD-Durchlauf einmal und verwendet sie für mehrere nahe Spieler. Der Cache wird nach jedem Durchlauf verworfen. Verborgene HUDs lösen keine Radarsuche aus.
- Neue Ausrüstung und bestehende Dropvorlagen werden beim Laden gebaut. Beim Kill werden Chancen gewürfelt und Itemvorlagen geklont; BASE64-/Lore-Verarbeitung wird nicht für jeden Drop wiederholt.
- Boss-Spezialangriffe verwenden den vorhandenen gemeinsamen Verhaltenstakt. Zusätzliche Projektile sind zeitlich begrenzt. Wiederholte Kaltprüfungen schreiben die AI-Einstellung nicht erneut.

Die normalen globalen/Welt-/Boss-/Minionlimits und die vorhandene TPS-Drosselung gelten unverändert. Chunk-/Weltscans bleiben für einmalige Startbereinigung, Wiederherstellung und administrative Gesamtbereinigung zulässig; sie gehören nicht mehr zu den regelmäßigen Mobabfragen.

## Arena-Chunks

Beim Aktivieren einer Arena werden ausschließlich **bereits geladene Chunks** mit einem Plugin-Ticket versehen. Die Ticketfläche ist standardmäßig auf Radius zwei Chunks begrenzt (höchstens 25); die Konfiguration begrenzt den Radius auf 0–4. Eine große Arena lädt dadurch keine neue Landschaft synchron. Sobald die Arena kalt wird, werden ihre Tickets freigegeben. Mehrere Arenen teilen Tickets mit Referenzzählung.

Die tatsächliche Arenengrenze des Spawnpunkts bleibt bestehen. Der Ticketradius begrenzt lediglich, welche geladenen Chunks durch TheMob gehalten werden. Für Spieler weit außerhalb einer Arena werden keine neuen Spawns erzwungen.

## Prüfen

`/mob performance` zeigt registrierte und kalte Mobs sowie die TPS-Messung. `/mob diag status`, `/mob diag budgets` und `/mob diag throttle` zeigen die bestehenden Servergrenzen. `catalog.yml` kann die neuen Definitionen und die zusätzliche Beute unabhängig von der Aktivitätsverwaltung konfigurieren.

Die Regression prüft unter anderem das Arbeitsbudget, Hot-/Cold-Hysterese, Wiederherstellen ursprünglicher AI, sofortiges Aufwachen im Kampf, NPC-/Kreativ-/Zuschauerausschluss, Chunkwechsel, Arena-Generationen, fehlende Chunk-Generierung, den Verzicht auf globale Entity-Suchen und die Aktualität des Radar-Caches. Die Bossprüfungen kontrollieren Vorwarnzeit, Ausweichen, Abklingzeit und die stabile Arena-Leine.

Diese Struktur- und Funktionstests sind kein Benchmark eines echten Minecraft-Servers. Eine feste TPS-Zahl oder prozentuale Lastverbesserung wurde nicht gemessen. Native KI, echte Weltgeometrie, viele gleichzeitige Spieler und das Zusammenwirken aller Serverplugins müssen auf der Testwelt beobachtet werden; dazu den Prüfstand in [VALIDATION.md](VALIDATION.md) beachten.

## Physische Dungeons ab 1.13.0

Eine eigene Hintergrundaufgabe berechnet den Bauplan ohne Bukkit-Weltzugriffe. Der zeitlich begrenzte Schreiber arbeitet im Hauptthread mit maximal einem Chunk gleichzeitig, standardmäßig 1536 Blockänderungen oder 3 ms pro Tick sowie einem begrenzten Scanbudget. Er überspringt bereits leere Luft, versetzt maximal 16 vorhandene Wesen pro Tick und gibt Chunk-Tickets nach jedem Chunk frei. Kein wiederkehrender Bautask läuft ohne aktiven Bau. Schutzabfragen verwenden einen Index nach Welt-UUID und Chunk; Kameradrehungen lösen keine Bereichssuche aus. Chunkladen/-erzeugung ist auf Spigot synchron und kann das Zeitbudget einmalig überschreiten. Echte Bauzeiten und TPS müssen auf dem Server geprüft werden. [DUNGEONS.md](DUNGEONS.md) enthält Größe, Freiräumen, Befüllung und Wiederaufnahme.
