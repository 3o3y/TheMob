# Baubares TheMob-Add-on-Beispiel

Build mit JDK 25; Java-17-Ausgabe für Minecraft/Spigot 1.18–26.3, TheMob 1.19.0. Im entpackten Developer SDK sind Gradle-Wrapper und beide API-1.3.1-JARs unter `libraries/` bereits enthalten. Hier `gradlew.bat build` beziehungsweise `sh gradlew build` ausführen und anschließend nur die erzeugte `build/libs/TheMobAddonExample-1.0.0.jar` auf einem Testserver mit TheMob installieren. Die erste Ausführung kann Gradle-/Maven-Downloads benötigen.

Beim direkten Arbeiten aus dem TheMob-Quellprojekt beide API-JARs nach `libraries/` kopieren oder das fertige SDK verwenden. Der Hauptbuild kompiliert das Beispiel separat gegen seine kleinen öffentlichen JARs, ohne interne Implementierungsklassen im Klassenpfad.

Das Beispiel bindet über `Bukkit.getServicesManager()` an. Es zählt berechtigte native TheMob-Kills und autoritative Dungeonabschlüsse ausschließlich als eigene Demonstrationswerte im Spielernamensraum. Der kommentierte Weiterleitungspunkt im Code ist für die **native API eures eigenen Quest-/Jobs-Plugins** vorgesehen. Es gibt keine echten Geld-/BattlePass-/Questbelohnungen und keine automatisch erzeugten Mobs.

- `/themobexample status`: als OP oder mit `themobexample.admin` Dienststatus und eigene Beispielzähler ansehen.
- `/themobexample spawn <Mob-ID>`: bewusst einen Testspawn über TheMob anfordern; Limits, Cooldown, Regionen und PreSpawn-Veto bleiben wirksam.

Normale Spieler dürfen beide Befehle nicht ausführen. Minions und NPC-Kills zählen standardmäßig nicht im Beispiel. Parallel dieselbe Quest nicht zusätzlich über Bukkit-Tod, eingebauten Adapter und dieses Beispiel belohnen. API-Klassen bleiben `compileOnly` und werden nicht in das Add-on kopiert.

SDK, Dokumentation und Beispiel stehen unter der beigefügten, auf die öffentliche API begrenzten MIT-Lizenz. Ein öffentliches Downloadhosting oder Maven-Central-Deployment ist nicht Teil des lokalen ZIPs.

API 1.3 zeigt außerdem einen eigenen Eintrag unter `/mob` → Plugin-Anbindungen und einen Itemprovider `themobexample:token`. Dienste werden beim Disable abgemeldet. Die Beispielmarke wird nur auf Anforderung erzeugt und nicht automatisch verteilt.
