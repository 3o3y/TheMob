# Gemeinsames CoinOverlay-Ressourcenpaket / TheMob 1.10.2

**Seit TheMob 1.16.2 / CoinOverlay 1.8.4:** Ohne bestätigtes Laden erscheint ein normaler Textkompass. Annahme oder Download allein aktivieren keine Grafik. Das gilt auch bei `offer-on-join: false`. Ablehnen und Ladefehler lassen die Spielmechaniken verfügbar. Plugin-Angebote sind immer optional; auch auf dem Hoster `require-resource-pack=false` setzen. Serverweite und persönliche Kompass-Schalter gelten weiterhin. Grafisches Haus und Bannermotive benötigen die entsprechenden Clientgrafiken.

**Das CoinOverlay-Ressourcenpaket liefert auch den Kompass.** Verwendet wird das gemeinsam erzeugte `plugins/CoinOverlay/resource-pack/CoinOverlay-HUD-26.3.zip` samt dessen SHA-1. Für den zuletzt bestätigten Serverstand liegt das bereits angepasste gemeinsame CoinOverlay-Paket im Serverpaket unter `client-resource-pack/` bereit.

TheMob installiert weiterhin seine Richtungszeichen und den goldenen Pfeil in BetterHud und steuert Anzeige, Regenbogenanimation und Kompassbefehle. CoinOverlay bleibt für den gemeinsamen Paketbuild, Export und Download zuständig. Die Kompassdateien werden dadurch zusammen mit Coins, Lebensanzeigen und den anderen aktiven BetterHud-Anzeigen gebaut. Die aktuelle CoinOverlay-Version benötigt dafür keinen eigenen Kompasscode.

## Kompass oberhalb der Bossbar

Die Reihenfolge ist jetzt **Kompass mit goldenem Pfeil → Bossbar(s) → Spielansicht**. Kompass und Pfeil stehen oben mittig bei Pixel Y 8 und 18. BetterHud erhält zwei unsichtbare Trägerzeilen; native Bossbars werden darunter dargestellt. Bei zwei Zeilen beginnt der erste native Bossbartitel bei Y 41, der Pfeil endet bei Y 27.

TheMob stellt diese Positionen vor jedem BetterHud-Build wieder her, nachdem CoinOverlay seine bisherige Positionsmigration angewendet hat. CoinOverlay bleibt allein für das gemeinsame Paketangebot und den Export zuständig. Die 26.3-Zeilenhöhe wird so kompensiert, dass die übrigen CoinOverlay-Anzeigen ihre Bildschirmposition behalten.

TheMob sichert die bisherigen betroffenen BetterHud-Dateien einmal unter `.before-themob-1.10.2`. In BetterHuds config.yml werden mindestens zwei `bossbar-line` und dieselbe `bossbar-resource-pack-line` gesetzt; `merge-boss-bar` wird false. Größere bestehende Trägerbereiche bleiben erhalten. Bei CoinOverlay 1.7.4 `overlay.resource-hud-offset-y: auto` verwenden. Ein-/Ausschalten des Kompasses entfernt den reservierten gemeinsamen Kopfbereich nicht dynamisch.

## Fertiges gemeinsames Clientpaket

`CoinOverlay-HUD-26.3-Kompass-oben.zip` liegt im Serverpaket unter `client-resource-pack/` und im Arbeitsprojekt unter `build/prepared-client/`. Es basiert exakt auf dem zuletzt bereitgestellten CoinOverlay-Paket mit SHA-1 `f7ca043de5c60e1ec720dbed72bd3f53c5a916ae`. Alle 1357 Dateien bleiben erhalten; nur zwei Kompassfonts und der passende 26.3-Shader ändern sich.

SHA-1 des neuen gemeinsamen Pakets:

```properties
resource-pack-sha1=9ff23d55ad1d667a97cc2c6901d15160180fc62b
```

**Dieses Paket benötigt TheMob 1.10.2 und dessen neuen Kopfbereich.** Nach dem vollständigen Serverneustart die ZIP unverändert veröffentlichen und Download-URL/SHA-1 gemeinsam aktualisieren. Für diesen bestätigten Serverstand ist dann kein zusätzlicher Paketbau nötig. Ein später auf dem Server neu erzeugtes ZIP hat gegebenenfalls eine andere Prüfsumme.

## Update und künftige Paketbauten

1. Server stoppen und die TheMob-JAR durch 1.10.2 ersetzen. CoinOverlay und BetterHud bleiben installiert.
2. Server vollständig starten und danach neu verbinden. Das ist für die Reihenfolge der Bossbar-Trägerzeilen erforderlich.
3. Entweder das oben genannte fertige gemeinsame Paket veröffentlichen oder mit `/coinsadmin hud reload` bzw. `/mobkompass reload` das aktuelle gemeinsame Serverpaket bauen.
4. Bei einem Server-Neubau `plugins/CoinOverlay/resource-pack/CoinOverlay-HUD-26.3.zip` samt seiner tatsächlichen SHA-1 veröffentlichen. Die Paket-URL/SHA-1 wird nur in CoinOverlay oder server.properties gepflegt.

TheMob beobachtet CoinOverlays Paket-ID und Ladebestätigung; es bietet beim Join kein zweites Paket an. `/mobkompass pack` leitet die Anfrage an CoinOverlay weiter. Die älteren Hashes f7ca043d… und 0aa6b2e8… gelten für die bisherigen ZIPs und dürfen nicht für das neue Paket übernommen werden.

## Befehle

`/mobkompass an/aus/status`, `/mobkompass server an/aus/status` und `/mobkompass spieler NAME an/aus/status` bleiben erhalten; `/kompass` ist ein Alias. Rechte und persönliche Speicherung bleiben unverändert. Mob-Radar und Kampfausblendung bleiben erhalten. Ohne BetterHud wird weiterhin die Textanzeige verwendet; die neue Reihenfolge betrifft den grafischen Kompass.

Automatisch geprüft sind Headerkonfiguration, Migration, Sicherung, wiederholte CoinOverlay-Neubauten, Paket-ID, Ladebestätigung sowie der Erhalt aller übrigen Paketdateien. Ein visueller Test im laufenden Minecraft-Client steht noch aus.
