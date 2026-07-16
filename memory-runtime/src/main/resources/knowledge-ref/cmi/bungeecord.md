---
title: CMI 跨服信息
tags: CMI, bungeecord, 跨服
source: https://www.zrips.net/cmi/bungeecord/
---
# BUNGEECORD & VELOCITY

CMI has support for Bungeecord and Velocity. This page will explain what is and isn’t supported. Over time, we will hopefully improve the support for Bungeecord and Velocity (no promise).

More information about Bungeecord (the proxy of your Minecraft server network) can be found here: https://www.spigotmc.org/wiki/bungeecord/ and more information about Velocity (also a proxy for your network) can be found here: https://docs.papermc.io/velocity

Since the mechanics are the same for either, the existing commands, permissions, and placeholders listed below apply to both.  

## Commands and Permissions:

```
/cmi bbroadcast (!) [message] (-s:[serverName,serverName])
-> cmi.command.bbroadcast - Sends special message to all players on all servers

/cmi msg [playerName] [message]
-> cmi.command.msg.togglebypass - Allows to send private messages even if player has pm toggled off
-> cmi.command.msg - Sends message to player
-> cmi.command.msg.[maingroupname].send - Allows to send private messages to specific player groups
-> cmi.command.msg.clean - Allows to send clean messages to player using ! at beginning
-> cmi.command.msg.noreply - Allows to send clean messages to player by using !- at beginning without option to reply
-> cmi.command.msg.vanish - Allows to send private messages to vanished players

/cmi reply [message]
-> cmi.command.reply - Reply to last message sender

/cmi sendall [serverName]
-> cmi.command.sendall.bypass - Prevents player from being sent to target server
-> cmi.command.sendall - Send all online players to target server

/cmi server [serverName] (playerName) (-f)
-> cmi.command.server.others - Connect to bungeecord server
-> cmi.command.server - Connect to bungeecord server

/cmi serverlist
-> cmi.command.serverlist - Show server list
-> cmi.command.serverlist.others - Show server list

/cmi staffmsg [message/toggle/on/off]
-> cmi.command.staffmsg - Sends message to staff channel

-> cmi.bungee.publicmessages.[servername] - Allows to send public messages to target server
```

## Placeholders

```
%cmi_user_bungeeserver%
%cmi_bungee_total_[serverName]%
%cmi_bungee_current_[serverName]%
%cmi_bungee_motd_[serverName]%
%cmi_bungee_onlinestatus_[serverName]%
```

## Configuration in CMI’s Settings/Chat.yml:

```
BungeeCord:
  # You can disable bungeecord support entirely if you are exrperiencing issues with it
  # When setting this to false some features like public messages over bungee cord, private messages over bungeecord, portals over bungecoord and other features will stop working
  # Keep in mind that regular behavior of those features will remain intacted
  Enabled: true
  # When set to true player names from entire bungee network will be included into tab complete
  NamesInTabComplete: true
```

and also:

```
  Bungee:
    # Attention! This will require you to have CMI Bungee plugin which can be found at zrips.net
    # Or direct download https://www.zrips.net/cmi/
    # Do you want to enable private messaging over bungeecord
    Messages: true
    # Do you want to enable public messaging over bungeecord
    # Player needs to have cmi.bungee.publicmessages.[servername] permission node to be able to send messages to target server
    PublicMessages: true
    # Do you want to enable staff messaging over bungeecord
    StaffMessages: true
```

## Installation Instructions:

-   [Buy a CMI license](https://www.spigotmc.org/resources/3742/) to use on your network if you don’t already have one.
-   Install CMI (and CMILib) on _each individual_ server, _**except**_ for the proxy.
-   Configure CMI, enable the CMI Chat, and enable BungeeCord using the instructions from this article. (it’s the same for Velocity)
-   Get the [CMI Bungee file](https://www.zrips.net/cmib/) or the [CMI Velocity file](https://www.zrips.net/cmiv/) and put it **on the proxy only.**
-   _Done_, start up your network, and keep an eye on the console (or latest.log) to catch any potential issues.

## Will the CMI’s BungeeCord / Velocity File Help Synchronize x y z?

At the moment, the answer is probably no. It won’t sync inventories, money, or things like warps and homes. Is there an interest by Zrips to add more support for this? Yes, from what I gathered, there is. I have no ETA on this.

Currently, it’s mainly for syncing public and private chats and staff messages, and to better support /cmi server, /cmi serverlist, and jumping through portals, and custom messages changing between servers.

## CMI Chat and BungeeCord / Velocity Chat

You have to enable CMI’s Chat Manager feature to use the BungeeCord chat features of CMI. You can do so by editing the CMI Settings/Chat.yml file:

```
Chat:
  # Will try to modify chat to display it in a defined format
  ModifyChatFormat: true
```

Then, walk through all the other options related to chat.

Don’t forget to set up appropriate permissions, such as: `cmi.bungee.publicmessages.[servername]`

## FAQ:

**If I run 3 servers and they’re all on the same network, do I need three licenses?**  
No, a single license covers the entire network.

**Is the chat global?**  
Yes, the chat will show on all servers across your network.

**Can I synchronize money on all of my servers?**  
This is not currently possible. However, using a third-party plugin called MySQL Player Data Bridge (we do not give support for this setup).

**Can all of the warps synchronize across each of the servers?**  
No, this is unfortunately not possible using CMI.

**Can homes synchronize across each of the servers?**  
No. Like warps, this is unfortunately not possible using CMI.

**Can I use portals to teleport players to another server on the network?**  
Yes, this can be achieved using the [[CMI 传送门|Portals feature]] of CMI.

**Can I use /server and /serverlist instead of /cmi server and /cmi serverlist?**  
Navigate to the Alias.yml file of CMI, and enable these commands there.

**If a command cannot be run on a server, can I still send it?**  
No, this is not possible with CMI currently.

**If a command doesn’t move a player to another server, can I still do it?**  
Yes, you can use /cmi aliaseditor that uses /cmi server and/or placeholders.

**Can I share inventories between servers?**  
This is not currently possible. However, using a third-party plugin called MySQL Player Data Bridge (we do not give support for this setup).

**Can I use a single database for all my CMI instances?**  
No, the default is SQLite. You can optionally use MySQL, but make sure that the tablePrefix option is unique for each of your servers to avoid issues.

**Why isn’t the CMI BungeeCord / Velocity file generating any folders?**  
This is completely normal. CMI’s BungeeCord file does not currently generate any additional files or folders. You can verify that CMI is hooked with BungeeCord / Velocity and checking /cmi version on any of your backend servers. It should show that BungeeCord is hooked.

**Is there Velocity support?**  
Yes, support has been added, and requires the latest CMI, CMILib jars, and the free-to-download [CMIV (velocity) jar](https://www.zrips.net/cmiv/) file. 

**Can I show a custom switched server message, so it doesn’t show joined or left the server?**  
Yes, you can find `ServerSwitch:` in config.yml and set it to true. When enabled and you have bungee server with CMIB on it (or Velocity with the CMIV), we will try to detect where the player went, and we will show a switch server message instead of logout if possible.
