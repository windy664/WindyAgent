---
title: CMI API
tags: CMI, api, 开发
source: https://www.zrips.net/cmi/api/
---
You can find the source code for CMI-API, and download it as a .jar file from [github.com/Zrips/CMI-API](https://github.com/Zrips/CMI-API)

Add to pom.yml

```
<dependency>  <groupId>com.github.Zrips</groupId>  <artifactId>CMI-API</artifactId>  <version>9.8.6.4</version>  <scope>provided</scope></dependency>
```

```
<repository>  <id>jitpack.io</id>  <url>https://jitpack.io</url></repository>
```

## Player/User Object

Most things related to the player can be accessed through users object

```
CMIUser user = CMIUser.getUser(player);
```

This can return NULL in some rare situations, so perform NPE check.

You can get the offline player object from this one by using

```
Player player = user.getPlayer();
```

This can be used to access some of the player’s data information even if he is offline. Keep in mind that this actually loads players’ information and is **highly not recommended** to be used to load hundreds or even thousands of players with this method as this will undoubtedly cause some strain on the server. You can tho update some of the player data but you will need to save it by (**ONLY if the player is offline**) using

```
 player.saveData();
```

or by using

```
CMI.getInstance().save(player);
```

This is more safe approach as the player object will be checked for appropriate save.

## Managers

Quite a few things can be accessed through appropriate managers. They all can be accessed through the basic method

```
CMI.getInstance().get[managerName]();
```

For example, to access the portal manager use

```
CMI.getInstance().getPortalManager();
```

## Worth

Get Items worth

```
// Item stack used to get worthItemStack item;WorthItem worth = CMI.getInstance().getWorthManager().getWorth(item);if (worth == null){  // Worthless item so we can return null or 0D, whatever is needed in your case  return null;}// Buy price used in exploit detectionDouble buyPrice  = worth.getBuyPrice();// Sell price defines actual worth of the fileDouble sellPrice = worth.getSellPrice();
```

Set items worth

```
// Item of which worth value we are gettingItemStack item;// Worth objectWorthItem worth = CMI.getInstance().getWorthManager().getWorth(item);if (worth == null){  // If its null then lets create new one  worth = new WorthItem(item);  // Adding worth to cache  CMI.getInstance().getWorthManager().addWorth(worth);}// Changing pricesworth.setBuyPrice(2D);worth.setSellPrice(1D);// Lets update values in save file CMI.getInstance().getWorthManager().updatePriceInFile(worth);
```

## Custom Events

**CMIAfkEnterEvent** – Fired when player enters AFK mode.

**CMIAfkKickEvent** – Fired when player should be kicked from server after being AFK.

**CMIAfkLeaveEvent** – Fired when players leaves AFK mode.

**CMIAnvilItemRenameEvent** – Fired on item rename in anvil.

**CMIAnvilItemRepairEvent** – Fired on item repair action with anvil.

**CMIArmorChangeEvent** – Fired when player changes items in armor slots.

**CMIAsyncPlayerTeleportEvent** – Fired when player is being teleported in Async mode. Can be canceled.

**CMIBackpackOpenEvent –** Fired on backpack open

**CMIChequeCreationEvent** – Fired before cheque is created

**CMIChequeUsageEvent** – Fired on cheque usage

**CMIChunkChangeEvent –** Fired when player changes chunk.

**CMIConfigReloadEvent** – Fired on config reload

**CMIEventCommandEvent** – Fired on event command

**CMIIpBanEvent** – Fired when IP gets ban.

**CMIIpUnBanEvent** – Fired when IP gets unban.

**CMIHologramClickEvent** – Fired on interaction with interactable hologram.

**CMIPlayerBanEvent** – Fired when player gets ban.

**CMIPlayerFakeEntityInteractEvent** – Fired when player interacts with fake entity, in most cases this will be hologram button interaction.

**CMIPlayerItemsSellEvent** – Fired on sell item action with command directly or when closing sell GUI

**CMIPlayerItemsSellEvent** – Fired on player item selling with worth command

CMIPlayerJailEvent – Fired on player jail

**CMIPlayerKickEvent** – Fired on player kick

**CMIPlayerNickNameChangeEvent** – Fired on player nickname change

**CMIPlayerOpenArmorStandEditorEvent** – Fired on player opening armor stand editor

**CMIPlayerSitEvent** – Fired on player sit action

**CMIPlayerTeleportRequestEvent –** Fired after player teleportation.

**CMIPlayerUnBanEvent** – Fired when player gets unban.

**CMIPlayerUnjailEvent** – Fired on player unjail

**CMIPlayerUnVanishEvent** – Fired when player exits vanish event.

**CMIPlayerVanishEvent** – Fired when player enters vanish event.

**CMIPlayerWarnEvent –** Fired when player gets a warning

**CMIPlayerWarpEvent –** Fired before warping player to target location

**CMIPortalCreateEvent** – Fired on nether portal creation event.

**CMIPortalUseEvent** – Fired on CMI portal use event.

**CMIPvEStartEventAsync –** Fired on pve start

**CMIPvEEndEventAsync –** Fired on pve end

**CMIPvPStartEventAsync** – Fired on pvpstart event.

**CMIPvPEndEventAsync**– Fired on pvpend event.

**CMISelectionEvent –** Fired on selection

**CMISelectionVisualizationEvent** – Fired before selection visualization starts showing. Can be canceled.

**CMIStaffMessageEvent** – Fired on staff message being sent

**CMIUserBalanceChangeEvent** – Fired on user balance change, if CMI Economy is used. Change types include: setBalance, Withdraw, Deposit.
