---
title: CMI 附加命令
tags: CMI, 命令
source: https://www.zrips.net/cmi/commands/attached-commands/
---
Attached commands can utilize **specialized command** mechanics, and you can read more about them **[[CMI 专用命令|here]]**

Multiple commands can be separated with **`;;`** For example, if you want to heal and feed a player you can use something like `**/cmi attachcommand cmi heal [playerName];;cmi feed [playerName]**`

Any item can contain one or multiple commands attached to it, and commands will be performed on item usage.

Option to define an item use amount, such as the number of times the item can be used before being removed. Currently, it’s only by using **`/cmi attachcommand`** command.

`**/cmi attachcommand cmi launch [interactedPlayer] a:45**` Will launch the player with which one you have interacted while holding this item.

`**[interactedBlock]**` this variable can be used to insert interacted block type

`**[interactedEntity]**` this variable can be used to insert interacted entity type

## !cc!

Performs command from console, requires `**cmi.command.attachcommand.cc**` permission, tho keep in mind that you only need permission when creating item and not when using it. 

## !right!

In case you only want to perform the command when the player right-clicks it, then add the `!right!` variable. For example, `**/cmi** **attachcommand !right!cmi heal**` it will perform the heal command when right-clicked, but nothing will happen when the player left-clicks.

## !left!

In case you only want to perform command when player left-click it, then add `**!left!**` variable. For example, `**/cmi**` **`attachcommand !left!cmi heal`** which will perform heal command when left clicked, but nothing will happen with right click

You can combine both click types into one item with something like `**/cmi attachcommand !left!cmi heal;;!right!cmi feed**` where left click will heal player and right one will feed him. Usage with `!silent!` variable: `**/cmi attachcommand !left!!silent!cmi heal;;!right!!silent!cmi feed**`

## !ignore!

By using this variable will perform item action and command

## !cooldown:[seconds]!

`/cmi attachcommand !cooldown:60!;;!cc!cmi heal [playerName]` will allow user to use this item every 60 seconds to heal himself. Keep in mind that duplicates of this item will share same cooldown, while newly created one, even if its has same exact command will have separate cooldown. 

## !limiteduse:[amount]!

In example `/cmi attachcommand !limiteduse:5!` will set item usage amount to `**5**`. Left usages will be shown in lore and in action bar when using item.  
An option to perform commands from the console, for example `/cmi attachcommand !cc!cmi fly [playerName]`. Yes, you can use variables to insert players name.  
Some examples would be:  
`/cmi attachcommand !limiteduse:3!;;!cc!cmi fly [playerName] true` what will toggle players fly mode to true.  
`/cmi attachcommand !limiteduse:3!;;!cc!cmi tfly [playerName] 60` which will enable fly mode for player for next 60sec and will have 3 uses  
`/cmi attachcommand !limiteduse:3!;;!cc!cmi heal [playerName]` will heal player for 3 times, after that item will disappear.

If an item is stacked, then each item in the stack will be consumed one by one after all uses are consumed. So 5 items with 3 uses, will have 15 uses in total.

## !safelimiteduse:[amount]!

Differently than `**!limiteduse:[amount]!**` this will **not** remove item after player used all usages. So when item reaches 0 usage count, item will be cleared from any extra data, lore will be cleared out from extra message and it will no longer perform any commands on click. In case player will have stack of items with safe limited use, then player will get clean version of that item into his inventory.

## !consume!

In case you want to perform commands only when player consumes food, then you can use **!consume!** variable at start. For example

```
/cmi attachcommand !consume!;;!cc!cmi me [playerName] says helo!;;!cc!cmi panimation circle;c:85,254,254;twist;part:5;r:0.5;pitch:90;move:0,0.33,0;offset:0,-0.2,0;target:[playerName];;cmi sound [playerName] ambient_cave
```

This will print out message into chat, play out particle effect and play out sound effect when player consumes item.

Keep in mind that **you cant have !limiteduse:[amount]!** while using this feature, as item should be consumed for commands to be performed.
