---
title: Compatability
tags: DecentHolograms, features
source: https://wiki.decentholograms.eu/features/compatability/
---
# Compatability

## Plugin support in DecentHolograms

DecentHolograms provides support for specific plugins. This can be support for basic features, or the ability to convert their holograms into DecentHolograms.

## Plugin Support

The following plugins are supported in DecentHolograms.

### PlaceholderAPI

DecentHolograms supports all placeholders from [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi).  
Placeholders can be used using their default `%identfier_values%` format (i.e. `%player_name%`) in Hologram Lines, actions and Animations.

Placeholders are parsed with the player viewing the Hologram.

Placeholders in animations

By default are placeholders in custom animations not parsed, to avoid performance issues.  
If you still want them to be parsed, enable `allow-placeholders-inside-animations` in the `config.yml` of DecentHolograms.

## Hologram Conversion

DecentHologram provides support for converting Holograms from the following Holograms.

Support may be limited.

### CMI

```
/dh convert CMI
```

**Default File Location:** `plugins/CMI/holograms.yml`

**Special Actions:**

-   `ICON:<item>` gets converted to `#ICON:<item>`
-   `!nextpage!` creates a new Hologram Page.
-   Holograms with names that start with `#<` or `#>` will be skipped

### FutureHolograms

```
/dh convert FutureHolograms
```

**Default File Location:** `plugins/FutureHolograms/holograms.yml`

**Special Actions:**

-   No notable actions outside basic Hologram conversion.

### GHolo

```
/dh convert GHolo
```

**Default File Location:** `plugins/GHolo/data/h.data`

**Special Actions:**

-   `ICON:<item>` gets converted to `#ICON:<item>`
-   `ENTITY:<entity>` gets converted to `#ENTITY:<entity>`
-   `[x]`, `[X]` and `[|]` get converted into Unicde characters
-   `[#rrggbb text #rrggbb]` gets converted to `<#rrggbb>text</#rrggbb>`

### Holograms

```
/dh convert Holograms
```

**Default File Location:** `plugins/Holograms/holograms.yml`

**Special Actions:**

-   `ITEM:<item>` gets converted to `#ICON:<item>`

### HolographicDisplays

```
/dh convert HolographicDisplays
```

**Default File Location:** `plugins/HolographicDisplays/database.yml`

**Special Actions:**

-   `ICON:<item>` gets converted to `#ICON:<item>`
-   `{papi: <placeholder>}` gets converted to `%<placeholder>%`
-   `{empty}` creates an empty line using a color code.
