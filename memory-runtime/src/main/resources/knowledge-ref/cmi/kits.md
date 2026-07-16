---
title: CMI 礼包 Kits
tags: CMI, 礼包, kits
source: https://www.zrips.net/cmi/kits/
---
![](https://www.zrips.net/wp-content/uploads/2019/02/KitEditor1.png)

![](https://www.zrips.net/wp-content/uploads/2019/02/KitEditor2.png)

## **What are kits used for?**

-   **Enable/Disable** a kit.
-   Add kit items. (Bottom row is the hotbar).
-   Add armor slot items.
-   Add offhand items.
-   Directly **clone** your inventory to the kit.

Items containing **{USERNAME} {DISPLAYNAME} {KITNAME} {WORLDNAME} {RANDOMPLAYER}** **{DATE}** variables in display name or lore will be replaced automatically to appropriate values when player gets kit.

## **How can you create a kit?**

1.  First, use the command and **skip to step 5**:  
    
    Code (Text):
    
    /cmi kiteditor new [kitname]
    
2.  If you want to go through the chat, use:  
    
    Code (Text):
    
    /cmi kiteditor
    
3.  Click the green “+” icon to create a new kit.
4.  You will now be prompted to define the kit’s name (Don’t worry you can change this later).
5.  After typing out the kit’s name you will now be able to modify the contents of the kit as seen in the below category.

## **How can you edit the advanced settings of a kit?**

To modify the settings, click the crafting table labeled “settings”. This will open up another GUI in which you can edit:​

-   Edit the **cool-down** in the form of a “**Delay**”.
-   Kit **name** (both locally and in config) and **description** editing.
-   Kit **groups** and **weighting**.
    -   A player will get access to the **highest-weighted kit in a group of kits**.
    -   **NOTE**: Cmi will automatically gather the weight with the user’s permission. All you have to do is give the user access to the permission for the kit that you would like them to have access to. CMI will automatically determine which kit they can use, going through and comparing the kit weighting to the permission node.
-   The item that is displayed in the GUI when the kit is available/unavailable.
-   **Exp/Money** cost of a kit.
-   **Slot** of a kit in the /kit GUI.
-   **Command** runs when the kit is used.
-   **Conditions** for the kit to be used. This represents a list of permission nodes the player needs to have to get this particular kit. 

## **Commonly asked questions:**

-   Why can’t users see the kit?
    -   Check that you have given them the correct permission as seen below.
    -   Make sure the kit is enabled, on the top right-hand side is there a green stained glass pane?
-   How can I open the kiteditor?
    -   Use “/cmi kiteditor” and click the kit you are looking to edit.
-   How can I setup a group with weighing?
    -   First, open the settings tab.
    -   Click “Kit group:”
    -   You will then be prompted to write the kit group in chat.
    -   Once you have done this the kit will be in the group you specified.
    -   To add additional kits to this group redo this process.
    -   To change the weight of the kit, search for weight in the settings GUI (information about how weightings work is in section 3)
-   How can I sort kits?
    -   The first option is to set the kit slot, this will force the kit to appear in a defined UI slot if possible, not an ideal situation if the kit amount changes.
    -   A better option is to use the kit weight option which will sort from lightest to heaviest kit without leaving empty slots

## Commands, Permissions, and Placeholders

**Commands:**

```
> cmi checkcommand kit

 /cmi kitusagereset (kitName) (playerName)
 /cmi kitcdreset (kitName) (playerName/all)
 /cmi kiteditor
 /cmi kit [kitName] (playerName) (-s)
>
```

**Permissions:**

```
> cmi checkperm kit

 cmi.command.kitusagereset.others - Reset kit usage counter
 cmi.kit.bypass.time - Allows to bypass kit time limitations
 cmi.command.kit.others - Gives predefined kit.
 cmi.kit.bypass.onetimeuse - Allows to bypass kit onetimeuse limitations
 cmi.kit.[kitname].preview - Allows to preview kit without having access to kit
 cmi.command.kit - Gives predefined kit.
 cmi.command.kitcdreset.others - Reset kit timer
 cmi.kit.bypass.money - Allows to bypass kit money requirement
 cmi.command.kiteditor.admin - Allows to define more dangerous aspects of kits, like commands
 cmi.command.kitusagereset - Reset kit usage counter
 cmi.command.kitcdreset - Reset kit timer
 cmi.kit.bypass.exp - Allows to bypass kit exp requirement
 cmi.command.kiteditor - Kit editor.
 cmi.kit.[kitname] - Allows to use kit
>
```

**Placeholders**

```
> cmi placeholders

 %cmi_user_kitcd_[kitName]%
 %cmi_user_kit_available%
 %cmi_user_kit_available_[kitName]%
 %cmi_user_kit_hasaccess_[kitName]%
>
```
