---
title: CMI 交互命令
tags: CMI, 命令
source: https://www.zrips.net/cmi/commands/interactive-commands/
---
Interactive command feature allows to create blocks or entities which will perform defined commands on interaction with them.

How to create one:  
1. Perform command **/cmi ic new [name]**, like **/cmi ic new healer** and you will get window like this in chat

[![](https://www.zrips.net/wp-content/uploads/2019/02/ic1.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/ic1.jpg)

2. Click on one of **+** signs to assign block (first one) or entity (second) you are looking at to this particular interactive command.

[![](https://www.zrips.net/wp-content/uploads/2019/02/ic2.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/ic2.jpg)

3. This will result in confirmation message and after that you can add more blocks or entities to this interactive command or move on by adding commands it self.  
4. To add command simply click on **!** and you can add new commands in presented list.  
5. Click on + sign to add new command, which will wait until you enter new command into chat window. Don’t start with / as regular commands, simply write basic command. **[playerName]** variable can be used to include players who interacted name.   
6. Enter new command into chat and press enter

[![](https://www.zrips.net/wp-content/uploads/2019/02/ic3.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/ic3.jpg)

After this you are ready to right click on block or entity and expect some results.   
You can add as many blocks or entities as you want.  
You can add as many commands as you want and specialised commands can be utilised. Read wiki about them.

Interactive command can be set to Public mode. This means that player with **cmi.interactivesign** permission node can create sign with the line as **[ic:[isName]]** to automatically add sign into IC list.  
Sign text will be replaced automatically to predefined for that particular IC. Ingame editor is included to manage them.

For “localized” commands run **asConsole!**, such as a PvP countdown, a World Area must be defined so the the Server “knows” where to run the command. Here is an Example that defines a counter where the Player clicks on the Interactive Command Block/Sign as defined in the Commands Section of the IC.  

In case you have created public IC. In example IC with name **warp** and with command like **asConsole! cmi warp $1 [playerName]** then players who has permission to create public IC signs can create one with top line as **[ic:warp]** and second line as in example **shop** then after clicking this sign you will get teleported to **shop** warp location. Commands will accept variables which are written on a signs. Only first line of the sign will get ignored and you can put as many variables as you can fit on a sign. This allows to have one public IC which can perform multiple different commands by simply changing lines.
