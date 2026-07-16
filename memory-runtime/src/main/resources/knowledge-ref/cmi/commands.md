---
title: CMI 命令列表
tags: CMI, 命令
source: https://www.zrips.net/cmi/commands/
---
**You have `/cmi checkcommand [keyword]` command to find commands related to the keyword**

For most of the commands, there is no difference if a player is online or not.  
**[someVariable] – required   
(someVariable) – optional**  
Some commands will have additional information about them, including extra permission nodes when using `**?**` sign, in example **/cmi alert ?** will show some explanation on needed permission nodes not only usage.  
Some commands will have special variable **-s** which will prevent any message output after performing, allowing for a silent command performance in case you need this. Players need to have **cmi.command.silent** permission node.  
The base permission node to use any command is **cmi.command** which is given by default for everyone. 

Every command follows basic permission requirements: **cmi.command.[commandName]** in example heal command will require **cmi.command.heal** keep in mind that this will not allow you to heal others, only yourself. To perform commands on others, you will need extra permission **cmi.command.heal.others**

Required permission nodes can be seen while hovering over the error message in-game

**(time)** the variable can be defined like: **1s** (1 second) **5m** (5 minutes) **2h** (2 hours) **3d** (3 days) **7w** (7 weeks) **1M** (1 month) **4y** (4 years) multiple variables can be combined to get more accurate time, like **5h32m52s**

Commands update to match **9.2.4.5** version, some small differences in comparison with ingame output can be seen in time.

actionbarmsg [playerName/all] (-s:[seconds]) [message]

Sends action bar message to player

-s:[seconds] defines time we should keep this message visible. Due to how it works it might override some other action bar messages from another plugin if this message was set to specific time and another plugin tries to show new message

afk (-p:playerName) (reason) (-s)

Toggle afk mode. Reason could be provided

afkcheck [playerName/all]

Check players afk status

air [playerName] [amount] (-s)

Set players air

alert [add/list/remove] [playerName] (reason) (-s)

Alerts administration on players login

Permissions: c**ommand.alert.info.inform** – to see alerts on player login

aliaseditor (new) (alias-cmd)

Create custom alias for one or multiple commands

More info **[HERE](/custom-alias/)**

anvil (playerName) (-s)

Open anvil

anvilrepaircost (playerName) [amount]

Set items repair cost

armoreffect [potioneffect]

Applies potion effect to an armor. Multiple effects can be applied at same time. Removing effect can be done by using **/cmi armoreffect** while holding armor piece which will show you ingame editor, this will allow you to add more than one effect and manage existing ones. Example **speed:3** will add speed level 3 effect, this will by default use 15 second timer which gets updated every 15 seconds

armorstand (last/near)

Opens armorstand editor

More information **[[CMI 盔甲架编辑器|HERE]]**

attachcommand [command/-clear]

Attaches command(s) to item

More information **[HERE](/attached-commands/)**

autorecharge (playerName) [exp/money/off] (-s)

Toggle auto flight recharge

back (playerName) (-s)

Teleports back to last saved location

balance (playerName)

Check money balance

baltop (playerName)

Check top money list

ban [playerName] (reason) (-s)

Ban player

banlist (-s)

Ban list

bbroadcast (!) [message] (-s:[serverName,serverName])

Sends special message to all players on all servers

blockcycling

Cycle block states

blockinfo

Check block information

blocknbt

Show block NBT information

book [Author/Title/Unlock] [value]

Book editing

bossbarmsg [playerName/all] (-sec:[seconds])(-t:[timeToKeepFor]) (-n:nameOfBar) (-p:[maxValue/current]) (-c:[color]) (-s:[1,6,10,12,20]) (-cmd:"command;;command2") (-pcmd:"command;;command2") (-a:[ticks]) [message] (-cancel:nameOfBar)

Sends boss bar message to player

More information [**HERE**](/bossbarmsg-handling/)

broadcast (!) [message] (-w:[worldName,worldName]) (-r:[range]) (-c:[world;x;y;z])

Sends special message to all players

If message starts with **!** then clean message will be shown without broadcast prefix

Worlds can be defined to broadcast messages only in those worlds

burn (playerName) (time) (-s)

Burn a player

cartographytable (playerName) (-s)

Open cartography table

charges [playerName] [add/set/take/clear/reset] (-f)

Shows left spawner charges

chat [create/join/leave/list/invite/kick/listrooms] (chatName/playerName) (-private) (-locked) (-persistent)

Create and join chat rooms

-locked will prevent players from leaving chat room

-private will make chat room accessible only with invites from room creator

-persistent creates room which remains after server restart

chatcolor (playerName)

Pick chat color

checkaccount (playerName/ip)

Search for a players other accounts by player name or ip address

checkban (playerName)

Check players ban status

checkcommand (keyWord)

Search for possible commands by keyword

checkexp (playerName)

Check players exp status

checkperm (keyWord)

Search for possible permissions by keyword

cheque (playerName) [amount]

Convert money into check. Real physical item. By default paper is needed to create one. Right clicking item will deduct money to players accounts

clear (playerName) (item(:amount)(;data)) (-s) (+clearType)

Clear players inventory

**Clear Types:**

+quickbar

+inventory

+partinventory

+weapons

+armorslots

+tools

+armors

+mainhand

+offhand

clearchat (self) (-s)

Clears chat

Permission: **cmi.command.clearchat.bypass** – to ignore chat cleaning

When using **self** variable, only your chat will be cleared

clearender [playerName] (-s)

Clear players ender chest

colorlimits (playerName)

Shows all posible colors

colorpicker (hex/colorname)

Pick hex color

colors (playerName)

Shows all posible colors

compass (targetName) (sourceName) (x) (z) (worldname) (reset) (-s)

Set players compass point to your location

Example:

-   /cmi compass Zhax
-   /cmi compass Zrips Zhax
-   /cmi compass LT_Craft 0 0 Zrips -s
-   /cmi compass reset Zrips

condense (itemName) (playerName) (-s)

Condense items into blocks

counter [join/leave/start] (t:time) (r:[range/-1]) (c:[world:x:y:z]) (msg:custom_message) (-f)

Starts counter for surrounding players

Permissions:  
**cmi.command.counter.force** – to force counter for everyone in range  
**cmi.command.counter.time** – define custom time range  
**cmi.command.counter.range** – to define custom range  
**cmi.command.counter.msg** – to define custom message  
**cmi.command.counter.autojoin** – joins counter automatically  
-f will force counter to all players in range  
Example: **/cmi counter start r:30 t:7 msg:&eCustom_message -f**

cplaytime (playerName)

Detailed playtime in GUI format

Keep in mind that this is separate from playtime command and can show slightly different playtimes as it uses different engine to tract it.

ctellraw [playerName/all] [formattedMessage]

Send tellraw type message

ctext [cText] (playerName/all) (sourcePlayer)

Shows custom text

More information [**HERE**](/custom-text/)

cuff [playername] (true/false) (-s)

Suspends players actions

customrecipe

Manages item custom recipes

This only works by ingredients material type. It will ignore any extra data like item name, lore, enchants and so on. This is vanilla behavior.

database [action] (playerName) (dataType) (fileName)

Manage backup data

dback (playerName) (-s)

Returns to death location

dialogs [dialogName] (playerName) ([variableName]:value) (-s)

Shows specified dialog

More information on dialog usage can be found [[CMI 对话框|**HERE**]]

disableenchant [enchant/id] (disable/enable)

Disable enchantment

dispose (playerName)

Dispose of unneeded items throw GUI

distance (playerName) (playerName)

Check distance between 2 points

donate [playerName] (amount)

Donate item you are holding

down [playerName] (-s) (max)

Teleport one floor down

if **max** variable used then we will try to find lowest point and not just go one level down

dsign (new [name])

Manage dynamic signs

More information **[HERE](/dynamic-signs/)**

dye (playerName) (red,green,blue/hexCode/colorName/random/clear/rainbow/day/biome/health) (-s)

Dye leather armor

Some colors are dynamic and will update by specific conditions

editctext

Custom text editor

More information **[HERE](/custom-text/)**

editlocale (keyword(-s))

Edit your locale file

Keep in mind that some lines can be too long to be shown in chat text editor and can be not editable throw this command.

editplaytime (playerName) [add/take/set] [amount] (-s)

Edit players playtime

editwarnings (playerName/clearall) (clear)

Edit players warnings

editwarp (warpName) (newName)

Edit warps

effect [playername/all] [effect/clear] (duration) (multiplier) (-s) (-visual)

Adds potion effect to player. use clear to remove all effects

**-visual** will add visible bubbles and icon on top right corner  
Examples:  
**/cmi effect zrips nightvision 60 1** – will give 60 sec night vision for Zrips  
**/cmi effect zrips nightvision +10** – will add 10 sec to current night vision time  
**/cmi effect zrips nightvision -10** – will take out 10 sec from current night vision time  
**/cmi effect all health_boost 60 1** – will boost hp by 1 for everyone online

enchant (playerName) [enchant] [level] (-o) (-onlyvalid) (-keeponlyvalid) (-inform) (-s) (-i:[itemName(:data)]) (clear)

Enchant items

**-o** will take item from offhand

**-onlyvalid** will check if item can be enchanted with that particular enchant. Useful when performing from console.

**-inform** will inform player whose item being enchanted. Useful when performing from console.

**-s** will perform command silently for command sender

**-i:[itemName:data]** will limit enchant to particular item

**-keeponlyvalid** will remove all enchants from item which are not valid for that particular item

ender (playerName) (playerName)

Opens players ender chest

endgateway

Toggles endgateway block beam on and off

entityinfo

Check entity information

entitynbt (-console)

Check entity nbt information

exp [playerName] [add/set/take/clear] [amount][%rand/10-20%][1%[min-max][[playerName]] (-s)

Set players exp. Use L to set levels

Example:  
/exp 10  
/exp add 10  
/exp set 10L  
/exp take 10  
/exp Zrips clear  
/exp Zrips add 10

Additional dynamic variable examples

**/exp add %rand/10-20%** will give random amount between 10 and 20  
**/exp take 5%[15-100][Zrips]** will take **5%** from players (Zrips, as reference player and not the one from whom exp will be taken away) current exp while limiting amount between **15** and **100**. Keep in mind that player name needs to be in brackets. If source player name is not provided then target player will be used as reference.

ext (playerName) (-s)

Extinguish a player

falldistance (playerName) (number) (-s)

Set players falldistance

feed (playerName/all) (-s)

Feed player

findbiome (biomeName/stop/stopall)

Finds nearest biome by name

fixchunk w [worldName] r [range in chunks] c [x:z]

Only works for 1.15 and older servers

Scans for damaged chunks

SubCommands:  
    **stats** – show current scanning stats  
    **pause** – pause scaning  
    **continue** – continue scanning  
    **stop** – stop scaning  
    **stopall** – stops all chunk fixes which are running currently  
    **speed [amount]** – set current scan speed  
    **autospeed [true/false]** – set autospeed turned off or on  
    **messages [true/false]** – set message output to off or on  
Example:  
/fixchunk w LT_Craft  
/fixchunk w LT_Craft r 50 c 1024:-2048  
/fixchunk w LT_Craft r g  
/fixchunk fix

flightcharge (add/take/set/show/expcharge/moneycharge/recharge) (playerName) (amount) (-s))

Manage and check flight charges

More information **[HERE](/flight-charge/)**

fly [playerName] (true/false) (-s)

Set players fly true or false

flyc (playerName) (true/false) (-s)

Toggle flight charge mode

flyspeed (playerName) [amount] (-s)

Set players fly speed from 0 to 10

gamerule (world) (gamerule) (value)

Manage gamerules in GUI or directly modify it

generateworth

Auto generate possible item worth values

getbook [cTextName] (playerName)

Get book of a ctext feature

give (playerName) [itemdata/hand] (playerName) (-slot:[number]) (unstack) (-s)

Give item to player

-slot:[number] will try to place item in defined slot if possible, if not then to any empty available slot in players inventory

More information [[CMI 单行造物品|HERE]]

giveall [itemname] (amount) (e|l|n|offline)

Give item for all players

– give item name or its id with data value  
– optionaly provide amount you want to give  
– n – to define itemname  
– l – to define item lore  
– e – to define item enchants  
– -s – wont show feedback message  
– h – followed with player name will give item from its hand  
– inv – followed by player name will give entire inventory for others   
– offline – to include offline players  
cmi giveall stone 1 n &2Uber_stone l &3Stone_lore offline  
cmi giveall h Zrips  
cmi giveall inv Zrips

glow (playerName) [true/false/color/gui]

Set players glow mode

Example: /glow Zrips red

Permissions: **cmi.command.glow.[color]** – allows to set particular glow color

gm (playerName) [gamemode]

Set players game mode

god (playerName) (true/false) (-s)

Set players god mode to true or false

grindstone (playerName) (-s)

Open grindstone

groundclean (+cb) (+cm) (+ci) (+b) (+sh) (+tnt) (+all) (+fl) (+named) (-w:[worldName]) (-s)

Clears server from unnecessary items

**+cm** will include minecarts into cleaning  
**+cb** will include boats into cleaning  
**+ci** defines if you want to include weapons and armors  
**+b** broadcasts clear message to everyone  
**+tnt** removes ignited tnt  
**+sh** includes shulkerbox itemstacks, keep in mind that dropped shulkerbox item can contain items in them which you might not want to remove

haspermission (playerName) [permissionNode]

Check if player has particular permission

hat (playerName)

Place item like hat

head [sourceName] (targetName) (-s) (amount)

Get players head

heal [playerName/all] (healamount/healpercent) (-nofeed) (-ignoreffects) (-dontextinguish)

Heal player

Example: 

-   **/cmi heal zrips**
-   **/cmi heal zrips 10**
-   **/cmi heal zrips 10%** 
-   **/cmi heal all**

helpop [message]

Sends message for help to staff members

Requires **cmi.command.helpop.inform** too see messages

hideflags (playerName) [flagName/clear] (flagName)

Hides item flags

hologram (new [name])/addline/deleteline/editline/info/update

Create or manage holograms

More information [**HERE**](/holograms/)

**addline [hologramName] [text]** – will add defined new line to hologram directly, this will place new line at the end  
**deleteline [hologramName] [lineNumber]** – deletes defined line  
**editline [hologramName] [lineNumber] [text]** – modifies defined line with new text  
**update [hologramName]** – will force update hologram in case you made some changes to it which dint got properly updated or you simply want to force update hologram without automatic update timer enabled  
**info [hologramName]** – shows information of defined hologram

hologrampages [holoName] (playerName) (next/prev/[pageNumber])

Change hologram page

Related to hologram feature and more information how to use this can be found [**HERE**](/holograms/)

home (homeName) (playerName) (whoTeleport)

Teleport to home location

homes (playerName/near:[range])

A list of homes that you can click to teleport to

hunger [playerName] [amount] (-s)

Set players hunger

ic (new [name])

Create interactive command

More information [**HERE**](/interactive-commands/)

ifoffline [playerName] (command)

Perform command only when player is offline

ifonline [playerName] (command)

Perform command only when player is online

ignore (playerName/uuid/all) (-p:[playerName])

Ignores player

**cmi.command.ignore.bypass** – to bypass ignore list

importfrom [essentials/hd] [home/warp/nick/logoutlocation/money/mail]

Import data from other plugins like essentials or holographicdisplays

importoldusers

Imports users from playerdata folder in main world folder. Server can suffer lag spike during import

info (playerName/uuid)

Show players information

**cmi.command.info.ip** is required to see players ip address. To see players country you will need [**THIS**](https://www.zrips.net/wp-content/uploads/2019/02/GeoIP.dat) file to be present in CMI folder

inv [playerName]

Opens players inventory

Permissions:

**cmi.command.inv.preventmodify** – prevents inventory editing

**cmi.invedit** – allows to edit inventory

**cmi.command.inv.location** – shows players location

invcheck (playerName) [id] (-e) (last)

Open saved inventory in preview mode

**-e** will open inventory in edit mode. **cmi.command.invcheck.edit** permission is required for this to have effect

**last** will open last saved inventory

invlist (playerName)

Show saved inventories list

invload (sourceName) (targetName) [id/last]

Load saved inventory

**last** will load last saved inventory

invremove (playerName) [id/all/last]

Remove saved inventories for player

**all** will remove all saved inventories for this player

**last** will remove last saved inventory

invremoveall [confirmed]

Removes all saved inventories

**confirmed** variable is required to avoid unintentional removal of saved inventories

invsave (playerName) (id) (-s)

Save inventory

ipban [ip/playerName] (reason) (-s)

Ban players ip

**cmi.command.banip.bypass** – to bypass ban

ipbanlist

Shows full list of banned ip’s and possible owner names of those ip’s

item [itemname] (amount)

Give item to yourself

itemcmdata [set/delete] (playerName) (id) (-s)

Check or modify items custom model data

itemframe (invisible/fixed/invulnerable/all)

Manage item frames

iteminfo (playerName)

Show item information

itemlore (-p:[playerName]) [linenumber/*] [remove/insert/ new lore line]

Change items lore

itemname (-p:[playerName]) [remove/your new item name]

Rename items

itemnbt (playerName)

Show item NBT information

jail [playerName] (time) (jailName) (cellId) (-s) (r:jail_reasson)

Jail player for time period

More information [**HERE**](/jail/)

jailedit

Edit jails

More information [**HERE**](/jail/)

jaillist (jailName) (cellId)

List out all jails with jailed players in them

jump

Jump to target block

**cmi.command.jump.[amount]** is required for max distance you can jump with this command

kick [playerName/all] (message) (-s)

Kick player with custom message

**cmi.command.kick.bypass** – prevents from being kicked out

kill [playerName] (-force) (damageCause) (-s) (-lightning)

Kill player

killall (-monsters/-pets/-npc/-animals/-ambient/-named/-f/-lightning/-list/-m:[mobType]) (-r:range) (-s) (-w:[worldName])

Kill mobs around you

More information **[[CMI 清理实体 KillAll|HERE]]**

kit [kitName] (playerName) (-s) (-open) (-preview) (-c)

Gives predefined kit

Permissions:”,  
**cmi.kit.[kitName]** – allows to use particular kit  
**cmi.kit.bypass.money** – bypass money requirement  
**cmi.kit.bypass.exp** – bypass exp requirement  
**cmi.kit.bypass.onetimeuse** – bypass one time use

**/cmi kit [kitName] [playerName]** – will give kit to another player

**-preview** will open UI where player can check what kit gives but cant actually pick anything from it. This can be achieved by using **/kitpreview** command

**-open** will open UI with items included in kit where player can choose what he wants to pick from it. Keep in mind that closing UI will delete items in top inventory permanently. This can be achieved by using **/kitopen** command

More information [**HERE**](/kits/)

kitcdreset (kitName) (playerName/all)

Reset kit timer

kiteditor

Starts kit editing

More information [**HERE**](/kits/)

kitusagereset (kitName) (playerName)

Reset kit usage counter

lastonline (-p:[page])

Show played players from last x minutes

launch (playerName) (p:[power]) (a:[angle]) (d:[direction]) (loc:[x]:[y]:[z]) (-nodamage)

Launch at direction you are looking or at angle

**-nodamage** will indicate that player who lands after being launched should not be effected by fall damage

**cmi launch** – will launch at direction you are looking  
**cmi launch p:3.2** – will launch with power of 3.2  
**cmi launch p:2.5 a:25** – will launch at direction you are looking with angle of 25 degrees and with power of 2.5  
**cmi launch Zrips d:east** – will launch to east direction same angle you are looking  
**cmi launch Zrips d:0** – will launch at 0 degrees direction (south) same angle you are looking  
**cmi launch d:45 a:30 p:2** – will launch south-west at 30 degree angle with power of 2  
**cmi launch loc:150:120:123** – will launch player to target location, keep in mind that this will only try to launch to that location, but some limitations aplies

lfix (range) (playerName) (stop/stopall)

only for 1.13 and older servers

Fix light in chunks around you

list

Shows online player list

Permissions:   
**cmi.command.list.admin** – places player into admin groupd  
**cmi.command.list.staff** – places player into staff group  
**cmi.command.list.hidden** – shows players who are hidden  
**cmi.command.list.group[number]** – places player into predefined group by its number

lockip (playername) [add/remove/list/clear] [ip]

Prevents logging into account from different ip

loom (playerName)

Open loom

mail [send/clear/read/sendtemp] [playerName] (time) (message)

Send and receive mail

**/cmi mail sendtemp Zrips 24h Quick reminder to vote!** this will send email which will expire in 24 hours

mailall [send/clear/remove] [message]

Check or send mail to all players

maintenance (true/false) (message)

Set server into maintenance mode

**cmi.command.maintenance.bypass** – to bypass maintenance mode

maxhp [set/add/take/clear] [playerName] [amount] (-s)

Set a player’s max hp

maxplayers [amount]

Changes maximum amount of players who can connect to server

Permissions:

**cmi.fullserver.bypass** – join full server

me [message]

Sends special message to all players

merchant [type] (playerName) (level)

Open merchant trade window

migratedatabase

Changes database system and migrates all data

mirror (start/stop)

Starts block place/break mirroring

mobhead [mobType] (entryNumber) (playerName) (-s)

Get mob head

money [pay/give/take/set] [playerName/all/alloffline/allonline] [amount][%rand/1-1000%][1%[min-max]][playerName]] (-s)

Manage money balance

Examples:

**/cmi money give Zrips 100** gives Zrips 100

**/cmi money give Zrips %rand/1-1000%** gives Zrips random amount between 1 and 1000

**/cmi money take Zrips 1%** takes from Zrips balance 1% of what he has

**/cmi money take Zrips 1%[100-500]** takes from Zrips balance 1% , but no less then 100 and no more then 500 of what he has

**/cmi money take Zrips 1%[100-500][Zhax]** takes from Zrips balance 1%, but no less then 100 and no more then 500 of what Zhax has

**/cmi money give all 1000** gives all online players 1000

**/cmi money give alloffline 1000** gives to everyone, including offline players 1000

more (playerName) (-clone/[amount])

Fills item stack to maximum amount

Permissions:  
**cmi.command.more.oversize**  to get oversized stacks

msg [playerName] [message]

Sends message to player

If message starts with **!** then clean message without sender name will be shown. Requires **cmi.command.msg.clean** permission

If message starts with **!-** then clean message without sender name will be shown and without option to reply. Requires **cmi.command.msg.noreply** permission

mute [playerName] (time) (-s) (reason)

Mute player

Examples:

-   /cmi mute zrips 1m
-   /cmi mute zrips 1h
-   /cmi mute zrips 1h For swearing

time values can defined with **+** or **–** in front to define if time should be taken away or added to existing mute time. By default defined time will be the time players is put into mute mode, with provided variables you can take away or add on top of existing remaining time.

-   /cmi mute zrips +30s
-   /cmi mute zrips -15m

mutechat (time) (-s) (reason)

Prevent public messages

Examples:

-   /cmi mutechat 1m
-   /cmi mutechat 1h

nameplate (playerName) (-pref:[some_prefix]) (-suf:[some_suffix]) (-c:[colorCode]) (reset) (-s)

Set players name plate prefix, suffix or its color. Glow effect will temporary override players name color.

To use a **space**, use an underscore **_** To parse a placeholder, use two underscores **__** Examples: To get it to say “**hi bob**” you type **/cmi nameplate -pref:hi_bob** To get it to say some placeholder, type: **/cmi nameplate -pref:%some__placeholder%**

near (distance)

Check who is near you

Default max distance 200. Can be increased with **cmi.command.near.max.[amount]** permission node

nick [newNickName/off] (playerName) (-s)

Changes player name

To change into different nick name: **cmi.command.nick.different** This allows to change name by only changing its colors but not actual name itself.

To bypass length protection use **cmi.command.nick.bypass.length**

notarget (playerName) (true/false) (-s)

Toggle no-mob target mode

note (playerName) [add/remove/clear/list] (id/note)

Manage players notes

openbook (cText) (playerName) (fileName.txt)

Open book gui

Optionally you can provide exact file which needs to be loaded and shown. This approach is less efficient then having already parsed ctext files, but it does allow you to show ctext which are not directly loading into memory. 

oplist

Check operator player list

options (playerName) (option) (enable/disable/toggle/status) (-s)

Modify personal options

Commands work individually. Without parameters will open a GUI where players can toggle to enable/disable certain features.  
As of CMI version 9.1.4.x these legacy commands have been moved into a newer more convenient single /options command: **togglecompass, toggleshiftedit, toggletotem, tagtoggle, socialspy, commandspy, signspy, msgtoggle, tptoggle, paytoggle**.

In the config.yml file you can further configure the /options’ GUI panel.

Permissions involved to allow modifying specific options: cmi.command.options.[**visibleholograms/shiftsignedit/totembossbar/bassbarcompass/tagsound/chatspy/cmdspy/signspy/acceptingpm/acceptingtpa/acceptingmoney/chatbubble**]. Players will also need the base command: **cmi.command.options**

Placeholders are grouped as well:

**%cmi_user_toggle_[msg|pay|tp|compass|sospy|sispy|cospy|schest|autoflightrecharge|totem|shiftedit|tagsound]%**

**%cmi_user_togglename_[msg|pay|tp|compass|sospy|sispy|cospy|schest|autoflightrecharge|totem|shiftedit|tagsound]%**

panimation (variable/playerName/stopAll) (stop)

Shows animated particles. More information at **[[CMI 粒子效果|WIKI]]** page

particlepicker (particleName)

Open particle picker UI

patrol

Teleports to random players without repeating same player until all players have been visited. 

pay [playerName] [amount] (-s)

Perform money transaction

ping (playerName/message)

Shows players ping

placeholders (parse) (placeholder) (playerName)

List out all placeholders

Option to parse placeholder to check out what it would return and which plugin will translate it

playercollision (playerName) [true/false] (-s)

Set players collision mode

Only for 1.9+ servers

playtime (playerName)

Shows player total play time

playtimetop (page)

Shows top list of player total play time

point (particleName) (playerName) (-self) (time) (-s:[speed])

Point to block. Particle line will be drawn towards target block

portals (new/nearest/forceupdate/setlocation/enabled) (portalName) (world:x:y:z:yaw:pitch)

Manage portals

More information [**HERE**](/portals/)

pos (playerName)

Show current position of a player

preview [range] (innerrange)

For 1.17 and older servers

Load chunks for given range

prewards (playerName)

Check playtime rewards

ptime (freeze/unfreeze/day/night/dusk/morning/realtime/reset) (playerName) (-s)

Controls player personal time

Example:

-   /ptime 13:00:00
-   /ptime 1pm
-   /ptime 13
-   /ptime 7000ticks
-   /ptime Zrips 1pm
-   /ptime freeze
-   /ptime unfreeze
-   /ptime realtime
-   /ptime reset

purge (stop)

Cleans player data from world folder by inactive days. This can only be done from console.

pweather (playerName) [sun/rain/reset] (-s)

Controls player weather

rankdown (playerName) (rankName) (confirm) (-cmd) (-cost)

Decrease your rank

rankinfo (playerName) (rankName)

Your rank information

ranklist

List of possible ranks

rankset (playerName) [rankName] (-cmd) (-cost)

Set a players rank

**-cmd** will perform command defined for that rank rankup

**-cost** will charge player with money, exp or items if defined for that rank rankup

player will get rank even if he doesn’t have enough money, exp or required items

rankup (playerName) (rankName) (confirm)

Increase your rank

realname (playerName/nickName)

Check players real name

recipe (itemName) (-c)

Check item recipe

By using **-c** variable we will only show custom recipes created by CMI

reload

Reloads plugins config and locale files

removehome (homeName) (playerName)

Removes home

removeuser [uuid/duplicates]

Removes user and its data

removewarp (warpName)

Remove warp point

repair [hand/offhand/armor/all] (playerName)

Repair items

**cmi.command.repair.hand** – allows a user to repair items in their hand  
**cmi.command.repair.offhand** – allows a user to repair items in their off hand  
**cmi.command.repair.armor** – allows a user to repair items in armor slots  
**cmi.command.repair.all** – allows a user to repair their whole inventory  
**cmi.command.repair.repairshare.bypass** – allows a user to repair items without adding repair share protection

repaircost (hand/armor/all) (playerName)

Set items repair cost

Example:  
/repaircost 10  
/repaircost set 10  
/repaircost add 10  
/repaircost take 10  
/repaircost Zrips clear  
/repaircost Zrips add 10

replaceblock id [blockName:data/id:data] w [blockName:data/id:data] r [range in chunks/g] y [max height]

Replaces blocks in current world around you

SubCommands:  
– pause – pause replacing  
– continue – continue replacing  
– stop – stop replacing  
– speed [amount] – set current replace speed  
– autospeed [true/false] – set autospeed turned off or on  
– messages [true/false] – set message output to off or on  
Example:  
/cmi replaceblock id 52 w stone r 10  
/cmi replaceblock id 52,gold_block w stone r 15 y 100  
/cmi replaceblock id 52 w air r g y 100  
/cmi replaceblock id iron_ore%75 w stone%90,dirt%5 r g

reply [message]

Replay to last message sender

resetback (playerName) (reason) (-death) (-s)

Resets players back location

resetdbfields [collumnName] (-w:[worldName]) (-p:[playerName])

Resets particular database columns to default value

ride

Ride target entity

**cmi.command.ride.[entityType]** – to have access in riding entity

rt (playerName) (worldName) (-s)

Teleports to random location

sameip

List players logged in from same ip

saturation (playerName) [amount]

Set players saturation

saveall (daysRange/-online)

Saves every player inventory

**-online** variable can be used to save only online player inventories

saveditems [save/get/remove/list] (savedItemName) (-t:playerName) (-a:amount) (-c:category/all) (-s)

Save or manage saved items

-t:[playerName] target player who gets this item

-c:category defines item category, can be used when creating item for better item management

sc (playerName)

Starts sign copy process  
If player name is provided, then target player can click sign and will copy over sign text instead of player who initialized this command

scale [set/add/take/clear] (playerName) [amount] (-s)

Set entities scale

scan

Scans defined range or entire map for particular items in all possible containers.

SubCommands:  
    – **stats** – show current scanning stats  
    – **pause** – pause scanning  
    – **continue** – continue scanning  
    – **stop** – stop scanning  
    – **stopall** – stops all active scannings  
    – **speed [amount]** – set current scan speed  
    – **autospeed [true/false]** – set autospeed turned off or on  
    – **messages [true/false]** – set message output to off or on  
    Variables:  
    **id [id:data]**  
    **q [minimum quantity]**  
    **r [range in chunks]** – option to use g instead of number to scan entire map  
    **n [item name]**  
    **l [item lore]**  
    **h** uses info from item in hand  
    **e [enchantname]**  
    **elvl [enchantminlevel]**  
    **oversize**  
    **purge** – removed found items, this feature should be enabled in config file  
   

Example:  
    /scan id 52 r 30  
    /scan id diamond_block r g q 32

scavenge (playerName)

Allows to break items into raw ingredients and extract enchantments with particular chance

schedule [scheduleName] (-updatetimer)

Trigger schedule

When using **-updatetimer** we will update existing scheduler timer to trigger after set period of time instead of keeping old one, this allows to trigger specific scheduler sooner and still keep correct interval in between it

se [SignLine] [Text]

Changes sign text line

Use /n for additional line

search

Search items/enchants/fly/maxhp/gm/oversize modes and other stuff from all users

    id [id:data]  
    name [some_custom_itemname]  
    lore [some_custom_lore]  
    enchant [lowest enchant level]  
    potion [lowest custom potion effect level]  
    fly [true or false]  
    gm [0/1/2/3 or survival/creative/adventure/spectator]  
    maxhp [lowest hp player have]  
    god [true/false]  
    oversize  
    Example:  
    /search gm 1  
    /search id 52  
    /search lore Uber_lore

seen [playerName/uuid]

Check when player was last seen

select (pos1/pos2/shift/expand/contract/clear) (amount)

Manage selection area

sell (all/blocks/hand/same/gui/material) (playerName) (-s)

Sell items from inventory

sendall [serverName]

Send all online players to target server

server [serverName] (playerName) (-f)

Connect to bungeecord server

serverlinks

Set server links

serverlist

Show server list

servertime

Show server time

setenchantworth

Allows to set enchantment worth which is used in sell hand and scavenger commands

setfirstspawn (playerName)

Sets first spawn point

sethome (homeName) (playerName) (-p) (-l:worldName;x;y;z) (block/Material) (slotNumber) (-overwrite)

Sets home location

**cmi.command.sethome.unlimited** – to have unlimited anount of homes

**-l:worldName;x;y;z** will define custom location for new home location, this requires **cmi.command.sethome.customloc** permission node

**-p** will define home as private and other players will not have option to teleport to this home location

**(block/Material)** allows to define custom icon in you home gui. In example **/sethome home block** will pick block under your feet and use as icon. While **/sethome home woodenshovel** will use actual wooden showel as icon. 

If slot number is provided then we will try to place that particular home in that slot in GUI. Keep in mind that this is from 0 to 900 which is equal to 20 pages. So having slot number as 52 will place it in first page if there are no ore icons after it. But if you will have one more which is above 55 then all homes above 45 will get moved to next page as wee need to free up space for buttons. Example **/sethome home 50** will set home at slot 50 in GUI. If you have more then one home with same slot, then they will get placed one after another.

You can override existing home location if you have **cmi.command.sethome.overwrite** permission node

setmotd [newMotd] (-s)

Set server motd

setrt (worldName) (center:[x]:[z]) (min:[range]) (max:[range]) (square/circle) (enabled/disabled)

Set random teleport bounds

setspawn (playerName) (true/false) (-g:[groupName]) (-rng:[range]) (-w:world,world_nether) (loc:[world;x;y;z;yaw;pitch])

Sets spawn command teleport point

More information [**HERE**](/spawn/)

setwarp [warpName] (reqPermission) (hand/head/head:[playerName]) (slot) (autoLore) (-g:[groupName]) (world;x;y;z;yaw;pitch) (-confirm)

Sets warp location

Examples:  
**/cmi setwarp spawn** – simple warp to spawn  
**/cmi setwarp spawn true** – creates warp and will require **cmi.command.warp.[warpname]** permission node to use it  
**/cmi setwarp spawn hand** – creates warp will take item from hand to display in gui for this warp  
**/cmi setwarp spawn 13** – creates warp and sets gui slot to be used in gui (1-54)  
**/cmi setwarp spawn true hand 13** – creates warp with icon from hand, slot 13 and requires permission  
**/cmi setwarp spawn true hand 13 false** – same as previous, but doesn’t generate lore

More information [**HERE**](/warps/)

setworth (itemname) (-s:[sellPrice])

Change item worth with GUI

shadowmute [playerName] (time) (-s) (reason)

Mute player without telling him that he is muted

shakeitoff (playerName) (-s)

Dismount any entity riding you

shoot (playerName) (-t:targetPlayer) (type) (speed)

Shoot projectile

Types:

-   smallfireball
-   largefirreball
-   thrownexpbottle
-   snowball
-   egg
-   witherskull
-   arrow
-   bullet
-   shulker
-   shulkerbullet
-   trident
-   spit
-   llama
-   llamaspit

silence

Blocks public messages

cmi.command.silence.bypass – to bypass silence

silentchest

Toggles silent chest

sit (playerName) (-persistent) (-s) (location) (on/off)

Sit at your position

**-persistent** variable prevents player from being kicked out of sit mode when he gets moved with a piston

skin [skinName/off/update] (playerName) (-s)

Changes players skin

smite (world;x;y;z/playerName) (-safe) (-s)

Strike ground or player with lightning

Player name or location can be used for where lighting should strike. For example **/cmi smite Zrips** or **/cmi smite LtCraft;15;168;458  
-safe** will define if lightning should not cause damage. With this we are only playing effects

smithingtable (playerName)

Open smithing table

solve [equation]

Solves complex equations ranging from basic **2+2** to **cos(1)*pi/0.4+tan(5)**

sound [sound] (-p:[pitch]) (-v:[volume]) (playerName/-all/-l:playerName) (world;x;y;z) (-r:[radius]) (-s)

Play sound at target location

spawn (playerName) (-s)

Teleports back to spawn location

spawner [EntityType]

Sets spawner

spawnereditor

Edit spawner

spawnmob [EntityType]

Spawns entity at your location

More information on usage **[[CMI 生成生物|HERE]]**

speed (playerName) [amount] (-s)

Set players walk or fly speed

staffmsg [message/toggle/on/off]

Sends message to staff channel

stats (playerName)

Check players stats

statsedit (playerName) [add/take/set] [statistic] (subType) [amount] (-s)

Edit players statistics

status

Show server status

stonecutter (playerName) (-s)

Open stonecutter

sudo [playerName] (command/c:[text])

Force another player to perform command

cmi.command.sudo.bypass – protects player from being ‘trolled’

suicide [playerName] (-s)

Kill your self

switchplayerdata [sourcePlayerName/uuid] [targetPlayerName/uuid]

Switch all data from one player to another

Use UUID for more accurate transfers, especially when usernames matching each other

tablistupdate (playerName) (-s)

Force tablist update for all or specific player

tempban [playerName] [timeValue] (reason) (-s)

TempBan player

Time value can be defined with + or – which will indicate change on current ban state. + will add extra time while – will take away, if resulting time is under 1 tick then we will simply unban player.   
Example: **/tempban Zhax +5m -s** will add 5 minutes to players ban, repeating this command will increase players ban time

tempipban [ip/playerName] [time] (reason) (-s)

Temporary ban players ip address

**cmi.command.banip.bypass** – to bypass ban

tfly [playerName] (timeInSec) (-s)

Set temporary players fly mode until relog or until time ends

Examples:  
tfly Zrips 30 – fly mode for next 30 sec  
tfly Zrips +30 – adds fly mode for an additional 30 sec  
tfly Zrips 0 – fly mode until relog  
tfly Zrips – check if player have tfly mode enabled and until when

tgod [playerName] (timeInSec) (-s)

Set players temporarily god mode until relog or time end

Examples:  
tgod Zrips 30 – god mode for next 30 sec  
tgod Zrips 0 – god mode until relog  
tgod Zrips – check how long a player has tgod for, if at all.

time (time) (world) (alter [value]) (-smooth)

Controls server time

Example:  
/day  
/night  
/time 13:00:00  
/time 1pm  
/time 13  
/time 7000ticks  
/time 1pm Lt_Craft  
/time 1pm all  
/time add 0:30  
/time take 0:30  
/time freeze  
/time unfreeze  
/time realtime  
/time autorealtime start/stop

titlemsg [playerName/all] [title \\n subtitle] (-in:[ticks]) (-out:[ticks]) (-keep:[ticks])

Sends title message to player

toast [playerName/all] (-t:[advType]) (-icon:[material]) [message]

Sends toast message to player

Only for 1.12+ servers

top [playerName] (-s)

Teleport to highest point at your location

tp [playerName] (playerName)

Teleports to player’s location

tpa [playerName] (playerName) (-c)

Ask the player if you can teleport to them

tpaall

Ask all online players to teleport to your location

tpaccept (playerName)

Accept teleport request

tpahere [playerName] (playerName) (-c)

Asks player to accept teleportation to your location

**-c** will teleport to location when offer was made and not where player is currently. This requires **cmi.teleport.currentlocation** permission node from command sender

tpall (playerName)

Teleports all online players to location

tpallworld [worldName] (worldName;x;y;z(;yaw;pitch)) (-a)

Teleports ALL players from specific world. Target location can be defined in case you want to perform this command from console.

Example: **/cmi tpallworld LT_Craft_nether LT_Craft;0;150;0**

**-a** will teleport everyone who is located in no longer existing world. This can happen if you have removed custom world with plugin like multiverse core and offline player is still located in that world

tpbypass (playername)

Bypass teleportation to unsafe location

tpdeny (playerName)

Deny teleport request

tphere [playerName] (playerName)

Teleports player to your location

tpo [playerName] (playerName)

Teleports to player’s location by force

tpohere [playerName] (playerName)

Teleports player to your location by force

tpopos (-p:playerName) [x] [y] [z] (world) (pitch) (yaw) (-rng:[range])

Teleports to location by force and will override any area protections player might have

-p: will define player you want to teleport. This will require you to have **cmi.command.tpopos.others** permission node

-rng: will teleport at random location from center point by given radius

x, y and z can be in relative coordinates. In example **/tpopos ~0 ~1 ~0** will teleport you 1 block up

tppos (-p:playerName) [x] [y] [z] (world) (pitch) (yaw) (-rng:[range])

Teleports to location

-p: will define player you want to teleport. This will require you to have **cmi.command.tppos.others** permission node

-rng: will teleport at random location from center point by given radius

x, y and z can be in relative coordinates. In example **/tppos ~0 ~1 ~0** will teleport you 1 block up

tps (-spikes)

Check servers tps status

tree (TreeType) (-p:[playerName])

Spawn tree where you are looking

Keep in mind that this command will ignore area protection plugins due to how minecraft handles this feature

trim (playerName) (-s) (clear)

Modify trim on armor

unban [playerName/ip] (-s)

Unban player or ip

unbreakable (playerName) (true/false)

Makes item unbreakable

uncondense (itemName) (playerName) (-s

Uncondense items into smaller parts

unjail [playerName] (-s)

Release player from jail

unloadchunks (-f)

Unloads chunks from server memory

**-f** variable will force for all chunks to be unloaded in one go

unmute [playerName] (-s)

Unmute player

unmutechat (-s)

Unmute public chat

usermeta [playerName] [add/remove/clear/list/increment] (key) (value) (-s)

Manage players meta data

Any set meta can be displayed with **%cmi_user_meta_[key]%** placeholder around plugin

More information [**HERE**](/user-meta/)

util (removeseats/testtarget)

Administration tools

vanish (playerName/list) (on/off) (-s)

Vanish player

vanishedit (playerName) (action) (true/false)

Edit vanish mode for player

version

Show plugin version

viewdistance [range] (playerName/worldName/reset) (view/simulation)

Only for paper 1.20.5+ servers

Manages per player or per world or global view distances

voteedit (playerName) [add/set/take/clear] [amount] (-s)

Manage players votes

Example:  
/voteedit Zrips  
/voteedit add 10  
/voteedit take 10  
/voteedit Zrips clear  
/voteedit Zrips add 10

votes (playerName)

Check players vote count

votetop (playerName)

Check top vote list

walkspeed (playerName) [amount] (-s)

Set players walk speed from 0 to 10

warn [playerName] (category) (reason) (-s)

Warn player

warnings (playerName)

Check player warnings

warp (warpName) (playerName) (-s) (-g:[groupName]) (-p:[pageNumber])

Teleports to warp location

warpgroups

Lists warp groups

weather (sun/rain/storm) (lock/duration) (worldName/all)

Controls server weather

Example:  
/sun  
/rain  
/storm  
/sun lock  
/sun 120  
/sun Lt_Craft

workbench (playerName) (-s)

Open workbench

world (normal/nether/end/1/2/3...) (playerName) (-s)

Teleports to different world

Requires **cmi.command.world.[worldName]** in addition to base permission node

worth (all/blocks/hand/materialName)

Check item worth

worthlist (playerName) (-missing)

Check list of items with set sell prices
