# Minecraft 1.18–26.3

One core JAR targets the whole range using `api-version: 1.18` and Java 17 bytecode. Minecraft sets the server runtime requirement; the full source build uses JDK 25. The automated matrix checks 32 official Spigot APIs, and separate MockBukkit suites exercise 1.18.2 and 26.3.

Older servers omit unavailable species/items and size attributes. Pre-1.19.4 displays use short-lived marked ArmorStands. Tuff-brick dungeon decoration falls back to stone bricks. Combat phases, HP bookkeeping, drop attribution and existing commands are retained. Folia is outside the supported scheduler model.

API linkage and simulations do not establish live testing of every server/client combination. Optional integrations need matching provider versions, and a 26.3 resource pack is not suitable for all older clients.

[Complete configuration and examples](../reference/KOMPATIBILITAET.md). The detailed operator references currently use German; the public API developer guide is English.
