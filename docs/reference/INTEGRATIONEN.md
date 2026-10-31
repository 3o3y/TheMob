# Optionale Integrationen in TheMob 1.19.1

Aktuelle Suite: **TheMob 1.19.1 / 20 ausgewählte Anbieter**. [Vollständige Funktionsmatrix](INTEGRATIONEN-20.md), [Adminmenü](ADMIN-MENUE.md). Die folgenden Abschnitte erläutern die ursprünglichen Adapter.

## Ursprüngliche Adapterverträge

Die neuen Level- und Bossdefinitionen verwenden dieselben API-Ereignisse und Zählregeln. Allgemeine BattlePass-Mobaufgaben zählen sie automatisch; Aufgaben mit festem Mob-ID-Filter benötigen die gewünschten neuen IDs. Einzelheiten in [MOBS-UND-ITEMS.md](MOBS-UND-ITEMS.md).

TheMob benötigt keinen dieser Anbieter zum Start. Installierte, aktivierte Anbieter werden automatisch erkannt; jeder ist über `integrations.<schlüssel>.enabled` abschaltbar. `/mob integrations` mit `themob.admin` zeigt ACTIVE, ABSENT, DISABLED oder INCOMPATIBLE mit Versions-/Fehlerdetail. Eine unpassende API deaktiviert die betroffene Anbindung. Neue Anbieter-JARs benötigen einen Serverneustart.

| Anbieter | Config-Schlüssel | Implementierte Funktion |
|---|---|---|
| Jobs Reborn | `jobs` | Vorhandene Jobs-Zahlungen, Punkte und Erfahrung pro Mob multiplizieren; kein zusätzlicher künstlicher Kill |
| Citizens 2 | `citizens` | NPC-Erkennung, Ausschluss von Killfortschritt/Zielwahl; optional Mobspawn bei Rechtsklick auf festgelegte NPC-ID |
| PikaMug Quests 5.x | `quests` | Eigenes Custom Objective `TheMob Kill`, Filter nach Mob-ID und Boss, Fortschritt über die native Objective-API |
| AdvancedPlugins BattlePass 5.0.12 | `battlepass` | Mob-/Boss-/Minionkills, eigene Aufgabenprofile, native Variablen und sechs Questbeispiele |
| PlaceholderAPI | `placeholderapi` | `%themob_...%` für Anzeigen und andere Placeholder-Nutzer |
| Vault | `vault` | Optionaler zusätzlicher Geldbetrag pro Mob an den Bukkit-Killer über den registrierten Economy-Provider |
| WorldGuard 7 | `worldguard` | Regionsflag `mob-spawning` am tatsächlichen Spawnort für normale Mobs, Bosse und Minions beachten |

Die Adapter folgen den öffentlichen Verträgen von [Jobs](https://www.zrips.net/jobs/api/), [Citizens](https://wiki.citizensnpcs.co/API), [Quests](https://pikamug.gitbook.io/quests/master/custom-quest-api), [BattlePass](https://battlepass.advancedplugins.net/developer-api/developer-api-creating-your-own-quest-types), [PlaceholderAPI](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/), [Vault](https://github.com/MilkBowl/VaultAPI) und [WorldGuard](https://worldguard.enginehub.org/en/latest/developer/regions/region-queries/).

## Jobs und Vault

Die mitgelieferten acht Mobdateien wurden nicht verändert. Folgende optionale Ergänzung kann in einer eigenen Mobdatei gesetzt werden; ein Kopierbeispiel liegt unter `examples/mob-integrations.yml`:

```yaml
integrations:
  jobs:
    money-multiplier: 1.0
    points-multiplier: 1.0
    experience-multiplier: 1.0
  vault:
    money: 0.0
```

Die Standards 1/1/1 und 0 erhalten bestehende Auszahlungen. Jobs muss den normalen Entity-Kill im jeweiligen Beruf bereits vergüten. Werte werden auf endliche, nichtnegative Multiplikatoren begrenzt. Vault ist ein zusätzlicher Betrag; wer schon über Jobs oder Todesbefehle bezahlt, sollte ihn gezielt einstellen. Ein Vault-Plugin allein ist keine Währung: Ein Economy-Provider muss seinen Service registrieren.

## Citizens

```yaml
# config.yml, unter integrations.citizens
enabled: true
click-cooldown-seconds: 10
spawn-on-click:
  '12': pig_lvl_1
  '27': zombie_boss_template
```

Nur diese IDs lösen einen Spawn aus. Spieler benötigen `themob.npc.use` (standardmäßig erlaubt); NPC-Klicks mit vorheriger Event-Abweisung bleiben unberührt. Cooldown pro Spieler und NPC; nach Erfolg gesetzt. Der Spawn verwendet dieselben Regions-, Budget- und Boss-Cooldown-Prüfungen wie die API. Nicht konfigurierte NPCs behalten ihr übliches Verhalten.

## Quests

Nach vollständigem Serverstart im Quests-Editor das Custom Objective **TheMob Kill** wählen. Felder: **Mob ID** (`*` für alle oder mehrere IDs mit Komma) und **Boss only** (`true`/`false`), dazu die gewünschte Anzahl. Fortschritt läuft für alle passenden aktiven Quests über Quests selbst, einschließlich dessen eigener Abschluss-/Gruppenregeln. Das Objective wird früh beim Laden registriert. Wird Quests erst später aktiviert oder extern neu geladen, gegebenenfalls seine Questdefinitionen erneut laden; Plugin-Hotloader sind nicht unterstützt.

## BattlePass

Die vollständige Einrichtung steht in [BATTLEPASS.md](BATTLEPASS.md). `/mob battlepass` zeigt Status, Mob-IDs, Aufgabenprofile und Konfigurationsfehler. `plugins/TheMob/battlepass-examples.yml` enthält sechs kopierbare Questvorlagen; gewünschte Einträge in eine aktive BattlePass-Questdatei übernehmen.

Typen: `themob_kill`, `themob_boss_kill`, `themob_minion_kill` mit Mob-ID als `variable` (`none` für alle) sowie `themob_objective` mit Profil-ID aus `battlepass.yml`. Profile kombinieren Mobgruppen, Welt, Mob-/Boss-/Minionart, Spawnart und letzte Bossphase. Native BattlePass-Subvariablen enthalten zusätzlich `mob`, `world`, `phase`, `spawn-reason`, `entity-type`, `boss` und `minion`. Fortschritt, Speicherung und Belohnungen bleiben bei BattlePass.

Die bisherige Registrierung mit `Function` war mit BattlePass 5.0.12 unvereinbar. Die neue Anbindung unterstützt dessen echte `Instantiator[]`-Signatur. Separate API-Tests führen Registrierung, Action-Builder und Variablenfilter mit der bereitgestellten Original-JAR 5.0.12 aus. Die Premium-JAR wird weder mitgeliefert noch für den normalen Build vorausgesetzt. Ein vollständiger BattlePass-Serverbetrieb mit Datenbank und Saison wurde hier nicht gestartet.

Ab 1.11.1 wird die `ActionRegistry` direkt über ihren exakten statischen API-Zugriff geladen. Die Hauptklasse von BattlePass enthält auch optionale PlaceholderAPI-/Vault-Typen; eine Auflistung ihrer Methoden würde bei fehlenden Anbietern scheitern. Die BattlePass-Anbindung benötigt diese Anbieter nicht. Bei der bisherigen Meldung INCOMPATIBLE mit `PlaceholderExpansion` die TheMob-JAR aktualisieren und den Server vollständig neu starten; vorhandene Questdateien behalten.

## PlaceholderAPI

Ohne zusätzlichen eCloud-Download verfügbar:

| Placeholder | Inhalt |
|---|---|
| `%themob_mobs_alive%` | Gesamtes geladenes TheMob-Budget einschließlich Bosse/Minions |
| `%themob_bosses_alive%` | Lebende Bosse |
| `%themob_minions_alive%` | Lebende Minions |
| `%themob_registered_mobs%` | Anzahl geladener Mobdefinitionen |
| `%themob_tps%` | Plugin-TPS-Messung mit zwei Nachkommastellen |
| `%themob_version%` | TheMob-Version |
| `%themob_kills_session%` | Eigene berechtigte Kills seit Pluginstart |
| `%themob_boss_kills_session%` | Eigene Bosskills seit Pluginstart |

Die Anzeigen lesen vorberechnete Snapshots; sie dürfen auch asynchron abgefragt werden. Globale Werte werden einmal pro Sekunde aktualisiert. Sessionzähler überleben `/mob reload`, werden aber beim Server-/Pluginneustart zurückgesetzt und ersetzen keine persistente Spielerstatistik.

## Zählregeln und Prüfgrenzen

Nur der native Bukkit-Spielerkiller zählt; NPCs und Tode ohne Spielerkiller erzeugen keinen externen Fortschritt. Minions sind mit `integrations.track-minion-kills: false` standardmäßig ausgeschlossen; dieselbe Regel blockiert ihre Jobs-Zahlungen. Mit true können sie anhand der Elternboss-ID zählen. Reload, Cleanup, API-Remove und Killall erzeugen weder API-Todesereignisse noch externe Belohnungen.

WorldGuard bekommt keinen Spieler als Abfragekontext: `mob-spawning: deny` gilt daher auch für administrative/API-Spawns und Minion-Wellen; es wird kein Spieler-Bypass angewendet. Andere Schutzplugins können native Spawnereignisse zusätzlich abbrechen.

Alle Anbieter gleichzeitig auf einem echten Server wurden hier nicht ausgeführt. Vor Produktiveinsatz insbesondere Quests-Gruppenfortschritt, BattlePass-Questkonfiguration, tatsächliche Jobs-Auszahlung und Economy-Provider auf der Testwelt prüfen. Die vollständige Testabgrenzung steht in `VALIDATION.md`.

## Dungeon-Erweiterung in 1.14.0

Freie Bewegung mit PKPlugin 0.7.1, Endboss-Zuordnung, Gruppenabschluss, `themob_dungeon_complete` und öffentliche Abschlussereignisse stehen in [DUNGEON-API.md](DUNGEON-API.md). `/mob dungeon boss ID Boss-Mob-ID` legt den Endboss fest; das Betreten allein wertet keinen Dungeonabschluss.
