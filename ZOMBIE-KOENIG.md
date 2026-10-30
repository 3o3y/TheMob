# Zombie-König ab TheMob 1.16.0

Der vorhandene `zombie_boss_template` bekommt automatisch eine eigene, bodengebundene Kampfsteuerung. Seine vier Phasen-IDs, HP-Grenzen, Grundwerte, Ausrüstung, Beute und BattlePass-/Dungeon-Zuordnungen bleiben erhalten. Die Originaldatei `mobs/boss_template.yml` wird nicht überschrieben. Das neue Profil wird beim Laden im Arbeitsspeicher ergänzt.

| Phase im Standardboss | Verhalten | Erkennbar und konterbar |
|---|---|---|
| 100–75 %, Royal Calm | Der König verfolgt mit normaler Wegsuche und holt zu einem schweren frontalen Klingenhieb aus. | Rauchbogen vor ihm, hölzernes Knacken, festgelegte Blickrichtung; seitlich oder hinter ihn ausweichen. Reichweite des Hiebs vier Blöcke, Winkel 120 Grad. |
| 75–50 %, Furious Assault | Auf mittlere Distanz ein kurzer, geradliniger Ansturm; im Nahkampf der schwere Hieb. | Eine Rauchspur markiert die Richtung. Nach dem Ausholen lenkt er nicht nach. Nur sein tatsächlich zurückgelegter Weg trifft; seitlich ausweichen und die Erholung nutzen. |
| 50–25 %, Royal Frenzy | Bodenstampfer wechseln sich mit Hieben und situationsabhängigen Anstürmen ab. | Rauchring vor dem Stampfer, danach drei nach außen wandernde Wellenabschnitte bis sechs Blöcke. Springen oder den Radius verlassen verhindert den Treffer. Jeder Spieler wird pro Welle höchstens einmal getroffen. |
| Unter 25 %, Desperate Escape | Der verwundete König taumelt höchstens zwei Sekunden zurück und setzt zum verzweifelten Gegenhieb an. | Tiefer Verletzungslaut, begrenzter Rückzug, dann erneut ein klarer Schlagbogen und längere Erholung. Er flieht nicht unbegrenzt aus der Arena. |

Normale Nahkampfangriffe zwischen den Spezialaktionen bleiben aktiv. Während Ausholen, Ansturm, Welle, Rückzug und Erholung werden zusätzliche Vanilla-Nahkampftreffer unterdrückt. Spezialschaden läuft durch die normalen Bukkit-Schadensereignisse einschließlich Rüstung, vorhandenen Phasenmodifikatoren und Schutzplugins. Verhinderte Treffer erzeugen keinen zusätzlichen Rückstoß.

Ein Phasenwechsel verwirft die vorherige Spezialaktion. Jede neue Phase beginnt mit zwei Sekunden Abstand vor ihrer ersten Spezialaktion. Heilung führt wie bisher nicht in eine frühere Phase zurück. Sichtverlust oder deutliches Verschieben beim Ausholen bricht den Angriff ab. Der König teleportiert sich nicht hinter Spieler und zerstört für seine Angriffe keine Blöcke. Die allgemeine Feststeckhilfe greift bei diesem Verhalten nicht ein.

## Einstellungen

Beim ersten Start entsteht `plugins/TheMob/zombie-boss.yml`. Dort stehen für `royal`, `hunt`, `frenzy` und `desperate` jeweils Ansage, Reichweite, Arena-Leine, Rohschaden, Ausholzeit, Erholung und Abklingzeit. 20 Ticks entsprechen bei normaler Servergeschwindigkeit einer Sekunde. Schaden wird vor Rüstung und vorhandenen Kampfmodifikatoren angegeben. Ausholen und Erholung haben jeweils mindestens 20 Ticks, die anschließende Abklingzeit mindestens 40 Ticks.

Bereits vorhandene Werte unter `phases.<ID>.behavior.*` haben Vorrang vor gemeinsamen Profilwerten. `behavior.mode` und `behavior.zombie-stage` werden für die bekannten Standardphasen gesetzt. Die HP-Zuordnung stammt weiterhin aus der Mobdatei. Standardmäßig gilt dieselbe Kampffamilie auch für die fünf Zombie-Warlords: `guard` → `royal`, `pressure` → `hunt`, `rage` → `frenzy`; deren drei Phasen und Levelskalierung bleiben erhalten. Andere Bosse und gewöhnliche Zombies erhalten diese Steuerung nicht.

Mit `include-warlords: false` wird nur der ursprüngliche Zombie-König umgestellt. `enabled: false` deaktiviert das gemeinsame Profil vollständig. Für eine einzelne eigene Bossdefinition kann `zombie-behavior-disabled: true` auf oberster Ebene gesetzt werden. Ein eigenes Zombieprofil kann die registrierte Steuerung auch direkt über `behavior.mode: zombie_king` und `behavior.zombie-stage: royal|hunt|frenzy|desperate` benutzen.

Nach Einstellungsänderungen `/mob reload` verwenden. Dieser Befehl entfernt wie bisher aktive TheMob-Mobs und baut die Steuerung neu auf. Zum Einspielen der neuen JAR ist ein vollständiger Serverneustart erforderlich. Das Reisemenü, Dungeonabschluss und die vorhandene Haupt-/Dungeon-API bleiben kompatibel mit PKPlugin 0.7.2. Der Kampf verwendet Vanilla-Partikel und -Geräusche und braucht keine neue Ressourcenpaket-Version.

## Lastbegrenzung und Prüfgrenzen

Zielwahl und Kampfentscheidungen laufen im bestehenden zentralen Zehn-Tick-Durchlauf. Nur gerade anstürmende oder zurückweichende Zombie-Bosse werden zusätzlich in einem gemeinsamen Zwei-Tick-Durchlauf bewegt. Dafür entstehen keine Aufgaben pro Boss oder Angriff. Die Bewegungsprüfung betrachtet höchstens den nächsten kurzen Schritt samt skalierter Körpergröße, prüft ausschließlich geladene Chunks und stoppt vor Wänden, fehlendem Boden, Flüssigkeiten oder Feuer. Übermäßig große Körper werden bei diesen Zusatzbewegungen angehalten. Die normale Wegsuche bleibt Minecraft überlassen.

Hot-/Cold-Pausen verwerfen laufende Aktionen; beim Aufwachen gibt es eine neue Wartezeit. Tote Bosse, Phasenwechsel und Plugin-Shutdown räumen die zugehörigen Bewegungszustände auf. Es gibt keine zusätzlichen Hilfsentities, Projektile, permanenten Partikelaufgaben oder Blockänderungen.

Automatisierte Prüfungen decken Ausweichen, getreue Phasenzuordnung, Ausdehnung der Welle, Sprünge, Ansturmrichtung, Kollisionen, Schutzereignisse und Hot-/Cold-Verhalten ab. MockBukkit integriert keine echte Minecraft-Physik; Bewegungen werden in den Tests gezielt simuliert. Native Kollisionen von Treppen/Türen, optische Wirkung, Gruppenkampf und Last sind auf einem echten Server noch zu prüfen.
