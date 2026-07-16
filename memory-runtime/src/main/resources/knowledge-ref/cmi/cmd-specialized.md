---
title: CMI 专用命令
tags: CMI, 命令
source: https://www.zrips.net/cmi/commands/specialized/
---
## Specialized Commands Usage

**Works with: Ranks, Scheduler, Portals, EventCommands, Interactive Commands, Kits and custom alias.**

###### Actions

Use only **one** action variable, otherwise, last one will be used.

Supports PlaceHolderAPI variables

-   **[playerName]** then target player name will be used if possible. This will not work with scheduler as we have no clue which player should be used in that case.
-   **msg!** and then player name is given, a simple message will be sent to that player if he is online. Example: **msg! Zrips Hello!**
-   **broadcast!** message will be sent to everyone on server in simple manner without any additional prefixes.
-   **actionbar!** all players will get action bar message defined after this variable
-   **title!** all players will get title message defined after this variable
-   **subtitle!** all players will get subtitle message defined after this variable
-   **kickall!** all players will be kicked from server with defined message. Useful before server stop.
-   **asPlayer!** command will be performed as player who sent original initialising command.
-   **asConsole!** command will be performed from console. This can be used in ares like customAlias which by default performs commands like player if not defined specifically to be performed as console.
-   **asFakeOp!** will get performed as a player who has OP. This is slightly different then asConsole! as you will not receive any feedback message and server will process command like it was done by real player. Keep in mind that this will not perform as player who initialized command, but as completely different fake OP player.
-   **allPlayers!** then command will be performed on all online players. [allPlayers] should be used to insert players name where needed. In example: **allPlayers! cmi heal [allPlayers]** will heal everyone who is online.

###### Conditions

-   **perm:[permissionNode]!** command will get performed if player has permission node. This will not work when we don’t know who the target player is. Example: **perm:cmi.announce.vip! broadcast! Hello vip guys**
-   **moneycost:[amount]!** command will get performed if player has enough money. This will remove defined amount of money from player balance before proceeding further. 
-   **hasmoney:[amount]!** command will check if player has enough money and will continue with commands or stop if player doesn’t have enough money. This will not remove money from the player, it only checks if player has enough money to proceed. 
-   **expcost:[amount]!** command will get performed if player has enough exp.
-   **hasexp:[amount]!** command will check if player has enough exp and will continue with commands or stop if player doesn’t have enough exp.
-   **item:[itemData]!** command will get performed if player has enough defined items. **Keep in mind** that this in addition to checking if player has enough items will actually remove them on successful command. Example: **item:coal;12!** will require 12 coal items for command to be performed. Item definition is based on one liner format about which you can read more [[CMI 单行造物品|**HERE**]]
-   **exactitem:[itemData]!** command will get performed if player has enough defined exact items. Example: **exact****item:coal;12!** will require 12 coal items for command to be performed. This is different from previous item definition in a sense that it will look for exact match, in this case only clean coal items will be picked and any with custom names or lore will get ignored
-   **hasitem:[itemData]!** command will get performed if player has enough defined items. This will not take them on command execution. For example **hasitem:coal;5** which will check for 5 coal items in players inventory. Item definition is based on one liner format about which you can read more [[CMI 单行造物品|**HERE**]] Proving more detailed item will produce more accurate results, if only item type is defined then we will pick any item which type matches, if items name is provided in addition then we are only pick items with matching type and name
-   **hasexactitem:[itemData]!** command will get performed if player has enough exact items. This is different from **hasitem** as it will only look for the one which match criteria, so for example **hasitem:coal;5** which will check for 5 coal items in players inventory, while excluding any coal items with custom names or custom lore in them, only clean coal item stacks will be recognized.
-   **ifonline:[playerName]!** command will get performed if player is online. Static name or [playerName] can be depending what result you want to get.
-   **ifoffline:[playerName]!** command will get performed if player is offline. Static name or [playerName] can be depending what result you want to get.
-   **ifempty:[hand/offhand/quickbar/armor/inv/subinv/ender]!** command will get performed if players inventory for defined area will be empty. **Subinv** are 27 slots, aka 3 lines of slots when opening inventory. When using **inv** as type, then every item in players inventory will be checked. Extra value can be defined for **quickbar/maininv/subinv** types, in example **maininv-5** will require you to have 5 free slots in your main inventory, while **subinv-10** will require to have 10 free slots in sub inventory and **quickbar-3** will require you to have 3 free slots in quick bar. Example of usage: **ifempty:maininv-3?!** 
-   **check:[value1][==|>|>=|<|<=|!=][value2][?][#]!** we will only proceed if condition is correct. So for example **check:%cmi_user_balance%>1000!** where it only performs command if players balance is above 1000. **check:%cmi_user_name%==Zrips!** will only perform command if players name is Zrips.   
    In case you want to compare value against empty field, use “null” or “[empty]” for example **check:$1==null!**  
    Optionally multiple variables can be used for **\==** and **!=** checks. For example **check:$1==join|leave!** will check if first entered variable is equal to **join** or **leave**, same thing applies with **!=** which will only perform following commands if variable isn’t one of them. 
-   **contains:[value1][=>][value2][@][#]!** we will only proceed if value1 contains value2. For example **contains:$1=>stone!** will check if first entered variable contains text **stone** which could be part of longer word like stonebricks
-   **votes:[amount]!** command will get performed if player has enough votes (**Votifier**)

###### Specials

-   **cooldown:[timeInSec]!** that line or any following ones (if defined) will have cooldown before reusing it. Example: **cooldown:5! cmi heal [playerName]** will heal player, but not more frequently than every 5sec.
-   **ucooldown:[timeInSec]!** differently than previous cooldown this will apply depending on source. So you can have different cooldowns if it was triggered from different InteractiveCommands or different custom alias.
-   **gcooldown:[timeInSec]!** cooldown will be applied server wide and if it was triggered no one else can trigger it again until cooldown ends
-   **delay! 5** to perform rest of commands after 5 seconds from trigger. This allows to create in example counter before server stop. Fractional   values can be used like
    
    ```
    delay! 0.5
    ```
    
    example of launching player 2 times with 2 second delay it between
    
    ```
    - cmi launch [playerName]
    ```
    
    Keep in mind that player will not be able to run command.  
    As of 9.1.3.2 version you can define delay identification name, for example
    
    ```
    - cmi launch [playerName]
    ```
    
    which can be used in **stopdelay:[name]!** variable
    
-   **stopdelay:[name]!** will cancel delay by provided identification name as explained above. for example **stopdelay:playerlaunch!**
-   **[randomPlayer]** placeholder can be used to get random online player name who doesn’t have **cmi.scheduler.exclude** permission node. This can be used to give rewards for random players on particular time. In example: **cmi give [randomPlayer] diamond %rand/1-5%**will give random amount from 1 to 5 diamonds to random online player

## Statements

**statement:[value]!**

**if:[value][@][#]!**

2 special variables which goes together to achieve special results.

Where can you utilize this? Its relatively simple.  
Lets take as an example setup like

```
    - statement:check1! hasexp:50! hasmoney:100! perm:cmi.command.fly!    - if:check1! msg! [playerName] PASS    - if:check1@! msg! [playerName] FAIL
```

We have 3 checks in first line which result gets attached to custom name, in this case its “check1”, you can name it anyway you want. Second 2 lines utilizes result of that check, so “**if:check1!**” line gets performed when first line returns true, while “**if:check1@!**” only gets performed when its false. This allows you to perform checks once and then utilize result for cleaner and faster command processing as we no longer repeating same checks, we will be utilizing results from previous one. So expanding on this you can create cleaner and more efficient setup like

```
    - statement:check1! hasexp:50! hasmoney:100! perm:cmi.command.fly!    - if:check1! command 1      - if:check1! command 2    - if:check1! command 3    - if:check1! command 4    - if:check1@! command 5    - if:check1@! command 6    - if:check1@! command 7
```

## Extra

-   perm:[value][@][?][#]!
-   bperm:[value][@][?][#]!
-   moneycost:[value][?][#]!
-   expcost:[value][?][#]!
-   hasmoney:[value][@][?][#]!
-   hasitem:[value][@][?][#]!
-   item:[value][?][#]!
-   hasexp:[value][@][?][#]!
-   votes:[value][@][?][#]!
-   cooldown:[value][?][#]!
-   ucooldown:[value][?][#]!
-   gcooldown:[value][?][#]!
-   ifonline:[value][?][#]!
-   ifoffline:[value][?][#]!
-   ifempty:[value][?][#]!
-   click:[value][#]!
-   ifinworld:[value][@][?][#]!
-   ifingamemode:[value][@][#]!
-   ifhashealth:[value][@][#]!
-   ifhashunger:[value][@][#]!
-   ifhasair:[value][@][#]!

All those are conditional checks. What this mean is that if players don’t have permission node or enough money/exp, then followed command will not be performed. Example **perm:cmi.testperm! cmi heal [playerName]**

-   In case you want to inform player that he doesn’t have the permission node required for that command or money/exp, then use **?** in check variable. Example **perm:cmi.testperm?! cmi heal [playerName]** so if player doesn’t have permission node **cmi.testperm** then they will get notification message about this and command will not be performed.
-   In case you would want to cancel all commands if players don’t meet requirements, then use **#** in check variable. Example 
    
    `- moneycost:150#! cmi heal [playerName]`
    
    `- cmi feed [playerName]`
    
    in this case player will not be healed or fed if they don’t have 150 money in their account to pay for it.
    
-   In case you want to perform following command and cancel rest of them which goes after this line, you can use **~** variable. For example
    
    ```
    - check:$1==null~! asConsole! cmi feed [playerName]- check:$1==join~! asConsole! cmi heal [playerName]- asConsole! cmi msg [playerName] !&4Wrong command!
    ```
    
    Will perform feed command if no variables got provided. If first variable is equal to “join” then player gets healed and none of the fallowing commands will be checked. While providing variable which isint empty and it’s not “join” then last line will be performed which prints out error message
    
-   If you want to check opposite condition, then use **@** For example **perm:cmi.testperm@! cmi heal [playerName]** this will only perform command if player **doesn’t** have **cmi.testperm** permission node
-   If you want to perform command only when player is in specific world, then utilize **ifinworld:[value][@][?][#]!** condition. This will perform commands only when provided worldname matches current player world. Multiple world names can be provided, for example **ifinworld:LT_Craft|LT_Craft_nether!** . And in case **@** is used, commands will get performed only when provided world name is not that one in which player currently is located. 
-   Extra conditions can be used to check opposite condition, inform player, cancel any further command performing actions if needed. Full variable can look something like **perm:cmi.testperm@?#! cmi heal [playerName]**

**bperm:[value][@][?][#]!** will try to bypass base permission check for cmi commands. This **only** works with CMI commands.  In example 

```
bperm:cmi.someCustom! cmi heal
```

Will allow for player to perform **/cmi heal** command without actually having **cmi.command.heal** permission node which is required. This can be utilized for things like limited item usage, where player could perform some of the commands he has no access to in regular situations, but he could perform them while using those items. 

**ptarget:[name]!** is special variable only usable from console. This will use target player when replacing placeholders or checking conditions. In example, having custom alias like **/givehomes** with command as 

```
asConsole! ptarget:$1! lp user $1 permission set cmi.command.sethome.%cmi_equationint_{cmi_user_maxperm_cmi.command.sethome_1}+1%
```

then you can use **/givehomes Zrips** from console to perform command which will add new permission node for a **Zrips** by those particular placeholders. In this case it will increase max home limit by one.

**click:[value]!** This is only for interactive commands feature while clicking on blocks. Where **value** can be on of **four** options: **left, right, leftshift, rightshift**. This will define for commands to be performed only when player clicks block in particular way. So in example **click:leftshift!** will only perform command if player uses left mouse button while sneaking. 

## Sub Actions

-   closeinv!

This can be used to close player’s inventory if this action is needed

-   ph!

When using variable existing placeholders will not be translated and will be passed over as they are to the command. This can be used to perform destination commands with placeholders in them, for example: **ph! cmi bossbarmsg all -sec:3 %cmi_server_time_mm:ss%** which would create dynamic bossbar message based on this placeholder, without provided **ph!** variable bossbar command would receive translated placeholder and would only show static information

-   ch!

When using variable existing color codes will not be translated and will be passed over as they are to the command. This can be used to perform destination commands with raw hex color codes in them, for example: **ch! somePlugin coolCommand #000001** **Welcome back!** which would trigger command in its raw format without modifying hex color, without provided **ch!** variable command would receive translated command which might not be valid format for that target plugin

## Examples

/cmi attachcommand !limiteduse:1!;;asConsole! check:%cmi_random_1_100%<=20! cmi money give [playerName] 100

Limited use item which has only one usage and will have 20% chance to give 100 bucks on usage

/cmi attachcommand !limiteduse:1!;;asConsole! cmi money give [playerName] %cmi_random_10_50%

Single use item which will give random amount from 10 to 50 bucks on usage

exactitem:coal;160?! asConsole! give [playerName] coal;n{&2Enchanted_Coal} -s

Will look for clean coal item of 160 amount and in case player has it then new coal with custom name will be given and 160 clean ones gets removed. In case player doesn’t have enough items then feedback message will be shown
