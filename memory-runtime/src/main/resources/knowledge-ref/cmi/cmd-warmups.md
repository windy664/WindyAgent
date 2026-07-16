---
title: CMI 前置延迟 Warmup
tags: CMI, 命令, warmup
source: https://www.zrips.net/cmi/commands/warmups/
---
![](https://www.zrips.net/wp-content/uploads/2019/02/2019-02-21_16-41-06.gif)

Any command can have warmup timer added to it. This is useful if you want to prevent instant command usage, like teleporting away in a pvp. When setting CMI command warmup, use its full name, in example warmup for warp command should look like

```
cmi warp :7:false
```

Where “**cmi warp** ” will be command path, which includes space to avoid adding timer when using **/cmi warp** command without providing destination. **7** is a warmup duration, in this case its 7 seconds. **False** will indicate that player can’t move while warmup is in action, he can still turn his camera around, but he cant move around. This can be useful in same pvp situation as any hits from enemy players will make player to move and cancels warmup timer.

Staff members can bypass warmup timer if they have **cmi.command.[comandName].warmupbypass** permission node, where command name will be command used in that warmup setup. In this case its **warp.** 

Extra variable can be defined to show some particle effects while warmup is in action, to inform everyone around player that he is performing command on warmup. Usage should look something like

```
cmi warp :7:false:GlyphHead
```

Some setup examples:

```
WarmUps:  Enabled: true  InformOnNoMove: true  List:  - tp:7:false  - back:7:true  - cmi spawn:7:false  - cmi home :7:false  - spawn:7:false  - cmi home :7:false  - cmi warp :7:false:GlyphHead  - cmi warp spawn:7:false:GlyphHead  - rtp:7:false:GlyphHead
```

[![](https://www.zrips.net/wp-content/uploads/2019/02/2019-02-21_16-27-39.gif)](https://www.zrips.net/wp-content/uploads/2019/02/2019-02-21_16-27-39.gif)
