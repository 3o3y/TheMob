# Update auf TheMob 1.19.0 · Minecraft 1.18–26.3

1. Server vollständig stoppen und `plugins/TheMob` sowie Welt-/Spielerdaten sichern.
2. Die bisherige TheMob-JAR ersetzen durch `TheMob-1.19.0-MC1.18-26.3.jar`. Nur eine TheMob-JAR im Pluginordner behalten. API-/Source-/JavaDoc-JARs gehören nicht in `plugins`.
3. Die zur Minecraft-Version passende Java-Laufzeit verwenden; [Versionsübersicht](KOMPATIBILITAET.md). Optional installierte Plugins müssen dieselbe Serverversion unterstützen. Eine aktuelle BetterHud-JAR nicht pauschal auf 1.18 übernehmen.
4. Server vollständig starten. `/mob` öffnet für Admins das Menü; bestehende Befehle bleiben nutzbar. Unter Plugin-Anbindungen den Status prüfen oder `/mob connect check` verwenden.
5. Bestehende Mob-/Dungeon-/Spielerdaten behalten. Für Aktivierungsänderungen an Skills oder Integrationen ist ein vollständiger Neustart nötig. Der bestätigte Hard-Reload entfernt weiterhin aktive TheMob-Mobs und vergibt dafür keine Killbelohnung.

Die Kampfmechanik, HP-Phasen, Itemstats, Dropregeln und BattlePass-Zählung bleiben erhalten. Die neue Kompatibilitätsschicht behandelt umbenannte Attribute/Partikel/Tränke, Attributmodifikatoren, Inventarfenster und optionale Ereignisse. Auf alten Versionen übernehmen ArmorStands die Schadensanzeigen/Itemdarstellungen. Fehlende neue Mobarten, Items oder Größenattribute werden ausgelassen. Der Menüeditor akzeptiert aufsteigende und absteigende HP-Bereiche.

Die API 1.3.1 erhält die öffentlichen 1.x-Verträge und ist als Java-17-Bytecode gebaut. Neue Add-ons können Kills, Phasen, Schaden, Drops, aufgenommenen Loot, Dungeonabschlüsse, Itemanbieter und Adminmenüs anbinden. [SDK](DEVELOPER-GUIDE.md), [BattlePass](BATTLEPASS.md), [20 Integrationen](INTEGRATIONEN-20.md).

Die Unterstützung älterer Serverversionen konvertiert keine neueren Minecraft-Welten oder neueren Item-Serialisierungen rückwärts. Für einen älteren Server eigene versionsgerechte Welten, Clientpakete und Anbieter-Versionen verwenden.

Echte Minecraft-Server-/Clienttests und Lastmessungen stehen noch aus. Der genaue automatisierte Prüfstand steht in [VALIDATION.md](VALIDATION.md).
