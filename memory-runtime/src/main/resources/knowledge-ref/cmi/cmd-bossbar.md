---
title: CMI Boss栏消息
tags: CMI, 命令, bossbar
source: https://www.zrips.net/cmi/commands/bossbarmsg-handling/
---
![](https://www.zrips.net/wp-content/uploads/2019/02/2018-03-22_13-20-48-1.gif)

###### /cmi bossbarmsg all Server shutting down in [autoTimeLeft]! -cmd:"stop" -sec:-60 -c:red

![](https://www.zrips.net/wp-content/uploads/2024/01/2024-01-24_17-52-02.gif)

###### /cmi bossbarmsg all -cw:0.05,10,yellow,red !!!Warning!!!

![](https://www.zrips.net/wp-content/uploads/2024/01/2024-01-24_17-58-50.gif)

###### /cmi bossbarmsg all -p:100 -a:1 -s:1 -t:30 -cw:0.05,10,green,pink,662266 Event starts soon!

Which will show bossbar message to **Zrips** with message “**Hey you**” and will disappear after 3 seconds

`cmi bossbarmsg Zrips &2Hey you! -t:5`  
Shows bossbar message to Zrips with message “Hey you” and will disappear after 5 seconds

`cmi bossbarmsg Zrips &2Hey you! -c:red`  
Shows red (default green) bossbar message to Zrips with message “Hey you”

`cmi bossbarmsg Zrips &2Hey you! -s:1`  
Set bossbar style. Possible ones: **1, 6, 10, 12, 20**. Which will split bar into particular amount of sections. So by using 1 you will get solid bar and by using 20 you will get bar splited into 20 parts.

`cmi bossbarmsg Zrips &2Hey you! -n:bossBarName`  
Defines bossbar name, it can be anything you want and will be used to identify same boss bar and update with new information if needed. So by running `cmi bossbarmsg Zrips &2Hey you! -n:myBar` and then rerunning `cmi bossbarmsg Zrips &2Whats up? -n:myBar` same boss bar will get updated with new text without creating completely new one. If name is not provided then each time command is performed, new bossbar will get created.

`cmi bossbarmsg Zrips &2Hey you! -p:100/23`  
Will define bossbar progression and by this example boss bar will be filled up to 23%. Some **PlaceHolderAPI** variable can be used, like **-p:%server_max_players%/%server_online%**  
Automatic progression change can be set by using something like  
`cmi bossbarmsg Zrips &2Hey you! -p:+1`  
which will mean that progression will be advanced by additional percentage point. If initial value is not set, then it will start from 0. In case  
`cmi bossbarmsg Zrips &2Hey you! -p:-1`  
is used, then bossbar value will start from 100 and will be decreased.  
In case of automatic progression results in 0 or 100 percents (depends from progression type) then boosbar will disappear after preset timer with –**t**. This can be utilized for any type of counter.

`cmi bossbarmsg Zrips &2Hey you! -cmd:"cmi broadcast !{#red}Timer run out;;cmi broadcast !{#cancan}Better luck next time"`

Defines commands we should perform after timer runs out. You can utilize global variables like [playerName] **BUT** this only works when you use this command on specific player. If you use **all** as target player which will show bossbar to every player on server then commands defined with **-cmd:** starts behaving slightly differently. In case you use **all** command gets triggered only once even if you have 50 players online and global variables like [playerName] or anything relating to players will **NOT** be translated. 

`cmi bossbarmsg all &2Hey you! -pcmd:"msg [playerName] hey;;cmi heal [playerName]"`  
Defines commands which will be performed when automatic progression reaches 0 or 100. Couple commands can be set by separating with ;; and its accepting specialized command formats. More info about them [[CMI 专用命令|**HERE**]] Player will require to have **cmi.command.bossbarmsg.admin** permission node to be able to include commands.  
Differently than **-cmd:** this will perform commands on every player who received bossbar message independent if you set command as single target or as **all** we will repeat same commands one every player while translating appropriate global variables or placeholders.   
Both -cmd: and -pcmd: can be used for single target (commands gets merged) or while using **all** variable. 

`cmi bossbarmsg Zrips &2Hey you! -a:20`  
Sets automatic bossbar update interval. Can be used to automatically update bossbar with new information every x ticks. Keep in mind that 20 ticks is 1 second.

`cmi bossbarmsg Zrips -cancel:testbar`

All variables are option except text it self. Any variable combinations can be used to achieve best desired results.  
`cmi bossbarmsg all -a:1 Healing incomming in [autoTimeLeft]! -t:2 -p:1 -pcmd:"msg [playerName] &2Some dude healed you!;;cmi heal [playerName] -s" -n:test -c:red` which will result in automatic bossbar message counting up every tick (20 times per second) from 0 to 100 by adding 1 with title **Healing incoming in 5sec!** (time counter will self update) and after its reaches 100 all online players will get message **Some dude healed you!** and will be healed. Boss bar will disappear after 2 seconds

If you want accurate time, utilise **-sec:[amount]** variable. While positive number will mean that counter will go up from 0 to target value, negative will mean that counter will go from provided number to 0. So in first case you will get filling up bar with increasing timer and in second case you will get decreasing bar going to 0. Basic example of server shut down is shown on top of page.

Setting timer to **-1** will result into infinite bossbar or until player relogs
