---
title: /dh hologram setupdateinterval
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/hologram/setupdateinterval/
---
# /dh hologram setupdateinterval

## Sets the interval in which placeholders should update

The `setupdateinterval` subcommand can be used to set the frequency at which a Hologram should update dynamic data such as Placeholders.

## Permissions

-   `dh.command.hologram`
-   `dh.command.hologram.setupdateinterval`

See the [[Command Permissions|Permissions]] page for more details.

## Aliases

-   `updateinterval`

## Arguments

### `<hologram>`

Name of the Hologram to change the update interval of.

### `<interval>`

New interval in ticks with 20 ticks being 1 second.  
Can only be between 1 and 1200.
