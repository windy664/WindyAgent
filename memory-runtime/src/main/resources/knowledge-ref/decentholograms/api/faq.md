---
title: FAQ
tags: DecentHolograms, api
source: https://wiki.decentholograms.eu/api/faq/
---
# FAQ

## Common questions regarding the DecentHolograms API.

This page covers common questions about the API of DecentHolograms. Should you have a question that is not answered here, consider [joining our Discord Server](https://discord.decentsoftware.eu) and ask us there.

## How do I only show the Hologram to specific Players?

Showing a Hologram only to specific players through the API is a bit of effort.  
A Hologram is by default displayed to everyone and just removing the players from the list of viewers won't work just like that.

Instead, after [[DHAPI|getting your Hologram]] you have to change its default visibility state to `false`:

```
hologram.setDefaultVisibleState(false);
```

After that, set the player that should be able to see the Hologram:

```
hologram.setShowPlayer(player);
```

You can also remove players from the viewer list:

```
hologram.removeShowPlayer(player);
```

If you want to do the oposite - show the Hologram to everyone except certain Players - can you do that too using the `setHidePlayer` method:

```
hologram.setHidePlayer(player);
```

This method also has a counterpart to remove players from the list:

```
hologram.removeHidePlayer(player);
```

Important to note is, that the `setDefaultVisibleState` needs to be set to `true` for the `setHidePlayer` method to work. This is the default state for a Hologram, unless you changed it.
