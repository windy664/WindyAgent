---
title: Command Permissions
tags: DecentHolograms, commands
source: https://wiki.decentholograms.eu/commands/permissions/
---
# Command Permissions

## Information about DecentHologram's set of permissions.

DecentHolograms provides a collection of permissions that can be used to set what commands a player should have access to.

The following types of permissions are available:

| Permission | Description |
| --- | --- |
| `dh.default` | Grants access to basic non-admin commands such as `/decentholograms` and `/dh version` |
| `dh.admin` | Grants access to all commands and subcommands of DecentHolograms |
| `dh.command` | Grants access to all commands and subcommands of DecentHolograms |
| `dh.command.<command>` | Grants access to a specific command of DecentHolograms. I.e. `dh.command.reload` allows to use `/dh reload` |
| `dh.command.<command>.<subcommand>` | Grants access to a specific command and subcommand of DecentHolograms. I.e. `dh.command.hologram.create` allows to use `/dh hologram create ...` |
