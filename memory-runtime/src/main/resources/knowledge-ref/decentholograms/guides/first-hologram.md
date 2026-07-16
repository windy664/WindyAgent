---
title: Create your first Hologram
tags: DecentHolograms, guides
source: https://wiki.decentholograms.eu/guides/first-hologram/
---
# Create your first Hologram

## This guide explains how to create your first Hologram.

This page aims to explain how to create your first hologram with DecentHolograms.  
It expects you to have the proper permissions for using the `/dh` commands shown. Refer to the command pages for their respective permissions or use `decentholograms.admin` to have all permissions.

## Choosing Hologram Types

Since `2.10.0` does DecentHolograms provide two types of Holograms: Armor Stand based and Display Entity based.  
Most people may be familiar with the Armor Stand ones, while others may be more familiar with the Display Entity ones.

It's important to note that not only are Display Entity based ones using a separate storage folder and sub-command ([[/dh displays|`/dh displays ...`]]) but it also has some features not available through the Armor Stand one. Where necessary will these differences be pointed out.

## 1. Create your Hologram

To create your first hologram, simply execute [[/dh hologram create|`/dh h create <name>`]] or [[/dh displays create|`/dh displays create <type> <name>`]].  
This will create a Hologram that is positioned at your current location.

Both commands support an additional argument at the end, which sets the text that should be displayed at first. If not present will it default to the default text set in the config.yml.

The `/dh h create` command provides two additional arguments named `-l:<world>:<x>:<y>:<z>` and `--center`.  
The first one is for spawning the Hologram at specific coordinates while the second one is used to center the hologram to the block.

## 2. Change Lines

Hologram lines can be edited in various ways. The main commands you probably will use are [[/dh lines set|`/dh l set ...`]] and [[/dh lines add|`/dh l add ...`]], or [[/dh displays setline|`/dh displays setline ...`]] and [[/dh displays addline|`/dh displays addline ...`]] respectively.

The set(line) command replaces a specific line with whatever content you provide, while the add(line) command adds a new line to the already existing one at the bottom.

Note that Armor Stand based Holograms can utilize [[Line Content|line types]] while Display Entities are limited to the Type you specified when creating the Hologram.

## 3. Add pages

This feature is not available for Display Entity Holograms.

DecentHolograms offers pages, allowing you to split content up into different parts that you can switch in between.  
To add a page, simply run [[/dh pages add|`/dh p add <name>`]]. Just like when creating the Hologram does this command offer an optional argument to set the initial line content of the new page.

To switch to the new page, either execute [[/dh pages switch|`/dh p switch <name> <page>`]] or setup Page Actions (Explained below).

## 4. Add Page Actions

This feature is not available for Display Entity Holograms.

DecentHolograms provides a [[Actions|collection of actions]] that you can set per hologram-page to perform certain actions on left or right clicking the Hologram.  
As an example, to allow a player to switch to our previously created page, we can execute [[/dh pages addaction|`/dh p addaction <name> <page> <type> <action>`]] where `<type>` is the click type (i.e. `RIGHT`) and `<action>` the Click Action, which in our case would be `NEXT_PAGE`.

This would now make the Hologram switch to the next page when the player right-clicks it while displaying the 1st page.
