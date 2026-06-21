# TerraFirmaGreg-Modern Knowledge Base for LLM Chat
#
# This file is injected into every AI request as a system message.
# It gives the AI domain knowledge about the modpack so it can answer
# player questions accurately.
#
# HOW TO USE:
#   1. This file ships with the mod repo. Copy it to your server's
#      config/ folder as "llmchat-knowledge.md" (or set knowledgeFile
#      in llmchat-common.toml to point anywhere you like).
#   2. Edit it to match your server's exact version and configuration.
#      Some details below are general knowledge about the parent mods
#      (TerraFirmaCraft and GregTech CEu Modern) and may differ from
#      the specific TFG 0.12.7 implementation. Verify recipes and
#      mechanics against your server and update as needed.
#   3. After editing, run /llmreload in-game (OP only) to reload it
#      without restarting the server.
#
# The larger this file, the more tokens each request costs. A few
# thousand words is fine and cheap. If it gets huge (50K+ chars),
# consider trimming to the most useful sections.

## What is TerraFirmaGreg-Modern?

TerraFirmaGreg (TFG) is a modpack that combines two major overhaul mods:
- TerraFirmaCraft (TFC): realistic survival mechanics. Stone age start,
  seasonal agriculture, food decay, body temperature, geology-based ore
  generation, knapping, pottery, bloomery, anvil forging.
- GregTech CEu Modern (GT): industrial tech progression. Steam age
  through extreme voltage tiers, complex ore processing chains, machine
  crafting, circuits, byproduct extraction.

The two mods are integrated so that TFC's early-game survival feeds into
GregTech's industrial progression. Players start in the stone age and
work their way up through bronze, iron, steel, and eventually into
electrically-powered GregTech machines.

The modpack runs on Minecraft 1.20.1 with Forge.

## TerraFirmaCraft Key Mechanics

### Early Game (Stone Age)
- You start with nothing. Break rocks to get stone, knap stone tools.
- Knapping: open the knapping GUI with 2+ of the same rock type in hand.
  Clay knapping is also used for pottery.
- Fire starter: use a stick and a firestarter to ignite a fire pit.
- Thatch: made from tall grass, used for early shelter.
- Rock types matter: different rocks (igneous, metamorphic, sedimentary)
  yield different tools and indicate what ores are nearby.

### Metal Ages
- Copper: the first metal. Smelt copper ore in a crucible over a fire pit
  or pit kiln, pour into a tool mold.
- Bronze: alloy of copper + tin (or copper + bismuth + zinc for bismuth
  bronze, or copper + zinc for brass). Mix in crucible.
- Iron: produced in a bloomery. Requires iron ore, charcoal, and a
  bloomery structure. The bloom must be refined on an anvil.
- Steel: higher tier, requires specific processing. In TFG, steel
  production may bridge into GregTech.

### Anvil Forging
- Requires a hammer and an anvil (stone anvil early, metal anvil later).
- Welding: join two items, requires flux (borax or from certain rocks).
- Forging: shape metal using a hammer, following a plan with specific
  steps (hit, draw, punch, etc.). The plan shows the last 3 steps needed.
- Each forging action requires the anvil to be at working temperature.

### Food and Survival
- Food decays over time. Different foods decay at different rates.
- Preserve food by: salting, pickling (in vinegar), brining, smoking,
  drying, or sealing in a ceramic vessel.
- Nutrition: eating a variety of food groups (grain, veg, fruit, meat,
  dairy) increases max health. Eating only one type caps health low.
- Body temperature: affected by biome, season, time of day, clothing.
  Can get too hot or too cold, taking damage.

### Agriculture
- Crops grow only in specific seasons. Check the calendar.
- Soil has three nutrient types: N, P, K. Different crops deplete
  different nutrients. Rotate crops.
- Irrigation (water nearby) speeds growth.
- Fruit trees take multiple in-game years to mature.

### Geology and Ores
- Ores generate in specific rock types, not randomly. Look for the right
  rock layer.
- Prospect by examining surface rocks or using a prospector's pick.
- Native ores (native copper, native gold) vs processed ores.

## GregTech CEu Modern Key Mechanics

### Voltage Tiers
Each tier is 4x the previous:
- ULV (Ultra Low Voltage): 8 EU/t
- LV (Low Voltage): 32 EU/t
- MV (Medium Voltage): 128 EU/t
- HV (High Voltage): 512 EU/t
- IV (Extreme Voltage): 2048 EU/t
- LuV, ZUV, and beyond for late game.

Machines explode if they receive more voltage than they can handle
(unless configured otherwise).

### Steam Age (Pre-LV)
- Bronze and steel steam machines run on steam, not electricity.
- Steam is produced by a boiler (solid or liquid fuel).
- Steam machines: steam extractor, steam furnace, steam macerator, etc.
- These are the bridge between TFC metalworking and GregTech electricity.

### Ore Processing Chain
GregTech rewards multi-step ore processing for more output and byproducts:
1. Ore -> Macerator/Pulverizer -> Crushed Ore (+ byproduct dust)
2. Crushed Ore -> Ore Washing -> Cleaned Ore (+ tiny byproduct dust)
3. Cleaned Ore -> Thermal Centrifuge -> Purified Ore (+ more byproducts)
4. Purified Ore -> Furnace/Smelter -> Ingot
Each step can yield extra byproduct materials.

### Materials and Byproducts
- Many ores contain trace secondary materials. Processing them through
  the full chain yields these as byproducts.
- Example: processing copper ore might yield cobalt or gold as byproducts.
- The material system defines properties (melting point, durability,
  conductivity) for hundreds of materials.

### Circuits
- Circuits are required for most machines and crafting.
- Each voltage tier needs a higher tier circuit.
- Circuits require electronic components (resistors, capacitors, diodes,
  transistors, circuit boards) which themselves require specific materials
  and machines to craft.

### Machine Crafting
- Machines need a machine casing (hull) + internal components.
- Each tier uses a different hull material (bronze, steel, aluminum,
  stainless steel, titanium, tungstensteel...).
- Machine crafting often requires an assembler or crafting bench.

## TFG Integration Notes

### Progression Flow
1. Stone age (TFC knapping, fire, shelter)
2. Copper age (TFC crucible, casting)
3. Bronze age (TFC alloying, better tools)
4. Iron age (TFC bloomery, anvil)
5. Steel (TFC/GT bridge)
6. Steam age (GT bronze/steel steam machines)
7. LV (GT electrical machines, first circuits)
8. MV, HV, and beyond (full industrial progression)

### Key Tips for Players
- Don't rush. TFC's survival mechanics will kill you if you ignore them.
- Food variety matters for health. Always keep multiple food types.
- Mark your ore veins. TFC ores are rare but rich in specific locations.
- Plan your base near water and multiple rock types.
- GregTech ore processing is worth the effort -- it multiplies your yield.
- Keep a notebook of recipes. TFG has hundreds and the wiki may not cover
  the exact version.

## Important Note for the AI

If a player asks about a specific recipe or mechanic and you are not
confident about the exact details for TFG 0.12.7, say so honestly.
Tell the player what you do know and suggest they verify in-game or
check the modpack's documentation. Making up recipes that don't work
is worse than admitting uncertainty.

When answering, remember players are in-game and reading chat. Be clear
and structured. If the answer is long, use line breaks. Avoid markdown.
