---
title: CMI 随机传送 RTP
tags: CMI, 命令, rtp
source: https://www.zrips.net/cmi/commands/rtp/
---
## Intro to Random Teleporting

The CMI **Random Teleport** feature allows the server owner to forcefully teleport players to a random location in a world.

It can be very handy for certain events, a custom alias, or for what it is mostly used for, the **/rtp** command.

## Configure the /rtp command

There are a few ways to configure the /cmi rt command, it’s common on many servers to call it /rtp. The **Alias.yml method:** Open the file Alias.yml, and find the /rt and /rtp commands, and set it to true behind enabled: and save the file.

```
  # /cmi rt $1-
  /rt:
    Enabled: true
    Tab: true
  /rtp:
    Enabled: true
    Tab: true
```

After saving the file you can do a /cmi reload, and you can use the commands right away. However, in newer versions of Minecraft these will show red until you do a /stop and then start the server again. **The AliasEditor method:** In-game you can type create a new /rtp command with the /cmi aliaseditor command, after typing it, press the green + icon, and then you call it rtp, and then the next green + icon will let you add (multiple) commands. You can use this to have commands to play particles, delays, give custom msg, actionmsg, etc. And run the full cmi rt command as a console command to force it to a certain world, etc. Below is an example of a cmi-rtp-cmd.yml file you can drop in the plugins/CMI/CustomAlias/ folder (and then /cmi reload or restart the server).

```
CustomAlias:
  rtp:
    Cmds:
    - asConsole! cmi sound block_conduit_deactivate [playerName]
    - asConsole! cmi titlemsg [playerName] &6⸭ ⸭ ⸭\n&fCharging teleporter
    - delay! 3
    - asConsole! cmi titlemsg [playerName] &6⸭ ⸭ ⸭\n&fTeleporter is ready!
    - asConsole! cmi rt [playerName]
    - delay! 0.2
    - asConsole! cmi sound entity_generic_explode [playerName]
```

This example shows you how to play a sound, add a delay, show a custom title message, run the cmi rt command, and play an end-sound. You can of course make the custom aliaseditor made /rtp command do whatever you want. Including calling the command /random or /rt or /teleporter.

## Configuration (command / config.yml)

There are a few ways you can (auto) configure your /cmi rt behavior. There are quite a few config.yml settings, as well as some in-game commands. Make sure your global and/or per-world specific settings are properly configured to avoid unwanted results.

**Commands:** (for full syntax, see further down this page)  
`/cmi setrt world center:0,0 min:250 max:5000 square enabled`

**Config.yml:**

```
RandomTeleportation:
  # If this set to true we will generate random teleport default settings for all detected worlds
  # Setting to false will not longer generate world setups, but you can add them manually if needed
  AutoGenerateWorlds: true
  Worlds:
    # World name to use this feature. Add annother one with appropriate name to enable random teleportation
    world:
      Enabled: true
      # Max coordinate to teleport, setting to 1000, player can be teleported between -1000 and 1000 blocks between defined center location
      # For example having centerX at 2000 and centerZ at 3000 while MaxRange is set to 1500 we will teleport player between x:500;z:1500 and x:3500;z:4500 coordinates
      MaxRange: 1000
      # If maxcord set to 1000 and mincord to 500, then player can be teleported between -1000 to -500 and 1000 to 500 coordinates
      MinRange: 500
      CenterX: 0
      CenterZ: 0
      Circle: false
      IgnoreWater: true
      IgnoreLava: true
      ignorePowderSnow: false
      minY: -50
      maxY: 300
    world_nether:
      Enabled: true
      MaxRange: 25000
      MinRange: 100
      CenterX: 25
      CenterZ: -50
      Circle: true
      IgnoreWater: false
      IgnoreLava: true
      ignorePowderSnow: false
      minY: 0
      maxY: 128
    world_the_end:
      Enabled: true
      MaxRange: 1000
      MinRange: 500
      CenterX: 0
      CenterZ: 0
      Circle: false
      IgnoreWater: true
      IgnoreLava: true
      ignorePowderSnow: true
      minY: 0
      maxY: 256
  # How long force player to wait before using command again.
  Cooldown: 5
  # How many times to try find correct location for teleportation.
  # Keep it at low number, as player always can try again after delay
  MaxTries: 20
  # List of biomes to exclude from random teleportation
  ExcludedBiomes:
  - Ocean
  - Deep ocean
```

## Warmups, Cooldowns, and Command-cost

CMI allows you to configure command cooldowns, warms, and a fee for running commands. You can configure this in the config.yml file. 

Note that CMI’s RT feature (see above) has a Cooldown: setting. 

Config.yml has a WarmUps: section where you can add the cmi rt command. Make sure the feature is set to enabled: true, of course. 

For managing the cost of running a command in CMI, check the file: commandCost.yml

## Redirect RT

You can use the specialized commands feature of CMI to update your /rtp command to recognize a player typing /rtp in for example the /lobby or /spawn worlds, and have them actually randomly teleport in a different world (redirecting the rt).

This currently only works if you make a custom aliaseditor made /rt or /rtp command (see further up on this page to learn how).

Edit your custom /rtp command, and before anything you have, add something like this:

```
ifinworld:spawn! asConsole! cmi rt [playerName] world_survival
ifinworld:lobby! asConsole! cmi rt [playerName] world_games
```

## Limited use of RT

Some servers owners desire players to only use the /rtp command once or only a few times. This information shows an example of how one could achieve this.

This currently only works if you make a custom aliaseditor made /rt or /rtp command (see further up on this page to learn how)

Edit your custom /rtp command, and adjust it to do the following:

```
perm:cmi.onetimertp! msg! [playerName] Sorry, you could only use /rtp once.
perm:cmi.onetimertp@! asConsole! cmi rt [playerName] %cmi_player_world%
perm:cmi.onetimertp@! asConsole! lp user [playerName] permission set cmi.onetimertp true
```

If you want to limit the use of /rtp to say 3 or 5 times, you might have to adjust the commands to read and set/increment the /cmi usermeta [needs more info ] [alias for usermeta needs to be made and tested]

## Commands, Permissions & Placeholders

**Commands:** (list of teleport related commands [[CMI 命令列表|here]])

```
> cmi checkcommand rt
 --------------------------------------------------
/cmi setrt (worldName) (center:[x]:[z]) (min:[range]) (max:[range]) (square/circle) (enabled/disabled)
/cmi rt (playerName) (worldName) (-s)
>
```

**Permissions:**

```
> cmi checkperm rt
 --------------------------------------------------
 cmi.command.rt - Teleports to random location
 cmi.safeteleport.bypass.[lava/void/suffocation/unknown/plugin/unsafeteleportation/noperm] - Allows to teleport into unsafe location without confirmation
 cmi.command.setrt - Set random teleport bounds
 cmi.teleport.with.[entitytype] - Allows teleportation with defined mount
 cmi.command.tpbypass - Bypass teleportation to unsafe location
 cmi.warmupbypass.[commandname] - Allows to bypass particular CMI command warmup
 cmi.randomteleport.cooldownbypass - Allows to bypass random teleport cooldown
 cmi.teleport.bypassblacklist - Allows to bypass protection from teleporting with blacklisted items
 cmi.command.rt.others - Teleports to random location
 cmi.chorusteleport - Allows to use chorus to teleport around
 cmi.safeteleport - Prevents teleportation to unsafe locations
>
```

**Placeholders:** (there are no rt placeholders, but these are quite handy)

```
%cmi_user_backloc%
%cmi_player_world%
%cmi_player_x%
%cmi_player_y%
%cmi_player_z%
%cmi_player_biome%
```
