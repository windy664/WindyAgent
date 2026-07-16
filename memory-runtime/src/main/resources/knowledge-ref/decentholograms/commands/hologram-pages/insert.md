---
title: /dh pages insert
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/hologram-pages/insert/
---
# /dh pages insert

## Insert a Page into a Hologram

The `insert` subcommand allows to insert a page into a Hologram at the position of the specified page.  
The specified page and any after it are moved back in the list.

## Permissions

-   `dh.command.pages`
-   `dh.command.pages.insert`

See the [[Command Permissions|Permissions]] page for more details.

## Arguments

### `<hologram>`

Name of the Hologram to insert a new page into.

### `<page>`

Page in the Hologram to insert a new page at.

### `[content]`

Optional content to have for the page's first line.  
If left empty uses the `defaults.text` value from the config.yml.
