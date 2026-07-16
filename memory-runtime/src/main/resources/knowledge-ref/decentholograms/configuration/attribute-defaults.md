---
title: attribute-defaults.yml
tags: DecentHolograms, configuration
source: https://wiki.decentholograms.eu/configuration/attribute-defaults/
---
# attribute-defaults.yml

## The default attributes for Display Entities

The `attribute-defaults.yml` file is the default file used for Display Entities.  
Its values are being used for newly created Display Entities, or for ones that have been reset through [[/dh displays reset-attribute|`/dh displays reset-attribute`]].

Default attributes-defaults.yml

The following is the default `attributes-defaults.yml` file.

```
# ============================================================
# > Display Attribute Defaults Configuration
# ============================================================
#
# This file defines the default attribute values for each display type.
# When a new display is created, these defaults are automatically applied.
#
# Structure:
#   display:
#     DISPLAY_TYPE:
#       attribute-name:
#         enabled: true/false  # Whether this default value is used for new displays
#         value-type: "type"   # The attribute value type
#         value: ...           # The default value
#
# Notes:
#   - enabled: false means the attribute is not set (uses Minecraft defaults)
#   - Existing displays are not affected by changes to this file
#   - When an attribute value is reset, it reverts to the default value defined here
#
# ============================================================

display:
  # ============================================================
  # > TEXT DISPLAY DEFAULTS
  # ============================================================
  TEXT:
    # Billboard constraint - how the display faces the player
    # Options: CENTER, VERTICAL, HORIZONTAL, FIXED
    billboard:
      enabled: true
      value-type: "billboard_constraints"
      value: "CENTER"

    # Scale - size of the display (1 = normal size)
    scale:
      enabled: true
      value-type: "vector3f"
      value:
        x: 1
        y: 1
        z: 1

    # Translation - offset from the display's base location
    translation:
      enabled: true
      value-type: "vector3f"
      value:
        x: 0
        y: 0
        z: 0

    # Yaw rotation override (horizontal rotation in degrees)
    # Disabled by default
    # Range: -180 to 180
    yaw:
      enabled: false
      value-type: "float"
      value: 0

    # Pitch rotation override (vertical rotation in degrees)
    # Disabled by default
    # Range: -90 to 90
    pitch:
      enabled: false
      value-type: "float"
      value: 0

    # Brightness override (0-15 for both block and sky light)
    # Disabled by default - display uses natural lighting
    brightness:
      enabled: false
      value-type: "brightness"
      value:
        block-light: 15
        sky-light: 15

    # Shadow radius - size of the shadow circle (0 = no shadow, 32 = max shadow)
    shadow-radius:
      enabled: true
      value-type: "float"
      value: 0

    # Shadow strength - opacity of the shadow circle (1 = normal entity shadow, >7 = pitch black shadow)
    shadow-strength:
      enabled: true
      value-type: "float"
      value: 1

    # Text alignment - how text is aligned
    # Options: LEFT, CENTER, RIGHT
    alignment:
      enabled: true
      value-type: "text_alignment"
      value: "CENTER"

    # Background color - color behind the text (RGBA)
    # Default: black with 25% opacity (64/255)
    background-color:
      enabled: true
      value-type: "rgba"
      value:
        red: 0
        green: 0
        blue: 0
        alpha: 64

    # Line width - maximum width before text wraps (in pixels)
    line-width:
      enabled: true
      value-type: "integer"
      value: 300

    # Text opacity - transparency of the text (0-255)
    # 255 = fully opaque, 0 = fully transparent
    text-opacity:
      enabled: true
      value-type: "integer"
      value: 255

    # See-through - whether text is visible through blocks
    see-through:
      enabled: true
      value-type: "boolean"
      value: false

    # Text shadow - whether text has a shadow effect
    text-shadow:
      enabled: true
      value-type: "boolean"
      value: false

  # ============================================================
  # > ITEM DISPLAY DEFAULTS
  # ============================================================
  ITEM:
    # Billboard constraint - how the display faces the player
    # Options: CENTER, VERTICAL, HORIZONTAL, FIXED
    billboard:
      enabled: true
      value-type: "billboard_constraints"
      value: "FIXED"

    # Scale - size of the item (1 = normal size)
    scale:
      enabled: true
      value-type: "vector3f"
      value:
        x: 1
        y: 1
        z: 1

    # Translation - offset from the display's base location
    translation:
      enabled: true
      value-type: "vector3f"
      value:
        x: 0
        y: 0
        z: 0

    # Yaw rotation override (horizontal rotation in degrees)
    # Disabled by default
    # Range: -180 to 180
    yaw:
      enabled: false
      value-type: "float"
      value: 0

    # Pitch rotation override (vertical rotation in degrees)
    # Disabled by default
    # Range: -90 to 90
    pitch:
      enabled: false
      value-type: "float"
      value: 0

    # Brightness override (0-15 for both block and sky light)
    # Disabled by default - item uses natural lighting
    brightness:
      enabled: false
      value-type: "brightness"
      value:
        block-light: 15
        sky-light: 15

    # Shadow radius - size of the shadow circle (0 = no shadow, 32 = max shadow)
    shadow-radius:
      enabled: true
      value-type: "float"
      value: 0

    # Shadow strength - opacity of the shadow circle (1 = normal entity shadow, >7 = pitch black shadow)
    shadow-strength:
      enabled: true
      value-type: "float"
      value: 1

    # Glow color - color of the glowing effect
    # Disabled by default
    glow-color:
      enabled: false
      value-type: "rgba"
      value:
        red: 255
        green: 128
        blue: 255
        alpha: 255

    # Display type - how the item is rendered
    # Options: NONE, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND,
    #          FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
    #          HEAD, GUI, GROUND, FIXED
    display-type:
      enabled: true
      value-type: "item_display_type"
      value: "NONE"

    # Enchanted - whether the item has enchantment glint
    enchanted:
      enabled: true
      value-type: "boolean"
      value: false

    # Leather color - color for leather armor items
    # Disabled by default
    leather-color:
      enabled: false
      value-type: "rgba"
      value:
        red: 255
        green: 128
        blue: 255
        alpha: 255

    # Skull texture - texture for player heads
    # Disabled by default
    # Supports: player names, base64 textures, placeholders, HEADDATABASE_<id>
    skull-texture:
      enabled: false
      value-type: "string"
      value: "{player}"

  # ============================================================
  # > BLOCK DISPLAY DEFAULTS
  # ============================================================
  BLOCK:
    # Billboard constraint - how the display faces the player
    # Options: CENTER, VERTICAL, HORIZONTAL, FIXED
    billboard:
      enabled: true
      value-type: "billboard_constraints"
      value: "FIXED"

    # Scale - size of the block (1 = normal size)
    scale:
      enabled: true
      value-type: "vector3f"
      value:
        x: 1
        y: 1
        z: 1

    # Translation - offset from the display's base location
    translation:
      enabled: true
      value-type: "vector3f"
      value:
        x: 0
        y: 0
        z: 0

    # Yaw rotation override (horizontal rotation in degrees)
    # Disabled by default
    # Range: -180 to 180
    yaw:
      enabled: false
      value-type: "float"
      value: 0

    # Pitch rotation override (vertical rotation in degrees)
    # Disabled by default
    # Range: -90 to 90
    pitch:
      enabled: false
      value-type: "float"
      value: 0

    # Brightness override (0-15 for both block and sky light)
    # Disabled by default - block uses natural lighting
    brightness:
      enabled: false
      value-type: "brightness"
      value:
        block-light: 15
        sky-light: 15

    # Shadow radius - size of the shadow circle (0 = no shadow, 32 = max shadow)
    shadow-radius:
      enabled: true
      value-type: "float"
      value: 0

    # Shadow strength - opacity of the shadow circle (1 = normal entity shadow, >7 = pitch black shadow)
    shadow-strength:
      enabled: true
      value-type: "float"
      value: 1

    # Glow color - color of the glowing effect (RGBA)
    # Disabled by default
    glow-color:
      enabled: false
      value-type: "rgba"
      value:
        red: 255
        green: 128
        blue: 255
        alpha: 255
```

## `display`

This option contains the default attributes for the individual Display Entity Types.

Each entity type (`TEXT`, `ITEM` and `BLOCK`) has its own section with a list of options to set.  
Each option, no matter for what Entity type, does have the same core keys:

-   `enabled` - Whether this attribute is to be applied or not.
-   `value-type` - The type of value that gets applied. This influences the `value` option itself.
-   `value` - The actual value to apply. The value type determines what value(s) are allowed.

### `TEXT`

Contains default attribute values for Text Display Entities.

#### `billboard`

Sets the default billboard attribute of the Display Entity.  
The billboard defines on what angles the Display Entity should rotate to face the player, if it should rotate at all.

**Value Type:** `billboard_constraints`

**Available Value Options:**  
A single string having one of the following options.

-   `CENTER` - Rotates on both angles, always facing the player.
-   `VERTICAL` - Rotates on the vertical axis, facing the player horizontally.
-   `HORIZONTAL` - Rotates on the horizontal axis, facing the player vertically.
-   `FIXED` - Does not rotate and stays at its position.

#### `scale`

Sets the text scale. A value of 1 equals normal scale.

**Value Type:** `vector3f`

**Available Value Options:**  
An `x`, `y` and `z` value can be configured to set the Scale on the X, Y and Z-axis respectively.

#### `translation`

Sets the offset of the Display based on its base location.

**Value Type:** `vector3f`

**Available Value Options:**  
An `x`, `y` and `z` value can be configured to set the Display Entities offset on the X, Y and Z-axis respectively.

#### `yaw`

Sets the horizontal facing angle of the Display Entity. This only has a real use for Display Entities with either `FIXED` or `HORIZONTAL` billboard attribute applied.  
Allowed values are between -180 and 180 inclusive, with (-)180 being facing north.

**Value Type:** `float`

**Available Value Options:**  
A single float value between -180 and 180 inclusive can be set.

#### `pitch`

Sets the vertical facing angle of the Display Entity. This only has a real use for Display Entities with either `FIXED` or `VERTICAL` billboard attribute applied.  
Allowed values are between -90 and 90 inclusive, with 0 looking straight forward.

**Value Type:** `float`

**Available Value Options:**  
A single float value between -90 and 90 inclusive can be set.

#### `brightness`

Sets the Brightness for this Display Entity.  
The brighness for both block and sky light can be set to a value between 0 and 15 inclusive, with 15 being fully bright.

**Value Type:** `brightness`

**Available Value Options:**

-   `block-light` - Sets the Block light level.
-   `sky-light` - Sets the Sky light level.

#### `shadow-radius`

Sets the shadow size the Display Entity should have. Allowed values are between 0 and 32 inclusive, with 0 being no shadow and 32 being full size shadow.

**Value Type:** `float`

**Available Value Options:**  
A single float value between 0 and 32 inclusive can be set.

#### `shadow-strength`

Sets how opaque/transparent the Entity shadow should be. Allowed values are between 1 and 7 or higher with 1 being a normal entity shadow and anything above 7 being fully opaque.

**Value Type:** `float`

**Available Value Options:**  
A single float value of at least 1 or higher can be set.

#### `alignment`

Sets the text alignment.

**Value Type:** `text_alignment`

**Available Value Options:**  
A single String having one of the following options.

-   `LEFT` - Text is aligned to the left
-   `CENTER` - Text is aligned to the center
-   `RIGHT` - Text is aligned to the right

#### `background-color`

Sets the background color this Display Entity should have.

**Value Type:** `rgba`

**Available Value Options:**  
Four options `red`, `green`, `blue` and `alpha` setting the respective color and alpha value in a range from 0 to 255.

#### `line-width`

Sets the max width of a line to have. The value is in pixels.  
Any text line that is larger than the max width will be split up and put on new lines.

**Value Type:** `integer`

**Available Value Options:**  
A single, non-zero, positive integer value setting the width in pixels.

#### `text-opacity`

Sets the opacity of the text. Allowed values are between 0 and 255 inclusive with 0 being fully transparent and 255 being fully opaque.

**Value Type:** `integer`

**Available Value Options:**  
A single integer value between 0 and 255.

#### `see-through`

Sets whether the text can be seen through any non-transparent blocks.

**Value Type:** `boolean`

**Available Value Options:**  
A boolean (`true`/`false`) setting whether the text can be seen through non-transparent blocks.

#### `text-shadow`

Sets whether the text should have a text shadow.

**Value Type:** `boolean`

**Available Value Options:**  
A boolean (`true`/`false`) setting whether the text should have a text shadow.

### `ITEM`

This option contains the default attributes for Item Display Entities.

#### `billboard`

Sets the default billboard attribute of the Display Entity.  
The billboard defines on what angles the Display Entity should rotate to face the player, if it should rotate at all.

**Value Type:** `billboard_constraints`

**Available Value Options:**  
A single string having one of the following options.

-   `CENTER` - Rotates on both angles, always facing the player.
-   `VERTICAL` - Rotates on the vertical axis, facing the player horizontally.
-   `HORIZONTAL` - Rotates on the horizontal axis, facing the player vertically.
-   `FIXED` - Does not rotate and stays at its position.

#### `scale`

Sets the item scale. A value of 1 equals normal scale.

**Value Type:** `vector3f`

**Available Value Options:**  
An `x`, `y` and `z` value can be configured to set the Scale on the X, Y and Z-axis respectively.

#### `translation`

Sets the offset of the Display based on its base location.

**Value Type:** `vector3f`

**Available Value Options:**  
An `x`, `y` and `z` value can be configured to set the Display Entities offset on the X, Y and Z-axis respectively.

#### `yaw`

Sets the horizontal facing angle of the Display Entity. This only has a real use for Display Entities with either `FIXED` or `HORIZONTAL` billboard attribute applied.  
Allowed values are between -180 and 180 inclusive, with (-)180 being facing north.

**Value Type:** `float`

**Available Value Options:**  
A single float value between -180 and 180 inclusive can be set.

#### `pitch`

Sets the vertical facing angle of the Display Entity. This only has a real use for Display Entities with either `FIXED` or `VERTICAL` billboard attribute applied.  
Allowed values are between -90 and 90 inclusive, with 0 looking straight forward.

**Value Type:** `float`

**Available Value Options:**  
A single float value between -90 and 90 inclusive can be set.

#### `brightness`

Sets the Brightness for this Display Entity.  
The brighness for both block and sky light can be set to a value between 0 and 15 inclusive, with 15 being fully bright.

**Value Type:** `brightness`

**Available Value Options:**

-   `block-light` - Sets the Block light level.
-   `sky-light` - Sets the Sky light level.

#### `shadow-radius`

Sets the shadow size the Display Entity should have. Allowed values are between 0 and 32 inclusive, with 0 being no shadow and 32 being full size shadow.

**Value Type:** `float`

**Available Value Options:**  
A single float value between 0 and 32 inclusive can be set.

#### `shadow-strength`

Sets how opaque/transparent the Entity shadow should be. Allowed values are between 1 and 7 or higher with 1 being a normal entity shadow and anything above 7 being fully opaque.

**Value Type:** `float`

**Available Value Options:**  
A single float value of at least 1 or higher can be set.

#### `glow-color`

Sets the glow color the Item should have.

**Value Type:** `rgba`

**Available Value Options:**  
Four options `red`, `green`, `blue` and `alpha` setting the respective color and alpha value in a range from 0 to 255.

#### `display-type`

Sets how the Item should be displayed. An item may show differently depending on how it is displayed (i.e. shows different in GUI than on the ground).

**Value Type:** `item_display_type`

**Available Value Options:**  
A single string with one of the following options:

-   `NONE` - Default. Does not apply any specific display type.
-   `THIRD_PERSON_LEFT_HAND` - Displays the item as if held by another player in their left hand.
-   `THIRD_PERSON_RIGHT_HAND` - Displays the item as if held by another player in their right hand.
-   `FIRST_PERSON_LEFT_HAND` - Displays the item as if held by you in your left hand.
-   `FIRST_PERSON_RIGHT_HAND` - Displays the item as if held by you in your right hand.
-   `HEAD` - Displays the item as if put on your head.
-   `GUI` - Displays the item as if put in your inventory.
-   `GROUND` - Displays the item as if dropped on the ground.
-   `FIXED` - Displayes the item fixed.

#### `enchanted`

Sets whether the item should have the enchantment glint or not.

**Value Type:** `boolean`

**Available Value Options:**  
A boolean (`true`/`false`) to set whether the item should have an enchant glint or not.

#### `leather-color`

Sets the color to use for Leather armor or other dyable items.

**Value Type:** `rgba`

**Available Value Options:**  
Four options `red`, `green`, `blue` and `alpha` setting the respective color and alpha value in a range from 0 to 255.

#### `skull_texture`

Sets the Player Texture to display for a Player Head.

**Value Type:** `string`

**Available Value Options:**  
Allowed values are a Player name, Base64-encoded Texture value, a Placeholder resolving into either (Both own and PlaceholderAPI ones) or `HEADDATABASE_<id>` with `<id>` being a valid Head ID from [minecraft-heads.com](https://minecraft-heads.com) (Requires the HeadDatabase plugin).

### `BLOCK`

This option contains the default attributes for Item Display Entities.

#### `billboard`

Sets the default billboard attribute of the Display Entity.  
The billboard defines on what angles the Display Entity should rotate to face the player, if it should rotate at all.

**Value Type:** `billboard_constraints`

**Available Value Options:**  
A single string having one of the following options.

-   `CENTER` - Rotates on both angles, always facing the player.
-   `VERTICAL` - Rotates on the vertical axis, facing the player horizontally.
-   `HORIZONTAL` - Rotates on the horizontal axis, facing the player vertically.
-   `FIXED` - Does not rotate and stays at its position.

#### `scale`

Sets the block scale. A value of 1 equals normal scale.

**Value Type:** `vector3f`

**Available Value Options:**  
An `x`, `y` and `z` value can be configured to set the Scale on the X, Y and Z-axis respectively.

#### `translation`

Sets the offset of the Display based on its base location.

**Value Type:** `vector3f`

**Available Value Options:**  
An `x`, `y` and `z` value can be configured to set the Display Entities offset on the X, Y and Z-axis respectively.

#### `yaw`

Sets the horizontal facing angle of the Display Entity. This only has a real use for Display Entities with either `FIXED` or `HORIZONTAL` billboard attribute applied.  
Allowed values are between -180 and 180 inclusive, with (-)180 being facing north.

**Value Type:** `float`

**Available Value Options:**  
A single float value between -180 and 180 inclusive can be set.

#### `pitch`

Sets the vertical facing angle of the Display Entity. This only has a real use for Display Entities with either `FIXED` or `VERTICAL` billboard attribute applied.  
Allowed values are between -90 and 90 inclusive, with 0 looking straight forward.

**Value Type:** `float`

**Available Value Options:**  
A single float value between -90 and 90 inclusive can be set.

#### `brightness`

Sets the Brightness for this Display Entity.  
The brighness for both block and sky light can be set to a value between 0 and 15 inclusive, with 15 being fully bright.

**Value Type:** `brightness`

**Available Value Options:**

-   `block-light` - Sets the Block light level.
-   `sky-light` - Sets the Sky light level.

#### `shadow-radius`

Sets the shadow size the Display Entity should have. Allowed values are between 0 and 32 inclusive, with 0 being no shadow and 32 being full size shadow.

**Value Type:** `float`

**Available Value Options:**  
A single float value between 0 and 32 inclusive can be set.

#### `shadow-strength`

Sets how opaque/transparent the Entity shadow should be. Allowed values are between 1 and 7 or higher with 1 being a normal entity shadow and anything above 7 being fully opaque.

**Value Type:** `float`

**Available Value Options:**  
A single float value of at least 1 or higher can be set.

#### `glow-color`

Sets the glow color the Block should have.

**Value Type:** `rgba`

**Available Value Options:**  
Four options `red`, `green`, `blue` and `alpha` setting the respective color and alpha value in a range from 0 to 255.
