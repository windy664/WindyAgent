---
title: CMI TabList
tags: CMI, 扩展, tab
source: https://www.zrips.net/cmi/extra/tablist/
---
![](https://www.zrips.net/wp-content/uploads/2020/08/2018-04-26_18-07-34.gif)

Supports multiple lines.  
Optimized to be run in Async mode to avoid any loads on main server actions  
Fully supports PlaceHolderAPI for any variable you would want to use in tablist  
And supports CMI placeholders  
Footer and header separate customization  
Supports auto updates in particular intervals (in an example when using clock, can update every minute or so) or updates on the particular event, like player login to update player count or similar.  
Option to define custom player name format in list it self  
Will exclude vanished players from online player count. An example in picture.  
Supports custom formats for different players. In example staff members can see a different layout than regular ones and staff members can have different name format than regular ones.  
No limitations in how many grouped format’s you can have (by default you will get 2)  
Grouped format (permission) check can be disabled for better performance if needed  
Grouped format is being defined with cmi.tablist.[groupId] permission node, like cmi.tablist.1 same as with other numerical permission nodes, bigger numbers will have priority in case player has more than one.

![](https://www.zrips.net/wp-content/uploads/2020/08/tablist.png)

When setting tablist updates, keep it at the highest value your setup placeholders need. Avoid setting it to a lower than 5-second interval or even setting it to -1 and updating only on particular events.

To create an **animated** tablist, you will need to duplicate the sections. For example: 

```
    Header:      '1':      – '&f————————————'      – '&7Online &f%server_online%&7/&f%server_max_players%'      – '&f————————————'      '2':      – '&7————————————'      – '&7Online &f%server_online%&7/&f%server_max_players%'      – '&7————————————'      '3':      – '&8————————————'      – '&7Online &f%server_online%&7/&f%server_max_players%'      – '&8————————————'
```

This will change tablist in a row on each tablist update.  
You can have different amounts of headers and footers for the same tablist

## Default config

```
# To disable tablist handling visit Modules.yml fileTabList:  # Defines if we want to run tablist updater in async mode  # While enabled it can increase overall performance but some plugins can have same issues handling async placeholder requests  Async: true  UpdateTabListNames: true  # Enable or disable grouped format tablist's  # You can save some resources by disabling this if you are not interested in grouped format tablist feature  # If disabled then this will use only default format and will skip checking for custom one to save some resources if needed  GroupedEnabled: true  Updates:    # automatically updates tab list every x seconds for ALL online players    # If you are using static Footer and Header you can disable this by setting to -1    # Consider setting this to -1 if its completely enough to update on player events    AutoInterval: 60    OnJoin: true    OnLeave: true    afkStateChange: true    OnWorldChange: true    OnDeath: false    OnTeleport: false    OnNickChange: true  # When set to false we will not add header text to tablist  addTabListHeader: true  # When set to false we will not add footer text to tablist  addTabListFooter: true  # PlaceholderAPI supported for any custom variable you want to insert into this  # In addition CMI will handle some placeholders without PlaceHolderAPI  # Full list can be checked ingame with /cmi placeholders  GeneralFormat:    PlayerName: '[playerDisplayName]'    Header:      '1':      - '&f------------------------------------'      - '&7Welcome'      - '&7Online &f%server_online%&7/&f%server_max_players%'      - '&f------------------------------------'    Footer:      '1':      - '&f------------------------------------'      - '&7%player_world% &f%player_x%:&7%player_y%:&f%player_z%'      - '&7Money: &f%vault_eco_balance_formatted% &7Time: &f%server_time_HH:mm:ss%'      - '&f------------------------------------'  useGeneralName: false  # When this set to true, in case you dint defined PlayerName, Header or Footer in GroupFormat, then default one from GeneralFormat will be used  useGeneralHeader: false  useGeneralFooter: false  # Defines custom formats to be used for players.  # Any player which have cmi.tablist.[number] permission node will use defined grouped format in tablist  # Id should be a number and in case player has more than one, bigger number id will be used  # You can have as many groups as you want by increasing increment  GroupFormat:    '1':      PlayerName: '&2{&r[playerDisplayName]&2}'      Header:        '1':        - '&f------------------------------------'        - '&7Online &f%server_online%&7/&f%server_max_players%'        - '&f------------------------------------'      Footer:        '1':        - '&f------------------------------------'        - '&7%player_world% &f%player_x%:&7%player_y%:&f%player_z%'        - '&7Time: &f%server_time_HH:mm:ss%'        - '&f------------------------------------'    '2':      PlayerName: '&c[&r[playerDisplayName]&c]'      Header:        '1':        - '&f------------------------------------'        - '&7Online &f%server_online%&7/&f%server_max_players%'        - '&f------------------------------------'      Footer:        '1':        - '&f------------------------------------'        - '&7Money: &f%vault_eco_balance_formatted% &7Time: &f%server_time_HH:mm:ss%'        - '&f------------------------------------'
```
