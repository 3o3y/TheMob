# Navigation HUD

The compass follows CoinOverlay's animation and pointer. The graphical layout uses the shared CoinOverlay HUD resource pack, positioned above the bossbar in the prepared layout. TheMob does not replace it with a second mandatory pack.

Players without a loaded pack receive the text compass. Declining, failed loading or removal returns to text; a successful pack load enables graphical output when the HUD provider is compatible. Match the resource pack and HUD provider to the client's Minecraft version. `/mobkompass` manages personal and server settings.

[Complete configuration and examples](../reference/KOMPASS.md). The detailed operator references currently use German; the public API developer guide is English.
