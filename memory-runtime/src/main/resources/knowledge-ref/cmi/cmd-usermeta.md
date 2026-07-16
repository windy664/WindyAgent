---
title: CMI 用户元数据
tags: CMI, 命令, meta
source: https://www.zrips.net/cmi/commands/user-meta/
---
The main usage of the command is:  
`**/cmi usermeta [playerName] [add/remove/clear/list/increment] (key) (value) (-s)**`

**Add** – Will add a defined key with a defined value. In case there is already a key with the same name, then it will be overridden with a new value

**Remove** – Will remove the defined key and its value

**Clear** – removes all recorded keys and values for the defined player

**List** – List all recorded keys with their values

**Increment** – Changes current value if one exists or creates a new one. This will attempt to add a defined value to the existing one. Both values need to be in a number format

This command provides the option to add unique values to the player and reuse them when needed with **%cmi_user_meta_[key]%** placeholder.

This can range from some general information you want to add to the player to information like the death counter.

So let us make a death counter for the player. The initial command will be **/cmi usermeta Zrips add deaths 0 -s** where **Zrips** is the player’s name who should get this, **add** will indicate that we want to add a unique date to the player, **deaths** are some custom name for this and it can be whatever you want, **0** is the initial value, and **-s** will simply perform this command without informing players.

Now we can retrieve this data with **%cmi_user_meta_[key]%** placeholder which will return 0.0 yes, it will return with fraction. So in this case lets use **%cmi_user_metaint_[key]%** and our exact placeholder should look like **%cmi_user_metaint_deaths%** which will return 0.

Now let’s manipulate this value. After all, we want to increase this value when the player dies.

So let’s add a command to eventCommands.yml file under playerDeath category.

Command itself will look like **/cmi usermeta [playerName] increment deaths +1 -s** this will increase current value by 1 without showing feedback message.

The same thing can be done with **-1** or any value you want which will (if possible) change the current value by the appropriate value.

And after performing this command **%cmi_user_metaint_deaths%** placeholder will return 1 instead of **0**, after the second time you will get **2,** and so on.
