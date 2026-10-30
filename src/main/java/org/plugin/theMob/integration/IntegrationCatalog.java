package org.plugin.theMob.integration;

import java.util.*;

/** Curated RPG/server suite, not a claim of a measured global popularity ranking. */
public final class IntegrationCatalog {
  public record Target(
      String id, String name, List<String> pluginNames, String feature, String source) {
    public Target {
      pluginNames = List.copyOf(pluginNames);
    }
  }

  private static Target entry(String id, String name, String names, String feature, String source) {
    return new Target(id, name, List.of(names.split(",")), feature, source);
  }

  private static final List<Target> TARGETS =
      List.of(
          entry(
              "jobs",
              "Jobs",
              "Jobs",
              "Jobs-Killzahlungen/XP ohne doppelte Killbuchung",
              "https://www.zrips.net/jobs/api/"),
          entry(
              "citizens",
              "Citizens",
              "Citizens",
              "NPC-Ausschluss und konfigurierte Spawn-Klicks",
              "https://wiki.citizensnpcs.co/API"),
          entry(
              "quests",
              "Quests",
              "Quests",
              "Natives Custom Objective TheMob Kill",
              "https://pikamug.gitbook.io/quests/master/custom-quest-api"),
          entry(
              "battlepass",
              "BattlePass",
              "BattlePass",
              "Neun native Questfamilien: Kills, Phasen, Schaden, Beute, Dungeons",
              "https://battlepass.advancedplugins.net/developer-api/developer-api-creating-your-own-quest-types"),
          entry(
              "placeholderapi",
              "PlaceholderAPI",
              "PlaceholderAPI",
              "TheMob-Platzhalter fuer Anzeigen und Fremdplugins",
              "https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/"),
          entry(
              "vault",
              "Vault",
              "Vault",
              "Economy-Service; Geld nur bei ausdruecklicher Mobkonfiguration",
              "https://github.com/MilkBowl/VaultAPI"),
          entry(
              "worldguard",
              "WorldGuard",
              "WorldGuard",
              "mob-spawning und Schutz benannter Regionen vor Dungeonbau",
              "https://worldguard.enginehub.org/en/latest/developer/regions/region-queries/"),
          entry(
              "luckperms",
              "LuckPerms",
              "LuckPerms",
              "Bukkit-Rechte und Dungeon-Kontext themob:dungeon",
              "https://luckperms.net/wiki/Developer-API"),
          entry(
              "essentialsx",
              "EssentialsX",
              "Essentials",
              "Vanish respektieren; native Teleports und Rechte bleiben zustaendig",
              "https://github.com/EssentialsX/Essentials/wiki/Hooking-into-Essentials"),
          entry(
              "multiverse",
              "Multiverse-Core",
              "Multiverse-Core",
              "Native Welt-/Spawnereignisse, Welt-UUIDs; kein Umgehen des Weltmanagers",
              "https://mvplugins.org/core/developers/api-usage/"),
          entry(
              "mythicmobs",
              "MythicMobs",
              "MythicMobs",
              "Mythic-Items als native Drops ueber mythicmobs:ID",
              "https://git.mythiccraft.io/mythiccraft/MythicMobs/-/wikis/API"),
          entry(
              "mmoitems",
              "MMOItems",
              "MMOItems",
              "Native Itemerzeugung und Metadaten via mmoitems:TYP/ID",
              "https://gitlab.com/phoenix-dvpmt/mmoitems/-/wikis/Main-API-Features"),
          entry(
              "auraskills",
              "AuraSkills",
              "AuraSkills",
              "Optionale explizite Kampf-XP; keine automatische Doppelauszahlung",
              "https://wiki.aurelium.dev/auraskills/api"),
          entry(
              "mcmmo",
              "mcMMO",
              "mcMMO",
              "Optionale explizite Skill-XP ueber ExperienceAPI",
              "https://github.com/mcMMO-Dev/mcMMO/wiki/API"),
          entry(
              "griefprevention",
              "GriefPrevention",
              "GriefPrevention",
              "Claim-Grundflaechen vor Dungeonbau schuetzen",
              "https://github.com/GriefPrevention/GriefPrevention"),
          entry(
              "towny",
              "Towny",
              "Towny",
              "Townblocks vor Dungeonbau schuetzen",
              "https://javadoc.io/doc/com.palmergames.bukkit.towny/towny/latest/index.html"),
          entry(
              "lands",
              "Lands",
              "Lands",
              "Land-Chunks vor Dungeonbau schuetzen",
              "https://github.com/angeschossen/LandsAPI"),
          entry(
              "advancedcrates",
              "AdvancedCrates",
              "AdvancedCrates",
              "Original-Kistenschluessel als Beute via advancedcrates:Kisten-ID",
              "https://advancedplugins.net/item/6/advancedcrates"),
          entry(
              "economyshopgui",
              "EconomyShopGUI",
              "EconomyShopGUI-Premium,EconomyShopGUI",
              "Verkaufsschutz fuer native TheMob-Spezialitems per Transaktionsereignis",
              "https://wiki.gpplugins.com/economyshopgui/api/api-usage"),
          entry(
              "decentholograms",
              "DecentHolograms",
              "DecentHolograms",
              "Optionale nichtpersistente Bossanzeigen und PlaceholderAPI",
              "https://wiki.decentholograms.eu/api/dhapi/"));

  public static List<Target> targets() {
    return TARGETS;
  }

  public static Optional<Target> find(String id) {
    if (id == null) return Optional.empty();
    return TARGETS.stream()
        .filter(
            t ->
                t.id().equalsIgnoreCase(id)
                    || t.name().equalsIgnoreCase(id)
                    || t.pluginNames().stream().anyMatch(n -> n.equalsIgnoreCase(id)))
        .findFirst();
  }

  private IntegrationCatalog() {}
}
