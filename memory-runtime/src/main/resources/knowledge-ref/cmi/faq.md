---
title: CMI 常见问题
tags: CMI, FAQ
source: https://www.zrips.net/cmi/faq/
---
Is there command doing X thing?

Maybe? Usually yes, there is.

We do have ~300 commands as of 9.2.x, so it is a quite big chance that it exists already.

If you want to find it without creating a ticket or waiting for a response on GitHub or Discord, simply use `**/cmi checkcommand [keyWord]**` and you will get commands related to that keyword. 

Players can use commands. Whats going on?

By default, players will not have access to any of the commands. You will have to define permission nodes for each command. And needed permission node can always be seen by hovering over error message or checking out console which prints out missing permission node. if you cant see missing permission node while hovering over error message, then ironically, you are missing **cmi.permisiononerror** permission node. Tho this one is given by default for everyone, so in this case, would mean that you specifically removed it.

Default format is **cmi.command.[commandName]** in example **cmi.command.heal**

How can i convert old player data from essentials?

If you are transferring from essentials to CMI you can use dedicated command to import players information from essentials to CMI. Essentials plugin is not needed for it to work.

```
/cmi importfrom essentials [home/warp/nick/logoutlocation/money/mail]
```

home – will import home locations  
**nick** – will import players nick name  
**logoutlocation –** will import players last logout location  
**money** – will imports player money balance  
**mail** – will import players mail  
**warp** – will import warp points

you can use more than one variable at a time, or even all of them, in example: **/cmi importfrom essentials home warp nick logoutlocation money mail**  
This process can take some time. in general its converting 200 users in one second and you will get feedback message informing about process.

If you want to use old **worth.yml** file, then simply copy/paste it into CMI folder from Essentials and you are good to go.

How can i have short commands?

By default only small amount of commands have alias enabled so you could use shortened commands. So by default majority of commands will require full command usage like **/cmi heal Zrips** To enable short command you have 2 options: to enable it in alias.yml or to create your own.

Enabling throw alias.yml: Go to alias.yml file and look up for any alias you would want as short command. In example if you want /heal command then simply go to appropriate section and set 

```
  /heal: true
```

Creating custom alias: You can create any alias you want throw ingame command **/cmi aliaseditor** which by itself has 2 options how you can create new alias:  
1. Use generic one liner: **/cmi aliaseditor new heal-cmi heal $1-** which will create command as **/heal  
**2. Click on **+** sign in chat window and enter **heal** into chat window, press enter, then again press on **+** sign, then enter **cmi heal $1-** and press enter again. You are done, now you have **/heal** enabled and working. You can add more than one command which will be performed in a row and which supports special CMI command format, we call specialized commands which wiki can be found **[HERE](https://www.spigotmc.org/wiki/cmi-specialized-commands/)** and which is extremely powerful

Extra variables in alias command like **$1** will mean that first variable will be taken and replaced in this alias. In example **/cmi aliaseditor new heal-cmi heal $1** and while performing **/heal Zrips** first variable, in this case **Zrips** will be replaced in original command and end command will look like **/cmi heal Zrips**  
This allows for variables to be replaced in ordering, in example **/cmi aliaseditor new tps-cmi tp $2 $1** and performing **/tps Zrips Zhax** will result in command like **/cmi tp Zhax Zrips**  
In case you need to add all variables after certain point, use format as **$1-** in example **/cmi aliaseditor new ms-cmi msg $1 $2-** and performing command like **/ms Zrips Hello World!** will result into **/cmi msg Zrips Hello World!**

How can i reset particular fields or entire database?

Short answer: use **/cmi resetfields [collumnName] (-w:[worldName]) (-p:[playerName])**

You can reset one particular column for every player and set it to the default value. You can reset things like homes which are located in a defined world. You can reset the field for a defined player.

Keep in mind that this only resets one column. If you are starting new server and you want to remove everything, then simply delete CMI tables if you are using MySQL or rename sqlite file. If you don’t know how to remove MySQL tables or you don’t have access, then you can simply rename table prefix in config file which will create new tables with empty data. 

Players are stuck in fly mode?

If you notice that your players change world or relog and are flying. They might be stuck in a fly mode. You can potentially resolve it like this:

eventcommands.yml  
You could add a command to the on join events as well as the teleporting events; This should then force the players in survival mode to fly off. For example,

`perm:cmi.command.fly@! check:%cmi_user_gamemode%==Survival! asConsole! cmi fly [playerName] false -s`

Another possible solution could be is to find the cause. Does this happen without any other plugins installed? If you believe you run into a bug, feel free to file a bug report. 

Also, you could review your config.yml’s WorldLimits settings are properly configured and that the player/group doesn’t have wildcards or operator, as that might grant them the .bypass perm nodes as well.

Finally, you could review [this (unofficial) article](https://faq.cmi.support/mode-stuck) to see if you really covered your bases.
