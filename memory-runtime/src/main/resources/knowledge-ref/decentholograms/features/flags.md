---
title: Flags
tags: DecentHolograms, features
source: https://wiki.decentholograms.eu/features/flags/
---
# Flags

## Allows the disabling of specific features

DecentHolograms provides a feature called flags.  
Flags allow you to disable specific features either on the entire hologram, or per line.

Flags are added using either [[/dh hologram addflag|`/dh h addflag`]] or [[/dh lines addflag|`/dh l addflag`]], and are removed using either [[/dh hologram removeflag|`/dh h removeflag`]] or [[/dh lines removeflag|`/dh l removeflag`]] respectively.

## Available Flags

### `DISABLE_UPDATING`

Disables the updating of a Hologram or specific Hologram line.  
This flag can be seen as a combination of `DISABLE_PLACEHOLDERS` and `DISABLE_ANIMATIONS`.

### `DISABLE_PLACEHOLDERS`

Disables the parsing of Placeholders.

### `DISABLE_ANIMATIONS`

Disables the updating of animations.

### `DISABLE_ACTIONS`

Disables the handling of actions.  
This does **not** disable the HologramClickEvent!
