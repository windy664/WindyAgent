---
title: Hologram
tags: DecentHolograms, configuration
source: https://wiki.decentholograms.eu/configuration/hologram/
---
# Hologram

## Example of a Hologram file

Below is an example Hologram file that you could find in the `holograms` folder of DecentHolograms.  
While you can edit the file manually, it's recommended to [[Commands|use commands]] instead to avoid possible configuration errors.

Example

The following is an example Holograms file called `holograms.yml`.  
**Last updated:** 08th of April, 2026

hologram.yml

```
location: world:0.500:100.0:0.500
enabled: true
display-range: 64
update-range: 64
update-interval: 20
facing: 0.0
down-origin: false
pages:
- lines:
  - content: "Page Line"
    height: 0.3
    offsetX: 0.0
    offsetZ: 0.0
  actions:
    RIGHT:
    - MESSAGE:You pressed right
    SHIFT_RIGHT:
    - MESSAGE:You pressed shift-right
    LEFT:
    - MESSAGE:You pressed left
    SHIFT_LEFT:
    - MESSAGE:You pressed shift-left
```

## `location`

Contains the location of the hologram in the format `<world>:<x>:<y>:<z>`.

## `enabled`

Enables/Disables the hologram.

## `display-range`

Sets the max distance from where the hologram should still be displayed.  
Note that this option may be influenced by the client's and server's own entity render distances.

## `update-range`

Sets the max distance from where a hologram would still be updated.  
Leaving this range will result in parts such as Placeholders to no longer update until you re-enter the range.

## `update-interval`

Sets the interval (in ticks) that a hologram should update things such as Placeholders.

## `facing`

Sets the facing angle that an entity or (small) head should have.

| Value | Cardinal direction |
| --- | --- |
| `0.0` | South |
| `90.0`/`-270.0` | West |
| `180.0`/`-180.0` | North |
| `270.0`/`-90.0` | East |

## `down-origin`

When set to true will the [[Hologram|`location`]] of the hologram be based on its lowest point and not its highest.  
In addition will newly added lines cause the hologram to move up.

## `pages`

Contains the individual pages a hologram can have. Each option is further explained below.

### `pages[*].lines`

Contains the individual lines of the hologram for this page.

#### `pages[*].lines[*].content`

The content of the hologram line. See the [[Line Content]] page for available options.

#### `pages[*].lines[*].height`

Sets the distance that other lines should have towards this line.  
Depending on the line type will a different Config value be used as the default:

| Line Type | Config value | Default |
| --- | --- | --- |
| `<text>` | `defaults.height.text` | `0.3` |
| `#ICON` | `defaults.height.icon` | `0.6` |
| `#HEAD` | `defaults.height.head` | `0.75` |
| `#SMALLHEAD` | `defaults.height.smallhead` | `0.6` |

#### `pages[*].lines[*].offsetX`

Sets the offset that the line should have on the X-axis. The distance is relative to the Hologram's center and can go from `-2.5` inclusive to `2.5` inclusive.

#### `pages[*].lines[*].offsetZ`

Sets the offset that the line should have on the Z-axis. The distance is relative to the Hologram's center and can go from `-2.5` inclusive to `2.5` inclusive.

### `pages[*].actions`

Contains the individual [[Actions|Click Types]] that can be set to perform specific actions.

#### `pages[*].actions.RIGHT`

Contains the individual [[Actions]] that should be performed when right-clicking the Hologram. Actions are executed in order.

#### `pages[*].actions.SHIFT_RIGHT`

Contains the individual [[Actions]] that should be performed when right-clicking the Hologram while sneaking. Actions are executed in order.

#### `pages[*].actions.LEFT`

Contains the individual [[Actions]] that should be performed when left-clicking the Hologram. Actions are executed in order.

#### `pages[*].actions.SHIFT_LEFT`

Contains the individual [[Actions]] that should be performed when left-clicking the Hologram while sneaking. Actions are executed in order.
