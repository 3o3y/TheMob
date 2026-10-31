# TheMob mit anderen Plugins verbinden

Stand: TheMob **1.19.1**, Minecraft **1.18–26.3**, Java je Serverversion (Pluginkern: Java 17). Die Verbindung läuft innerhalb des Servers über Bukkit-Dienste und Ereignisse. Die öffentliche API ist bereits in TheMob enthalten.

**Im Spiel:** `/mob` → Plugin-Anbindungen. Alle bisherigen Befehle bleiben erhalten. [Komplettes Adminmenü](ADMIN-MENUE.md), [Funktionen aller 20 Anbieter](INTEGRATIONEN-20.md). Die folgende Tabelle erklärt die ursprünglichen sieben Adapter; die ergänzten 13 stehen in der vollständigen Matrix.

## Der einfache Ablauf

1. Bei gestopptem Server die neue TheMob-JAR und die gewünschten passenden Plugins in `plugins/` ablegen. Je Plugin nur eine Version behalten. Vorhandene Datenordner behalten.
2. Server vollständig starten.
3. Als OP/Admin oder über die Konsole **`/mob connect check`** ausführen.
4. Bei Quests/BattlePass die gewünschte Aufgabe im jeweiligen Plugin anlegen beziehungsweise aktivieren. Eine bereitstehende Anbindung erstellt keine neue Saison, keine Aufgaben und keine Auszahlungen von selbst.

Die 20 ausgewählten Anbindungen sind standardmäßig aktiviert und erkennen vorhandene Anbieter automatisch. Für diese Schnittstellenänderung braucht es kein neues Ressourcenpaket. Ein zusätzliches API-JAR gehört nicht in `plugins/`.

Ein eigenes Spieler-Skill-/Stats-System gibt es nicht mehr. Boss- und Itemeffekte werden über `/mob abilities` verwaltet. Fremde Spieler-Skillplugins führen ihre eigenen Werte und Menüs. [Anleitung](FAEHIGKEITEN.md).

| Plugin | Was Admins einstellen |
|---|---|
| Jobs Reborn (`jobs`) | Den normalen Entity-Kill im Beruf konfigurieren. TheMob kann dessen vorhandene Auszahlung/XP multiplizieren; es erfindet keinen zusätzlichen Kill. |
| PikaMug Quests (`quests`) | Im Questeditor **TheMob Kill** als Custom Objective hinzufügen; Mob-ID oder `*`, optional Bossfilter und Anzahl einstellen. Gleichnamige andere Quests-Produkte benötigen einen eigenen passenden Adapter. |
| AdvancedPlugins BattlePass (`battlepass`) | Vorlagen aus `battlepass-combat-examples.yml` (20 neue Aufgaben), `battlepass-examples.yml` oder `battlepass-dungeons.yml` in eine aktive Woche übernehmen; danach `/bpa reload`. `/mob battlepass` zeigt Typen und Fehler. |
| Citizens 2 (`citizens`) | NPC-Erkennung erfolgt automatisch. Spawn-Klicks erst nach ausdrücklicher NPC-ID-Zuordnung in `integrations.citizens.spawn-on-click`; Recht `themob.npc.use` beachten. |
| PlaceholderAPI (`placeholderapi`) | `%themob_...%` ist nach erfolgreicher Registrierung verfügbar; keine zusätzliche eCloud-Erweiterung nötig. |
| Vault (`vault`) | Zusätzlich muss ein Economy-Anbieter seinen Vault-Service registrieren. Der zusätzliche TheMob-Geldbetrag ist standardmäßig 0. Bereits über Jobs/Quests vergütete Aktionen nicht nochmals auszahlen. |
| WorldGuard 7 (`worldguard`) | Die Flag `mob-spawning` in den gewünschten Regionen einstellen. TheMob prüft den aufgelösten Spawnort. Eine inkompatible aktivierte WorldGuard-Anbindung blockiert Spawns bis zur Reparatur; der Fehler erlaubt keinen Schutz-Bypass. |

Die bisher gegen BattlePass geprüfte Anbieter-JAR ist 5.0.12. Die übrigen Adapter verwenden ihre dokumentierten Schnittstellen; die konkrete Kombination eurer installierten Versionen bleibt auf dem Server zu prüfen. Ein fremdes Plugin kann TheMob über die offene API unterstützen, benötigt dafür jedoch einen eingebauten Adapter oder ein entsprechendes Add-on. Die Existenz einer API verbindet nicht automatisch jedes beliebige Plugin.

## Verbindung prüfen und Änderungen vormerken

```text
/mob connect check
/mob connect jobs
/mob connect quests
/mob connect battlepass
/mob connect jobs enable
/mob connect jobs disable
```

`/mob integrations` bleibt als Alias erhalten. `an`/`aus` funktionieren ebenfalls. Tabvervollständigung zeigt die erlaubten Anbieter und Aktionen.

Die Ausgabe unterscheidet **Anbindung bereit**, **nicht installiert/aktiv**, **deaktiviert** und **nicht kompatibel**. Dazu kommen API-Status und passende Einrichtungshinweise. „Anbindung bereit“ bestätigt den eingebauten Adapter, nicht die richtige Auswahl einer aktiven Quest oder eines Jobs im Fremdplugin.

`enable` und `disable` speichern genau den jeweiligen Schalter in `config.yml`. **Die Änderung wirkt nach einem vollständigen Serverneustart.** Bis dahin steht „Neustart ausstehend“ an der laufenden Verbindung. Der Befehl lädt keine Plugins nach und setzt weder Mobs noch Quests zurück. Nicht `/mob reload` als Verbindungsassistent verwenden: Dieser bestehende Befehl entfernt aktive TheMob-Mobs durch seinen Hard-Reset.

Vor jeder erfolgreichen Änderung entsteht eine bytegenaue Sicherung der vorherigen Datei: `plugins/TheMob/config.yml.connect.bak`. Eigene Konfigurationseinträge bleiben erhalten. Ungültige YAML-Dateien oder falsche Abschnittstypen werden nicht durch Defaults überschrieben. Schlägt das Speichern fehl, bleibt auch der Schalter im Arbeitsspeicher unverändert. Die Sicherung enthält den Zustand vor dem letzten erfolgreichen Connect-Befehl.

## Rechte

| Recht | Standard | Wirkung |
|---|---|---|
| `themob.integrations.view` | OP | Status und Einrichtungshinweise lesen |
| `themob.integrations.manage` | OP | Einzelne eingebaute Anbindungen für den Neustart konfigurieren; enthält Leserecht |
| `themob.admin` | OP | Enthält beide Rechte |

Normale Spieler können dadurch keine Verbindung verändern und keine Mobs erzeugen. Die Schalter steuern die eingebauten TheMob-Adapter. Fremde Add-ons verwalten ihre eigene Funktion und ihre eigenen Berechtigungen.

## Eigenes Add-on eines Entwicklers installieren

Der Entwickler baut sein Add-on gegen das **TheMob Developer SDK 1.19.1 / API 1.3.1**. Du installierst nur dessen fertige Plugin-JAR und die von ihm genannten Anbieter. Die Dateien `TheMob-API-...jar`, `...sources.jar`, `...javadoc.jar` und der Maven-Ordner sind Entwicklerdateien und bleiben außerhalb des Server-Pluginordners.

Der Entwickler sollte `depend: [TheMob]`, eindeutige Befehle und passende Adminrechte deklarieren. Add-ons sind ausführbarer Servercode; installiere nur JARs aus einer Quelle, der du auch normalen Pluginzugriff anvertrauen würdest. Die Schnittstelle erhält Spawnlimits, Boss-Cooldowns und abbrechbare PreSpawn-Prüfungen, ersetzt aber keine Kontrolle über die installierten Plugin-JARs.

Das SDK-Beispiel ist ausdrücklich ein **Test-/Lern-Add-on**. Es zahlt kein Geld und vergibt keine echten Questbelohnungen. Sein OP-Befehl `/themobexample status` prüft die Verbindung, `/themobexample spawn <Mob-ID>` fordert bewusst einen Testspawn über die normale API an. Es wird nicht automatisch zusammen mit TheMob installiert.

## Wenn etwas nicht zählt

- **Fehlt:** gewünschtes Plugin und dessen Abhängigkeiten installieren/aktivieren, dann vollständig neu starten.
- **Nicht kompatibel:** Fehlermeldung aus `/mob connect <Anbieter>` und Serverlog dem Entwickler geben. Keine fremden API-JARs in den Pluginordner kopieren.
- **Adapter bereit, Aufgabe bleibt 0:** aktive Quest/Woche, IDs, Welt, Boss-/Minionfilter und Killerzuordnung prüfen. Ein administratives Entfernen, Reload oder bloßes Dungeon-Betreten ist kein gewerteter Kill/Abschluss.
- **Doppelte Belohnung:** nur einen Fortschritts-/Auszahlungsweg pro Ziel verwenden; insbesondere nicht parallel Bukkit-Tod und TheMob-Todesereignis für dieselbe Aufgabe zählen.
- **Neue Dateiversion installiert:** vollständig neu starten; Plugin-Hotloader sind für diese Kombination nicht unterstützt.

Diese Version wurde lokal mit automatisierten API-, Rechte-, Backup- und Beispielprüfungen kontrolliert. Ein Test mit echten Spielern und euren installierten Fremdplugins bleibt notwendig. Die Dateien sind zum Weitergeben vorbereitet; ein öffentlicher Downloadhost oder GitHub-Release wird dadurch nicht automatisch angelegt.
