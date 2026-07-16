---
title: CMI 聊天过滤
tags: CMI, 扩展, 聊天
source: https://www.zrips.net/cmi/extra/chat-filter/
---
## Introduction

The CMI Chat Filtering feature allows server owners to better manage and moderate the content within their server. Separate from command warmups and cooldowns, this will help detect command spamming, caps-lock shouting, in-chat swearing, and more.

Through regular expressions and flexible customizations, a server owner can make a dynamic chat filter that suits their community and server setup.

The customizations are done in the `~/plugins/CMI/Settings/ChatFilter.yml` file. Which has a few sections.

-   Enable/Disable the chat filtering feature.
-   Blocking of Advertising in ipBlock.
-   Blocking of Swearing (you can add many) (handy for not just swearing, you could block phrases, political chat, or certain words).
-   Anti-blocking of certain domains, through a Whitelist.
-   Blocking of Duplicated messages.
-   Blocking of Capitalization abuse (with whitelist, also ignores playernames).
-   And great integration with CMI’s Chat Manager.

## Requirements

For chat filtering to work it’s required to edit the `~/plugins/CMI/Settings/ChatFilter.yml` file and set Enabled: at the top to true.

CMI as Chat Manager is recommended, but not required.

## Commands

There are no direct commands for this feature. However, a worthy note here is that you can run commands that you want on the person triggering the chat filter. You can set these in the `~/plugins/CMI/Settings/ChatFilter.yml` file. Instead of automatically banning someone, you could consider these CMI features: jail, cuff, mute, ban, temp ban, kick, mail, clearchat, or any valid command from other plugins.

You can use specialized commands.

## Permissions

You could grant the inform permission to your team members, and maybe grant some bypass permissions to higher or special groups/ranks.

```
cmi.chatfilter.bypass.[groupname] - Allows to bypass particular chat filter group
cmi.commandfilter.bypass - Allows to bypass command spam filter
cmi.chatfilter.capbypass - Allows to bypass chat caps filter
cmi.chatfilter.inform - Informs player when someone breaks chat filter rules
cmi.chatfilter.spambypass - Allows to bypass chat spam filter
```

## Placeholders

There are no placeholders for this feature. However, if you use the CMI Warnings feature you can use %cmi_user_warning_count%, and %cmi_user_warning_points%. There are also a couple of ‘spy’ placeholders (allowing you as a team member to see what they’re spamming). 

## Tip: RegEx (regular expressions) and Testing

Regular expressions can be quite difficult to master. We strongly recommend to properly test on a development environment first.

There are websites out there that you can use to build your own regular expressions with: [https://regexone.com/](https://regexone.com/) and [https://regex101.com/](https://regex101.com/)

There’s also an unofficial (and unsupported) tool by Alax you can try to use [https://dkalaxdk.github.io/CMIRegexFilter/](https://dkalaxdk.github.io/CMIRegexFilter/)

## Enabling or Disabling the chat filtering feature.

If you want to start filtering for advertising, bad words, duplicated messages, commands, and/or capitalization then change the Enabled value below from false to true and configure everything, after which you can do a /cmi reload.

```
Enabled: true
```

## Blocking Advertising with ipBlock

With CMI you can prevent players from posting domain names and IP addresses. You can enable/disable ipBlock.

You can define a group and use this to prevent your team members or a special group from being blocked. To keep track of who triggers ipBlock, you can log to console.

```
# Defines filter group and defines required permission node to bypass this filter: cmi.chatfilter.bypass.[groupName]Group: Advertising# When set to true, each time player triggers filter, console will receive information about who triggered it, which filter and with what messageInformConsole: true
```

Using regular expression you can fine-tune domain names and ip addresses that are being posted by players, and block them. You can also define what to replace the caught word with. 

```
# Regex expression to filter by. How to use regex https://regexone.com/Regex:- '[a-zA-Z0-9-.]+\s?(.|dot|(dot)|-|;|:|,|_|\/)\s?([a-zA-Z]{2}|aero|asia|biz|cat|com|coop|edu|gov|info|int|jobs|mil|mobi|museum|name|net|org|pro|tel|travel)\b'- \b[0-9]{1,3}(.|dot|(dot)|-|;|:|,|(\W|\d|_)*\s)+[0-9]{1,3}(.|dot|(dot)|-|;|:|,|(\W|\d|_)*\s)+[0-9]{1,3}(.|dot|(dot)|-|;|:|,|(\W|\d|_)*\s)+[0-9]{1,3}\b# With what we need to replace word, if not defined found expression will not be changedReplaceWith: ''
```

The next part of ipBlock will let you define how you wish to deal with blocked messages. You can set it to also check private messages, if it is completely removed from chat, or if everybody still sees a message. And you can define how to inform staff.

```
# With what we need to replace word, if not defined found expression will not be changedReplaceWith: ''# possible: none, others, all# Where 'none' means everyone will receive this message# 'others' means that sender will get message but not other players, this is usefull to prevent advertising and silently block it# 'all' means that no one will receive sent messageBlockType: others# When enabled rule will be applied to private messagesincludePrivateMessages: true# Players with cmi.chatfilter.inform permission will receive defined message when rule is brokenmsgToStaff: '&4!&6[playerName] &4advertising with: &r[message]'
```

If you need to you can give ipBlock one or more commands to perform when someone’s message is blocked. You can use this to automatically mute or kick someone. Or send them a bossbar-, or actionbar message, or send a moderator and the player both a /cmi mail message, maybe jail the player. You can use specialized commands here, the [senderName] variable, as well as other global variables. Below are a couple of examples.

Run no commands:

```
# List of commands to perform when rule is broken. Use [senderName] to include message sender name. Supports global variables same as locale fileCommands: []
```

Run a single command:

```
 # List of commands to perform when rule is broken. Use [senderName] to include message sender name. Supports global variables same as locale fileCommands:- cmi mail [senderName] Please refrain from Advertising, see /rules
```

Run multiple commands:

```
# List of commands to perform when rule is broken. Use [senderName] to include message sender name. Supports global variables same as locale fileCommands:- cmi mail [senderName] Please refrain from Advertising, see /rules- cmi kick [senderName] Please refrain from Advertising, see /rules- cmi note [senderName] add Broke rule Advertising
```

The last part of ipBlock is very dynamic. There are two examples listed besides ipBlock: swearing1, swearing2, you can add as many as you want. For example, if you wish to want players to stop saying bedwars or hypixel, or some political phrase, you can add block sections for them here. Just copy/paste one and configure it accordingly. You can add multiple regular expressions per section, or give each word its own section. Allowing certain words to get a warning, but for example, automatically ban when it’s hate speech. 

## Using the whitelist on the block-list

If you still want your members to help others by linking them to your discord, forum, youtube channel, etc. Or allow certain domains such as twitter and twitch, but block everything else. You can then use regular expressions to make exceptions by adding them to the whitelist. Reminder, you can also give certain groups a chatfilter bypass permission for everything or a specifically defined group.

```
# List of regex filter to exlude from block list. Usefull if you want to block all ip/host address but want to allow usage of your own server.WhiteList:- \bgoogle.\s?([a-zA-Z]{2,4})\b- \bspigotmc.\s?([a-zA-Z]{2,4})\b
```

## Prevent players from sending duplicated messages

The second section of the `~/plugins/CMI/Settings/ChatFilter.yml` file is about DuplicatedMessagePrevention: where you can prevent players from making similar messages in a short period of time. You can also enable this, and give groups bypass permissions.

```
# When set to true, plugin will prevent spamming of same or similar messages in short time range. Can be bypassed with cmi.chatfilter.spambypass permissionUse: true
```

The following options allow you to fine tune how quickly message spam is caught:

```
# How much in percentage message is counted as samePercentage: 80# Defines how often in seconds you can send same/similar messageInterval: 5# How many commands you can repeat before stopped for cooldownMinAmount: 2
```

Command spam filter is currently (stil) in `~/plugins/CMI/config.yml` – they’re almost the same options as message spam filtering. Search for: **CommandFilter:**

## Filter chat messages with multiple capitalized letters (CAPS SPAM)

The last section of `~/plugins/CMI/Settings/ChatFilter.yml` lets you define how to deal with chat messages which contain multiple capitalized characters. You can enable this feature by setting the filter to true. Side-note: Playernames will not be counted.

```
# When nebaled we will try to prevent chat messages with multiple capitalized letters by defined criteria# Can be bypassed with cmi.chatfilter.capbypass permission nodeFilter: true
```

And you can fine tune how quickly something is considered caps-spam.

```
# Defines amount of letters we can ignore# For example ':DDD' would be made of 2 letters with 3 capitalized letters which would make it 75% capitalizedIgnoreUnder: 6# Amount in percentage of capitalized letters we should not cross over.# In example 'GOOD thing' would be 4 capitalized and 5 not, spaces gets ignored, whic makes it 44% capitalized and passes checkPercentage: 50
```

You can also run no commands, one or more commands. Further up in this document I showed examples of how that would look. You can apply that to the below options:

```
# Commands:- asConsole! cmi titlemsg [playerName] &cToo many caps! -keep:20
```

You can also use a whitelist for capitalizations, to help prevent the filter being too sensitive and triggering on just a quick hello, or commonly used words.

```
# List of messages, excluding color codes, player can use even if it 100% capitalizedWhiteList:- AFAIK- AFK- BRB- IMHO- OMG- ROFL- ROFLMAO- LQTM- LSMH- LMHO
```

When you are done configuring `~/plugins/CMI/Settings/ChatFilter.yml` you have to think about the permissions you give your players. And optionally you can use command warmups and cooldowns options in config.yml to further fine-tune how you wish players to ‘behave’ on your servers.
