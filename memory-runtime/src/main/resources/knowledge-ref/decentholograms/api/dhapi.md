---
title: DHAPI
tags: DecentHolograms, api
source: https://wiki.decentholograms.eu/api/dhapi/
---
# DHAPI

## Explanation of the various methods inside the DHAPI class

The `DHAPI` is a class introduced in version 2.0.12 of DecentHolograms. It purpose is to be the main usage of the DecentHolograms API as it offers methods for creating, editing and deleting Holograms, Hologram Pages and Hologram Lines, without having to deal with those parts directly.

## Creating a Hologram

The `DHAPI` class offers `createHologram` methods to create a Hologram with or without peristance and with or without pre-set lines.

### Basic `createHologram` method

The most basic version has the following Structure:

```
DHAPI.createHologram(String, Location);
```

The first argument is the name of the Hologram and the second the location (Needs to be a `org.bukkit.Location`) of where the Hologram should be.

Using this method creates a Hologram with the default text from the Config as its first line.  
It is also worth noting that this Hologram is **not persistent**, meaning that it won't be saved to a YAML file and won't remain after a reload/restart of the server or plugin.

* * *

### Creating persistent Holograms

To make the Hologram persistent, use the `createHologram` method with the additional boolean argument:

```
DHAPI.createHologram(String, Location, boolean);
```

Setting the boolean to `true` will tell DecentHolograms to save the Hologram to a YAML file inside its `holograms` folder. This Hologram would then be loaded automatically whenever the plugin is enabled.

Important!

Calling `createHologram` with a name of an already existing Hologram will cause an Exception to be raised.  
To avoid this, check first if a Hologram with that name has already been loaded.

Example:

```
public void createHologram(String name, Location location){
    if(DHAPI.getHologram(name) != null)
        return;

    DHAPI.createHologram(name, location);
}
```

* * *

### Add initial Lines

The `createHologram` method also offers a variant to set a List of Lines that the newly created Hologram should have by default, overriding the default text set in the config.  
Here is the method Structure:

```
DHAPI.createHologram(String, Location, List<String>);
```

Of course, as with [[DHAPI|the first example]] would this one not create a persistent Hologram. However, there is of course a method available to allow this too:

```
DHAPI.createHologram(String, Location, boolean, List<String>);
```

Just like with the [[DHAPI|second example]], setting the boolean to `true` will make the Hologram persistent.

## Getting a Hologram

To edit a Hologram with this class, one has to first obtain a `Hologram` instance. This can easily be done by using the `getHologram` method:

```
DHAPI.getHologram(String);
```

This method returns `null` should a Hologram of the provided name not exist.

## Getting a Hologram Page

DecentHologram allows a Hologram to have multiple pages with different lines. As such is there a method available to obtain a `HologramPage` instance from a Hologram itself.

The method itself is relatively straight forward:

```
DHAPI.getHologramPage(Hologram, int);
```

The `int` argument is the 0-indexed index of the page in the Hologram.

Important Notes

-   The method may return `null`, should the provided index be less than 0 or above, or equal to, the size of pages.
-   Unless [[DHAPI|manually removed]], a Hologram instance always has a `HologramPage` instance at index 0.

## Getting a Hologram Line

Due to a Hologram being able to have multiple pages does the `getHologramLine` method provided by the `DHAPI` class require a `HologramPage` instance.  
See [[DHAPI|the above section]] on how to obtain such a `HologramPage` instance.

Once you obtained a valid `HologramPage` instance can you just use the `getHologramLine` method:

```
DHAPI.getHologramLine(HologramPage, int);
```

The `int` argument is the 0-indexed line position in the `HologramPage`.  
Alternatively can you directly call `getLine` on the retrieved `HologramPage` instance.

Please mind the notes given in the previous section about the HologramPage's availability. Always do proper null-checks first before using a HologramPage.

## Editing Hologram Lines

There are various methods available to edit the lines of a Hologram, be it adding new ones, editing existing ones or removing existing ones.

### Adding new Lines

To add new lines to a Hologram, use the `addHologramLine` method. The most basic method looks like this:

```
DHAPI.addHologramLine(Hologram, String);
```

This would add a new Line to the first page of a Hologram.  
If your Hologram has multiple pages and you want to add a line to a page other than the first, you can use the following variant of the method:

```
DHAPI.addHologramLine(Hologram, int, String);
```

The `int` argument would be the index of the page to edit. Note that the numbers are 0-indexed, meaning the first page is 0, the second is 1 and so on.

Tip

You can replace the String argument with either a `org.bukkit.Material` or `org.bukkit.inventory.ItemStack` instance.  
This will create a [[Line Content|floating item]] using the provided Material/ItemStack as its value.

Do also note that in case of an ItemStack, only certain NBT values may get added. Material does not hold any NBT values.

* * *

### Setting Lines

You can also set a specific line to a new value or even set the lines for an entire page of a Hologram.

To set a specific line, you can use the following method:

```
DHAPI.setHologramLine(Hologram, int, String);
```

The `int` argument is the line index on the Hologram's first page. It is 0-indexed, meaning 0 is the first line, 1 is the second and so on.

If you want to edit the Line of a page other than the first, use this method instead:

```
DHAPI.setHologramLine(Hologram, int, int, String);
```

The first `int` argument would be the index of the page (Also 0-indexed) while the second would be the index of the line at that position.

Tip

You can replace the String argument with either a `org.bukkit.Material` or `org.bukkit.inventory.ItemStack` instance.  
This will create a [[Line Content|floating item]] using the provided Material/ItemStack as its value.

Do also note that in case of an ItemStack, only certain NBT values may get added. Material does not hold any NBT values.

Finally can you also edit the lines of an entire page. To do so, simply use the `setHologramLines` (Note the `s` at the end) method:

```
DHAPI.setHologramLines(Hologram, List<String>);
```

Just like with the examples before does this method change the lines of the first page of the Hologram. And just like the other methods is there a variant that allows you to edit the lines of a specific page:

```
DHAPI.setHologramLines(Hologram, int, List<String>)
```

* * *

### Insert Lines

Lines can be inserted into a page, making them apear before the line you specified.

The most basic method looks like this:

```
DHAPI.insertHologramLine(Hologram, int, String);
```

This would insert a line before the provided Line index number (0-indexed) on the first page of the Hologram.

Just like the other examples before does this method offer a version to insert a line on a specific page that may not be the first:

```
DHAPI.insertHologramLine(Hologram, int, int, String);
```

The first `int` argument is the page-index (Also 0-indexed) and the second the line index on that page.

Tip

You can replace the String argument with either a `org.bukkit.Material` or `org.bukkit.inventory.ItemStack` instance.  
This will create a [[Line Content|floating item]] using the provided Material/ItemStack as its value.

Do also note that in case of an ItemStack, only certain NBT values may get added. Material does not hold any NBT values.

* * *

### Removing Lines

Removing a line is a Straight forward process:

```
DHAPI.removeHologramLine(Hologram, int);
```

This will remove the line at the provided index (0-indexed) on the first page of the Hologram.

To remove a line from any other page than the first, use this method instead:

```
DHAPI.removeHologramLine(Hologram, int, int);
```

This removes the line on the provided line index (Third argument) of the provided page index (second argument. Also 0-indexed).

Note

Just like `List#remove` does calling these methods return the `HologramLine` instance that got removed, which can be useful for when you still need it for something.

* * *

## Editing Hologram Pages

DecentHolograms offers the feature for a Hologram to have multiple pages to display content with.  
As such are there methods available to edit and manage these pages.

### Adding new Pages

Adding a new Hologram Page is very simple, Just call the `addHologramPage` method:

```
DHAPI.addHologramPage(Hologram);
```

This will add a new Hologram page, who's initial lines only contains the default one from the config.yml.

To create a Page with already set Lines, use this method instead:

```
DHAPI.addHologramPage(Hologram, List<String>);
```

* * *

### Insert Pages

Hologram Pages can be inserted by using the `insertHologramPage` method:

```
DHAPI.insertHologramPage(Hologram, int);
```

This method would add a new `HologramPage` instance before the provided page index (0-indexed). The added `HologramPage` would only have the default line from the config.yml added to it.

A variant of this method exists to set a list of lines that the added `HologramPage` should have by default:

```
DHAPI.insertHologramPage(Hologram, int, List<String>);
```

* * *

### Removing Pages

Removing a page can be done by calling `removeHologramPage`:

```
DHAPI.removeHologramPage(Hologram, int)
```

This method returns the `HologramPage` that got removed, which can be useful if you still need it for something.  
Should the provided page index (0-indexed) be less than 0, or above or equal to the number of Hologram Pages, will `null` be returned.

Any HologramPages that came after the one removed will be moved down on the list (i.e. removing page 1 will cause page 2 to become page 1, page 3 becomes page 2, etc.).
