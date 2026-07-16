---
title: Events
tags: DecentHolograms, api
source: https://wiki.decentholograms.eu/api/events/
---
# Events

## Custom Events offered by DecentHolograms

DecentHolograms provides a collection of events that a plugin can listen to.  
These events are primarely focused around Holograms.

## Events

-   ### [[DecentHologramsEvent]]
    
    * * *
    
    The basic event that all others are extending from.
    
-   ### [[DecentHologramsReloadEvent]]
    
    * * *
    
    Called whenever the [[/dh reload|`/dh reload`]] is executed.
    
-   ### [[HologramClickEvent]]
    
    * * *
    
    Called whenever a player interacts with (clicks) a Hologram.
    
-   ### [[HologramDisableEvent]]
    
    * * *
    
    Called whenever a Hologram is disabled via its `disable(DisableCause)` method.
    
-   ### [[HologramEnableEvent]]
    
    * * *
    
    Called whenever a Hologram is enabled via its `enable()` method.
    
-   ### [[HologramRegisterEvent]]
    
    * * *
    
    Called whenever a Hologram gets registered via `HologramManager#register(Hologram)`.
    
-   ### [[HologramUnregisterEvent]]
    
    * * *
    
    Called whenever a Hologram gets unregistered via `HologramManager#unregister(Hologram)`.
