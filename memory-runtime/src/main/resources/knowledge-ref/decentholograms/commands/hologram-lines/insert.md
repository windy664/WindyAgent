---
title: /dh lines insert
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/hologram-lines/insert/
---
# /dh lines insert

## Inserts a line before others.

The `insert` subcommand inserts a new line at the location of the provided line index.  
The provided line and any line after are moved downwards.

## Permissions

-   `dh.command.lines`
-   `dh.command.lines.insert`

See the [[Command Permissions|Permissions]] page for more details.

## Arguments

### `<hologram>`

Name of the Hologram to insert a line into.

### `<page>`

Page in the Hologram to insert a line into.

### `<line>`

Line number to insert the new line into.

### `[content]`

Optional Line content to use.  
If not set uses the `defaults.text` value from the config.yml.
