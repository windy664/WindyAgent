---
title: config.yml
tags: DecentHolograms, configuration
source: https://wiki.decentholograms.eu/configuration/config/
---
# config.yml

## The config.yml, its structure and options

The `config.yml` contains various settings that can be changed to your liking.

Default config.yml

The following is the default config.yml that gets generated when the plugin starts for the first time.  
**Last updated:** 08th of April, 2026

config.yml

```
# # # # # # # # # # # # # # # # #
#
# Welcome to DecentHolograms config.yml.
#
# - We recommend you to visit our wiki for
# detailed explanation of all features and
# configuration options as this plugin has
# a ton of them.
#
# - You should also join our discord server for
# more information, support and updates. Our
# discord server is the main way of reporting
# bugs or ideas for possible improvements.
#
# - Web: www.decentholograms.eu
# - Wiki: wiki.decentholograms.eu
# - Discord: discord.decentsoftware.eu
# - GitHub: github.decentsoftware.eu
#
# # # # # # # # # #

defaults:
  # Default line
  text: Blank Line
  # Default Hologram display range in blocks.
  display-range: 48
  # Default Hologram update range in blocks.
  update-range: 48
  # Default Hologram update interval in ticks.
  update-interval: 20
  # Maximum amount of cached pattern processing results
  # Do not change if you do not know what it means
  # Increasing this number will result in higher memory usage
  # Range: 5 - 10000
  # Default: 500
  lru-cache-size: 500
  # Default heights of different hologram line types.
  height:
    text: 0.3
    icon: 0.6
    head: 0.75
    smallhead: 0.6
  # Default value of Down Origin
  down-origin: false

# Check for updates on plugin startup? [true/false]
update-checker: true

# Click cooldown in ticks
click-cooldown: 1

# Do we want to replace placeholders inside animation frames?
#
# WARNING! Setting this to true will have a negative impact
# on CPU usage, so if you don't NEED to use placeholders inside
# animation frames, keep this disabled.
allow-placeholders-inside-animations: false

# If true, the visibility of holograms will be updated when a player gets teleported or respawned.
#
# By default, this is disabled because it causes visual glitches where even if a player gets teleported
# by a fraction of a block, the holograms still disappear and reappear for them.
#
# Some clients (or client versions?) need this though, so if you are experiencing issues with holograms
# not showing up after a player gets teleported or respawned, you can enable this.
update-visibility-on-teleport: false

# Set this to true if you want holograms to appear at the player's eye level.
# When enabled, holograms will be positioned at the player's eye height when created or moved.
# When disabled, holograms will be positioned at the player's feet height when created or moved (default).
holograms-eye-level-positioning: false

#
# Sets the time in seconds that DecentHolograms should have when trying to fetch Skin data for players both
# by name and UUID.
# Value can go as low as 1 second and as high as 60 seconds.
#
# Note: Increasing this value can have a negative impact on your server's performance, especially on bad
#       internet connections.
#       You should only ever change this if you encounter the following warning:
#
#       Failed to fetch UUID for player <player>
#       Cause: Connect|Read timed out
player-skin-connection-timeout: 5

# # # # # # # # # # # # # # # # #
#
# Damage Display
#
# Temporary damage display that shows up on every successful hit
#
# # # # # # # # # #

damage-display:
  # Do you want this feature enabled? [true/false]
  enabled: false
  # Do you want to display damage for players? [true/false]
  players: true
  # Do you want to display damage for mobs? [true/false]
  mobs: true
  # Do you want to display 0 (or less) damage? [true/false]
  zero-damage: false
  # How long will the hologram stay in ticks
  duration: 40
  # Damage placeholder: {damage}
  # Animations and Placeholders DO work here
  appearance: '&c{damage}'
  # Appearance of the damage, if the damage is critical
  critical-appearance: '&4&lCrit!&4 {damage}'
  # Height offset
  height: 0

# # # # # # # # # # # # # # # # #
#
# Healing Display
#
# Temporary damage display that shows up on every health increase
#
# # # # # # # # # #

healing-display:
  # Do you want this feature enabled? [true/false]
  enabled: false
  # Do you want to display healing for players? [true/false]
  players: true
  # Do you want to display healing for mobs? [true/false]
  mobs: true
  # How long will the hologram stay in ticks
  duration: 40
  # Heal placeholder: {heal}
  # Animations and Placeholders DO work here
  appearance: '&a+ {heal}'
  # Height offset
  height: 0

# # # # # # # # # # # # # # # # #
#
# Custom text replacements
#
# Replace specific patterns in Holograms with custom replacements, similar to HolographicDisplays
#
# # # # # # # # # #

custom-replacements:
  '[x]': '█'
  '[X]': '█'
  '[/]': '▌'
  '[,]': '░'
  '[,,]': '▒'
  '[,,,]': '▓'
  '[p]': '•'
  '[P]': '•'
  '[|]': '⎹'
```

## `defaults`

The `defaults` option contains the default values to use for newly created Holograms, and Hologram Lines.

Note that already created Holograms/Hologram Lines won't be affected by changes made to these settings.

### `text`

This option sets the text that is displayed by default when creating a new Hologram, or a new Hologram Line, without providing an initial line content through the command.

### `display-range`

Sets how far the player can be away from the Hologram to still be visible.

Visibility is also influenced by the Client's and Server's entity render distance.

### `update-range`

Sets how far the player can be away from the Hologram to still be updated.

### `update-interval`

How frequent a Hologram should update its lines.  
The value is in ticks.

### `lru-cache-size`

Sets the max number of cached pattern processing results.  
If you do not know what this is or does, do not change it!

An increased number results in higher memory usage.  
Allows values between 5 and 10,000.

### `height`

Contains the default heights for the individual [[Line Content|Line Types]]

#### `text`

Sets the height for [[Line Content|Text Lines]].

#### `icon`

Sets the height for [[Line Content|`#ICON` Lines]].

#### `head`

Sets the height for [[Line Content|`#HEAD` Lines]].

#### `smallhead`

Sets the height for [[Line Content|`#SMALLHEAD` Lines]].

### `down-origin`

Sets whether the Hologram's anchor point should be at the bottom instead of the top.

## `update-checker`

Enables/Disables the update-checker of the Plugin.  
It is recommended to keep this enabled to be informed about plugin updates.

Update checks only happen during the plugin's enable-phase.

## `click-cooldown`

Sets a cooldown in ticks between interactions ("clicks") with the hologram.  
This can be used to reduce possible spam-clicking of holograms, by having the actions only execute every X ticks when clicked constantly.

## `allow-placeholders-inside-animations`

Enables/Disables the parsing of Placeholders (both own and PlaceholderAPI ones) inside [[Animations]].  
Enabling this option may result in a performance impact for your server.

## `update-visibility-on-teleport`

Enables/Disables whether Holograms should be updated (Disabled and Enabled again) whenever a player teleports.

This is a workaround for a bug existing in older Minecraft versions, where a player teleporting causes holograms to despawn and not re-appear.  
While this option "fixes" the issue, does it cause Holograms to flicker, even when players teleport short distances.

## `holograms-exe-level-positioning`

Enables/Disables whether Holograms should be positioned at the player's Eye level instead of their feet when created or moved to them.

## `player-skin-connection-timeout`

Sets how long DecentHolograms should have when fetching Skin data for players based on their name or UUID.  
Allowed values are between 1 and 60.

Increasing this timeout can cause a negative performance impact on your server, especially on bad connections.

Changing this option is only necessary if you encounter the following error frequently:

```
Failed to fetch UUID for player <player>
Cause: Connect|Read timed out
```

## `damage-display`

Contains settings for the Damage display feature of DecentHolograms.  
The Damage display Feature shows damage dealt to entitites/players as temporary holograms.

### `enabled`

Whether this feature should be enabled or not.

### `players`

Whether player damage should be displayed or not.

### `mobs`

Whether mob (non-player) damage should be displayed or not.

### `zero-damage`

Whether 0 (or less) value damage should be displayed or not.

### `duration`

How long (in ticks) the hologram should be shown.

### `appearance`

The displayed text for the damage Hologram.  
`{damage}` can be used to display the actual damage dealt. Additionally are Animations and other placeholders supported too.

### `critical-appearance`

The displayed text for critical damage dealt.  
`{damage}` can be used to display the actual damage dealt. Additionally are Animations and other placeholders supported too.

### `height`

The height offset the hologram should have.

## `healing-display`

Contains settings for the Healing display feature of DecentHolograms.  
The Healing display Feature shows health increases to entitites/players as temporary holograms.

### `enabled`

Whether this feature should be enabled or not.

### `players`

Whether player healing should be displayed or not.

### `mobs`

Whether mob (non-player) healing should be displayed or not.

### `duration`

How long (in ticks) the hologram should be shown.

### `appearance`

The displayed text for the healing Hologram.  
`{heal}` can be used to display the actual healing received. Additionally are Animations and other placeholders supported too.

### `height`

The height offset the hologram should have.

## `custom-replacement`

Contains a set of keys and values, with the keys being replaced by the value in any [[Line Content|Text Line]].

As an example, `'[x]': '█'` replaces any appearance of `[x]` with `█`.  
The default values are equal to what HolographicDisplays offers.
