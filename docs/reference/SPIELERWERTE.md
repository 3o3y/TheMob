# Ausrüstungsboni ab TheMob 1.19.1

TheMob besitzt kein eigenes Spieler-Skill-/Statsmenü und keine Spieler-Tiers mehr. Werte entstehen ausschließlich durch die aktuell ausgerüsteten Gegenstände. Andere Plugins können ihre eigenen Spielerwerte und Menüs unabhängig verwalten.

Erhalten bleiben zusätzlicher Angriff und Schaden, Rüstung, Defense, zusätzliches Leben, Bewegungs- und Angriffsgeschwindigkeit, Rückstoßresistenz und Glück sowie kritische Treffer, Lebensraub und besondere Itemeffekte. Native Verzauberungen, Sterne, Seltenheit, Item-IDs und bestehende Items bleiben erhalten.

Die Ausrüstungsprüfung liest Helm, Brust, Hose, Schuhe sowie Haupt- und Nebenhand. Eigene temporäre Attributmodifikatoren ergänzen die Vanilla-Werte; fremde Modifikatoren werden nicht ersetzt. Schaden und zusätzliche Defense werden weiterhin genau einmal berücksichtigt.

Zusätzliche Lebenspunkte bleiben beim Aus- und Einloggen korrekt erhalten. Es wird nur der tatsächlich beim Entfernen eigener Ausrüstungsmodifikatoren verlorene Lebensanteil einmalig wieder gutgeschrieben. Verletzungen bleiben bestehen; Tod/Respawn verwirft alte Gutschriften. Hunger bleibt der normale Minecraft-Nahrungswert und erhält keine TheMob-Spielerstufe.

`/stats` und `/skills` gehören gegebenenfalls einem anderen Plugin. TheMob registriert diese Befehle nicht. Boss- und Itemeffekte lassen sich über `/mob` → Boss- und Itemfaehigkeiten beziehungsweise `/mob abilities` steuern.
