---
title: /dh displays create
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/displays/create/
---
# /dh displays create

## Creates a new Display Entity

The `create` subcommand creates a new Text, item or Block entity.

## Permissions

-   `dh.command.displays`
-   `dh.command.displays.create`

See the [[Command Permissions|Permissions]] page for more details.

## Aliases

-   `new`
-   `c`

## Arguments

### `<type>`

The type for the display entity to have.  
Supported types are `TEXT`, `ITEM` and `BLOCK`.

Based on the provided type can the content only accept specific values.

### `<name>`

Name of the Display entity to have.

### `[content]`

Optional content for the Display Entity to start with.  
Depending on the type defined will this option accept either any text, or specific item/block values.
