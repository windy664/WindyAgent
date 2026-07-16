---
title: CMI 粒子效果
tags: CMI, 扩展, 粒子
source: https://www.zrips.net/cmi/extra/particles/
---
**/cmi panimation {variableExpresion}**  
For a detailed explanation of what you should use, look belove. 

Some of the CMI features uses particles which can be changed in config file. Each particle is defined with one line and no spaces. Multiple variables can be used if more complex results is needed.

## Circle

Variables should be separated with **;** In example **circle;effect:heart;dur:0.5**  
Possible variables for circle as follows:

effect:[particleName]

**ef** can be used for shorter code

Particle effect name should be defined. In example **ef:flying_glyph**

fireworks_spark, crit, magic_crit, potion_swirl, potion_swirl_transparent, spell, instant_spell, witch_magic, note, portal, flying_glyph, flame, lava_pop, footstep, splash, particle_smoke, explosion_huge, explosion_large, explosion, void_fog, small_smoke, cloud, coloured_dust, snowball_break, waterdrip, lavadrip, snow_shovel, slime, heart, villager_thundercloud, happy_villager, large_smoke, water_bubble, water_wake, suspended, barrier, mob_appearance, end_rod, damage_indicator, sweep_attack, totem, spit, squid_ink, bubble_pop, current_down, bubble_column_up, nautilus, dolphin, water_splash, campfire_signal_smoke, campfire_cosy_smoke, falling_dust, sneeze, composter, flash, falling_lava, landing_lava, falling_water, dripping_honey, falling_honey, landing_honey, falling_nectar, soul_fire_flame, ash, crimson_spore, warped_spore, soul, dripping_obsidian_tear, falling_obsidian_tear, landing_obsidian_tear, reverse_portal, white_ash

center:[playerName]/[World],[x],[y],[z]

**ct** can be used for shorter code

Player name or location should be provided. In example: **ct:Zrips** **ct:LT_Craft,15,256,21**

radius:[distance]

**r** can be used for shorter code

Defines circle radius. For decimal value use . instead of , In example: **r:1.2**

fixed

Defines that particle should stay in one place and avoid following player if player was defined as source of location. This doesn’t require any extra variable and should be used as **circle;effect:heart;center:Zrips;fixed**

radiuschange:[distance]

**rc** can be used for shorter code

Changes radius of circle with each tick. **rc:0.1** will increase radius by 0.1 with each tick and **rc:-0.1** will decrease radius by 0.1 Particle effect will stop at moment radius goes belove 0

duration:[time]

**dur** can be used for shorter code

Defines time in seconds to keep that particle effect shown. This can be used to make fail safe in case particle keeps playing or make it defined length if you need to show short effect. Default time is **5** seonds.

Usage: **dur:1.5** which will show particle for 1.5 seconds

maxradius:[distance]

**mr** can be used for shorter code

Defines max radius for circle. If its reaches this threshold then it will stop playing. Default value is 2

usage: **mr:5**

offset:[x],[y],[z]

**off** can be used for shorter code

Defines offset from center location. This can be used to start particle showing not at players location by above his head if needed. 

Usage: **off:0,1.5,0**  **off:0,-1,0**

move:[x],[y],[z]/[playerName(,[speed])]

Moves center point. This can be defined as vector or as target player look direction with optional speed value.

Usage: **move:0,0.1,0** which will move particles up by Y and by 0.1 blocks each tick **move:Zrips** will move particles at direction Zrips is looking at. **move:Zrips,0.1** will move particles at direction Zrips is looking at and at 0.1 blocks per tick speed.

maxmovedistance:[distance]

**maxd** or **mmdist** can be used for shorter code

Defines max distance particle can travel from origin location. Can help out to limit particles going forever to some direction.

Usage: **maxd:5.2**

rotating

**twist** can be used for shorter code

This forces particles to rotate around its axis. Can achieve interesting effects. Doesn’t require any extra value.

particles:[amount]

**part** can be used for shorter code

Defines count of particles in one circle

Usage: **part:3**

pitch:[degrees/playerName]

Defines circle pitch. This could be used to turn circle to one or another side if needed. Value if from 0 up to 180. Player name can be used to take his pitch as value.

Usage: **pitch:152 pitch:Zrips** 

yaw:[degrees/playerName]

Defines circle yaw. This could be used to turn circle to one or another side if needed. Value if from 0 up to 180. Player name can be used to take his yaw as value.

Usage: **yaw:152 yaw:Zrips** 

pitchchange:[degrees]

**pitchc** can be used for shorter code

Changes pitch by defined amount. Allows to create rotating circles effect.

Usage: **pitchc:5**

yawchange:[degrees]

**yawc** can be used for shorter code

Changes yaw by defined amount. Allows to create rotating circles effect.

Usage: **yawc****:5**

followdirection

**fdir** can be used for shorter code

**Pitch** and **yaw** will be updated by target players yaw and pitch if player is defined. No extra variable is needed

color

**c** can be used for shorter code

Defines particle color is that particle can be colorized.  
Couple different variations can be used:  
[r],[g],[b] – defines color by basic red green and blue colors number which range from 0 up to 255  
Hex color code is supported like {#6600cc} or {#red}  
**r** – will make colors to change constantly and cycle throw every possible one  
**rs** – will rainbow colors depending on pitch and yaw from center location  
**rfs** – colorizes particles depending on distance from start position

Usage: **c:100,250,10 c:r c:rs c:rfs**

target:[playerName]

**tr** can be used for shorter code

Targets custom player to follow him

Usage: **tr:Zrips**

hidewhenvanished

**hwv** can be used for shorter code

When set particles will be hidden for players who cant see source player to which particles are attached to.

## Preset

For simplicity sake you can use presets for most used particle effects. Presets are taken form config.yml file under **Particles** section. Defining preset is done with basic **preset:[name]** value, while any extra variable will override presets one if it was already defined or will add it to it for this particular use case. So **/cmi panimation preset:TotemHalo;target:Zrips** will show particle attached to Zrips which means that you can use dynamic variable instead of player name to have best use case scenarion for it: **/cmi panimation preset:TotemHalo;target:[playerName]**

## Examples

[![](https://www.zrips.net/wp-content/uploads/2019/03/2019-03-02_16-37-04.gif)](https://www.zrips.net/wp-content/uploads/2019/03/2019-03-02_16-37-04.gif)

**circle;effect:flying_glyph;yaw:[playerName];pitch:[playerName];r:0.1;part:3;rc:0.03;mr:1;twist;off:0,2.5,0;target:[playerName]**

![](https://www.zrips.net/wp-content/uploads/2019/03/2019-03-02_16-28-11.gif)

**circle;effect:heart;dur:0.1;part:1;offset:0,1.7,0;radius:0.3;target:[playerName]**

![](https://www.zrips.net/wp-content/uploads/2019/03/2019-03-02_16-30-01.gif)

**circle;effect:flying_glyph;dur:5;pitchc:15;part:10;offset:0,1.7,0;radius:0.5;yawc:12;color:rs;pitch:90;target:[playerName]**

![](https://www.zrips.net/wp-content/uploads/2019/03/2019-03-02_16-31-22.gif)

**circle;c:0,255,0;twist;part:5;r:0.75;pitch:90;move:0,0.1,0;rc:-0.02;target:[playerName]**

![](https://www.zrips.net/wp-content/uploads/2019/03/2019-03-02_16-25-28.gif)

**circle;c:200,50,210;twist;part:5;r:0.5;pitch:90;move:0,0.33,0;offset:0,-0.2,0;target:[playerName]**

![](https://www.zrips.net/wp-content/uploads/2019/03/2019-03-02_16-54-23.gif)

**circle;effect:reddust;dur:5;pitchc:5;part:10;offset:0,1,0;radius:1;yawc:5;color:rs;target:[playerName]**
