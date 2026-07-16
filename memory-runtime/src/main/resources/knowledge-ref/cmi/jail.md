---
title: CMI 监狱 Jail
tags: CMI, 监狱
source: https://www.zrips.net/cmi/jail/
---
You have 4 commands for the **CMI Jail Feature**:

**`/cmi jail [playerName] (time) (jailName) (cellId) (-s) (r:jail_reasson)`** This will jail a player where time is optional and will use the default one if not defined. jailName is optional and if not defined then the nearest will be picked. cellid, again, is optional and if not defined free cell will be used. Jail reason can be anything you want and will be displayed in placeholders.

`**/cmi jailedit**`  
The main command to manage all of your jails.

`**/cmi jaillist**`  
This will list all the jails with cell count and how many you have prisoners in each jail. Which can be clicked to get more detailed information.

`**/cmi unjail [playerName]**`  
The last and not least, will unjail player.

Before we start with jail creation we have to check out the config.yml file.

```
Jail:
  # Defines in milliseconds how often to check if the player leaves the jail area
  # Bigger numbers can help slightly lower server load
  CheckInterval: 500
  # Defines default jail time when time is not provided with a command
  DefaultTime: 300
  # Chat range in blocks while the player is in jail
  # Set to 0 to allow talking
  # set to -1 to prevent talking in general while jailed
  ChatRange: 20
  # When set to true jail time will decrease while the player is offline
  # When set to false jail time will only be counted while the player is online
  CountWhileOffline: false
  # When set to true jail time will not decrease if player gets into afk mode while being jailed
  # When set to false, time will pass normally
  NoAfk: false
  # Do you want to prevent players damage while he is in jail
  PreventDamage: true
  # Do you want to prevent players hunger while he is in jail
  PreventHunger: true
  Commands:
    # Commands to be performed when the player gets jailed
    OnJail:
    - ''
    # Commands to be performed when the player gets unjailed
    OnUnJail:
    - ''
  WhiteListedCmds:
  - cmi msg
  - cmi reply
```

Set config settings to your own liking and your own needs. For example, default jail time could be something we would want to change depending on your server type. By default it’s only 5 minutes.

Chat range can be useful in case you want to prevent jailed players from spamming chat, as what else would they be doing. So you can limit that to a particular range in blocks to allow them to talk but only to nearby players, maybe with someone who is already in jail

By default, jail time will not go down if the player logs out. This will help out to force players to sit down the entire time they have been jailed and not simply log out and return when time runs out.

Preventing the player from going AFK can help out too in case the player simply leaves the account logged in while he is doing other things, like homework (Yes, that can happen…)

In case you will want to still allow some basic commands for jailed players you can define them in the white list.

Now the fun part.

First of all, **get some basic jail** set up. Something like:

![](https://www.zrips.net/wp-content/uploads/2019/06/jail1.jpg)

No need to be fancy here.

Next, let’s use the CMI selection tool (the default is a **wooden shovel**) and select an area. Your selection will be marked with particle effects.

![](https://www.zrips.net/wp-content/uploads/2019/06/jail2.jpg)

Next lets create new jail with `**/cmi jailedit addjail [jailName]**` so let’s pick some extremely original jail name and lets name it **myJail**, so the command will look like this **/cmi jailedit addjail myJail**

After this, you will get the list of existing jails and you should see your newly created one like:

![](https://www.zrips.net/wp-content/uploads/2019/06/jail3.jpg)

And now for the easy part (yes, that one was the hardest part, who knows how to build jails anyways…) click in chat on your new and awesome jail to create new cells. For newly created jail you will only see **[+]** sign. So go to the first cell and press that **[+]** button. you will get the list of existing cells which should include your new one, like:

![](https://www.zrips.net/wp-content/uploads/2019/06/jail4.jpg)

Repeat this step as many times as you want. There is no limit to how many cells you can have. But in general, they should be separate from each other, not limited by that, but the general rule of thumb. After all, we don’t want inmates to plan an escape!

At this point, you have working in jail. Though, small inconveniences can be noticed. Players who survived throw 5 min jail time will remain in jail, even tho they are no longer officially jailed, but someone forgot to give out a memo or something. So let us get rid of players who are no longer jailed, so lets set outside the teleport location. As you can see 2 pictures back, there is a grey out **[tp]** button. Let’s travel to a location outside the jail and simply click it. You will get a confirmation message like “**Set new outside location**” Fancy! After that, you can change this location to whatever you want. 

So now you are done. You have a jail with cells and an outside location where players will go after they got released. 

## Bonus Stuff

Jailed players will get a bossbar message showing how long they are jailed. So that’s fancy too!

![](https://www.zrips.net/wp-content/uploads/2019/06/jail5.jpg)

## Jail Signs

In case you want to show to others who are jailed here and for how long then you have 2 options: Use dynamic signs or use holograms.

So let’s set signs. Place a sign, location doesn’t matter. And let’s create new dynamic sign with **/cmi dsign new** after this you will get something like:

![](https://www.zrips.net/wp-content/uploads/2019/06/jail6.jpg)

I know, it’s not so fancy, but it does what it should.

Let’s add new lines like

```
%cmi_jail_time_myJail_1%%cmi_jail_username_myJail_1%%cmi_jail_reason_myJail_1%
```

This will show jailed time, who is jailed, and for what reason, if the reason was defined while jailing the player.  So end setup should look like:

![](https://www.zrips.net/wp-content/uploads/2019/06/jail7.jpg)

You can open dynamic sign GUI (Open GUI) and lower down default refresh rate if you want it to be faster, but in general, for jail cells default time is fine.

![](https://www.zrips.net/wp-content/uploads/2019/06/jail8.jpg)

## Jail Holograms

For those who like fancy things, let’s make **holograms**. Stand where you want to place the hologram and perform something like **/cmi hologram new myJailCell1** where myJailCell1 is just some random hologram name. You will get something like:

![](https://www.zrips.net/wp-content/uploads/2019/06/jail9.jpg)

Does it look familiar? Yes, indeed it does. So let’s add lines

```
%cmi_jail_time_myJail_1%%cmi_jail_username_myJail_1%%cmi_jail_reason_myJail_1%
```

This will add jailed players’ information in the same way as it was added for dynamic signs. Keep in mind that the default hologram update interval can be not enough to have smooth updates, so open GUI (open settings GUI) and set the update interval to something like 1 second. 

![](https://www.zrips.net/wp-content/uploads/2019/06/jail10.jpg)
