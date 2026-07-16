---
title: /dh displays set-facing
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/displays/set-facing/
---
# /dh displays set-facing

## Sets the facing angle of the Display Entity

The `set-facing` subcommand sets the facing angle of the Display entity.

## Permissions

-   `dh.command.displays`
-   `dh.command.displays.facing`

See the [[Command Permissions|Permissions]] page for more details.

## Aliases

-   `setfacing`
-   `facing`
-   `face`

## Arguments

### `<name>`

Name of the Display entity to change the facing angle of.

### `<yaw>`

The Yaw (rotation on the vertical axis) the entity should have.  
Allowed values are between -180 and 180 inclusive, with (-)180 being facing towards north.

### `[pitch]`

Optional Pitch (rotation on the horizontal axis) the entity should have.  
Allowed values are between -90 and 90 inclusive with 0 being facing straight forward.

If not set, defaults to the Entity's current Pitch.s
