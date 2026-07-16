---
title: /dh hologram clone
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/hologram/clone/
---
# /dh hologram clone

## Clones a Hologram

The `clone` subcommand allows to (temporarely) clone a Hologram, including all its lines, actions, etc.

## Permissions

-   `dh.command.hologram`
-   `dh.command.hologram.clone`

See the [[Command Permissions|Permissions]] page for more details.

## Aliases

-   `copy`

## Arguments

### `<hologram>`

Name of the Hologram to copy.

### `<name>`

Name of the copy to have.

### `[temp]`

Optional boolean to mark the clone as temporary.  
A Temporary Hologram will not be saved to file and won't exist anymore after a server reload.

### `[-l:<world>:<x>:<y>:<z>]`

Optional Location argument to set the Holograms world, X, Y and Z position.

Warning

This argument is required when executing this subcommand from the console.

## Examples

```
/dh h clone test test_clone true
/dh h clone test test_clone -l:world:0:100:0
/dh h clone test test_clone true -l:world:0:100:0
```
