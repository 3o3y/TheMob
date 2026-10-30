# Clientpaket-Herkunft

Ab 1.10.1 dient dieser Stand als historische Testbasis. Im gemeinsamen Betrieb baut und liefert CoinOverlay das aktuelle HUD-Paket einschließlich der installierten TheMob-Kompassdateien. Das neue TheMob-Serverpaket enthält keine eigenen Client-ZIPs; siehe KOMPASS.md.

Basis ist das bereits vom Server erzeugte CoinOverlay-HUD-Paket aus `8804139.zip`.
SHA-256 der unveränderten inneren Ausgangs-ZIP: `482a6a56f4a66c2f013f2fcd2f0a7e10157553bd4c6db42f28ef64e08e8eef6d`.

Die vorhandenen Shader, Schriften und Texturen bleiben als zusammengehöriger Satz enthalten. TheMob erhält zusätzlich eigene, inhaltlich identische Kompass-Schriften und Texturen unter `themob_compass`; der Pfeil entspricht pixelgenau dem CoinOverlay-Pfeil. Alle acht Mobvorlagen bleiben unberührt.

`client-pack/` enthält das eigenständige Kompasspaket (109 Dateien); `client-pack-coinoverlay/` den kombinierten Stand mit den ursprünglichen CoinOverlay-Ressourcen plus eigenen Kompassfont-Dateien. Beim eigenständigen Paket wurde die CoinOverlay-Maskierung der Vanilla-XP-Anzeige entfernt. `gradlew.bat clientPackHash` erzeugt beide reproduzierbaren ZIPs und tatsächliche SHA-1-Dateien. Neue oder geänderte weitere Server-HUDs werden über den gemeinsamen BetterHud-Neubau auf dem Server ergänzt.

BetterHud-Materialien stehen unter MIT; die Lizenz liegt im Client- und Serverpaket.
