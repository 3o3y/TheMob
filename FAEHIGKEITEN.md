# Boss- und Itemfähigkeiten in TheMob 1.19.1

Das eigene Spieler-Skill-/Stats-System wurde entfernt. Es gibt kein TheMob-Statsmenü, keine Spieler-Tiers und keine Weiterleitung zum Skilltree-Menü. `/stats` und `/skills` werden von TheMob nicht registriert.

Bossfähigkeiten und die Spezialeffekte ausgerüsteter Gegenstände bleiben erhalten. Dazu gehören die vier Zombie-König-Phasen, kritische Treffer, Lebensraub, Gift, Feuer, Verlangsamung, Hinrichtung und Rückstoß. Angriffs-, Rüstungs-, Verteidigungs-, Lebens-, Bewegungs- und Angriffsgeschwindigkeitsboni werden weiterhin direkt aus den getragenen Gegenständen gelesen. Es werden dafür keine Spielerlevel oder permanenten Charakterwerte geführt.

## Einrichten

Im Adminmenü `/mob` den Eintrag **Boss- und Itemfaehigkeiten** öffnen. Globale, gruppenweise und einzelne Schalter werden in `abilities.yml` gespeichert und nach vollständigem Serverneustart angewendet. Alternativ:

```
/mob abilities status
/mob abilities items.poison aus
/mob abilities bosses.zombie-stomp an
```

Recht: `themob.abilities.manage` oder `themob.admin`. Beim Ändern entsteht `abilities.yml.bak`. Ein normaler TheMob-Reload setzt keinen laufenden Bosskampf zurück, um vorgemerkte Schalter zu aktivieren.

## Bestehende Server

Falls noch keine `abilities.yml` existiert, werden gültige Boss-/Itemschalter aus der bisherigen `skills.yml` bytegleich übernommen. Die ursprüngliche Datei bleibt erhalten. Ungültige alte Schalter werden nicht überschrieben; die Fähigkeiten bleiben dann deaktiviert, bis die Konfiguration korrigiert ist. Eine bereits vorhandene `abilities.yml` hat Vorrang.

Die alte `stats.yml` wird nicht mehr erzeugt, geladen oder im Admineditor angeboten. Eine vorhandene Datei wird unverändert liegen gelassen. `progression.enabled` wird nicht mehr ausgewertet. Gegenstände, ihre PDC-Werte, Verzauberungen und Beutetabellen müssen nicht neu erstellt werden.
