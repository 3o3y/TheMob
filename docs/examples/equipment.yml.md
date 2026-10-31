# equipment.yml — configuration example

This is a reference file from TheMob 1.19.1. Keep your tuned production configuration when upgrading. Use materials and entity types supported by your Minecraft version.

```yaml
# Zwei Seltenheiten x 5 Sterne je Vorlage. Items werden pro Reload einmal aufgebaut.
# max-enchant begrenzt die Vanilla-Verzauberung; Sterne/Stats bleiben eigene Itemdaten.
pieces:
  sword: {name: Jaegerschwert, material: IRON_SWORD, epic-material: DIAMOND_SWORD, enchant: sharpness, max-enchant: 5, stat: damage}
  axe: {name: Streitaxt, material: IRON_AXE, epic-material: DIAMOND_AXE, enchant: sharpness, max-enchant: 5, stat: damage}
  mace: {name: Kriegshammer, material: MACE, enchant: density, max-enchant: 5, stat: damage}
  spear: {name: Jagdspeer, material: IRON_SPEAR, epic-material: DIAMOND_SPEAR, enchant: sharpness, max-enchant: 5, stat: damage}
  bow: {name: Jagdbogen, material: BOW, enchant: power, max-enchant: 5, stat: crit_chance}
  crossbow: {name: Armbrust, material: CROSSBOW, enchant: quick_charge, max-enchant: 3, stat: crit_chance}
  trident: {name: Gezeitendreizack, material: TRIDENT, enchant: impaling, max-enchant: 5, stat: damage}
  shield: {name: Wachtenschild, material: SHIELD, enchant: mending, max-enchant: 1, stat: defense}
  helmet: {name: Waechterhelm, material: IRON_HELMET, epic-material: DIAMOND_HELMET, enchant: protection, max-enchant: 4, stat: defense}
  chestplate: {name: Waechterbrustpanzer, material: IRON_CHESTPLATE, epic-material: DIAMOND_CHESTPLATE, enchant: protection, max-enchant: 4, stat: defense}
  leggings: {name: Waechterbeinschutz, material: IRON_LEGGINGS, epic-material: DIAMOND_LEGGINGS, enchant: protection, max-enchant: 4, stat: defense}
  boots: {name: Waechterstiefel, material: IRON_BOOTS, epic-material: DIAMOND_BOOTS, enchant: protection, max-enchant: 4, stat: defense}
  pickaxe: {name: Runenspitzhacke, material: IRON_PICKAXE, epic-material: DIAMOND_PICKAXE, enchant: efficiency, max-enchant: 5, stat: defense}
  shovel: {name: Runenschaufel, material: IRON_SHOVEL, epic-material: DIAMOND_SHOVEL, enchant: efficiency, max-enchant: 5, stat: defense}
  hoe: {name: Runenhacke, material: IRON_HOE, epic-material: DIAMOND_HOE, enchant: efficiency, max-enchant: 5, stat: defense}
  shears: {name: Runenschere, material: SHEARS, enchant: efficiency, max-enchant: 5, stat: defense}
```
