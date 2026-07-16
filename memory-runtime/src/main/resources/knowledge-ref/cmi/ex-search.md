---
title: CMI 搜索 Search
tags: CMI, 扩展
source: https://www.zrips.net/cmi/extra/search/
---
Applies for CMI from **9.7.12.0** version  
This command is used to check players inventories and players ender chests for defined items. Could be used to check for specific player states, like game modes or fly mode

![](https://www.zrips.net/wp-content/uploads/2025/04/searchSearching.png)

Item search results can be identified with multiple locations for better feedback on where item you are looking for is located at. This can be generic ender chest or inventory, additionally items inside shulker boxes or bundles will be checked and if found this will be indicated in results window.  
As an example of all locations for searched items for a single player. 

![](https://www.zrips.net/wp-content/uploads/2025/04/searchList.png)

In this case we have 17 items in ender chest and 7 in regular inventory while being spread out inside shulker boxes or bundles. This only provides general location where items are located at, to find exact item you are looking for will be up to you

## Command usage examples

```
/cmi search diamondsword
```

Will check all players (first online and then offline ones) inventories and ender chests for diamond sword by a material, this will include any swords, with or without custom names, lore, enchants and so on

```
/cmi search diamondsword;n{Some_Custom_Name}
```

Search by items name (format as defined in **[[CMI 单行造物品|ICWOL]]**). Keep in mind that this will look for items which contains provided text, so partial one can be used. Additionally this doesn’t require accurate colors to be defined, all of them will be ignored and only text will be checked

```
/cmi search diamondsword;l{Some_Custom_Lore}
```

Search by items lore. Similar as searching by name this will look for lore which contains defined text and not exact match

```
/cmi search diamondsword;e{sharpness:5}
```

If enchant data is provided then in addition to checking for material we will be looking for swords with sharpness 5 or higher. Multiple enchants can be used to look for more specific items, for example **diamondsword;e{sharpness:3,luck:2}**

```
/cmi search diamondsword;cm{123456}
```

Item search by defined custom model data

```
/cmi search diamondsword:3
```

Item search by defined amount, this will include amounts from entire inventory and anything what is higher over provided number  
Keep in mind that this will provide total amount of items inside ender chest or inventory which might or might not include things like shulker boxes or bundles, so total amount will be provided by combining items from entire location which is slightly different behavior when you are just looking for matching items

```
/cmi search hand
```

Will use item from your main hand and look for matching one  
Search will dependent on items complexity, if item has custom name then we will be looking for items with one, if it will have enchants then that one will be one of the criteria, but if item is plain then we are only looking by items material

```
/cmi search lead;e{thorns:1} diamondsword;e{sweeping:1}
```

There is option to look up for multiple different items at same time by providing items information as separate inputs. In this example we will be looking for a lead and diamond swords with defined enchants.  
Keep in mind that result output only indicates found item amount and not what specific item was found when you are looking for multiple items at same time.  
Additionally, keep in mind that if you would look for diamond sword with specific enchant and diamond swords in general then same item might be detected more than once and total found amount in that place might be higher than actual item countser

```
/cmi search oversize
```

Will look for item stacks which size is over their default max limit

```
/cmi search results
```

Prints out current or last search results. 

```
/cmi search cancel
```

Cancels current active search

###### Item search custom criteria

In case you want to use item from your hand but you don’t want to check by all criteria this item have, you can define custom ones, for example **/cmi search hand material name** which would only look by material and items name while ignoring everything else. This will allow you to search for specific things only, for example to look up any item with defined enchant **/cmi search hand enchant** keep in mind that looking by enchant will include enchanted books as a viable hit  
Valid options: **material, name, lore, enchant, modeldata**

## Extra search options

```
/cmi search survival/cmi search creative/cmi search spectator/cmi search adventure
```

Will look for players with defined game modes

```
/cmi search god
```

Will look for players with god mode enabled

```
/cmi search fly
```

Will look for players with fly access

```
/cmi search maxhp:21
```

Will look for players with defined max hp or higher, value can be anything you want, default players hp is 20
