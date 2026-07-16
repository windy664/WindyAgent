---
title: Custom Animations
tags: DecentHolograms, features
source: https://wiki.decentholograms.eu/features/animations/custom-animations/
---
# Custom Animations

## How to make and use custom Animations

DecentHolograms allows you to create Custom Animations to display in Hologram lines.

## Usage

Custom Animations can be used through the same format as [[Animations|built-in animations]]:

```
<#ANIM:<name>>Text</#ANIM>
```

Notes

-   `Text` is only used when the `{text}` placeholder is used inside the custom animation file.
-   `<name>` is the File name in the `animations` folder without the file extension.  
    Should the file name start with `animation_` will this part be removed from the name.

## File Structure

The following is an example file structure for a custom animation:

```
speed: 2
pause: 20
steps:
  - 'Example 1 {text}'
  - 'Example 2'
  - 'Example 3'
  - 'Example 4'
  - 'Example 5'
```

## Options

### `speed`

Sets the delay between steps in ticks with 20 ticks being 1 second.

### `pause`

Sets the delay in ticks for the animation to wait when reaching the end, before starting again.  
20 ticks are 1 second.

### `steps`

List of Strings that should be displayed in the Hologram line.  
Allows [[Line Content|Formatting and Color codes]] and also Placeholders, if `allow-placeholders-inside-animations` is enabled.

The `{text}` placeholder can also be used to display the text in-between the `<#ANIM></#ANIM>` tags.
