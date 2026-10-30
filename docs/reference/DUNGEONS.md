# Physische Dungeons in TheMob 1.13.0

`/mob dungeon` erzeugt **am Standort des ausführenden Admins** eine große, leere Anlage. Der Eingang liegt am bisherigen Standort, die Anlage erstreckt sich nach **Norden (-Z)**. Spieler können gemeinsam hineinlaufen oder `/mob dungeon enter ID` nutzen. TheMob setzt keine Mobs, Loottruhen oder Questziele ein; diese richtet ihr selbst mit euren Mobs, Eiern, Befehlen und Block-Spawnern ein.

## Größe und Bauarten

Alle Anlagen reservieren **113 × 133 Blöcke Grundfläche**. Die zugesagten 500 Blöcke beziehen sich auf **mindestens 500 begehbare Blöcke im verbundenen Wegenetz über alle Ebenen und Flügel**. Das ist keine 500 × 500 große Fläche und keine zugesicherte kürzeste Entfernung zwischen Eingang und Ziel.

| Stil | Ebenen | Aufbau |
|---|---:|---|
| `krypta` | 4–6 | Große Kammern, zufällige Verbindungen und Schleifen, Bosskammer, steinerne Toranlage |
| `turm` | 5–7 | Runde Festung, Kammern, zentrale Halle, Fenster, Zinnen und breite Treppen |
| `mine` | 4–6 | Felsmassiv mit großen Stollenhallen, Holzstützen und mehreren Ebenen |
| `weltenbaum` | 5–7 | Hohler Stamm, Wurzeln, begehbare Äste und blattgedeckte Asthallen |
| `gottesturm` | **10** | Hoher Festungsturm mit zehn begehbaren Stockwerken und Kammern |
| `labyrinth` | 1 | Zufällig verzweigtes, zusammenhängendes Labyrinth mit 169 Zellen |

Ohne Stil wählt der Befehl zufällig aus allen sechs Familien. Jede neue Anlage erhält einen neuen Zufallsseed und eine fortlaufende ID wie `d00001`. Räume, Verbindungen, Asthallen und/oder Materialwahl variieren mit dem Seed. Es werden keine fertigen Bauvorlagen kopiert. Gleichwertige Flächen auf unterschiedlichen Welten sind möglich; bestehende Dungeon-Grundflächen derselben Welt dürfen sich nicht überlappen.

Die Toröffnung ist für Gruppen ausgelegt, Hauptgänge sind 13 Blöcke breit, die normalen Verbindungen sieben und die Treppen fünf. Das Labyrinth hat fünf Blöcke breite Wege. Es gibt keine Leitern oder Sprungpassagen als notwendige Verbindung der Stockwerke.

## Freiräumen und Bau

**Der Erzeugungsbefehl ersetzt vorhandenes Gelände und Bauten im Baufeld.** Entfernt/ersetzt wird die gesamte Grundfläche vom Fundament bis zur oberen Baugrenze der Welt. Der Fundamentboden liegt drei Blöcke unter den Füßen des ausführenden Admins; der Innenboden eine Ebene unter dessen Füßen. Unter dem Fundament und außerhalb der Grundfläche werden keine Blöcke verändert. Entfernte Container und Blöcke werfen keine Items ab. Ein automatisches Wiederherstellen des vorherigen Geländes ist nicht enthalten.

Vor dem ersten Blockwechsel prüft TheMob die Welthöhe. Spieler im Baufeld werden an einen freien Warteplatz südlich der Anlage versetzt. Bereits vorhandene lebende Wesen werden pro bearbeitetem Chunk ebenfalls dorthin versetzt. Blockiert eine andere Erweiterung deren Teleport, stoppt der Bau. Ein fehlender sicherer Warteplatz oder eine Überschneidung mit einem vorhandenen Dungeon verhindert den Start. Baubereiche sind bereits ab Reservierung geschützt und während des aktiven Baus für Spieler gesperrt.

Die Planung läuft ohne Weltzugriff auf einem eigenen Hintergrundthread. Blockänderungen laufen im Hauptthread: höchstens ein Chunk gleichzeitig, ein Blockbudget und ein Zeitbudget je Tick. Leere Luft wird übersprungen. Maximal 16 vorhandene Wesen werden je Tick versetzt. Nach jedem Chunk wird dessen temporäres Ladeticket freigegeben. Das Zeitbudget ist ein Arbeitsziel; ein einzelnes synchrones Laden/Erzeugen eines Chunks kann länger dauern. Es wurde kein echter Serverlasttest durchgeführt.

Es läuft immer nur ein Bau pro Server. Der Initiator bekommt Fortschritt und wird nach Fertigstellung standardmäßig zum Eingang teleportiert. Konfiguration in `plugins/TheMob/dungeon.yml`:

```yaml
enabled: true
auto-enter-creator: true
build:
  blocks-per-tick: 1536
  milliseconds-per-tick: 3.0
```

## PKPlugin-Anbindung

Mit **TheMob 1.13.1 und PKPlugin 0.7.1** wird der Dungeonbereich über die öffentliche TheMob-API erkannt. `/mob dungeon enter ID` kann in einen fertigen Dungeon teleportieren, ohne dort ein eigenes PK-Gebiet zu benötigen. Innerhalb der fertigen Grundfläche gelten keine PK-Bewegungsgrenze und keine PK-Grenzpartikel; das funktioniert auch nach einem erneuten Login. Außerhalb gelten wieder die normalen Gebietsrechte. Der Rückteleport `/mob dungeon leave` benutzt weiterhin den gespeicherten Ausgangspunkt und respektiert dessen aktuelle Rechte.

PK-Banner einschließlich neutraler Admin-Banner sind im gesamten reservierten Baufeld gesperrt, auch im Dungeon-Baumodus und bei unfertigen Anlagen. PK-Gebäude dürfen ebenfalls keine Dungeonblöcke überschreiben. TheMobs vorhandener Bau-/Explosionsschutz und seine Befehlsberechtigungen gelten weiter. Für dieses Update beide JARs ersetzen und vollständig neu starten; vorhandene Dungeons und das Ressourcenpaket bleiben erhalten. API-Vertrag für weitere Add-ons: [API.md](API.md).

## Befehle und Rechte

| Befehl | Wirkung |
|---|---|
| `/mob dungeon` | Zufällige Anlage am Admin-Standort erzeugen |
| `/mob dungeon gottesturm` | Bestimmte Bauart erzeugen; gilt entsprechend für die anderen Stile |
| `/mob dungeon status` | Aktueller Baufortschritt |
| `/mob dungeon list [Seite]` | Liste der Anlagen |
| `/mob dungeon info ID` | Stil, Ebenen, Wege, Seed; für Admins auch Eingang und Welt |
| `/mob dungeon enter ID` | Zum Eingang eines fertigen Dungeons |
| `/mob dungeon leave` | Zum gespeicherten Ausgangspunkt zurück; beim Hineinlaufen ohne Teleport zum südlichen Außenbereich |
| `/mob dungeon edit ID` | Persönlichen Admin-Baumodus nur für diese Anlage aktivieren |
| `/mob dungeon edit off` | Baumodus ausschalten |
| `/mob dungeon cancel` | Aktiven Bau stoppen; Teilbau und Schutz bleiben erhalten |
| `/mob dungeon resume ID` | Unterbrochenen/abgebrochenen Bau aus demselben Seed neu aufbauen; der Teilbau wird ersetzt |

**Nur Admins mit `themob.admin` oder `themob.dungeon.admin`** können erzeugen, abbrechen, weiterbauen oder den Baumodus aktivieren. Beide Rechte sind standardmäßig OP-Rechte. Andere Spieler haben standardmäßig `themob.dungeon.enter`; das erzeugt keine Baurechte. Fertige Anlagen können gemeinsam betreten werden, es gibt keine Spielerinstanzen oder Gruppengrößenbegrenzung.

## Unzerstörbare Bauten und spätere Befüllung

Der Schutz umfasst die ganze reservierte Grundfläche auf jeder Höhe. Abbauen und Platzieren sind standardmäßig **auch für OPs gesperrt**. Nur der ausdrücklich aktivierte persönliche Baumodus erlaubt dem jeweiligen Admin Blockänderungen in genau dieser Anlage, etwa zum Einrichten von TheMob-Spawnern. Das Recht wird bei jedem Zugriff erneut geprüft. Logout, Weltwechsel, Verlassen des Dungeons und Reload beenden den Baumodus.

Explosionen, Feuer, Kolben, Flüssigkeitsfluss, blockverändernde Wesen, Verfall/Wachstum, Schwämme und Portalbildung dürfen die Anlage nicht verändern. Werkzeuge dürfen den Weltenbaum nicht entrinden. Türen und normale Interaktionen bleiben nutzbar. Der Schutz verarbeitet Bukkit-Ereignisse; direkte Blockänderungen anderer Admin-Erweiterungen wie WorldEdit oder bewusst umgehender Plugins können dadurch nicht verhindert werden.

Natürliche Spawns, Patrouillen und Überfälle sind innerhalb der Anlage gesperrt. Manuelle TheMob-Spawns und vorhandene API-Ereignisse, BattlePass-Killziele, Jobs und Quests bleiben verwendbar. Der Generator setzt keine eigenen Spawnregeln oder Mobauswahl für den Dungeon fest. Damit bleiben Befüllung und Aufgaben vollständig bei euch.

`plugins/TheMob/dungeons.yml` speichert ID, Welt-UUID, Position, Seed, Zustand, Ebenen und Raumpositionen atomar, mit zusätzlicher `.bak`-Sicherung. Nach Neustart/Reload wird ein laufender Bau als `INTERRUPTED` geladen; er wird nicht stillschweigend fortgesetzt. Sein Schutz bleibt aktiv. `/mob dungeon resume ID` setzt ihn ausdrücklich neu auf. Fertige Anlagen und die Metadaten müssen gemeinsam mit der jeweiligen Serverwelt erhalten bleiben. Es wird keine zusätzliche Welt und kein zusätzliches Ressourcenpaket benötigt.

## Dungeon-Erweiterung in 1.14.0

Freie Bewegung mit PKPlugin 0.7.1, Endboss-Zuordnung, Gruppenabschluss, `themob_dungeon_complete` und öffentliche Abschlussereignisse stehen in [DUNGEON-API.md](DUNGEON-API.md). `/mob dungeon boss ID Boss-Mob-ID` legt den Endboss fest; das Betreten allein wertet keinen Dungeonabschluss.

## Ausgangsaltar ab 1.15.0

Im Bossraum gibt es jetzt einen physischen Ausgangsaltar mit PK-Zielauswahl und Rückreise. Vorhandene besuchte Dungeons werden nachgerüstet. `/mob dungeon altar ID [here]` ergänzt, repariert oder versetzt den Altar. TheMob 1.19.0 und eine passende PKPlugin-Version (mindestens 0.7.2 für Reisen) verwenden. Anleitung: [AUSGANGSALTAR.md](AUSGANGSALTAR.md). Die Reise-API liegt in TheMob-Dungeon-API-1.3.1.jar; Haupt-API 1.1 und bestehende BattlePass-Ziele bleiben kompatibel.
