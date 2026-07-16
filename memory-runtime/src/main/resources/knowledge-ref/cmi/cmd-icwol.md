---
title: CMI 单行造物品
tags: CMI, 命令, item
source: https://www.zrips.net/cmi/commands/icwol/
---
Defining item stack with a one-liner. Keep in mind that it should not contain any spaces, and in case you need to define space for something like item name or item lore, use an underscore like `_` and in case you need an actual underscore, then use double, like `__`

##### Examples of more complicated items

```
diamondsword;{#Gray}My_Uber_Sword;&2Goblin_Slayer!\n{#pink}With_Love!;sharpness:3,durability:3;hideenchants
```

```
bow;AttackDamage:1.5:multiply_base:Mainhand,Luck:1:multiply_base:Head;unbreakable;hideunbreakable
```

```
leatherchestplate;#pink;Redstone:Coast
```

##### Material

Item definition needs to start with a material name. For example `**snowball**` or `**SNOWBALL**` or even `**SNOW_BALL**` will be accepted.

###### Sub values

Sub-values are separated with `**;**` followed with an indicator to define what type it is, and its value is enclosed with `**{}**`. If you need `**;**` inside a name or lore, then use **`;;`** No spaces allowed; use `**_**` for space and `**__**` for underscore.

##### Amount

Amount is defined after material by separating material name with -, for example, **`ACACIA_PRESSURE_PLATE-5`** which will define 5 items. If the amount is not defined then 1 will be used.  
Alternatively, you can separate the amount with `**;**` like **`ACACIA_PRESSURE_PLATE;5`**

##### Item Name

The item name is defined as `**n{&2Custom_Item_Name}**` or clean `**&2Custom_Item_Name**` if this is the first entry as a text that can’t be recognized as any other variable, ignored if it contains `\n`, which will be used for lore entry instead

Example: `**BREAD-12;&2Tasty!**` or `**BREAD-12;n{&2Tasty_food!}**`

##### Item Lore

Item lore is defined as `**l{&2First_Line\n&3Second_line}**` or clean `**&2First_Line\n&3Second_line**`separate lore lines can be defined by including `\n`, no empty spaces in it. If it is a single line, then it will need to be enclosed with `l{}` or have an item name already defined before it.

For example: `**BREAD-12;l{&2Eat_Me!}**` or `**BREAD-12;&2Eat_Me!\n{#blue}Tasty!**`

##### Enchants

Item enchants are defined as `**sharpness:2,durability:5**` separate enchants if you want to include more than one.

Example: `**Bow;durability:5,power:1**`

##### Custom Model Data

Item custom model data is defined as `**cm{123456}**` this needs to contain an integer-type number

Example: `**carrot;cm{1234}**`

##### Attributes

Item attributes are defined as **`AttackDamage:1.5:add:Mainhand,Luck:1:multiply_base:Head`** the first value will define the attribute type, followed by the amount it needs to change. This can be any fractional value that will be used in a defined operation. The operation can be defined by numbers 0, 1, 2 or by operation names add, multiply_base, multiply

Types

-   **Armor**
-   **ArmorToughness**
-   **AttackDamage**
-   **AttackKnockback**
-   **AttackSpeed**
-   **FlyingSpeed**
-   **FollowRange**
-   **KnockbackResistance**
-   **Luck**
-   **MaxHealth**
-   **MovementSpeed**
-   **JumpHeight**
-   **SpawnReinforcements**

Operations

-   **0** or **add**
-   **1** or **multiply_base**
-   **2** or **multiply**

Slots

-   **Mainhand**
-   **Offhand**
-   **Head**
-   **Chest**
-   **Legs**
-   **Feet**

Example: `**bow;AttackDamage:1.5:multiply_base:Mainhand,Luck:1:multiply_base:Head**`

##### Item Flags

Item flags are defined as `**hide_attributes,hide_enchants**` separate each flag with,

Flags

-   **hide_armor_trim**
-   **hide_attributes**
-   **hide_destroys**
-   **hide_dye**
-   **hide_enchants**
-   **hide_placed_on**
-   **hide_potion_effects**
-   **hide_unbreakable**

Example: `**diamondsword;hideunbreakable,hideattributes,hide_enchants**`

##### Spawner type

It is only applicable for spawners. Spawner type can be defined in a basic way as `**creeper**` or any other entity type existing in your server version.   
Side note: setting the spawner name will change the spawner’s custom name, and if you are defining items’ custom name without using **n{[name]}** format, then you should set the name before setting the spawner name; the name will be shifted into the lore section.

Example: `**spawner;&2My_Creepy_Spawner!;creeper**` or `**spawner;creeper;n{&2My_Creepy_Spawner!}**`

##### Leather armor colors

It is only applicable for leather armor item stacks. Defined like `**leatherboots;662266**` or `**leatherboots;red**` or **`leatherboots;125,23,123`** 

Example: `**leatherboots;lightpurple**` or `**leatherboots;random**`

##### Painting

It is only applicable to paintings. Defined by simply providing the paintings’ name

Types

-   Alban
-   Aztec
-   Aztec2
-   Bomb
-   Burning_skull
-   Bust
-   Courbet
-   Creebet
-   Donkey_kong
-   Earth
-   Fighters
-   Fire
-   Graham
-   Kebab
-   Match
-   Pigscene
-   Plant
-   Pointer
-   Pool
-   Sea
-   Skeleton
-   Skull_and_roses
-   Stage
-   Sunset
-   Void
-   Wanderer
-   Wasteland
-   Water
-   Wind
-   Wither

Example: **`painting;pool`**

##### Goat Horns

It is only applicable to goat horns. Defined by simply providing goat horns’ name

Types

-   Ponder
-   Sing
-   Seek
-   Feel
-   Admire
-   Call
-   Yearn
-   Dream

Example: **`goathorn;call`**

##### Armor trims

For 1.20+ servers

Armor trims are defined as `iron:dune` any combination of type and pattern can be used, but only one trim and one pattern should be used

Trim types

-   **Iron**
-   **Copper**
-   **Gold**
-   **Lapis**
-   **Emerald**
-   **Diamond**
-   **Netherite**
-   **Redstone**
-   **Amethyst**
-   **Quartz**

Trim pattern

-   **Coast** 
-   **Dune**
-   **Eye**
-   **Host**
-   **Raiser**
-   **Rib**
-   **Sentry**
-   **Shaper**
-   **Silence**
-   **Snout**
-   **Spire**
-   **Tide**
-   **Vex**
-   **Ward**
-   **Wayfinder**
-   **Wild**

Example: **`chainmailchestplate;iron:dune`**

##### Potions

When you have potion itemstack you can define its type, if it is upgraded, and if it is expanded with something like **`speed:true:true`**

Example: `**potion;speed:true:true**`

##### Specials

Special variables to define specific things about an item. Define as `**unbreakable**`

**Unbreakable** – makes item unbreakable   
Example: `**diamondsword;unbreakable**`

##### Sherds

Only for decorated pots **`decoratedpot;Skull,Archer,Angler,Explorer`** you can have less than 4 sherds. Anything above 4 will be ignored

-   **Angler**
-   **Archer**
-   **Arms_up**
-   **Blade**
-   **Brewer**
-   **Burn**
-   **Danger**
-   **Explorer**
-   **Friend**
-   **Heartbreak**
-   **Heart**
-   **Howl**
-   **Miner**
-   **Mourner**
-   **Plenty**
-   **Prize**
-   **Sheaf**
-   **Shelter**
-   **Skull**
-   **Snort**

Example: **`decoratedpot;Skull,Archer,Angler,Explorer`**
