# Ausgangsaltar im Bossraum – TheMob 1.15.0 / PKPlugin 0.7.2

Jeder neu erzeugte Dungeon erhält im Bossraum einen **Ausgangsaltar**: ein Zaubertisch auf einer 5×5-Blöcke-Steinplatte mit weinendem Obsidian und vier Seelenlaternen. Die Kammer und alle anderen Räume bleiben über normale Wege erreichbar. Rechts- oder Linksklick auf den Zaubertisch öffnet das Reisemenü.

Der Altar funktioniert auch vor dem Bosskampf. Benutzen oder Verlassen des Dungeons löst keinen BattlePass-Abschluss aus; dafür zählt weiterhin der festgelegte Endboss. Der normale Dungeon-Bauschutz gilt auch für den Altar.

## Ziele auswählen

Mit PKPlugin 0.7.2 bietet der Altar dieselben persönlichen Ziele wie die Bannerreise:

- das Homebanner;
- die eigenen aktiven, verbundenen Teleportknoten, auch in anderen geladenen Welten;
- das nächste zugängliche und fertig gebaute Stadtzentrum derselben Welt.

Zusätzlich bietet das Menü **„Zum Eintrittsort zurück“** und **„Zum Dungeon-Eingang“**. Diese beiden Optionen stehen auch ohne PKPlugin zur Verfügung. Bei mehr als 45 PK-Zielen gibt es weitere Seiten; „Ziele aktualisieren“ liest die aktuellen Knoten neu ein.

PK prüft Zugriffsrechte, Verbindung und Bestand des Ziels sowie eine sichere Landestelle erneut beim Reisen. Ein nach Öffnen des Menüs entferntes oder gesperrtes Ziel ist nicht mehr benutzbar. Das Menü ist an den Altar gebunden: Der Spieler muss online, am Leben, im fertigen Dungeon und höchstens acht Blöcke vom vorhandenen Altar entfernt sein. Ein altes Menü vergibt keine Fernreiseberechtigung. Reisen kosten keine Gegenstände. Verhindert ein anderes Plugin den Teleport, wird kein Erfolg gemeldet.

## Vorhandene Dungeons und eigene Bossräume

Beim nächsten `/mob dungeon enter ID`, beim Login im Dungeon oder beim Reload mit anwesenden Spielern wird ein noch fehlender Altar im gespeicherten Bossraum ergänzt. Die Nachrüstung ändert nur die kleine Altarfläche und deren vier Blöcke hohen Freiraum; sie baut den Dungeon nicht neu und setzt keine Mobs ein. Ein begrenzter Bautask bearbeitet höchstens einen Chunk gleichzeitig und gibt seine Ladetickets danach frei. Vor dem Schreiben wird die ganze Altarfläche geprüft. Vorhandene Block-Behälter, Beutetruhen und Spawner werden erhalten; ein belegter Platz wartet auf einen freien, vom Admin gewählten Ort. Nachrüstung erfolgt für besuchte Anlagen; unbenutzte Dungeons laden deshalb keine zusätzlichen Chunks.

Als Admin:

```text
/mob dungeon altar d00001
```

Der Befehl ergänzt oder repariert den gespeicherten Altar. Wenn ihr den Boss selbst in einer anderen Kammer untergebracht habt, stellt euch an die gewünschte Altarposition und verwendet:

```text
/mob dungeon altar d00001 here
```

Die ganze 5×5-Fläche muss innerhalb dieses Dungeons liegen. Dort werden Boden und Freiraum des Altars eingerichtet. Beim Versetzen wird der alte Zaubertisch deaktiviert. Der neue Ort wird gemeinsam mit den Dungeon-Metadaten gespeichert. Der persönliche Admin-Baumodus erlaubt weiterhin die Einrichtung der Kammer; mit Schleichen kann ein Admin dort am Altar vorbeibauen.

`themob.dungeon.altar` ist standardmäßig für alle Spieler freigegeben. Erzeugen und Versetzen erfordern `themob.dungeon.admin` oder `themob.admin`. Für PK-Ziele wird zusätzlich `pk.use` geprüft. Es wird kein PK-Banner im Dungeon angelegt und kein PK-Bypass eingeschaltet.

## Update

Beide JARs aus dem gemeinsamen PKPlugin-0.7.2-Serverpaket übernehmen und den Server vollständig neu starten: **PKPlugin 0.7.2 und TheMob 1.15.0**. Vorhandene Dungeon-, Endboss-, BattlePass- und PK-Daten weiterverwenden. Ein neues Ressourcenpaket ist für den Altar nicht erforderlich.

## Öffentliche Reise-API

Die Haupt-API **TheMob-API-1.3.1.jar** bleibt unverändert. Die additive **TheMob-Dungeon-API-1.3.1.jar** ergänzt:

```java
TheMobDungeonApi api = Bukkit.getServicesManager().load(TheMobDungeonApi.class);
boolean supported = api.supportsExitAltars();
boolean sourceValid = api.atExitAltar(player, dungeonId);
```

Eigene Reiseplugins können `org.plugin.theMob.api.dungeon.DungeonTravelProvider` beim Bukkit-ServicesManager registrieren. `destinations(Player, String)` liefert unveränderliche Anzeigedaten vom Typ `DungeonTravelTarget`: stabile Ziel-ID, Name, Material-Icon und Beschreibung. `travel(Player, String, String)` muss die Altarquelle, aktuelle Zielrechte und die Landestelle erneut prüfen und nur bei einem erfolgreichen Teleport `true` liefern. Es werden keine Konsolenbefehle aus Menütexten ausgeführt.

Der ServicesManager wählt den aktiven Anbieter anhand seiner Priorität. PKPlugin registriert seinen Anbieter mit normaler Priorität. Wird der Anbieter entfernt, verwirft das Menü alte Zielverweise. Add-ons verwenden beide API-JARs als `compileOnly`; diese JARs werden weder in das Add-on kopiert noch separat im Serverordner `plugins` installiert. Alle Methoden laufen auf dem Serverthread. Ältere Abschluss-API-Clients bleiben kompatibel; die neuen Methoden haben sichere Defaults für ältere Implementierungen.

Automatisierte Prüfungen decken Baupläne, Nachrüstung, Persistenz, Rückreise, Seitenwechsel, gesperrte Inventarverschiebung, veraltete Menüs/Anbieter und PK-Zielrechte ab. Native Chunk-Ladetickets und Tile-Enumeration werden für die in MockBukkit fehlenden Methoden gezielt simuliert. Optik, Lichtberechnung, echte Klick-/Teleportabläufe und Bauzeit müssen zusätzlich auf dem laufenden Server geprüft werden.
