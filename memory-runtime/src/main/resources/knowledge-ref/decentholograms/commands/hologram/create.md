---
title: /dh hologram create
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/hologram/create/
---
# /dh hologram create

## Creates a new Hologram

The `create` subcommand allows you to create a new Hologram.

## Permissions

-   `dh.command.hologram`
-   `dh.command.hologram.create`

See the [[Command Permissions|Permissions]] page for more details.

## Aliases

-   `c`

## Arguments

### `<name>`

Name of the Hologram to have.

### `[-l:<world>:<x>:<y>:<z>]`

Optional Location argument to specify a world and the X, Y and Z location of the Hologram.

Warning

This argument is required when the subcommand is executed from the server console.

### `[--center]`

Optional argument to have the Hologram centered on the Block

### `[content]`

Optional argument to set the text that is displayed when creating the hologram.  
Defaults to the `defaults.text` config value when not specified.

Note

Any content that does not match `-l:<world>:<x>:<y>:<z>` or `--center` will be seen as Line content.

## Examples

```
/dh h create test
/dh h create test -l:world:0:100:0
/dh h create test First Line
/dh h create test -l:world:0:100:0 First Line
/dh h create test --center
```
