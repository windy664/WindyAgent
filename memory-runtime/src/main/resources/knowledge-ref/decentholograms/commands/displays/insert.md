---
title: /dh displays insertline
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/displays/insert/
---
# /dh displays insertline

## Inserts a new line into a Text Display Entity

The `insertline` subcommand inserts a new Text line into the Display Entity at the specified Line number.  
The current line at the specified number and any other following it are moved downwards.

This command only works for Text Display Entities!

## Permissions

-   `dh.command.displays`
-   `dh.command.displays.text.insertline`

See the [[Command Permissions|Permissions]] page for more details.

## Arguments

### `<name>`

Name of the Display entity to insert a line to.

### `<index>`

The Line number where to add a line into.

### `<text>`

Text line to insert.
