---
title: CMI 升阶系统
tags: CMI, rankup, 权限组
source: https://www.zrips.net/cmi/rankup-system/
---
Main server folder **spigot.yml** and check that stats saving prevention is set to false **stats->disable-saving: false**  
Players will get the default rank group depending on the set permission node **cmi.rank.[rankName]**  

Not enabled ranks will be ignored.  

Do check the available commands for rank [[CMI 命令列表|here]].

## Base options

**DisplayName** section is optional and it will be used to represent rank ingame. This is NOT defining the required permission node and you still need to use the node name to rankup to this rank  

**DefaultRank** section defines if the player can be assigned to this rank if he doesn’t have any. There can be more than one default rank, and depending on the permission node, the first rank will be assigned to that player when needed

**AutoRankup** section defines if the player will be auto-ranked to this rank if possible. Keep in mind that if there is more than one legit rankup’s, the player will have to confirm to which rank he wants to rankup

**RankupConfirmation** section defines if you want to add additional confirmation for this rankup. Useful to avoid unintentional rankups. Keep in mind that **autorankup** will request confirmation for rankup to this rank even if there is only one legit rankup option

**NextRanks** is the list of possible next ranks from this rank. Can be one or can be dozens. Keep in mind that each rank requirement will be shown in chat and it can clutter quite a lot if there is a bunch of them at once

**MoneyCost** defines how much the player will have to pay to rankup to this rank

**ExpCost** defines how much the player will have to pay in exp to rankup to this rank

**Votes** defines amount of Votes player needs to have. This will be taken from CMI vote recording based on Votifier plugin

**Commands** are a list that will be performed on rankup. Can be additional actions, like message broadcast or anything else  

**CommandsOnRankDown** is a list of commands which will be performed on rankdown action. Commands will be performed when you are ranking down **from this** rank.

## **StatsRequirements** 

The main section is **StatsRequirements** and it can have a lot of different values in it:  
In general, format goes like this **[mainStat]( : optionalSubStat):amount**  
For example, **MonsterKills:100** will require from player to have 100 monster kills while **MonsterKills:Zombie:100** will require to have **100 zombie** kills. Both of them can be used if needed.

List of possible requirements and basic explanations:

Distance in blocks:

-   **Travel**– Travel in total
-   **Walk**– has to walk
-   **Sneak**– has to sneak
-   **Sprint**– has to sprint
-   **Swim**– has to swim
-   **Fall**– has to fall
-   **Climb**– has to climb
-   **Fly**– has to fly
-   **WalkUnderWater** – has to walk underwater
-   **WalkOnWater**– has to walk on water
-   **MinecartTravel**– has to travel with a minecart
-   **BoatTravel**– has to travel by boat
-   **PigTravel**– has to ride a pig
-   **HorseTravel**– has to ride a horse
-   **ElytraTravel**– has to fly with elytra
-   **GhastTravel**– has to travel on ghast
-   **NautilusTravel**– has to travel on nautilus
-   **Strider travel**– has to travel on strider

Time in sec:

-   **PlayTime**– total playtime
-   **AccountAge**– the time from the first player login into the server
-   **FromLastDeath**– from last death
-   **SneakTime**– sneak time
-   **TotalWorldTime**

Amounts:

-   **GameQuit**– how many times player left the game
-   **Jump**– jump count
-   **DamageDealt**– total damage made
-   **DamageTaken**– total damage taken
-   **Deaths**– count of deaths
-   **MobKills**– the total amount of mobs killed by the player
-   **PlayerKills**– the total amount of players killed. Indirect kills don’t count
-   **ItemEnchanted**– the number of enchanted items
-   **AnimalsBred**– animals bred
-   **FishCaught**– fish caught
-   **TalkedToVillager**– times talked to a villager
-   **TradedWithVillager**– traded with villagers
-   **CakeSlicesEaten**– cake slices eaten
-   **CauldronFilled**– cauldron filled
-   **CouldronUsed**– cauldron used
-   **ArmorCleaned**– times armor cleaned (leather armor)
-   **BannerCleaned**– times banner cleaned
-   **BrewingstandInteractions**– brewing stand interactions
-   **BeaconInteractions**– beacon interactions
-   **CraftingTableInteractions**– crafting table interactions
-   **FurnaceInteractions**– furnace interactions
-   **DispenserInspected**– dispenser inspected
-   **DropperInspected**– dropper inspected
-   **HopperInspected**– hopper inspected
-   **ChestOpen**– chest open
-   **TrappedChestTriggered**– trapped chest triggered
-   **EnderchestOpened**– enderchest opened
-   **NoteblockPlayed**– noteblock played
-   **NoteblockTuned**– noteblock tuned
-   **FlowerPotted**– flower potted
-   **RecordPlayed**– record played
-   **SleeptInBed**– sleept in bed
-   **ShulkerBoxOpened**– shulker box opened
-   **ShulkerBoxCleaned** – cleaned shulker boxes
-   **ItemDropped**– defines the amount of dropped items. If a specific item is not defined, then the total amount is used
-   **ItemPickups**– defines the amount of picked-up items. If a specific item is not defined, then the total amount is used
-   **BlocksMined**– defines the number of blocks mined. If a specific block is not defined, then the total amount is used
-   **ItemBreaks**– defines the number of broken items. If a specific item is not defined, then the total amount is used
-   **ItemCrafts**– defines the number of crafted items. If a specific item is not defined, then the total amount is used
-   **ItemsUsed**– defines the amount of item usage. like mining with a pickaxe. If a specific item is not defined, then the total amount is used
-   **MonsterKills**– defines the number of monster kills. If a specific monster is not defined, then the total amount is used
-   **KilledBy**– defines the number of deaths by a monster. If a specific monster is not defined, then the total amount is used
-   **InteractionWithBlastFurnace**
-   **InteractionWithSmoker**
-   **InteractionWithLectern**
-   **InteractionWithCampfire**
-   **InteractionWithCartographyTable**
-   **InteractionWithLoom**
-   **InteractionWithStonecutter**
-   **InteractionWithAnvil**
-   **InteractionWithGrindstone**
-   **InteractionWithSmithingTable**
-   **BellRing**
-   **RaidTrigger**
-   **RaidWin**
-   **TargetHit**
-   **OpenBarrel**
-   **DropCount**

```
  StatsRequirements:
  - "PlayTime:3600"
  - "travel:1000"
  - "MonsterKills:zombie:10"
  - "MonsterKills:slime:5"
  - "MonsterKills:wolf:5"
  - "AccountAge:1209600"
```

## ItemRequirement

List of items player needs to have in their inventory. Defined as material followed with amount.

```
  ItemRequirement:
  - "stone:10"
  - "stone:1:20"
  - "book:20"
```

## JobsRequirement

List of jobs player needs to be in and their minimal level. Additionally you can use **totallevel** to ask for total level amount across all active players jobs

```
  JobsRequirement:
  - "miner:10"
  - "totallevel:20"
```

## McMMORequirement

List of mcmmo skills and their minimal levels. Additionally you can use **power** to require specific total skill level

```
  McMMORequirement:
  - "woodcutting:10"
  - "power:20"
```

## PermissionRequirement

List of permission requirements. It needs to have actual permission node and followed with its short display name which will be used while showing rank information in game

```
  PermissionRequirement:
  - "cmi.command.fly:Fly"
```

## PlaceholderRequirements

List of placeholder requirements. This can check by numeric placeholder or text based. For numeric placeholders use **>=** or **\==** to check for equal or higher than or exact value. For text based placeholders always use **\==** to check for exact match, tho we will ignore capitalization. This will need to include short description which will be used as display name for rank information in game

```
  PlaceholderRequirements:
  - '%cmi_user_level%>=10;Get to level 10'
  - '%cmi_user_homeamount%>=3;Get 3 homes'
  - '%cmi_user_weather%==rainy;We like rain'
```

## Default file example

```
Newbie:
  Enabled: true
  DisplayName: "&2Newbie"
  DefaultRank: true
  AutoRankup: true
  NextRanks:
  - Branch1
  - Branch2
Branch1:
  Enabled: true
  DisplayName: "&2Branch1"
  AutoRankup: true
  RankupConfirmation: true
  Votes: 5
  PermissionRequirement:
  - "cmi.command.fly:Fly"
  McMMORequirement:
  - "woodcutting:10"
  - "power:20"
  JobsRequirement:
  - "miner:10"
  - "totallevel:20"
  ItemRequirement:
  - "stone:10"
  - "stone:1:20"
  - "book:20"
  NextRanks:
  - Branch1Rank1
  MoneyCost: 100
  Commands:
  - "broadcast! &6[playerDisplayName] &eleveled up to Branch1 rank!"
  StatsRequirements:
  - "PlayTime:3600"
  - "travel:1000"
  - "MonsterKills:zombie:10"
  - "MonsterKills:slime:5"
  - "MonsterKills:wolf:5"
  - "AccountAge:1209600"
  PlaceholderRequirements:
  - '%cmi_user_level%>=10;Get to level 10'
  - '%cmi_user_homeamount%>=3;Get 3 homes'
  - '%cmi_user_weather%==rainy;We like rain'
Branch1Rank1:
  Enabled: true
  DisplayName: "&2Branch1Rank1"
  DefaultRank: false
  AutoRankup: true
  RankupConfirmation: false
  PermissionRequirement:
  - "cmi.command.heal:Heal"
  NextRanks:
  - Last
  MoneyCost: 300
  ExpCost: 350
  Commands:
  - "broadcast! &6[playerDisplayName] &eleveled up to Branch1Rank1 rank!"
  StatsRequirements:
  - "travel:10000"
  - "blocksmined:3000"
  - "blocksmined:diamond_ore:30"
  - "MonsterKills:1000"
  - "AccountAge:1209600"
  McMMORequirement:
  - "taming:10"
  - "unarmed:30"
Branch2:
  Enabled: true
  DisplayName: "&2Branch2"
  AutoRankup: true
  RankupConfirmation: true
  MoneyCost: 150
  NextRanks:
  - Branch2Rank1
  Commands:
  - "broadcast! &6[playerDisplayName] &eleveled up to Branch2 rank!"
  PermissionRequirement:
  - "cmi.command.tp:Teleport"
  StatsRequirements:
  - "PlayTime:3600"
  - "travel:2000"
  - "MonsterKills:zombie:15"
  - "MonsterKills:slime:10"
Branch2Rank1:
  Enabled: true
  DisplayName: "&2Branch2Rank1"
  AutoRankup: true
  RankupConfirmation: false
  MoneyCost: 100
  ExpCost: 150
  NextRanks:
  - Branch2Rank2
  Commands:
  - "broadcast! &6[playerDisplayName] &eleveled up to Branch1Rank1 rank!"
  StatsRequirements:
  - "PlayTime:4600"
  - "Swim:1000"
  - "SneakTime:180"
  - "MonsterKills:100"
Branch2Rank2:
  Enabled: true
  DisplayName: "&2Branch2Rank2"
  AutoRankup: true
  RankupConfirmation: false
  MoneyCost: 100
  ExpCost: 150
  NextRanks:
  - Last
  Commands:
  - "broadcast! &6[playerDisplayName] &eleveled up to Branch2Rank2 rank!"
  StatsRequirements:
  - "walk:15000"
  - "HorseTravel:1000"
  - "ItemsUsed:10000"
  - "PlayerKills:10"
Last:
  Enabled: true
  DisplayName: "&2Last"
  AutoRankup: true
  StatsRequirements:
  - "PlayTime:21600"
  - "walk:30000"
  - "MonsterKills:1000"
  - "blocksmined:30000"
  Commands:
  - "broadcast! &6[playerDisplayName] &eleveled up to Last rank!"
  - "cmi heal [playerName]"
  - "money give [playerName] 100"
```
