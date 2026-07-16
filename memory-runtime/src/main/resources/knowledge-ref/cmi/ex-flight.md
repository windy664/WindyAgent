---
title: CMI 飞行计费
tags: CMI, 扩展, 飞行
source: https://www.zrips.net/cmi/extra/flight-charge/
---
![](https://www.zrips.net/wp-content/uploads/2019/02/2018-03-22_15-02-35.gif)

Option to have limited flight mode. Yes, we have **tfly**, but that one is based only on time. This one is a lot more powerful and more fun to use.

This feature is **disabled by default** as we have to perform couple extra checks on the player move and if you are not interested in using it, then better avoid doing those checks at all.

Players can **buy fly charges** by using exp or money (possible item thingy in future updates).

By default, the player can buy 1000 charges. Which will allow for the player to fly 1000 blocks.

In case a player hovers in one place, then 1 charge every second will be taken to discourage that behavior.

To prevent no damage on falling down from huge heights, as vanilla behavior prevents any fall damage if you can toggle fly mode, players can suffer one of two or both feedbacks:

The player will lose flight charge depending on the fallen height x 2. For example, if fallen down from 7 blocks height, then (7 – 3) * 2, as you can only suffer damage if you fall from higher than 3 blocks, so the result will be 8 lost charges. Configurable.

The second result can be actual player damage. Simulates the same damage as if you would not have fly mode. By default, this only applies when jumping from a cliff, but will not apply when toggling off fly mode while flying. Configurable.

By default, even if you would jump from 200 height, you will not die, but you will have 1 heart left. Again, configurable.​

There is a built-in GUI for recharging, so type in **/recharge** and you are good to go.

By default, by spending one exp point you will get one charge point and the same goes for money. This can be adjusted in the config file.

As we have a Bossbar engine, players will see a nice boss bar indicating how much of a charge they have left

This includes your current left charge, how much you lost/gained in the last check, and the maximum possible amount.

A graded bar will update with the amount and will change color depending on how much of the charge you have left. >50% green, >30% yellow, <30% red.  
**/cmi flightcharge (add/take/set/show/expcharge/moneycharge/recharge) (playerName) (amount)** can be used if needed, but for basic recharge you can simply use **/recharge** command.

To check left charge, an alias command can be used for simplicity **/fcharge**  
Players with **cmi.command.flightcharge.admin** permission node can **add/take/set**  

Currently, if the player uses all charges and he is in mid-air, then he will get teleported back to the ground, to avoid “accidents”
