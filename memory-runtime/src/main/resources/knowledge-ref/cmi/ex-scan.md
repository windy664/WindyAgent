---
title: CMI 扫描 Scan
tags: CMI, 扩展
source: https://www.zrips.net/cmi/extra/scan/
---
Applies for CMI from **9.7.13.0** version  
This command is used to check block or entities inventories for defined items in defined range around you or in entire world. Keep in mind that this doesn’t look into players inventories, for that we have **[[CMI 搜索 Search|/cmi search]]** command

![](https://www.zrips.net/wp-content/uploads/2025/04/scanprogress.png)

Item search results will be shown in two base groups, one for block type location and one for entity. There will be continuous output into action bar with currently found locations where item is located at, amount of chunks already scanned, total amount needed to be checked, skipped chunks and general scan speed.   
Total chunk count is based on general generated chunk count in defined area, this amount can be slightly off (due to how base quick check is done on which chunks need to be scanned), to address this potential discrepancy there is skipped chunk count. Skipped chunks are only the ones which are not actually fully generated while system still has some type of record on them, so scan was skipped on these ones as no items will be located there. Scan speed will auto adjust based on current server load, this is only general idea of how many chunks will be scanned in one tick, but doesn’t guarantee it, so actual speed can fluctuate.   
As an example of locations for searched items. 

![](https://www.zrips.net/wp-content/uploads/2025/04/scanlist.png)

This only provides general location where items are located at, to find exact item you are looking for will be up to you. Clicking on the line will teleport you to that location.

## Command usage examples

```
/cmi scan diamondsword
```

Will check all inventories for diamond sword by a material, this will include any swords, with or without custom names, lore, enchants and so on

```
/cmi scan diamondsword r:g
```

By default scan will only be done on 9 chunk area (3×3) around player. Providing **r:[range/g]** will change this range, if you want to scan entire world then use **r:g** which stands for global scan. Range is based on “**((range * 2)  – 1) ^ 2**” equation, so if range of 2 is used then 3*3=9 chunk area is checked, while providing range of 1 will only scan chunk you stand in. Keep in mind that with higher ranges you might see lower numbers of chunks being scanned, this is just because not all chunks in given are generated, so those get excluded from the start.

```
/cmi scan diamondsword c:-2;1
```

This defines center from where we will do scanning. This value is based on **Chunk** coordinates and not location. You can check your current chunk coordinates with **/cmi pos** command and hovering over output message.

```
/cmi scan diamondsword w:LT_Craft
```

Defines world where scan should be done which can be different from the one you are in currently

```
/cmi scan diamondsword;n{Some_Custom_Name}
```

Search by items name (format as defined in **[[CMI 单行造物品|ICWOL]]**). Keep in mind that this will look for items which contains provided text, so partial one can be used. Additionally this doesn’t require accurate colors to be defined, all of them will be ignored and only text will be checked

```
/cmi scan diamondsword;l{Some_Custom_Lore}
```

Search by items lore. Similar as searching by name this will look for lore which contains defined text and not exact match

```
/cmi scan diamondsword;e{sharpness:5}
```

If enchant data is provided then in addition to checking for material we will be looking for swords with sharpness 5 or higher. Multiple enchants can be used to look for more specific items, for example **diamondsword;e{sharpness:3,luck:2}**

```
/cmi scan diamondsword;cm{123456}
```

Item search by defined custom model data

```
cmi scan diamondsword:3
```

Item search by defined amount, this will include amounts from entire inventory and anything what is higher over provided number  
Keep in mind that this will provide total amount of items inside inventory which might or might not include things like shulker boxes or bundles, so total amount will be provided by combining items from entire location

```
/cmi scan hand
```

Will use item from your main hand and look for matching one  
Search will dependent on items complexity, if item has custom name then we will be looking for items with one, if it will have enchants then that one will be one of the criteria, but if item is plain then we are only looking by items material

```
/cmi scan oversize
```

Will look for item stacks which size is over their default max limit

```
/cmi scan lead;e{thorns:1} diamondsword;e{sweeping:1}
```

There is option to look up for multiple different items at same time by providing items information as separate inputs. In this example we will be looking for a lead and diamond swords with defined enchants.  
Keep in mind that result output only indicates found item amount and not what specific item was found when you are looking for multiple items at same time.  
Additionally, keep in mind that if you would look for diamond sword with specific enchant and diamond swords in general then same item might be detected more than once and total found amount in that place might be higher than actual item count

```
/cmi scan results
```

Prints out current or last scan results.

```
/cmi scan cancel
```

Cancels current active scan

```
/cmi scan pause
```

Pauses current active scan in case you want to delay scanning

```
/cmi scan resume
```

Resumes current active scan

###### Item search custom criteria

In case you want to use item from your hand but you don’t want to check by all criteria this item have, you can define custom ones, for example **/cmi scan hand material name** which would only look by material and items name while ignoring everything else. This will allow you to search for specific things only, for example to look up any item with defined enchant **/cmi scan hand enchant** keep in mind that looking by enchant will include enchanted books as a viable hit  
Valid options: **material, name, lore, enchant, modeldata**
