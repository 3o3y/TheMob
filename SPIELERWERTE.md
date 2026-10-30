# Leben, Rüstung und Hunger – Korrektur 1.16.1

Zusammengehörige Versionen: **TheMob 1.16.1**, **CoinOverlay 1.8.3** und, falls installiert, **SkilltreePlugin 1.0.1**. Java 25 / Spigot oder Paper 26.3. Bestehende Mobdateien, Items, Konten, Skills und Dungeonzuordnungen bleiben verwendbar.

## Einspielen

1. Server vollständig stoppen.
2. Alte TheMob- und CoinOverlay-JAR ersetzen. Je Plugin darf nur eine JAR im `plugins`-Ordner liegen.
3. Wenn SkilltreePlugin bereits installiert ist, auch dessen JAR durch 1.0.1 ersetzen. Es muss für diese Korrektur nicht neu installiert werden. Im gemeinsamen ZIP liegt dieses optionale Update getrennt unter `bei-installiertem-skilltree/`.
4. Datenordner behalten und Server vollständig neu starten. Ein neues Client-Ressourcenpaket oder ein neuer SHA1-Wert ist für diese Änderung nicht nötig; Bilder, Fonts und Layouts werden nicht verändert.

## Leben: voller oder verletzter Zustand bleibt erhalten

Das Omega-Set enthält zusammen **74 zusätzliche Lebenspunkte**: 14 + 30 + 18 + 12. Ohne weitere Lebensboni ergibt das **20 + 74 = 94 maximales Leben**. Bei 94/94 ausloggen und wieder verbinden ergibt wieder 94/94. Bei 70/94 bleibt es 70/94. Absorption, beispielsweise zusätzliche goldene 16 Punkte, bleibt ein separater Wert und zählt nicht zum normalen Leben oder dessen Maximum.

Der bisherige Fehler entstand beim Entfernen der eigenen Attributboni während Quit/Shutdown: Das maximale Leben fiel auf die Basis zurück, und höhere aktuelle Lebenspunkte konnten verloren gehen. Die Korrektur speichert je Bonusanbieter nur den dadurch tatsächlich verlorenen Anteil:

```text
behaltenes Leben = min(aktuelles Leben, Maximum nach Entfernen des eigenen Bonus)
Lebensgutschrift = aktuelles Leben - behaltenes Leben
beim Wiederherstellen: min(neues Maximum, jetziges Leben + Lebensgutschrift)
```

Jede Gutschrift wird genau einmal verbraucht. Bei gleichzeitigem Skilltree und Rüstung sind Reihenfolge beim Verlassen und spätere Ladereihenfolge damit unabhängig. Schaden oder Heilung zwischen den beiden Wiederherstellungen bleibt berücksichtigt. Normales Anlegen zusätzlicher Rüstung füllt die Lebenspunkte nicht kostenlos auf. Tod und Respawn verwerfen Gutschriften aus dem vorherigen Leben. Basiswerte und Attributboni anderer Plugins werden erhalten.

Die Korrektur verhindert künftige Verluste. Lebenspunkte, die eine ältere Version bereits dauerhaft als 20 gespeichert hat, lassen sich nicht sicher von regulärem Schaden unterscheiden und werden nicht pauschal auf volle Gesundheit gesetzt.

## Schild: Rüstung ist keine zweite Lebensleiste

Der Schild zeigt das tatsächliche Minecraft-Attribut **Rüstung**, mit dem nativen Bereich **0–30**. Das frühere `/20` war die Skala der alten Vanilla-Anzeige, nicht die Attributobergrenze. Beispiele: 12/30 = 40 % Balken, 20/30 = etwa 66,7 %, 30/30 = voll. Werte oberhalb der nativen Obergrenze erzeugen keine zusätzliche Balkenfüllung.

Die Rüstungsboni auf TheMob-Items werden wie bisher zu den sonstigen Rüstungsattributen addiert. Minecraft begrenzt den wirksamen Gesamtwert. **30 Rüstung bedeutet nicht 100 % Schadensschutz**: Die native Reduktion hängt unter anderem von Schaden, Rüstungshärte und Verzauberungen ab. Es wird keine zweite Vanilla-Rüstungsreduktion im Plugin nachgerechnet.

Die zusätzliche Itemeigenschaft **Defense** ist eine getrennte RPG-Schadensminderung. Sie wirkt jetzt auch bei Mob-Nahkampf und deren Projektilen; bei Spielerangriffen wird sie nicht doppelt angewendet. Mit dem bisherigen Standard `combat.defense.k = 200` gilt vor der nativen weiteren Abwicklung:

```text
Schadensfaktor = 200 / (200 + max(0, Defense))
Defense 22: Faktor ≈ 0,9009, also etwa 9,91 % zusätzliche Reduktion
Defense 200: Faktor 0,5
```

Schutzplugins können Treffer weiterhin abbrechen. Fall- und Hungerschaden erhalten diese zusätzliche Kampf-Defense nicht. Das TheMob-Statistikmenü liest für maximales Leben und Rüstung die aktuellen Serverattribute einschließlich anderer Anbieter.

Quelle zum nativen Rüstungsbereich: [NeoForged – eingebaute Attribute](https://docs.neoforged.net/docs/entities/attributes/).

## Hunger

Hunger bleibt ein unabhängiger Minecraft-Wert **0–20**: 17/20 entspricht 85 %. Rüstung, maximale Lebenspunkte und Absorption ändern weder diesen Nenner noch den gespeicherten Nahrungsstand. Sättigung und Erschöpfung werden durch TheMobs Rejoin-Korrektur nicht verändert. Der Skilltree-Ausdauerbonus reduziert weiterhin Erschöpfung; er erzeugt keine zusätzliche Hungerleiste.

## Prüfung

Automatisiert geprüft: 94/94 und verletzte Werte, wiederholtes Verbinden und Reload, veränderte Ausrüstung, fremde Attributboni, Tod/Respawn ohne alte Gutschriften, Nahrung/Sättigung/Erschöpfung/Absorption, Mob-/Projektil-/PvP-Defense und abgebrochene Treffer. Zwölf zusätzliche Prüfungen laden die tatsächlich gebaute Skilltree-JAR und führen beide Anbieter in allen vier Entfernen-/Wiederherstellen-Reihenfolgen aus, jeweils mit vollem oder verletztem Leben und zusätzlichem Schaden während des Ladens.

MockBukkit simuliert die Serverereignisse; echter Spieler-NBT-Speicher beim Serverneustart und die Anzeige im Minecraft-Client bleiben live zu prüfen. Dafür mit demselben Set bei 94/94 und beispielsweise 70/94 ausloggen, erneut verbinden und danach einen vollständigen Neustart testen. Nahrung und Absorption separat kontrollieren.
