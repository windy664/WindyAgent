---
title: HologramClickEvent
tags: DecentHolograms, api
source: https://wiki.decentholograms.eu/api/events/hologramclickevent/
---
# HologramClickEvent

## Extends DecentHologramsEvent

This event is called whenever a player is interacting with a hologram by left or right-clicking it.  
The event is cancellable, allowing you to stop any further handling of it.

## Methods

| Method | Description |
| --- | --- |
| `getPlayer()` | Gets the Player that interacted with the Hologram. |
| `getHologram()` | Gets the Hologram that was interacted with. |
| `getHologramPage()` | Gets the HologramPage that was interacted with. |
| `getClickType()` | Gets the Click type (Whether it was left or right-click and whether the player was sneaking). |
| `getEntityId()` | Gets the ID of the Entity that was interacted with. |
