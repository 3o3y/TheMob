# TheMob-Skills und SkilltreePlugin 2.0.0

TheMob 1.17.0 passt das bestehende **Your Stats**-Menü an den aktuellen Skilltree mit 100 Fähigkeiten und fünf Stufen an. Die beiden Plugins behalten ihre jeweiligen Aufgaben: Skilltree verwaltet gelernte Skills, Punkte und situative Boni; TheMob verwaltet Mobs, Bossphasen und besondere Itemeffekte.

## Das angezeigte Menü

`/stats` zeigt für Leben, Zusatzleben, Rüstung, Bewegungstempo, Angriffstempo, Rückstoßresistenz und Glück die tatsächlichen Bukkit-Attribute einschließlich Ausrüstung, Tränken und Skilltree-Modifikatoren. Es zeigt bei aktivem Skilltree keine alten Ausrüstungs-Tiers als vermeintlich gelernte Skillstufen. Die übrigen Werte sind als TheMob-Ausrüstungseffekte gekennzeichnet. Situative prozentuale Schadensboni lassen sich nicht als dauerhaftes einzelnes Attribut ausdrücken; Skilltree zeigt deren Stufen in `/skills`.

Ein Stern im freien Slot 49 öffnet das Skilltree-Menü. Bestehende Belegung dieses Slots wird nicht überschrieben. Das Recht `skilltree.use` wird geprüft. Skilltree bleibt auch direkt über `/skills` erreichbar.

TheMob berechnet seinen Waffen-/RPG-Basiswert vor den HIGH-Kampfmodifikatoren von Skilltree. Dadurch skaliert z.B. Schwertmeister auch den zusätzlichen TheMob-Waffenschaden genau einmal: `(10 nativer Schaden + 25 Itembonus) × 1,10 = 38,5` vor weiteren Schutzabzügen. Lebensraub-Heilung von TheMob verwendet ein normales abbrechbares CUSTOM-Heilereignis; Heilkunst aus Skilltree kann darauf wirken. Beide Plugins verändern nur ihre eigenen Attribute und schreiben keine Skillpunkte des anderen Plugins.

## Schalter für Admins

```text
/mob skills status
/mob skills all aus
/mob skills all an
/mob skills bosses aus
/mob skills items aus
/mob skills bosses.zombie_king aus
/mob skills bosses.summon-minions aus
/mob skills items.lifesteal aus
/mob skills items.poison an
```

Recht: `themob.skills.manage` oder `themob.admin`, standardmäßig OP. Tabvervollständigung zeigt alle Schalter. `enable`/`disable` funktionieren ebenfalls.

**Änderungen werden in skills.yml für den vollständigen Serverneustart gespeichert.** Sie verändern keinen laufenden Bosskampf. Die Statusausgabe zeigt laufenden und gespeicherten Stand; ein global ausgeschalteter Bereich übersteuert seine Einzelschalter. `/mob reload` übernimmt diese vorgemerkten Schalter absichtlich nicht. Die Datei `skills.yml.bak` enthält bytegenau den vorherigen Stand. Fehlerhafte YAML-/Booleanwerte werden nicht überschrieben; beim Start mit ungültiger Datei bleiben TheMob-Spezialskills ausgeschaltet, bis sie repariert und der Server neu gestartet wird.

| Bereich | Einzelschalter |
|---|---|
| Bossverhalten | bosses.zombie_king, bosses.bruiser, bosses.ranged, bosses.guardian, bosses.berserker, bosses.aggressive, bosses.passive, bosses.flee |
| Zombie-König einzeln | bosses.zombie-cleave (Hieb), bosses.zombie-lunge (Ansturm), bosses.zombie-stomp (Bodenwelle), bosses.zombie-retreat (Rückzug) |
| Boss-/Mobzustände | bosses.phase-abilities (auch Basis-Abilities), bosses.phase-buffs, bosses.phase-combat |
| Weitere Bossaktionen | bosses.phase-effects, bosses.world-effects, bosses.summon-minions |
| Itemeffekte | items.critical, items.lifesteal, items.poison, items.ignite, items.slow, items.execute, items.knockback |

Alle Schalter sind anfänglich an und erhalten die bestehende Mechanik. Ausgeschaltete Spezialtaktiken fallen auf Vanilla-Verhalten zurück. Boss-HP-Grenzen, Phasen-IDs, Bossbar, Kills, Drops und BattlePass-Abschlüsse bleiben unabhängig davon vorhanden. Normale Ausrüstungsattribute und native Minecraft-Verzauberungen bleiben wirksam; die Itemschalter steuern TheMob-Procs. Gift/Feuer/Verlangsamung sind auf 1–30 Sekunden begrenzt, Verlangsamung auf Amplifier 0–4; nicht endliche Werte werden abgewiesen beziehungsweise auf Defaults begrenzt.

Diese Schalter verändern keine Skilltree-Skills oder Skilltree-Konfiguration. Beispielsweise kann Skilltree-Vampirismus weiter wirken, wenn nur TheMobs Item-Lebensraub aus ist. Ein serverweiter Eingriff in Skilltree gehört in dessen eigene Konfiguration. Das Ressourcenpaket bleibt unter der bestehenden gemeinsamen Paketverwaltung; für diese TheMob-Anpassung ist kein neues Paket nötig.

Geprüft gegen die echte SkilltreePlugin-2.0.0-JAR: Lebenspunkte/Rejoin in allen Lade-/Entladereihenfolgen, Schwertmeister auf TheMob-Waffenbonus und Heilkunst auf TheMob-Heilung. Native Anzeige, Schalter/Rechte/Sicherungen und deaktivierte Bossfähigkeiten werden zusätzlich simuliert. Ein echter Server-/Clienttest steht noch aus.
