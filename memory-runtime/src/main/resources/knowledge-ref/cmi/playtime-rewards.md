---
title: CMI 在线奖励
tags: CMI, 在线时长, 奖励
source: https://www.zrips.net/cmi/playtime-rewards/
---
**Playtime rewards** is a system where you can perform commands when a player accumulates a particular amount of playtime on the server.

You can perform the command every X seconds or for X seconds in total. So you can make regular payments for each 1-hour playtime and give a bigger reward for 6 hours in total or even for 24 hours.

The default command `**/cmi prewards**` alias is enabled by default and its **/prewards** will show a playtime reward list where you can check upcoming rewards and claim some.

![](https://www.zrips.net/wp-content/uploads/2019/02/playtimerewards-1.jpg)

Default example:

```
hourly1:  DisplayName: "&7Hourly reward"  AutoClaim: true  Description:  - "&2Get reward for every hour you are online"  - "&2Free heal and 20 bucks into your pocket"  PayEvery: 3600  Commands:  - asConsole! cmi heal [playerName] -s  - asConsole! cmi money give [playerName] 20daily1:  DisplayName: "&7Daily reward"  AutoClaim: true  Description:  - "&2Get reward for every 24 hours of playtime"  - "&22000 bucks into your pocket!"  PayEvery: 86400  Commands:  - asConsole! cmi money give [playerName] 20001hour:  DisplayName: "&fOne hour reward"  AutoClaim: false  Description:  - "&2Get reward for 1 hour you have been online"  - "&2500 bucks into your pocket!"  PayFor: 3600  Commands:  - asConsole! cmi money give [playerName] 5006hour:  DisplayName: "&f6 hour reward"  AutoClaim: false  Description:  - "&2Get reward for 6 hour's you have been online"  - "&22 000 bucks into your pocket!"  PayFor: 21600  Commands:  - asConsole! cmi money give [playerName] 200012hour:  DisplayName: "&f12 hour reward"  AutoClaim: false  Description:  - "&2Get reward for 12 hour's you have been online"  - "&25 000 bucks into your pocket!"  PayFor: 43200  Commands:  - asConsole! cmi money give [playerName] 500024hour:  DisplayName: "&f24 hour reward"  AutoClaim: false  Description:  - "&2Get reward for 24 hour's you have been online"  - "&215 000 bucks into your pocket!"  PayFor: 86400  Commands:  - asConsole! cmi money give [playerName] 150007days:  DisplayName: "&f7 day reward"  AutoClaim: false  Description:  - "&2Get reward for 7 days you have been online"  - "&250 000 bucks into your pocket!"  PayFor: 604800  Commands:  - asConsole! cmi money give [playerName] 5000030days:  DisplayName: "&f30 day reward"  AutoClaim: false  Description:  - "&2Get reward for 30 days you have been online"  - "&2500 000 bucks into your pocket!"  PayFor: 2592000  Commands:  - asConsole! cmi money give [playerName] 500000
```

In the above example “**hourly1**” is the unique identification for that playtime reward (further known as PTR). You can make anything you want, but it should be different for each PTR. Avoid using extended characters, spaces, or underscores. Keep it short and simple for the unique identifier.

**DisplayName** – will define the name which will be shown in the reward list. You can use color codes to separate you from others. Can be anything you want.

**AutoClaim** – This will define if the reward will be given out automatically to the player when he reaches a target, or if they will have to claim it manually. If it’s manual, then the player gets a reminder every X minutes (configurable in the config file) that he has some awaiting rewards. To see messages he has to have **cmi.prewards.notification** permission node.

**Description** – Will define the description for that reward which will be shown when hovering over the reward list. You can add as many lines as you want and use color codes if needed.

**PayFor** – will define time in seconds when to give out a reward. This, in particular, will pay only once and when the player reaches a certain amount of playtime in total.

**PayEvery** – is a repeating reward for each x seconds played on the server.

**StackRewards** – True or False. Defaults to True if not provided. Defines if you want to stack rewards and allow for the player to get more than one reward at a time if he stacked them.

**Commands** – List of commands to perform when the reward is being claimed. Supports [[CMI 专用命令|**specialized commands**]].

## Commands, Permissions, & Placeholders

**Commands**:

```
> cmi checkcommand reward
 --------------------------------------------------
 /cmi prewards (playerName)
>
```

**Permissions**:

```
> cmi checkperm reward
 --------------------------------------------------
 cmi.command.prewards.others.claim - Allows to claim rewards for others
 cmi.command.prewards - Check playtime rewards
 cmi.command.prewards.others - Check playtime rewards
 cmi.prewards.[preward] - Allows to get particular playtime reward
 cmi.prewards.notification - Allows to see playtime rewards notifications
>
```

**Placeholders**:

```
%cmi_user_prewards_count%
```
