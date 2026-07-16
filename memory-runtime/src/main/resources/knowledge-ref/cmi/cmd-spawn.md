---
title: CMI 出生点 Spawn
tags: CMI, 命令, spawn
source: https://www.zrips.net/cmi/commands/spawn/
---
**cmi setspawn (playerName) (true/false) (-g:[groupName]) (-rng:[range]) (-w:world,world_nether)**  
Permission needed to use it: cmi.command.setspawn

Defines players spawn point for /cmi spawn command or location where player resurrects.  
To set the spawn, you need cmi.command.setspawn permission.

`cmi setspawn`

(true/false) – Defines players spawn point after death, if set to true, for /cmi spawn command. This command also includes the previous example.

`cmi setspawn true`

(-g:[groupName]) – Defines players spawn point after death, if set to true, for /cmi spawn command for a specific group. In this case it’s necessary to associate these to permissions to the relative group.  
– cmi.spawngroup.[groupName]  
– cmi.respawngroup.[groupName] (Indicate in which spawn to go after death. If it’s missing, the reference spawn is the main one).

`cmi setspawn true -g:Moderator`

(-rng:[range]) – Defines players spawn point to be randomized depending by provided range. This allows to spread players in area and avoid placing them in each other. This command also includes the previous example.

`cmi setspawn true -g:Moderator -rng:5`

(-w:world,world_nether) – Defines players spawn point depending where player is located. In this example, this spawn point will only be used when player is in world or world_nether worlds. This can be combined with groups.

`cmi setspawn true -rng:5 -w:world,world_nether`

## Extra and Respawning

-   Auto-respawn; No need to click the respawn button on death-screen.  
    This permission allows you to return to the spawn immediately: **cmi.autorespawn**.
-   To send another player to the spawn, you need **cmi.command.spawn.others** permission.
-   You can activate BlackListedItems (Option to prevent player teleportation when they have blacklisted items in their inventory. Can be bypassed with **cmi.teleport.bypassblacklist**) for the spawn in config file.
-   You can define the respawn order if defined world is not present in Specific list.
-   Possible respawn locations: 

   `PriorityOrder:`  
   `- anchor`  
   `- spawn`  
   `- bedLocation`  
   `- homeLocation`  
   `- worldSpawn`

# **Spawn** is preset spawnlocation with /cmi setspawn command, that location should have RespawnLocation set to true  
# **bedLocation** is a location set by interacting with bed, BedInteraction should be set to false and players requires cmi.bedhome to set bed location  
# **homeLocation** is the location set by the player which is with the default (Home) name, if that one doesn’t exist then first in the list will be used if possible  
# **worldSpawn** is a location preset to this world, this is not CMI location but the default world spawn location  
# **anchor** is a location defined by interacting with respawn anchor. This, in general, will only apply when you die in nether world, otherwise, bed location is used  
# **warp! [warpName]** can be any valid warp you set for players to be teleported, they will bypass any requirements for that warp

You can define players’ first spawn point with **/cmi setfirstspawn (playerName)** command. This is for when they log into the server for the first time.

_Don’t forget to set the [vanilla](https://minecraft.fandom.com/wiki/Commands/spawnpoint) spawnpoint, and if you use a world manager like Multiverse-Core, you should probably also do /mvsetspawn_

## Commands, Permissions & Placeholders

Commands:

```
> cmi checkcommand spawn
 --------------------------------------------------
/cmi setfirstspawn (playerName)
/cmi spawn (playerName) (-s)
/cmi setspawn (playerName) (true/false) (-g:[groupName]) (-rng:[range]) (-w:world,world_nether) (loc:[world;x;y;z;yaw;pitch])
>
```

Permissions:

```
> cmi checkperm spawn
 --------------------------------------------------
cmi.respawngroup.[respawngroup] - Defines player individual respawn point
cmi.spawngroup.[spawngroup] - Defines player individual spawn point
cmi.command.setwarp - Sets warp location
cmi.command.spawn.others - Teleports back to spawn location
cmi.command.spawn - Teleports back to spawn location
cmi.autorespawn - Allows to respawn automatically
cmi.spawnonjoin.bypass - Allows to bypass spawnOnJoin option
cmi.command.setspawn - Sets spawn command teleport point
cmi.command.setfirstspawn - Sets first spawn point
>
```

Placeholders:

```
There are no placeholders for this feature.
```
