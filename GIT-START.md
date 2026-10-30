# IntelliJ, Git und GitBook

## Eigenständiges Projekt

Das Quellprojekt-ZIP in einen eigenen Ordner außerhalb eines bereits bestehenden Git-Repositories entpacken, zum Beispiel `IdeaProjects/TheMob`. Den Ordner mit `settings.gradle.kts` in IntelliJ öffnen; Gradle JVM und Project SDK auf JDK 25 stellen. Der Produktionscode wird mit `--release 17` kompiliert. `gradlew.bat build` baut Plugin, SDK, Quellprojekt und Dokumentation.

Es gibt keine Abhängigkeit auf CoinOverlay-, PK-, Skilltree- oder Cosmetics-Quellprojekte. Die optionalen echten Anbieter-API-Tests benötigen ausdrücklich angegebene, selbst beschaffte JARs. Diese JARs sind nicht im Projekt enthalten. Die mitgelieferten Compile-Bibliotheken unter `libraries` und der Gradle Wrapper gehören dagegen zum Projekt; Lizenzhinweise dort behalten.

## Verbundenes GitHub-Repository

Dieser Checkout ist mit `https://github.com/3o3y/TheMob.git` verbunden. Der aktuelle Update-Branch heißt `codex/themob-1.19.0-nosqlite`. Die bisherige Historie auf `master` bleibt erhalten. In IntelliJ diesen Projektordner öffnen; ein erneutes `git init` ist hier nicht nötig.

```powershell
git status
git pull --ff-only
# Nach eigenen Änderungen:
git add .
git commit -m "Describe your changes"
git push
```

Die folgenden Schritte gelten nur für einen späteren unabhängigen Export ohne Git-Historie.

## Export in ein anderes Git-Repository übernehmen

Im neuen Projektordner ausführen, nachdem der Zielordner geprüft ist:

```powershell
git init -b main
git add .
git status
git commit -m "Initial TheMob 1.19.0 source"
```

Danach im gewünschten Git-Host ein Repository erstellen und dessen echte Remote-Adresse eintragen. Es wurde hier kein Repository erstellt und nichts hochgeladen. `.gitignore` schließt Builds, Caches, IntelliJ-Lokaldaten, Logs und lokale Umgebungsdateien aus. Keine Serverwelten, Zugangsdaten, Premium-JARs oder Produktiv-Spielerdaten hinzufügen. Vor einer öffentlichen Veröffentlichung die Rechte am vollständigen Plugin prüfen; die API-/SDK-Lizenz erweitert nicht automatisch die Lizenz des Gesamtprojekts.

## GitBook

Die vollständigen Dokuquellen liegen unter `docs/`, Navigation in `docs/SUMMARY.md`, Konfiguration in `.gitbook.yaml`. Die bisher veröffentlichten Seitenpfade werden beibehalten; neue Seiten ergänzen Adminmenü, API, BattlePass, Integrationen, Dungeons, Skills und Kompatibilität. `TheMob-GitBook-1.19.0.zip` enthält diese Dateien zur Übernahme.

Die Dateien sind für GitBook Git Sync vorbereitet. Eine bestehende GitBook-Anbindung zuerst mit ihrem tatsächlichen Repository abgleichen; initiale Sync-Richtung bewusst wählen. `docs/SUMMARY.md` ist ein Inhaltsverzeichnis, keine Aufforderung, ein bestehendes Space zu löschen oder neu anzulegen.

Öffentliche Adresse: https://3o3y.gitbook.io/the-mob. Die Aktualisierung wurde am 28.10.2026 als Change Request #95 zusammengeführt und veröffentlicht. API 1.3.1, Adminmenü, Integrationen, Skills, Dungeons und die Kompatibilitätsgrenzen sind dokumentiert.

Git Sync ist weiterhin nicht verbunden. `docs/public-url-map.json` ordnet jede lokale Inhaltsseite ihrem tatsächlichen öffentlichen Pfad zu. Einige beim Import hinzugefügte Seiten haben abweichende öffentliche Pfade (zum Beispiel `reference-1` und `getting-started-1`). Vor einem späteren Git Sync diese Zuordnung und die bestehende Navigation übernehmen; ein bloßer Neuimport wäre kein URL-erhaltender Abgleich. Detailseiten bleiben über die Kapitelübersichten erreichbar, auch wenn sie aus der Hauptnavigation ausgeblendet sind.
