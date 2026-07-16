---
title: CMI 自定义别名
tags: CMI, 命令, alias
source: https://www.zrips.net/cmi/commands/custom-alias/
---
CMI provides method to create any command which will perform one or multiple defined commands.

[![](https://www.zrips.net/wp-content/uploads/2019/02/2018-03-22_15-24-00-1.gif)](https://www.zrips.net/wp-content/uploads/2019/02/2018-03-22_15-24-00-1.gif)

Custom alias support [[CMI 专用命令|specialised commands]]

For simple alias creation with one command use **/cmi aliaseditor new [alias]-[original command]** in example to create **/h** as alias for **/cmi heal** perform **/cmi aliaseditor new h-cmi heal [playerName] $1-** where **[playerName]** will get replaced with players name who performed alias command. **$1-** will mean that any extra variables added into alias will be placed at this point. In example **/h zrips 10** will actually perform **/cmi heal zrips 10** command.

You can use build in editor to add more than one command. Simply perform **/cmi aliaseditor** and scroll down to your desired alias, click on it and you will get command list. You can edit them, remove, change order, add new ones.

Alias with 2 commands like:  
**– asConsole! moneycost:20#?! cmi heal [playerName] -s  
–** asConsole**! msg [playerName] !&2You just been healed by God’s of minecraft!**  
will result in charging player with money by taking out 20 bucks of hies balance. In case he doesn’t have money, then he will get message informing about this. This is made possible by using **?** sign. Check specialised commands for this. And if he doesn’t have enough money, then second command will not be performed, this is because **#** is used in condition variable. Condition variable always ends with **!** and **? #** is optional variables. In case player had money, then **/cmi heal Zrips -s** command will get performed from console, which will result in quite command performance and then second command will get performed and message will be sent to target player.

Alias with delay in commands:  
**–** asConsole**! cmi launch [playerName]  
– delay! 3  
–** asConsole**! cmi launch [playerName]**  
will result in player being launched into air where he is looking at, and after 3 seconds he will be launched again.

Posible **?** variable. This should be used in alias itself and mainly used to print out help page if player enters incorrect sub command. In example **/cmi aliaseditot new tipi sub-asConsole! cmi msg [playerName] apple** and **/cmi aliaseditot new tipi ?-asConsole! cmi msg [playerName] carrot** and when player performs **/tipi** he gets response of **carrot**, **/tipi bla** results in **carrot**, but **/tipi sub** results in **apple**

## Custom Tab Completes

There is option to either disable tab completes entirely or to use custom ones. By default plugin will try to suggest tab completes by using first command in your custom alias. Which obviously could be something what is not what you want to have. For this you can define your own tab completes by either providing specific words or using some of the dynamic variables provided in the list below.  

Custom tab completes can be modified by clicking the grey **CustomTab** text in customAlias editor.

Tab completes can branch out, which means that picking one variable you will get completely different ones if you would have picked different one. Lets use basic example:

```
- first subFirst- second subSecond
```

In this case you will have “first second” as initial suggestion, while picking “second” will suggest you a “subSecond” only  
In case you want to provide multiple choices then separate them with a comma “,” so basic example

```
- first subFirst,subSecond,[playername]
```

This will not only allow to pick from “subFirst” and “sunSecond” but will add online player names into suggestions

By default tab completes will override any which could already exist for this command, if you want to keep original tab completes and add your own, you will need to enable **AddTab** button in alias editor. This will combine tab completes

## Dynamic tab complete variables

-   **[allPlayername]** – All players including vanished ones
-   **[playername]** – All players excluding vanished ones
-   **[allIGNPlayername]** – All players excluding vanished ones but only their real player names
-   **[mutedplayername]** – All online muted players
-   **[damageCause]** – Damage cause variations
-   **[bannedplayername]** – Banned player names
-   **[gamemode]** – Game modes
-   **[worlds]** – Worlds
-   **[itemname]** – Materials
-   **[EntityType]** – Entity Types
-   **[cleanEntityType]** – Entity Types without _
-   **[kit]** – Kit names by access
-   **[kitnames]** – Kit config names by access
-   **[kitp]** – Kits by preview access
-   **[chatroom]** – Chat rooms
-   **[biome]** – Biomes
-   **[treeType]** – Tree types
-   **[maxplayers]** – Server max player limit
-   **[potioneffect]** – Potion effects
-   **[effect]** – Particle effects
-   **[merchants]** – Villager professions
-   **[enchant]** – Enchant names
-   **[halfViewRange]** – Half of the max server view range
-   **[doubleViewRange]** – Double of max server view range
-   **[ViewRange]** – Server view range
-   **[maxenchantlevel]** – Max enchant level. Uses the previous variable to determine enchantment
-   **[currentItemName]** – Item name in the main hand
-   **[loreLine]** – Lists numbers of lore lines of the item in the main hand
-   **[currentItemLore]** – Lists lore of item in the main hand
-   **[currentX]** – Current player X position
-   **[currentY]** – Current player Y position
-   **[currentZ]** – Current player Z position
-   **[currentWorld]** – World name player is in
-   **[currentPitch]** – Players pitch
-   **[currentYaw]** – Players yaw
-   **[itemFlag]** – Item flag values
-   **[nickName]** – Users display name
-   **[nickNames]** – All online users nicknames
-   **[homes]** – Users’ home list
-   **[warps]** – Warps by access to them
-   **[allwarps]** – All warps
-   **[playerwarps]** – Warps by access to them
-   **[rankname]** – Rank names
-   **[statstype]** – Statistics names
-   **[statssubtype]** – Sub-statistics names. Uses the previous variable to determine the main statistic
-   **[motd]** – Servers motd
-   **[bungeeserver]** – Bungee servers
-   **[scheduleName]** – Schedule names
-   **[ctext]** – Custom text names
-   **[jail]** – Jail names
-   **[cellId]** – Cell ids. Uses the previous variable to determine jail
-   **[sound]** – Sound names
-   **[placeholders]** – Placeholders
-   **[warncategory]** – Warn categories
-   **[projectiletype]** – Projectile types
-   **[holograms]** – Hologram names
-   **[mobtype]** – Mob types
-   **[signLine]** – Sign line text. Uses the previous variable to determine the line number
-   **[allportals]** – All portals, including disabled ones.
-   **[portals]** – All enabled portals
-   **[playeritems]** – List of all items in the player’s inventory.
-   **[gamerule]** – List of world gamerules.
-   **[attachedCommand]** – List of commands attached to the item the player is holding in their main hand.
-   **[customalias]** – List of custom aliases.
