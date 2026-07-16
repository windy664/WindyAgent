---
title: CMI 生成生物
tags: CMI, 命令, spawnmob
source: https://www.zrips.net/cmi/commands/spawnmob/
---
## Base variables

`**/cmi spawnmob [type] ([amount]) ([location]) (sp:[range]) (ps:[data]) (ps2:[data])**`

**[type]** – defines entity type with possible additional variables, which are explained below.

**[amount]** – amount of entities you want to spawn in. Additionally, format as **q:[amount]** can be used.

**[location]** – where you want to spawn in the entity. Defined in regular location format like **LT_Craft;46.62;-59.7;-1.7;-67.49;25.49,** which can be retrieved from **/cmi pos** command as an example, last two numbers defining pitch and yaw are optional. Additionally, in case you want to spawn a mob at a specific player’s location, you can use **l:** variable. For example, **l:Zrips,** which will spawn mob at **Zrips** location if he is online.

**sp:[range]** – defines the spread of entities in range. If you are spawning in multiple entities, you might want to set a defined spread range to distribute those more evenly and not bunch them all in one single block. For example, **sp:5.**

**ps:[data]** – defines passenger of the original entity. This on itself can contain the same type of data as the base entity.

**ps2:[data]** – same as the previous one, except that this one will try to add the entity to the second vehicle slot if possible. The most common use case would be with boats.

Simple command example: **/cmi spawnmob skeleton_horse 5 LT_Craft;46;-59;-1 ps:skeleton sp:10** which will spawn in 5 skeleton horses with 5 skeletons as their riders at the defined location and spread out in a range of 10 blocks.

## Common entity data

Command example: **/cmi spawnmob skeleton_horse;baby;hp{50};n{{#brown}Death_Bringer};s{3}**

**glow** – enables entities to glow.

**n{[name]}** – defines entities visible name. For example, **n{{#pink}My_Little_Ponny},** if you want to insert spaces in the name then use single `_` and in case you want to use actual underscore in the name, then use double like __

**hidename** – will hide entities name. Use this after setting its name, otherwise it will be shown. This can be useful in case you want to have named entity which could be more easily excluded from mob killing commands and plugins

**effect{[data]}** – potion effects to be applied to the entity. **[name]/[InSeconds]/[level]** for example **effect{speed/6666/2}** multiple effects can be defined like this **effect{speed/6666/2,fire_resistance/6669/1}**

**scale{number}** – defines entities scale. Additionally range of scales can be provided to randomize between those values, for example **sheep;scale{0.5-2.5}** which will spawn sheep with randomized size

**s{[amount]}** – entities speed, this is an alternative option for effect command if you want to increase entities speed in a simpler way.

**hp{[amount]}** – entities hp between 1 and 2048, if not defined, then the default entities hp will be used.

**pickup** – defines entities as able to pick up items from the ground.

**nopickup** – defines entities as unable to pick up items from the ground.

**immortal** – makes entity immortal.

**invisible** – makes entity invisible.

**nograv** – disables gravity for entity. This will make entities floating, but at the same time, unable to move. Has no effect on entities that can fly.

**baby** – spawns in the baby version of the entity, if possible. This will soft lock the entity in a baby stage which then technically will never grow up.

**adult** – spawns in the adult version of the entity, if possible. Keep in mind that not defining **adult** or **baby** might result in a random outcome

**dumb** – spawns the entity without AI. This will disable the entity’s movement or interactions with other entities or players.

**expire{[time]}** – defines the time (in seconds) before the entity gets automatically removed. This is limited to 20 minutes max (1200 seconds). Entities spawned with this command will be removed on server restart even if the timer has not run out yet.

**notpersistent** – spawned entities will be removed on server restart or chunk unload

**t{[playerName]}** – sets entities target to the defined online player. This will make entities attack the defined player at the moment they spawn in.

**upwards** – spawns in an upside-down entity. This option isn’t compatible with a custom entity name.

onfire – for 1.17+ servers . Spawns entity with fire effect.{{

## Armor items

**helmet{[material]}** – item in helmet slot. This can be either any material or a player’s head defined as **head:[name].** For example, **helmet{head:Zrips}**

**chest{[material]}** – item in the chest plate slot.

**legs{[material]}** – item in the legs slot.

**boots{[material]}** – item in the boots slot.

**mhand{[material]}** – item in the main hand.

**ohand{[material]}** – item in the off-hand.{

## Class dependent entity data

**tamed** – spawn in the entity which is tamed if the entity is tamable.

**saddle** – adds a saddle to the entity if they can have one (like horses, pigs, llama and so on).

**chest** – adds a chest to the entity if they can have one. This is for mule-, and donkey-type entities.

**angry** – spawns in an aggravated entity. Mainly for wolves and bees.

**bounce** – defines if a projectile should bounce off when it hits something.

## Entity type dependent data

**Goat**

**screaming** – sets the goat to the screaming state.

**Primed TNT**

incendiary – sets the primedtnt to the incendiary type.

**Creeper**

**charge** – sets the creeper’s charged state.

**Armor stand**

**noplate** – removes the plate from the armor stand.  
**arms** – enables the arms for the armor stand.  
**noarms** – disables the arms for the armor stand.  
**small** – sets the armor stand to the small size.

**Mushroom cow**

**brown** – sets the mushroom cow to the brown state.

**Villagers**

**Professions**: armorer,  butcher, cartographer, cleric, farmer, fisherman, fletcher, leatherworker, librarian, mason, nitwit, shepherd, toolsmith, weaponsmith.  
**Type**: desert, jungle, plains, savanna, snow, swamp, taiga  
Example: **villager;butcher;jungle;3** which will spawn butcher villager with jungle type and level 3.

**Experience orbs**

Providing integer number will set how much exp the player gets by picking it up. For example, **experience_orb;50**

**TNTPrimed**

Providing an integer number will set the timer when the TNT explodes. The time is in seconds and can be fractional. For example, **primed_tnt;5** which will spawn tnt, which will explode after 5 seconds.

**Ocelot**

**Types**: red, siamese, wild, or black.

**Sheep**

**Types**: which can be: black, blue, brown, cyan, gray, green, light_blue, light_gray, lime, magenta, orange, pink, purple, red, white, yellow, or rainbow.

**Horse**

**Types**: white, black, chestnut, creamy, darkbrown, or gray.

**Slime**

Providing a number will define the slime’s size. The default is: 3. For example, **slime;5**

**Magma cube**

Providing a number will define the magma cube’s size. The default is: 3. For example, **magma_cube;5**

**Llama**

**Types**: brown, creamy, gray, or white.

**Parrot**

**Types**: blue, cyan, gray, green, or red.

**Snowgolem**

**Types**: derp

**Panda**

**Types**: aggressive, brown, lazy, normal, playful, weak, or worried.

This entity can take two variables at the same time. For example, **panda;lazy,worried** which defines the main gene and hidden one, respectively.

**Cat**

**Types**: all_black, black, british_shorthair, calico, jellie, persian, ragdoll, red, siamese, tabby, or white.

**Fox**

**Types**: snow.

**Axolotl**

**Types**: blue, cyan, gold, lucy, or wild.

**Wolf**

**Types**: ashen, black, chestnut, pale, rusty, snowy, spotted, striped, woods

**Salmon**

**Types**: small, medium, large

**Boat**

As of 1.21.2+ servers boat types have their own dedicated type names

**Types**: acacia, bamboo, birch, cherry, dark_oak, jungle, mangrove, oak, or spruce.

Bamboo has no boat type, it will spawn as a raft.

**Happy Ghast**

**Types**: white, orange, magenta, lightblue, yellow, lime, pink, gray, lightgray, cyan, purple, blue, brown, green, red, black

**Area Effect Cloud**

**color**: any color, either color name or hex color code like **#662266** keep in mind that this could be overridden/ignored by having custom particles**potion:** name of potion to be used as effect of this cloud  
**particle**: effect to be used  
**radius**: initial effect radius in blocks. Max 32  
**radiustick**: radius change every tick. Can be negative value, when effects radius reaches 0 it will be removed automatically.   
**duration**: how long to keep this effect active. By default it will be show indefinitely.  
**Examples**:  
**/spawnmob area_effect_cloud;radius{32};potion{healing};radiustick{-0.1};particle{dust:green}** this will show effect with initial radius of 32 which will have healing effect and will shrink by 0.1 blocks every tick, so 2 blocks per second until it reaches 0 and disappears. Effect will be shown and green dust

## Commands:

`**/cmi spawnmob [type] ([amount]) ([location]) (sp:[range]) (ps:[data]) (ps2:[data])**`

## Permissions:

**cmi.command.spawnmob** – Spawns entity at your location

## Placeholders:

This feature has no placeholders.
