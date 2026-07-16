---
title: CMI 电梯
tags: CMI, 扩展, 电梯
source: https://www.zrips.net/cmi/extra/elevator/
---
![](https://www.zrips.net/wp-content/uploads/2019/02/2019-02-21_12-07-34.gif)

Cmi Elevators is a simple way for creating signs which will teleport you up or down with as little as simply putting signs down and setting top line to predefined text. By default top sign line should look like

![](https://www.zrips.net/wp-content/uploads/2019/02/elevatorsign.png)

Next, place sight directly above it or below it with same top line and you have elevator. Right click will send you up and shift+right click will send you down if possible. 

Player should have **cmi.elevator.create** to be able to create elevator sign and **cmi.elevator.use** to be able to use one. 

In addition you can set second sign line to **[*]** (default value, can be changed in config file) which will make this sign static tyoe. This means that player will get teleported in front of sign instead of keeping players **x** and **z** coordinates.
