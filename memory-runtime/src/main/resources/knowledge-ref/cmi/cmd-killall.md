---
title: CMI 清理实体 KillAll
tags: CMI, 命令, kill
source: https://www.zrips.net/cmi/commands/kill-all/
---
The **/cmi kill****all** feature provides an option to clean up currently loaded entities by your provided criteria.

– By default entities which has custom name will _not_ get removed.  
– Entities in boats or minecarts will be ignored.  
– The range can be provided to help remove entities in close range only.

## Command arguments explained

-monsters will remove all monster-type entities. This in general includes zombie, skeletons, wither, and so on.  
-pets will include any entity which can be tamed, like a horse or wolf  
-npc will include entities with metadata of NPC usually results of plugin like Citizens  
-animals removes animal-type entities, like pigs or cows  
-ambient entities like a bat  
-named any entity with a custom name (angry and friendly mobs) (you currently cannot provide a name)  
[mobType] any mob type can be defined to only remove that type of entity (or with **-m:[mobType]**)  
-f will combine monsters, pets, npc, golems, animals, ambient, and vehicles into one  
-lightning strikes lightning where the entity got removed  
**[range]** a number in blocks, to help remove entities in close range. (or with **-r:[range]**)  
-list print-out entities in the appropriate categories.  
**-w:[world]** to remove all entities from specified worldname  
**-s** will silence the output of the command to the player.

## Examples

/cmi killall – will remove all (unnamed) monsters.  
/cmi killall -list will list entities.  
/cmi killall 60 will remove monsters in 60 block range from you.  
**/cmi killall -r:5** will remove monsters in a range of 5 blocks from you.  
/cmi killall zombie will remove all zombies.  
/cmi killall zombie skeleton will remove all zombies and skeletons.  
**/cmi killall -m:sheep** will remove all (unnamed) sheep.  
/cmi killall -monsters -lightning will remove all monsters, and strikes them with lightning at their location.  
**/cmi killall -named** will remove all mobs with a name (monster & friendly). It won’t remove unnamed monsters.  
**/cmi killall -monsters -named** will remove all monsters, and all named mobs (monsters and friendly).  
**/cmi killall -w:world_nether** will remove all monsters from the world ‘world_nether’.  
/**cmi killall -f -named** will forcefully remove all friendly mobs, monsters, and named ones.

## Commands, Permissions & Placeholders

Commands:

```
> cmi checkcommand killall
 --------------------------------------------------
 /cmi killall (-monsters/-pets/-npc/-animals/-ambient/-named/-f/-lightning/-list/-m:[mobType]) (-r:range) (-s) (-w:[worldName])
> 
To kill a player, use /cmi kill instead
```

Permissions:

```
> cmi checkperm killall
 --------------------------------------------------
 cmi.command.killall - Kill mobs around you
>
```

Placeholders:

```
There are no placeholders for this feature
```
