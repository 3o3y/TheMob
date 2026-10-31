# TheMob 1.19.1: 20 passende RPG-/Server-Plugins

Dies ist eine ausgewählte RPG-/Server-Suite, keine gemessene weltweite Beliebtheitsrangliste. Alle Anbieter sind optional. Status und Einstellungen: **`/mob` → Plugin-Anbindungen**. Die Befehle `/mob connect check` und `/mob connect <Anbieter> enable|disable` bleiben erhalten. Änderungen der Aktivierung gelten nach vollständigem Neustart.

| Anbieter | Konkrete Verbindung | Grundlage |
|---|---|---|
| Jobs Reborn | Vorhandene native Killzahlungen/-XP multiplizieren; kein zweiter künstlicher Kill | [API](https://www.zrips.net/jobs/api/) |
| Citizens 2 | NPCs von Fortschritt ausschließen; ausdrücklich konfigurierte NPC-Spawnklicks | [API](https://wiki.citizensnpcs.co/API) |
| Quests von PikaMug | Custom Objective `TheMob Kill` mit Mob-ID und Bossfilter | [API](https://pikamug.gitbook.io/quests/master/custom-quest-api) |
| BattlePass von AdvancedPlugins | Neun native Aufgabenfamilien: Kills, Boss-/Minionkills, Gruppen, Phasen, Schaden, Drops, Loot und Dungeonabschluss | [API](https://battlepass.advancedplugins.net/developer-api/developer-api-creating-your-own-quest-types) |
| PlaceholderAPI | `%themob_...%` für Anzeigen und andere Plugins | [API](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/) |
| Vault | Registrierter Economy-Service; zusätzliche Mobbelohnung standardmäßig 0 | [API](https://github.com/MilkBowl/VaultAPI) |
| WorldGuard 7 | `mob-spawning` vor Spawns; benannte Regionen vor Dungeonbau schützen | [API](https://worldguard.enginehub.org/en/latest/developer/regions/region-queries/) |
| LuckPerms | Bestehende Bukkit-Rechte plus Kontext `themob:dungeon=<Dungeon-ID>` | [API](https://luckperms.net/wiki/Developer-API) |
| EssentialsX | Bukkit-Kompatibilität: `vanished`-Metadaten beim Boss-/Combat-Targeting respektieren; normale Rechte/Teleports | [Projekt](https://github.com/EssentialsX/Essentials) |
| Multiverse-Core | Bukkit-Kompatibilität: Welt-UUIDs und normale Spawn-/Teleportereignisse; keine Umgehung des Weltmanagers | [API](https://mvplugins.org/core/developers/api-usage/) |
| MythicMobs | Native Itemfabrik für `mythicmobs:ID` | [API](https://git.mythiccraft.io/mythiccraft/MythicMobs/-/wikis/API) |
| MMOItems | Native Itemfabrik für `mmoitems:TYP/ID` | [API](https://gitlab.com/phoenix-dvpmt/mmoitems/-/wikis/Main-API-Features) |
| AuraSkills | Ausdrücklich konfigurierte zusätzliche Skill-XP pro Mob, standardmäßig 0 | [API](https://wiki.aurelium.dev/auraskills/api) |
| mcMMO | Explizite Skill-XP über `ExperienceAPI.addRawXP`, standardmäßig 0 | [Quell-API](https://github.com/mcMMO-Dev/mcMMO/blob/master/src/main/java/com/gmail/nossr50/api/ExperienceAPI.java) |
| GriefPrevention | Claimflächen vor Dungeon-Neubau/-Fortsetzung schützen | [Quelle](https://github.com/GriefPrevention/GriefPrevention) |
| Towny | Townblocks vor Dungeonbau schützen; Towny-Mobregel vor Spawns beachten | [Quelle](https://github.com/TownyAdvanced/Towny) |
| Lands | Land-Chunks vor Dungeonbau schützen; Abfrage ohne Chunkladen | [API](https://github.com/angeschossen/LandsAPI) |
| AdvancedCrates | Original-Kistenschlüssel als Beute über `advancedcrates:Kisten-ID`; native TheMob-Items/Eier können als gespeicherte Kistenbelohnung verwendet werden | [Anbieter](https://advancedplugins.net/item/6/advancedcrates) |
| EconomyShopGUI / Premium | Native Shopitems über `economyshopgui:Sektion.Item`; Schutz vor Verkauf spezieller Ausrüstung zum Vanilla-Preis | [API](https://wiki.gpplugins.com/economyshopgui/api/api-usage) |
| DecentHolograms | Optionale nichtpersistente Bossanzeigen, begrenzt auf 24 aktive Anzeigen, ein gemeinsamer Sekundentakt | [API](https://wiki.decentholograms.eu/api/dhapi/) |

EssentialsX und Multiverse nutzen bewusst die gemeinsamen Bukkit-Verträge; sie haben hier keinen eigenen Serviceadapter. Die übrigen Zeilen benennen die jeweils eingebundene Schnittstelle. „Bereit“ bestätigt die erreichbare Schnittstelle, keine vollständige gleichzeitige Serverprüfung sämtlicher Anbieter. Beliebige weitere Plugins können eigene Brücken über die öffentliche API registrieren. PKPlugin und CoinOverlay bleiben zusätzliche projektspezifische Anbindungen. Ein eigenes Spieler-Skill-/Stats-System enthält TheMob nicht mehr.

## Originalitems als Drops

Im Mobeditor unter `drops` ein Listenelement mit `provider`, `item`, `chance` und optional `amount` anlegen. Beispiel:

```yaml
drops:
  - provider: advancedcrates
    item: starter
    chance: 0.05
    amount: 1
  - provider: mmoitems
    item: SWORD/FLAME_BLADE
    chance: 0.01
    amount: 1
```

`chance` verwendet wie vorhandene TheMob-Drops Werte von 0 bis 1: `0.05` entspricht 5 %, `1.0` entspricht 100 %. IDs müssen im jeweiligen Anbieter existieren. Ohne passenden aktiven Anbieter gibt es für diesen Eintrag keinen Ersatzdrop. Die Menge ist 1–64 und wird auf die native maximale Stapelgröße begrenzt. `legendary-drops` unterstützt dieselben Referenzen.

TheMob klont die Originalitems und verändert ihre Namen, Verzauberungen oder Inventar-PDC nicht. Auch die Seltenheit eines fremden Kistenschlüssels wird nicht als TheMob-Seltenheit umgeschrieben. Für Aufgaben auf solche Drops die native ID wie `advancedcrates:starter` verwenden. Die Herkunft und Item-ID werden am gedroppten Item-Entity gespeichert, sodass Drop-/Lootaufgaben funktionieren und Schlüssel ihr originales NBT behalten. Nach erneutem Wegwerfen aus einem Spielerinventar entsteht kein neuer TheMob-Killloot. `inspectItem` erkennt fremde IDs aus einem begrenzten laufenden Fabrikcache; nach Neustart ist diese Erkennung für beliebige Inventaritems nicht garantiert. Die persistierte Loot-ID am Boden bleibt davon unabhängig.

`/mob` → Items und Drops → Fremditem testen gibt einem berechtigten Admin eine Kopie. API `createItem("provider:ID")` erzeugt ebenfalls nur das Item: keine Bezahlung, Kauftransaktion, Shopbestandsänderung oder automatische Ausgabe. Für echte Käufe muss die Transaktions-API des Shopplugins verwendet werden. Fremde Entwickler registrieren `TheMobItemProvider` über den Bukkit ServicesManager; `themob` bleibt reserviert.

## Schutz, Rechte, Erfahrung und Anzeigen

`integrations.<worldguard|griefprevention|towny|lands>.protect-dungeons` ist standardmäßig `true`. Vor Neubau und Fortsetzung wird die gesamte Grundfläche geprüft, bei benannten WorldGuard-Regionen außer `__global__`. Anspruchsberechtigung oder OP umgehen diese TheMob-Prüfung nicht. Ein installierter inkompatibler Schutzadapter blockiert den Bau. Die Prüfung erfolgt vor dem Planungs-/Bauschritt, sie verhindert nicht nachträglich neue Claims während eines laufenden Baus. Fremde Claims werden nicht verändert. Für andere Schutzsysteme steht das abbrechbare `TheMobDungeonPreCreateEvent` bereit; vorhandene PK-Dungeonregeln bleiben erhalten.

LuckPerms erhält den Kontext nur in fertigen Dungeons. Der Serverthread aktualisiert den Standortcache einmal pro Sekunde; die Kontextberechnung selbst liest nur diesen Cache. Daher ist der Kontext für Rechte/Anzeige geeignet, hat aber bis zu einer Sekunde Verzögerung und ersetzt keinen unmittelbaren Bewegungsschutz. Beim Beenden wird der Calculator abgemeldet.

Zusätzlich prüft TheMob die gespeicherten PK-Bannerkreise über `territories().all()`. Es verwendet `centerX`, `centerZ` und **den Radius jedes einzelnen Markers**, einschließlich der 64-Block-Grundgebiete in PKPlugin 0.9.0 und größerer Ausbauten. Fremde Welten bleiben getrennt. `integrations.pkplugin.protect-dungeons: true` kann im Editor für den Neustart geändert werden. Bei einem vorhandenen, aber unlesbaren PK-Vertrag wird der Dungeonbau abgelehnt. Diese projektspezifische Verbindung ist zusätzlich zu den 20 Anbietern und verändert keine PK-Dateien.

Zusätzliche Skill-XP werden **pro Mobdefinition** ausdrücklich gesetzt, z.B. `integrations.auraskills.bonus-xp: 2.5` und `integrations.auraskills.skill: FIGHTING`, oder `integrations.mcmmo.bonus-xp: 2.5` und `integrations.mcmmo.skill: SWORDS`. Nicht gleichzeitig dieselbe Belohnung über mehrere Systeme verdoppeln. Bestehende Skill-/Waffen-/Heilungsregeln eures SkilltreePlugin bleiben erhalten.

`integrations.economyshopgui.protect-custom-items: true` prüft native Verkaufsereignisse. Enthält das Inventar ein erkanntes Spezialitem mit demselben Material wie eine Verkaufsvorlage, aber anderen Metadaten, wird die gesamte Transaktion vorsorglich abgelehnt. Das Item kurz auslagern oder eine exakt passende Custom-Shopvorlage konfigurieren. Käufe bleiben unverändert. Ein Fehler im gebundenen Transaktionsadapter blockiert weitere Transaktionen bis zur Reparatur/Neustart; den Schalter kann ein Admin bewusst deaktivieren. Fehlt schon die passende Ereignisklasse, ist die Anbindung inkompatibel und kann keinen Verkaufsschutz registrieren; dieser Status muss vor Nutzung korrigiert werden.

`integrations.decentholograms.boss-holograms: false` bleibt die Voreinstellung. Aktivieren zeigt Namen, Leben und Phase über aktiven Bossen. Cold-Zonen erzeugen keine dauernden Anzeigenupdates. Es werden nur eigene, nichtpersistente Hologramme entfernt.

## Prüfumfang

Lokale Tests verwenden die echten API-Klassen aus BattlePass **5.0.12**, AdvancedCrates **4.7.4**, EconomyShopGUI-Premium **6.5.1**, LuckPerms-API **5.5**, AuraSkills-API **2.2.4** sowie die lokale SkilltreePlugin-**2.0.0**-JAR. Das startet keine kompletten Premiumplugins oder deren Datenbanken. Die übrigen Adapter verwenden dokumentierte API-Verträge und gezielte lokale Fixtures. Für eure konkrete Serverkombination sind reale Spieltests erforderlich. Anbieter-JARs und API-Abhängigkeiten werden nicht im TheMob-Update/SDK verteilt. Einzelheiten und endgültige Testzahlen: [VALIDATION.md](VALIDATION.md).
