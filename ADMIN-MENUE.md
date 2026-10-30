# TheMob 1.19.0: Administration im Spiel

Als OP oder mit `themob.admin.menu` öffnet **`/mob`** ein normales Inventarmenü. Es benötigt kein Ressourcenpaket. **Alle bisherigen Befehle, Argumente, Aliasse und Befehlsrechte bleiben erhalten.** Konsole und Spieler ohne Menüzutritt erhalten bei `/mob` weiter die Hilfe.

| Bereich | Möglichkeiten |
|---|---|
| Mobs und Bosse | Katalog durchsuchen, Mob spawnen, Ei oder Blockspawner erhalten, Mob kopieren oder neu anlegen, vollständige Definition bearbeiten |
| Items und Drops | Rare-/Epic-Katalog, native Fremditems testen, Droptabellen im Mobeditor bearbeiten |
| Spawns und Wellen | Assistent für feste Punkte, Zufallsradius, Spielerverfolgung und Zufallswelt; laufende Punkte auflisten/entfernen; vollständiger Editor für `auto_spawn.yml` |
| Dungeons | Stil wählen, Bau bestätigen, Anlagen auflisten/betreten/verlassen, Baumodus, Endboss, Ausgangsaltar, Bau abbrechen/fortsetzen |
| Skills | Globale und einzelne Boss-/Itemeffekte für den Neustart ein-/ausschalten; gespeicherte und laufende Werte sehen |
| Alle Einstellungen | Eigene Konfigurationsdateien, verschachtelte Abschnitte und Listen vollständig bearbeiten, einschließlich Bossphasen, Ausrüstung, Drops und Integrationsoptionen |
| Plugin-Anbindungen | Status und Aktivierung der 20 ausgewählten Anbieter, Konfiguration, registrierte Add-on-Menüs |
| Diagnose und Wartung | TPS, verwaltete Mobs/Cold-Zone, Limits, HUD-Konfiguration, persönlicher HUD-Schalter, Debug, Item-Base64, bestätigtes Entfernen/Neuladen |

## Werte und Phasen bearbeiten

`/mob` → Mobs und Bosse → Mob wählen → Definition bearbeiten. Ein generierter Katalogmob erhält auf Wunsch eine eigene Datei in `mobs/admin/`, die ihn nach Neustart/Hard-Reload überschreibt. Originalkatalog und mitgelieferte Mobdateien bleiben erhalten. Bei einer vorhandenen Mobdatei wird diese bearbeitet.

Abschnitte und Listen lassen sich öffnen, ergänzen und nach Bestätigung entfernen. Bei neuen Einträgen den Typ wählen: Text, Ganzzahl, Dezimalzahl, Schalter, Abschnitt oder Liste. Vorhandene Werte behalten beim Ändern ihren Typ. Zahlen werden geprüft; Dezimalwerte akzeptieren Punkt oder Komma. Namen und Zahlen kurz im Chat eingeben. Die Eingabe wird nicht öffentlich gesendet; `abbrechen` führt zurück, nach zwei Minuten verfällt sie.

Der Editor zeigt die tatsächlichen YAML-Schlüssel. Für Werte und Einheiten gelten weiterhin die jeweiligen Anleitungen: [Mobs/Items](MOBS-UND-ITEMS.md), [Zombie-Boss](ZOMBIE-KOENIG.md), [Dungeons](DUNGEONS.md), [Skills](SKILLS.md), [Anbindungen](INTEGRATIONEN-20.md). Auch neue oder eigene Schlüssel sind editierbar; der Editor ist kein semantischer Assistent für jede mögliche Einstellung.

## Speichern und Anwenden

Änderungen erfolgen zunächst im persönlichen Entwurf. Beim Dateiwechsel fragt das Menü nach dem Verwerfen ungespeicherter Änderungen. Schließen behält den Entwurf bis zur Sitzungsbereinigung nach zehn Minuten Inaktivität; Abmelden oder Hard-Reload verwirft ihn.

**Speichern** bestätigt die Datei, prüft Struktur, endliche Zahlen sowie grundlegende Mob-/Phasen-/Skillschalterwerte und erstellt `<Datei>.admin.bak` als exakte Sicherung des vorherigen Inhalts. Die neue YAML-Datei wird zunächst vollständig geparst und anschließend ersetzt. Hat ein anderer Admin, Befehl oder Editor die Datei inzwischen geändert, wird das Speichern abgelehnt. Dann neu öffnen und die gewünschten Änderungen erneut vornehmen. Links und Pfade außerhalb des TheMob-Datenordners werden abgelehnt.

Fehlende mitgelieferte Einstellungen erscheinen als Ergänzung im Entwurf; eigene Einstellungen bleiben erhalten. Erst Speichern schreibt diese Ergänzungen. Laufzeitdatenbanken, Dungeonregister, fremde Plugin-Dateien und aktive BattlePass-Wochen werden nicht durch den Dateieditor verwaltet. Dungeon-Endboss/Altar werden über ihre eigenen Menüpunkte gesetzt. BattlePass-Aufgabenprofile stehen im Editor bereit; aktive Wochen verwaltet weiterhin BattlePass.

**Ein vollständiger Serverneustart wendet alle gespeicherten Einstellungen an.** Der bestätigte Hard-Reload unter Wartung lädt Mob-/Spawn-/Konfigurationsdaten neu und entfernt aktive TheMob-Mobs; er verwirft auch offene Menüentwürfe. Skills und Integrationsschalter benötigen den vollständigen Neustart. Spawnen, Eierausgabe, Spawnassistent und Dungeonaktionen wirken dagegen unmittelbar über die bestehenden Dienste. Der Spawnassistent legt zunächst ENDLESS-Spawns mit den bisherigen Standardwerten an; Modus, Nachrichten und alle weiteren Felder lassen sich im Spawneditor ändern und anschließend anwenden.

## Rechte und Bedienung

- `themob.admin.menu`: Menü öffnen; Standard OP. Allein dieses Recht erlaubt keine Einstellungen oder Spawnaktionen.
- `themob.admin.edit`: Konfigurationsentwürfe bearbeiten und speichern; Standard OP.
- `themob.admin`: enthält Menü-/Editorrechte und die bestehenden Adminrechte; Standard OP.
- Aktionen prüfen zusätzlich ihre bisherigen Rechte, z.B. `themob.spawn`, `themob.spawn.set`, `themob.spawner`, `themob.dungeon.admin`, `themob.reload`, `themob.integrations.manage` oder `themob.skills.manage`.

Nur normaler Linksklick löst eine Schaltfläche aus. Inventarverschieben, Ziehen, Hotbar-/Offhand-Tausch und Doppelklick sind in diesem Menü gesperrt. Aktionen prüfen Besitzer, aktuelles Fenster und Rechte erneut auf dem Serverthread. Geländeveränderungen, Abbruch, Massenentfernen, Dateilöschen und Hard-Reload zeigen eine Bestätigung. Die Dungeonbau-Bestätigung ist keine Vorschau aller später gesetzten Einzelblöcke; sie nennt Stil und Standort und warnt vor dem Freiräumen.

Für zusätzliche Entwickler-Menüs und Itemanbieter siehe [API](API.md) und [SDK](DEVELOPER-GUIDE.md).
