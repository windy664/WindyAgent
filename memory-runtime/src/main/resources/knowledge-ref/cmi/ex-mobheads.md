---
title: CMI 自定义头颅
tags: CMI, 扩展, 头颅
source: https://www.zrips.net/cmi/extra/custom-mob-heads/
---
![](https://www.zrips.net/wp-content/uploads/2019/03/customheads.png)

CMI provides the option to drop custom monster heads when you kill an entity. To customize dropped heads check out `~/plugins/CMI/Settings/CustomHeads.yml`.

This is defined by percentage amount in general. Like for example, you can have **5%** to drop a **sheep’s head** but only **1%** to drop a **creeper’s head** when killing one. 

```
  creeper:    DropChance: 1.0  sheep:    DropChance: 5.0
```

In addition to this, you can define by how much you want to lower the chance for each player after they got the head for this type of mob. This is helpful in preventing exploitation while killing hundreds of mobs to farm heads. Keep in mind that this limitation will reset to the original state after each server restart.

In this example player has 5% to getting a sheep head and after each instance, they got one, the chance to get the next one will go down by 50% from the previous one, so if the initial chance was 5%, the second one will be 2.5%, next 1.25%, then 0.625% and so on.

```
  sheep:    DropChance: 5.0    LowerWithEachDrop: 50.0
```

You can have as many custom heads as you want. There is no hard limit on this one, though the recommendation would be to keep it in reasonable numbers. To add a new head simply add new text line which would contain a texture string.  You can find custom head texture string at any site which has database set for it. As an example of one: https://minecraft-heads.com/.

Custom head texture line should start with numbers which will define the chance of dropping that head in particular. So you can have some more rare heads than others for the same entity type. Example

```
    - 100:eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzI2NTIwODNmMjhlZDFiNjFmOWI5NjVkZjFhYmYwMTBmMjM0NjgxYzIxNDM1OTUxYzY3ZDg4MzY0NzQ5ODIyIn19fQ==
```

In this case, head has 100% chance to be picked if the random algorithm checks it before others. In case it would have less than 100%, lets say it’s only 10%. Then the algorithm will try to roll “dice” and check if we can pick this head. If not, then it will move to another random head in the list and will try to pick it. So to avoid not dropping a head at all even though the player should, keep at least one head in the list for a particular mob type with 100% chance.

Optionally you can add a custom head custom name by inserting it into texture string. Example:

```
    - 100:&2Custom Head Name:eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjZhNDExMmRmMWU0YmNlMmE1ZTI4NDE3ZjNhYWZmNzljZDY2ZTg4NWMzNzI0NTU0MTAyY2VmOGViOCJ9fX0=
```

In this case, you will have a head with **Custom Head Name** as an item name. It’s completely optional and if not defined then the default name will be used.

And last option is to define criteria for the particular head. Format should be like **c-[criteria1],[criteria2],[criteria3]** This can range from simple check if the creature was tamed or not, if the creature is in a particular color or particular villager type. Example:

```
  creeper:    DropChance: 2.0    LowerWithEachDrop: 50.0    Heads:    - 100:c-unpowered:eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQyNTQ4MzhjMzNlYTIyN2ZmY2EyMjNkZGRhYWJmZTBiMDIxNWY3MGRhNjQ5ZTk0NDQ3N2Y0NDM3MGNhNjk1MiJ9fX0=    - 100:c-powered:eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTNmMTcyZDI5Y2Y5NGJjODk1NjA4YjdhNWRjMmFmMGRlNDljNzg4ZDViZWNiMTYwNWYxZjUzNDg4YTAxNzBiOCJ9fX0=
```

## Head lore

In case we need custom lore to be added to the dropped head we can do that by adding lore:{“Some text in here”} while extra lines can be added with “,” so full basic example would

```
     - '100:{#edward>}DjCreeper{#cancan<}:lore{"{#red>}||||||||||||||||||||||||||||||||{#purple<>}||||||||||||||||||||||||||||||||{#cancan<}","{#cancan>}Got      with [chance]% chance{#edward<}","&2Lucky guy: %cmi_user_name%","{#red>}||||||||||||||||||||||||||||||||{#purple<>}||||||||||||||||||||||||||||||||{#cancan<}"}:YTNmMTcyZDI5Y2Y5NGJjODk1NjA4YjdhNWRjMmFmMGRlNDljNzg4ZDViZWNiMTYwNWYxZjUzNDg4YTAxNzBiOCJ9fX0='
```

This will result into head with lore like

![](https://www.zrips.net/wp-content/uploads/2020/08/customheadlore.jpg)

## Example

![](https://www.zrips.net/wp-content/uploads/2019/03/customheads2.png)

This will have 2% chance to drop creeper head. But depending on creeper type, if its powered or not, it will drop one or another head. 

Multiple criteria can be used. In example

```
    - 100:c-baby,tamed,angry:eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTk1Y2JiNGY3NWVhODc2MTdmMmY3MTNjNmQ0OWRhYzMyMDliYTFiZDRiOTM2OTY1NGIxNDU5ZWExNTMxNyJ9fX0=
```

Which will only drop head if entity was baby wolf which was in angry mode. !?!Who would kill a killer baby wolf!?!

All possible criteria options:

Tamed

Only drops head if killed entity was tamed.

Untamed

Will only drop head if creature can be tamed but was not at moment it died

Baby

Only drops head if creature was in baby state. This doesn’t apply for slime or magma cubes. They have different criteria.

Adult

Will only drop head if entity can have age states and is in adult one. This will not work for creatures like creepers which doesn’t have age states. 

Angry

Drops head if wolf was in angry mode when it was killed

Pasive

Drops head when wolf was in passive mode

Red_cat

Used to drop head only if entity is red cat. Use only for ocelots. 

Siamese_cat

Used to drop head only if entity is siamese cat. Use only for ocelots. 

Wild_ocelot

Used to drop head only if entity is wild cat. Use only for ocelots. 

Black_cat

Used to drop head only if entity is black cat. Use only for ocelots. 

White

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Orange

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Magenta

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Light_Blue

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Yellow

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Lime

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Pink

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Gray

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Light_Gray

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Cyan

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Cyan

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Blue

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Brown

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Green

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Red

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Black

Color indication of entities like Sheep, shulker boxes or similar entities which has this color indicator.

Rainbow

Only applies for sheep which has name as jeb_ which creates rainbow sheep

Chestnut

Horse type indicator

Creamy

Horse type indicator

Dark_brown

Horse type indicator

Size1 - Size10

Size1 Size2 Size3 Size4 …. Size10

Can be used to identify slime and magma cube sizes. 

Powered

Only drops head if creeper was powered

Unpowered

Only drops for regular creeper

Normal

Villager type

Farmer

Villager type

Librarian

Villager type

Priest

Villager type

Blacksmith

Villager type

Butcher

Villager type

Nitwit

Villager type

## Loot bonus

Optionally you can have extra chance when using weapon with looting enchantment on it. This can add extra variation and will increase worth of weapons with this enchantment.   
By default only 3 levels will be checked as vanilla enchantment can only have 3 levels. If you need more then this add extra lines in customHeads.yml file which should look something like

```
LootMobBonus:  Enabled: true  Lvl1: 5.0  Lvl2: 15.0  Lvl3: 30.0  Lvl4: 35.0  Lvl5: 40.0    Lvl6: 45.0
```

Keep in mind that this value doesn’t add raw drop chance, but increases current one by particular amount. So if you have head drop chance at 1% and you have looting 3 enchantment, then final chance will be 1.3%
