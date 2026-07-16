---
title: CMI 定时任务
tags: CMI, schedule, 定时
source: https://www.zrips.net/schedule/
---
The `~/plugins/CMI/Settings/Schedule.yml` file is used to set up repeating commands at particular times or in particular intervals. This can be used for broadcasting messages, performing some preset tasks, like restarting the server, or simply triggering commands when particular conditions are met. 

Supports CMI and **PlaceHolderAPI** placeholders

Some possible setups:

**Enabled: (true/false)** only schedulers which are enabled will be used automatically. Keep in mind that **/cmi schedule [scheduleName]** will trigger that scheduler even if it’s disabled

There can be 2 types of schedules: 1. Performed in regular time intervals (in seconds) 2. Performed at a particular time

**Delay: (number)** defines how long to wait between each action, 600 means that actions will be performed every 10 minutes

**PerformOn:** section will define particular times when we need to perform commands. The name of that time frame should be defined and then additional time frames should be given  
It can have: **Month, Day, Hour, Minute, Second** sections. All of them are in number format and hours use the **24 hour format**  
Example

```
   PerformOn:     FirstTimeFrame:       Hour: 4     SecondTimeFrame:       Hour: 22       Minute: 30
```

This will set commands to be performed at 04:00 o’clock in the morning and at 22:30. Good way to control server backups when there are fewer players online.

**Day:** **[dayOfMonth/weekday]** you can either use the exact day of the month, like **Day: 5** or you can define the day of the week like **Day: Sunday** which will perform the scheduler every Sunday

**Repeat: (true/false)** if set to false, the action will be performed only once, otherwise it will be repeated all the time in intervals or at a particular time

**MinPlayers: (amount)** Will skip scheduler if there are not enough players online that that moment when the scheduler should start

**MaxPlayers: (amount)** Will skip scheduler if there is more than the defined amount of players

**FeedBack: (true/false)** if set to false will not show a feedback message in console in case there are not enough players to perform this schedule

**Commands:** List of commands to be performed when time is correct. This utilizes **[[CMI 专用命令|specialized commands]]**

Insert line with ‘**delay! 5**‘ to perform the rest of the commands after 5 seconds from this point in the command list. This allows to create an example counter before the server stop. Check examples.

**[randomPlayer]** placeholder can be used to get a random online player name who don’t have **cmi.scheduler.exclude** permission node. This can be used to give rewards to random players at a particular time. For example: **– cmi give [randomPlayer] diamond %rand/1-5%** will give random amount from **1** to **5** **diamonds** to random online player

**[allPlayers]** placeholder can be used to perform commands for all players. For example: **– cmi heal [allPlayers]** will heal everyone who is online.

```
# Saves map every 10 minutessaveMaps:  Enabled: false  Delay: 600  Repeat: true  Commands:  - save-all# Gives Diamonds from 1 to 5 for a random person if there are more then 2 online at 18:0 time. Repeats every dayGiveDiamonds:  Enabled: false  MinPlayers: 3  Repeat: true  PerformOn:    1:      Hour: 18    Commands:  - cmi give [randomPlayer] diamond %rand/1-5%  - msg! [randomPlayer] &eYou just got diamonds!# Stops server at 3:59:30 with 30 second warning before that and some repeating messagesStopServer:  Enabled: false  PerformOn:    1:      Hour: 3      Minute: 59      Second: 30  Commands:  - actionbar! &eServer will stop in &630 &esec!  - delay! 5  - actionbar! &eServer will stop in &625 &esec!  - delay! 5  - actionbar! &eServer will stop in &620 &esec!  - delay! 5  - actionbar! &eServer will stop in &615 &esec!  - delay! 5  - actionbar! &eServer will stop in &610 &esec!  - delay! 5  - actionbar! &eServer will stop in &65 &esec!  - delay! 1  - actionbar! &eServer will stop in &64 &esec!  - delay! 1  - actionbar! &eServer will stop in &63 &esec!  - delay! 1  - actionbar! &eServer will stop in &62 &esec!  - delay! 1  - actionbar! &eServer will stop in &61 &esec!  - delay! 1  - kickall! &eServer will be back online soon!  - delay! 1  - stop# Example scheduler with all possible optionsAllInOneJustExample:  Enabled: false  MinPlayers: 3  MaxPlayers: 10  Delay: 600  Repeat: true  PerformOn:    1:      Month: 12       Day: Monday          Hour: 18          Minute: 36      Second: 15       2:       Hour: 18  Commands:  - cmi give [randomPlayer] diamond %rand/1-5%  - msg! [randomPlayer] &eYou just got diamonds!  - broadcast! &e[randomPlayer] just got some stuff!   - delay! 1  - actionbar! &eServer will stop in &61 &esec!  - kickall! &eServer will be back online soon!// Shows random message from the list every 10 minutesAnnouncer:  Enabled: false  MinPlayers: 1  Delay: 600  Repeat: true  Randomize: true  Commands:  - broadcast! &eRules can be found at &e/rules  - broadcast! &eKits can be accesed with &e/kits  - broadcast! &eIf you need help, ask staff  - broadcast! &eAdvertisement of other servers will be punished
```
