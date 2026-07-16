---
title: CMI 占位符
tags: CMI, 占位符, papi
source: https://www.zrips.net/cmi/placeholders/
---
Locale file can contain **PlaceholderAPI** placeholders to be shown with CMI messages. Only placeholders starting with **%cmi_** will be used by **PlaceHolderAPI**, but **all** of them can be used by CMI in most places.

To date placeholder list can always be found with **/cmi placeholders** command ingame

A placeholder inside another placeholder is possible too. For example,  **%cmi_equationint_{cmi_user_maxperm_cmi.command.sethome_1}+1****%** which will mean that you have 2 placeholders and the first one which will be processed is one which is enclosed with **{** and **}** which in this case returns max value by permission node. After that, it will process as a regular equation and will return its result. 

Some **custom/none-static** placeholders:

## Kit cooldowns

**%cmi_user_kitcd_[kitName]%** to show kit left cooldown time. Dash (`-`) will be shown if the kit can be used.

## Top Votes

**%cmi_votetop_[1-10]%** to show the top 10 places by vote count.

## Worth

**%cmi_worth_buy_[itemIdName:data]%** to show items buy price from worth file.  
**%cmi_worth_sell_[itemIdName:data]%** to show items sell price from worth file.

As an example **%cmi_worth_buy_diamondsword%**

## User Meta

**%cmi_user_meta_[key]%** to be used with custom placeholders. This is utilized by using `**/cmi usermeta [playerName] [add/remove/clear/list/increment] (key) (value)** **(-s)**` command, so by using **/cmi usermeta Zrips add testkey Coffee** and by utilizing **%cmi_user_meta_testkey%** across CMI or on other plugins, you will get a result as **Coffee. The amount** of custom metadata one player can have is unlimited but strongly recommended to keep it in reasonable amounts.  
The **increment** can be used to change the number value. For example. **/cmi usermeta Zrips increment counting +0.5** will result in 0.5, while performing a second time will result in 1. Negative numbers can be used to take out value.  
**%cmi_user_metaint_[key]%** which will try to output meta value as **integer value**.

## Jail

**%cmi_jail_reason_[jailName]_[cellid]%** to show the player jail reason if it was defined when jailing by particular jail and cell id. If there is more than 1 player inside one cell, then the most recent will be shown.  
**%cmi_jail_time_[jailName]_[cellid]%** to show time left in jail by particular jail and cell id. If there is more than 1 player inside one cell, then the most recent will be shown.  
**%cmi_jail_username_[jailName]_[cellid]%** to show player name in jail by particular jail and cell id. If there is more than 1 player inside one cell, then the most recent will be shown.

## Permission Check

**%cmi_user_maxperm_[corePerm]_[defaultValue]%** – gets max possible from permission. For example, if a player has these permissions:

-   **cmi.command.sethome.1** 
-   **cmi.command.sethome.3** 
-   **cmi.command.sethome.4** 

Then placeholder like **%cmi_user_maxperm_cmi.command.sethome_0%** will return **4**. While 0 is the default value in case the player doesn’t have any permission node like that with value.

If you have a placeholder with a default value higher than what the player has, then the default value will be used. In this case, if you used the  **%cmi_user_maxperm_cmi.command.sethome_6%** placeholder, it would return **6** instead of **4**. 

## Random values

**%cmi_random_[from]_[to]%** will return random value between provided ones. In example **%cmi_random_1_5%** can return values: **1 2 3 4 5  
**This can be used in some specialized commands to get random results when performing it

## BungeeCord

Placeholders oriented around the bungee network. For best results, you will need to set up information about your servers in the network. Check out the config.yml file under the **BungeeCord** section.

**%cmi_bungee_total_[serverName]%** – Returns max online number for that server  
**%cmi_bungee_current_[serverName]%** – Returns current player count  
**%cmi_bungee_motd_[serverName]%** – Returns motd of that server  
**%cmi_bungee_onlinestatus_[serverName]%** – Returns Online/Offline status of server

## Equation solver

**%cmi_equation_[equation]%** or **%cmi_equationint_[equation]%** will process mathematical equation and returns result. In the first case, it can be with a decimal like **2.4** and in the second case it returns a full number only like **2**.

Equations can be as simple as **2+2** and as complex as **sin(6)+pi*tan(5)/1.5**

## Statistics

**%cmi_user_stats_[mainStat(:optionalSubStat)]%** returns players statics by provided criteria. The format is the same as used for rankup system. For example **%cmi_user_stats_MonsterKills%** returns the total number of monster kills, while **%cmi_user_stats_MonsterKills:zombie%** will return the total zombie kills.  
This placeholder can return **3** types of values, _clean number_, like for mob kills, time, like total playtime and distance. 

## Full List of Placeholders

Static placeholders can be checked in-game with **/cmi placeholders** command, which will show examples with possible output values:

-   **%cmi_user_charges_left%** – Remaining count of spawner charges
-   **%cmi_user_charges_max%** – Maximum allowed amount of spawner charges
-   **%cmi_user_charges_time%** – Time until next spawner charge
-   **%cmi_user_charges_cooldown%** – Spawner charge cooldown
-   **%cmi_user_display_name%** – Formatted players display name
-   **%cmi_p_[playerName]_display_name%** – Formatted players display name
-   **%cmi_user_cleannickname%** – Players nick name if set or players name otherwise. No color codes
-   **%cmi_user_nickname%** – Players nick name if set or players name otherwise
-   **%cmi_user_name%** – Original players name
-   **%cmi_p_[playerName]_name%** – Original players name
-   **%cmi_user_uuid%** – Player uuid
-   **%cmi_user_uuid_[playerName]%** – Player uuid
-   **%cmi_user_deathloc%** – Players last death location
-   **%cmi_user_backloc%** – Players back location
-   **%cmi_user_cuffed%** – Identification if player is cuffed
-   **%cmi_p_[playerName]_cuffed%** – Identification if player is cuffed
-   **%cmi_user_muted%** – Identification if player is muted
-   **%cmi_p_[playerName]_muted%** – Identification if player is muted
-   **%cmi_user_inpvp%** – Identification if player is in pvp mode
-   **%cmi_user_god%** – Identification if player has god mode enabled
-   **%cmi_p_[playerName]_god%** – Identification if player has god mode enabled
-   **%cmi_user_sneaking%** – Identification if player is sneaking
-   **%cmi_user_mail_count%** – Amount of mails player have
-   **%cmi_user_warning_count%** – Amount of warnings player have
-   **%cmi_user_warning_points%** – Amount of warning points player have
-   **%cmi_user_afk%** – Afk state
-   **%cmi_user_afk_symbol%** – Afk symbol
-   **%cmi_user_afk_msg%** – Afk message if present
-   **%cmi_user_afk_for%** – Time for how long player is in afk mode
-   **%cmi_user_afk_in%** – Time when player enters auto afk mode
-   **%cmi_user_spy%** – Spy state
-   **%cmi_user_cmdspy%** – Command spy state
-   **%cmi_user_signspy%** – Sign spy state
-   **%cmi_user_joinedcounter%** – Indication if player is joined counter
-   **%cmi_user_banned%** – Indication if player is banned
-   **%cmi_p_[playerName]_banned%** – Indication if player is banned
-   **%cmi_user_maxhomes%** – Max amount of homes player can have
-   **%cmi_user_homeamount%** – Amount of homes player has
-   **%cmi_user_homelist%** – List of players homes
-   **%cmi_user_missingexp%** – Missing exp amount until next level
-   **%cmi_user_missingexpp%** – Missing exp in percentage until next level
-   **%cmi_user_exp%** – Current exp amount for current level
-   **%cmi_user_expp%** – Current exp in percentage for current level
-   **%cmi_user_totalexp%** – Total amount of exp player have
-   **%cmi_user_level%** – Players level
-   **%cmi_user_ping%** – Ping
-   **%cmi_user_gamemode%** – Game mode
-   **%cmi_user_op%** – OP state
-   **%cmi_user_pweather%** – Player weather
-   **%cmi_user_weather%** – Weather at players world
-   **%cmi_user_weatherduration%** – Weather duration at players world
-   **%cmi_user_canfly%** – Players ability to fly
-   **%cmi_user_flying%** – Is player currently flying
-   **%cmi_user_vanished_symbol%** – Vanish symbol
-   **%cmi_user_balance_formated%**
-   **%cmi_user_balance_formatted%** – Formatted users balance
-   **%cmi_user_balance%** – Clean users balance
-   **%cmi_user_prefix%** – Players prefix set by permission plugin
-   **%cmi_user_suffix%** – Players suffix set by permission plugin
-   **%cmi_user_group%** – Players main permission group name
-   **%cmi_user_nameplate_prefix%** – Players nameplate prefix
-   **%cmi_user_nameplate_suffix%** – Players nameplate suffix
-   **%cmi_user_tfly%** – Left temp fly amount in seconds
-   **%cmi_user_tfly_formated%**
-   **%cmi_user_tfly_formatted%** – Formatted temp fly amount
-   **%cmi_user_flightcharge%** – Flight charge amount
-   **%cmi_user_tgod%** – Time in seconds for temp god mode
-   **%cmi_user_tgod_formated%**
-   **%cmi_user_tgod_formatted%** – Formatted time for temp god mode
-   **%cmi_user_votecount%** – Amount of votes
-   **%cmi_user_dailyvotecount%** – Daily vote count
-   **%cmi_user_chatcolor%** – Player chatcolor
-   **%cmi_user_rank%**
-   **%cmi_user_rank_displayname%** – Current rank display name
-   **%cmi_user_rank_name%** – Current rank name
-   **%cmi_user_nextranks%** – List of next ranks
-   **%cmi_user_nextrankpercent%** – Percentage done for next rank
-   **%cmi_user_nextvalidranks%** – Rank list to which player can rank up to
-   **%cmi_user_canrankup%** – Returns true if player can rank up
-   **%cmi_user_country%** – Users country from geoip feature. Example: Germany
-   **%cmi_user_country_code%** – Users country code from geoip feature. Example: UK
-   **%cmi_user_city%** – Users city name from geoip feature
-   **%cmi_user_name_colorcode%** – Bukkit color code from nameplate command and -c: variable
-   **%cmi_user_glow_code%** – Bukkit color code from glow command.
-   **%cmi_user_glow_name%** – Color name from glow command. Example: Red
-   **%cmi_user_jailed%** – True or false if player is jailed
-   **%cmi_p_[playerName]_jailed%** – True or false if player is jailed
-   **%cmi_user_jailname%** – Jail name user currently is in. Example: Prison
-   **%cmi_p_[playerName]_jailname%** – Jail name user currently is in. Example: Prison
-   **%cmi_user_jailcell%** – Jail cell id user currently is in. Example: 1
-   **%cmi_p_[playerName]_jailcell%** – Jail cell id user currently is in. Example: 1
-   **%cmi_user_jailtime%** – Left jail time. Example: 1hour 5minutes
-   **%cmi_p_[playerName]_jailtime%** – Left jail time. Example: 1hour 5minutes
-   **%cmi_user_jailreason%** – Jailed reason
-   **%cmi_p_[playerName]_jailreason%** – Jailed reason
-   **%cmi_user_jailedby%** – Jailer name
-   **%cmi_p_[playerName]_jailedby%** – Jailer name
-   **%cmi_user_riding%** – Entity name player is riding
-   **%cmi_user_beingriddenby%** – Player name who is riding user
-   **%cmi_user_bungeeserver%** – Bungee server name
-   **%cmi_user_rt_cooldown%** – Remaining cooldown on random teleportation
-   **%cmi_user_rt_cooldown_[worldName]%** – Remaining cooldown on random teleportation by world
-   **%cmi_user_playtime_formatted%** – Formatted playtime
-   **%cmi_user_playtime_days%** – Playtime in days
-   **%cmi_user_playtime_dayst%** – Playtime in days with fraction
-   **%cmi_user_playtime_hours%** – Playtime in hours
-   **%cmi_user_playtime_hoursf%** – Total playtime in hours
-   **%cmi_user_playtime_hourst%** – Total playtime in hours with fraction
-   **%cmi_user_playtime_minutes%** – Playtime in minutes
-   **%cmi_user_playtime_minutest%** – Total playtime in minutes
-   **%cmi_user_playtime_seconds%** – Playtime in minutes
-   **%cmi_user_playtime_secondst%** – Total playtime in minutes
-   **%cmi_user_prewards_count%** – Number of claimable prewards
-   **%cmi_user_world_formatted%** – Current players world name by using custom identification
-   **%cmi_user_online%** – Returns player online status
-   **%cmi_p_[playerName]_online%** – Returns player online status
-   **%cmi_user_itemcount_[itemIdName(:data)]%** – Number of items in players inventory by provided material
-   **%cmi_user_maxperm_[corePerm]_[defaultValue]%** – Maximum value by provided permission node, and if it doesn’t exist, returns default value
-   **%cmi_user_toggle_[holograms|shiftedit|totembar|compass|tagsound|chatspy|cmdspy|signspy|msg|tp|pay|chatbubble|pmsound|rideme|pvenumbers|pvpnumbers|durability|receivepets|deathmessages|notarget|schest|staffchat|autoflightrecharge]%** – Outputs 1 or 0 if defined feature is toggled on or off
-   **%cmi_user_togglename_[holograms|shiftedit|totembar|compass|tagsound|chatspy|cmdspy|signspy|msg|tp|pay|chatbubble|pmsound|rideme|pvenumbers|pvpnumbers|durability|receivepets|deathmessages|notarget|schest|staffchat|autoflightrecharge]%** – Outputs formatted True or False if defined feature is toggled on or off
-   **%cmi_user_holo_page_[hologramName]%** – Outputs page number of hologram player is in at the moment
-   **%cmi_equation_[equation]%** – Result of provided mathematical equation with fraction
-   **%cmi_equationint_[equation]%** – Result of provided mathematical equation without fraction
-   **%cmi_color_[text]%** – Colorizes text, replace spaces with _, underscore can be added by doubling it like __
-   **%cmi_stripcolor_[text]%** – Removes color codes from text
-   **%cmi_iteminhand_displayname%** – Items in main hand display name or formatted material name
-   **%cmi_iteminhand_realname%** – Items in main hand formatted material name
-   **%cmi_iteminhand_type%** – Items in main hand material name
-   **%cmi_iteminhand_itemdata%** – Items in main hand data value. As of 1.13+ returns 0
-   **%cmi_iteminhand_amount%** – Amount of items in main hand
-   **%cmi_iteminhand_durability%** – Items in main hand left durability
-   **%cmi_iteminhand_maxdurability%** – Items in main hand max durability
-   **%cmi_iteminhand_custommodeldata%** – Items in main hand custom model data
-   **%cmi_iteminhand_worth%** – Returns total worth value of items in main hand
-   **%cmi_iteminhand_worth_one%** – Returns worth value of one item from main hand
-   **%cmi_iteminhand_worthc%** – Returns total worth value of items in main hand without formatting
-   **%cmi_iteminhand_worthc_one%** – Returns worth value of one item from main hand without formatting
-   **%cmi_material_realname_$1%** – Items in main hand formatted material name
-   **%cmi_schedule_nextin_[schedName]%** – Left time until next schedule trigger
-   **%cmi_schedule_endat_[schedName]%** – Left time until scheduler triggers last command
-   **%cmi_baltop_cname_[1-10]%** – Clean name of player from a provided place in a list
-   **%cmi_baltop_name_[1-10]%** – Name of player from a provided place in a list
-   **%cmi_baltop_money_[1-10]%** – Balance of player from a provided place in a list
-   **%cmi_baltop_shortmoney_[1-10]%** – Balance of player from a provided place in a list
-   **%cmi_playtimetop_cname_[1-10]%** – Clean name of player from a provided place in a list
-   **%cmi_playtimetop_name_[1-10]%** – Name of player from a provided place in a list
-   **%cmi_playtimetop_time_[1-10]%** – Playtime of player from a provided place in a list
-   **%cmi_votetop_[1-10]%** – Name of player from a provided place in a list
-   **%cmi_votetopcount_[1-10]%** – Vote count of player from a provided place in a list
-   **%cmi_worth_buy_[itemIdName(:data)]%** – Value of the item
-   **%cmi_worth_sell_[itemIdName(:data)]%** – Sell value of the item
-   **%cmi_worthc_buy_[itemIdName(:data)]%** – Value of the item without formatting
-   **%cmi_worthc_sell_[itemIdName(:data)]%** – Sell value of the item without formatting
-   **%cmi_bungee_total_[serverName]%** – Total allowed amount of players in defined server
-   **%cmi_bungee_current_[serverName]%** – Current amount of players in defined server
-   **%cmi_bungee_motd_[serverName]%** – Motd of defined server
-   **%cmi_bungee_onlinestatus_[serverName]%** – True/false of servers online status
-   **%cmi_tps_1%** – Ttps from last 1 second
-   **%cmi_tps_60%** – Tps from last 60 seconds
-   **%cmi_tps_300%** – Tps from last 5 minutes
-   **%cmi_tps_[range]_colored%** – Tps from defined range
-   **%cmi_tps_[range]%** – Tps from defined range
-   **%cmi_random_player_name%** – Returns random online player name
-   **%cmi_lastrandom_player_name%** – Returns last random online player name
-   **%cmi_random_[from]_[to]%** – Random number from defined range
-   **%cmi_lastrandom_[playerName]%** – Last random number assigned to player from random placeholder
-   **%cmi_user_rank_percent_[rankName]%** – Percentage of defined rank rankup progress
-   **%cmi_user_meta_[key]%** – Players metadate by defined key
-   **%cmi_user_metaint_[key]%** – Players metadate by defined key as number
-   **%cmi_chatmute_time%** – Provides left time for global chat mute
-   **%cmi_chatmute_reason%** – Provides reason for global chat mute
-   **%cmi_user_baltop%** – Player position in baltop
-   **%cmi_user_playtimetop%** – Player position in playtimetop
-   **%cmi_user_stats_[mainStat(:optionalSubStat)]%** – Player statistics
-   **%cmi_user_kitcd_[kitName]%** – Cooldown of defined kit
-   **%cmi_user_kit_available%** – Amount of kits you can claim
-   **%cmi_user_kit_available_[kitName]%** – True/false if kit is available
-   **%cmi_user_kit_hasaccess_[kitName]%** – True/false if user can use this kit
-   **%cmi_jail_time_[jailName]_[cellId]%** – Time of a jail cell
-   **%cmi_jail_username_[jailName]_[cellId]%** – Name of user who is jailed in particular cell
-   **%cmi_jail_reason_[jailName]_[cellId]%** – Reason of particular jail cell
-   **%cmi_weather_[worldName]%** – World weather
-   **%cmi_weatherduration_[worldName]%** – World weather duration
-   **%cmi_afk_count%** – Number of afk players
-   **%cmi_maintenance_state%** – Maintenance state
-   **%cmi_maintenance_message%** – Maintenance message
-   **%cmi_chat_range%** – Range for chat messages
-   **%cmi_server_uptime%** – How long server is running
-   **%cmi_server_uptime_seconds%** – How long server is running in seconds
-   **%cmi_server_worlds%** – List of all existing worlds on server in a list format
-   **%cmi_server_vanished%** – Online vanished player count
-   **%cmi_server_users%** – Recorder user count
-   **%player_world%** – Current players world name
-   **%cmi_player_world%** – Current players world name
-   **%player_x%** – Current players x position
-   **%cmi_player_x%** – Current players x position
-   **%player_y%** – Current players y position
-   **%cmi_player_y%** – Current players y position
-   **%player_z%** – Current players z position
-   **%cmi_player_z%** – Current players z position
-   **%player_biome%** – Current players biome name
-   **%cmi_player_biome%** – Current players biome name
-   **%vault_eco_balance_formatted%** – Formatted players balance. Deprecated
-   **%server_online%** – Online player amount
-   **%cmi_server_online%** – Online player count
-   **%server_max_players%** – Max allowed players
-   **%cmi_server_max_players%** – Max allowed players
-   **%server_online_[worldName]%** – Online amount in particular world. Don’t use _ in world name
-   **%cmi_server_online_[worldName]%** – Online amount in particular world. Don’t use _ in world name
-   **%server_unique_joins%** – Unique player joins to the server
-   **%cmi_server_unique_joins%** – Unique player joins to the server
-   **%onlineplayers_names%** – Formatted list of online players
-   **%cmi_onlineplayers_names%** – Formatted list of online players
-   **%onlineplayers_displaynames%** – Formatted list of online players by using their display names if possible
-   **%cmi_onlineplayers_displaynames%** – Formatted list of online players by using their display names if possible
-   **%server_time_[timeFormat]_[timeZone]%** – Time of server by defined format and timezone
-   **%cmi_server_time_[timeFormat]_[timeZone]%** – Time of server by defined format and timezone
-   **%server_time_[timeFormat]%** – Local server time
-   **%cmi_server_time_[timeFormat]%** – Local server time
-   **%world_time12_[worldName]%** – World time in 12 hour format
-   **%cmi_world_time12_[worldName]%** – World time in 12 hour format
-   **%world_time24_[worldName]%** – World time in 24 hour format
-   **%cmi_world_time24_[worldName]%** – World time in 24 hour format
