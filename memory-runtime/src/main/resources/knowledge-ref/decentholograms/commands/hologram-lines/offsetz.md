---
title: /dh lines offsetz
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/hologram-lines/offsetz/
---
# /dh lines offsetz

## Sets the Z offset of the line

The `offsetz` subcommand allows to change the offset of the line on the Z-axis.  
This is useful for situations where multiple lines are on the same height and you want to space them out vertically.

## Permissions

-   `dh.command.lines`
-   `dh.command.lines.offsetz`

See the [[Command Permissions|Permissions]] page for more details.

## Aliases

-   `offz`
-   `zoff`
-   `zoffset`

## Arguments

### `<hologram>`

Name of the Hologram to change a line's Z-offset in.

### `<page>`

Page in the Hologram to change a line's Z-offset in.

### `<line>`

Line number to change the Z-offset of.

### `<offset>`

Offset in blocks on the Z-axis of the line.  
The value can be between -2.5 and 2.5 inclusive with 0 being centered on the Hologram.
