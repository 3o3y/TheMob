# Moblevel, Bosskämpfe und Beute in TheMob 1.15.0

Für Minecraft 26.3 enthält der Katalog **90 spawnfähige Mob-Spezies mit jeweils Level 1–5**: 450 Varianten. Hinzu kommen vier eigene Bossarchetypen in je fünf Stufen, also 20 Bossvarianten. Die acht bisherigen Definitionen bleiben erhalten; standardmäßig stehen damit **478 Mobdefinitionen** bereit. Spieler, Rüstungsständer und Mannequins sind keine Katalogmobs. Wither und Enderdrache sind als Mob-Spezies enthalten; ihre Varianten behalten ihre native KI und werden als Bosse verwaltet.

Die Auswahl erfolgt aus den tatsächlichen Mobtypen der Ziel-API. Die Levelvarianten werden beim Laden erzeugt und gemeinsam mit den eigenen YAML-Definitionen registriert. Sie spawnen **über TheMob-Spawns, Eier, Befehle, Blockspawner und die API**. Natürliche Spawns werden entsprechend deiner Auswahl nicht ersetzt.

## Benutzen

```text
/mob mobs 1
/mob spawn tm_zombie_lvl_5
/mob egg tm_zombie_lvl_5 4
/mob set autospawn tm_skeleton_lvl_3 30 4
/mob spawn tm_boss_warlord_lvl_5
/mob items 1
/mob item epic_sword_5
/mob item rare_chestplate_3 Spielername
/mob performance
```

Die Verwaltungsbefehle für die Kataloge und Items verwenden `themob.admin`; Spawn- und Eierbefehle behalten ihre vorhandenen Berechtigungen. Alle Mob- und Item-IDs sind mit Tab vervollständigbar. `/mob mobs [Seite]` und `/mob items [Seite]` zeigen jeweils zwölf IDs; der BattlePass-Status gibt nur eine kurze Auswahl aus.

Normale Mob-IDs: `tm_<entitytyp>_lvl_<1-5>`, etwa `tm_cow_lvl_2`, `tm_breeze_lvl_4`, `tm_copper_golem_lvl_3` oder `tm_zombie_nautilus_lvl_5`. Die konkrete Liste liefert `/mob mobs` für die laufende API-Version.

## Level und eigene Anpassungen

| Level | Gesundheitsfaktor | Schadensfaktor |
|---|---:|---:|
| 1 | 1,00 | 1,00 |
| 2 | 1,30 | 1,15 |
| 3 | 1,65 | 1,30 |
| 4 | 2,10 | 1,50 |
| 5 | 2,70 | 1,80 |

Die Grundgesundheit ist eine eigene Balancevorgabe pro Speziesgruppe. Nahkampfschaden wird über das vorhandene Angriffsattribut gesetzt, nativer Projektilschaden über den Levelmultiplikator. Passive Tiere erhalten dadurch keine neue Angriffs-KI. Bereits nach Level berechnete Bossprojektile werden ausdrücklich nicht nochmals verstärkt.

`plugins/TheMob/catalog.yml` steuert die Faktoren, die Bossgrundgesundheit und die neue Beute. Eigene Dateien unter `plugins/TheMob/mobs/` mit derselben `mob-id` haben Vorrang. Damit kann beispielsweise `tm_zombie_lvl_5` durch eine vollständig eigene YAML-Definition ersetzt werden. `enabled: false` in catalog.yml deaktiviert die generierten Definitionen; vorhandene eigene Mobdateien bleiben geladen.

Änderungen werden mit `/mob reload` geladen. Wie bisher entfernt dieser Befehl die aktuell aktiven TheMob-Mobs. Vorhandene Dateien werden beim Update nicht durch die neuen Standarddateien ersetzt; fehlende `catalog.yml` und `equipment.yml` werden ergänzt.

## Vier Bossarchetypen

| ID-Muster | Mob | Kampf |
|---|---|---|
| `tm_boss_warlord_lvl_N` | Zombie | Nahkampf, angekündigte Flächenhiebe; unter 30 % HP Berserkerdruck |
| `tm_boss_marksman_lvl_N` | Skelett | Abstand halten und angekündigte Pfeilangriffe mit eigener Abklingzeit |
| `tm_boss_broodmother_lvl_N` | Spinne | Nahkampf, ab der zweiten Phase begrenzte Höhlenspinnenwellen |
| `tm_boss_guardian_lvl_N` | Eisengolem | Verteidigt seinen Ankerbereich, angekündigter Rückstoßangriff |

`N` ist 1–5. Die Phasen heißen `guard` (über 65 %), `pressure` (30–65 %) und `rage` (bis 30 %). Die Bossgesundheit beträgt standardmäßig `100 + 45 × Level`; XP `15 × Level`. Ein Cooldown von 120 Sekunden pro Bossdefinition begrenzt Wiederholungen nach einem Kill. Die globalen Boss-, Mob- und Minionbudgets gelten weiterhin.

Die zusätzlichen Angriffe haben eine Vorwarnzeit von einer Sekunde. Flächenhieben kann man ausweichen; Schaden wird nur in der angekündigten Umgebung und bei Sichtkontakt ausgeteilt. Ranged-Angriffe prüfen ebenfalls Sichtkontakt. Spieler im Kreativ-/Zuschauermodus sowie NPCs werden nicht als Angriffsziele gewählt. Die eigene Ankerposition bleibt über Phasenwechsel erhalten; außerhalb der Leine kehrt der Boss zurück, ohne zum Spieler teleportiert zu werden. Der Guardian zieht sich bereits bei größerer Entfernung von seinem Wachpunkt zurück.

Die neuen Verhaltensmodi sind `bruiser`, `ranged`, `guardian`, `berserker` und `vanilla`. Sie lassen sich auch in eigenen Phasen unter `behavior.mode` wählen. Zusätzliche Felder: `range`, `leash-radius`, `attack-damage`, `cooldown-ticks`, `windup-ticks`. Alte Modi bleiben verfügbar. Die eigene KI läuft im zentralen Verhaltenstakt; pro Bossangriff wird kein weiterer periodischer Scheduler gestartet. Zusätzliche Pfeile werden nach sechs Sekunden, Phasenwechsel, Tod oder Kaltstellung entfernt.

Minions der Brut-Mutter haben begrenzte Anzahl, Lebenszeit und Wiederholungsrate. Sie droppen keine Gegenstände/XP. BattlePass-Minions bleiben über die vorhandene Option standardmäßig vom Fortschritt ausgeschlossen.

## 160 Ausrüstungsvarianten

16 Vorlagen × Rare/Epic × fünf Sternstufen:

| Gruppe | IDs für die Vorlage |
|---|---|
| Nahkampf | `sword`, `axe`, `mace`, `spear`, `trident` |
| Fernkampf | `bow`, `crossbow` |
| Verteidigung | `shield`, `helmet`, `chestplate`, `leggings`, `boots` |
| Werkzeuge | `pickaxe`, `shovel`, `hoe`, `shears` |

Format: `rare_<vorlage>_<sterne>` oder `epic_<vorlage>_<sterne>`. Beispiel: `epic_sword_5` ist ein Epic-Diamantschwert mit fünf sichtbaren Sternen. Rare verwendet bei den entsprechenden Vorlagen Eisen, Epic Diamant; spezialisierte Waffen wie Streitkolben, Dreizack und Bögen behalten ihr natives Material.

Die Items haben echte, passende Vanilla-Verzauberungen innerhalb ihrer maximalen Stufen: Schärfe, Stärke, Dichte, Schnellladen, Harpune, Schutz oder Effizienz, ergänzt um Haltbarkeit. Der Schild verwendet Reparatur. Sterne und Seltenheit stehen im Namen, in der Lore und in persistenten Itemdaten. Die vorhandene Itemstat-/Kampflogik verarbeitet die zusätzlichen Damage-, Defense- oder Crit-Chance-Werte; Vanilla-Rüstung wird dabei nicht nochmals als eigene Rüstung hinzugefügt.

`equipment.yml` enthält die 16 anpassbaren Vorlagen. Jede Variante wird beim Laden einmal aufgebaut und bei einer Ausgabe geklont. Sie kann in eigenen Drops oder Ausrüstung referenziert werden:

```yaml
drops:
  - catalog-item: epic_sword_5
    chance: 0.02
    amount: 1
```

## Neue Beute

Alle regulären TheMob-Mobs können zusätzlich Ausrüstung und eine speziesgebundene Essenz erhalten. Das gilt auch für die bisherigen acht Mobs. Standardmäßig ist dafür ein echter Spielerkiller erforderlich; NPCs zählen nicht. Vorhandene alte Dropdefinitionen bleiben bestehen. Bei den neuen generierten Mobs bleiben die nativen Vanilla-Drops erhalten. Zusätzliche Drops laufen durch das normale EntityDeathEvent, sodass andere Drop-Plugins sie verarbeiten können.

| Level | Ausrüstungschance | Epic-Anteil bei einem Ausrüstungsdrop | mögliche Sterne |
|---|---:|---:|---|
| 1 | 4 % | 8 % | 1 |
| 2 | 6 % | 11,5 % | 1–2 |
| 3 | 8 % | 15 % | 1–3 |
| 4 | 10 % | 18,5 % | 1–4 |
| 5 | 12 % | 22 % | 1–5 |

Bei regulären Mobs werden die möglichen Sternstufen gleichverteilt gewürfelt. Bosse geben standardmäßig ein Ausrüstungsteil mit ihrer Level-Sternstufe; 60 % davon sind Epic. Essenzen haben bei normalen Mobs 50 % Chance und bei Bossen 100 %. Sie sind Sammel-/Questmaterial mit Spezies und Level; es wird noch kein Herstellungsrezept dafür eingeführt. Alte Bosse ohne eigenes `level` verwenden für die neue Beute Level 5, alte normale Mobs Level 1.

Die Chancen stehen unter `loot` in catalog.yml. `loot.enabled: false` deaktiviert die neue gemeinsame Beute. `no-drops`, Minionunterdrückung, Killall, API-Remove und administrative Bereinigungen erhalten keine neuen Belohnungen. Die bisherige normale Beute wird beim Laden vorkompiliert; erst beim tatsächlichen Kill werden die Chancen ausgewürfelt.

## BattlePass und Ressourcenpaket

Die neuen Definitionen nutzen dieselbe öffentliche API 1.0 und die vorhandenen Killereignisse. `themob_kill` mit `variable: none` zählt sie automatisch. Eine konkrete Aufgabe kann etwa `variable: tm_zombie_lvl_5` oder eine OR-Liste verwenden. Vorhandene Aufgaben mit fest eingetragenen alten Mob-IDs zählen weiterhin genau diese IDs; für neue Varianten die gewünschten neuen IDs ergänzen. Eine Saison muss dafür nicht zurückgesetzt werden.

Alle neuen Gegenstände verwenden native Materialien und Verzauberungen. Das gemeinsame CoinOverlay-Kompasspaket benötigt durch dieses Update keinen neuen Hash. Mehr zur Lastbegrenzung: [PERFORMANCE.md](PERFORMANCE.md).
