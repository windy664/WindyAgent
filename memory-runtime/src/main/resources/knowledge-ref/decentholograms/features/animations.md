---
title: Animations
tags: DecentHolograms, features
source: https://wiki.decentholograms.eu/features/animations/
---
# Animations

## How to use built-in and custom Animations

This page only covers built-in animations. For custom ones, see [[Custom Animations|this page]].

DecentHolograms provides a collection of built-in animations to alter the appearance of your Hologram's text.

## Usage

Animations can be used through the following format:

```
<#ANIM:<name>>Text</#ANIM>
<#ANIM:<name>:<args>>Text</#ANIM>
```

## Placeholders

By default are placeholders inside animations not parsed. To allow this, enable the `allow-placeholders-inside-animations` option in the config.yml.  
Note that enabling this option may cause performance issues depending on the placeholders parsed.

## Available Animations

The following animations are available to use.

### `colors`

Goes through all available colors, similar to the `&u` custom color code which HolographicDisplays provides, and DecentHolograms supports.

FormatExample

```
<#ANIM:colors>Text</#ANIM>
```

```
<#ANIM:colors>Hello World</#ANIM>
```

### `wave`

Colors the entire text in the first color and has the second color slowly move through it.

Formatting codes can be used alongside or instead of color codes.

FormatExample

```
<#ANIM:wave:<color1>,<color2>>Text</#ANIM>
```

```
<#ANIM:wave:&f,&b&l>Hello World</#ANIM>
```

### `burn`

Colors the entire text in the first color and changes it from left to right to the second color.

Formatting codes can be used alongside or instead of color codes.

FormatExample

```
<#ANIM:burn:<color1>,<color2>>Text</#ANIM>
```

```
<#ANIM:burn:&f,&e&l>Hello World</#ANIM>
```

### `typewriter`

Writes out the provided text one character at a time.

FormatExample

```
<#ANIM:typewriter>Text</#ANIM>
```

```
<#ANIM:typewriter>Hello World</#ANIM>
```

### `scroll`

Scrolls through the provided text, only displaying a small part of it.  
The max length displayed is calculated from `<text length> / 3 * 2`.

FormatExample

```
<#ANIM:scroll>Text</#ANIM>
```

```
<#ANIM:scroll>Hello World</#ANIM>
```
