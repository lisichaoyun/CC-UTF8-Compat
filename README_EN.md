# CC UTF-8 Compat

CC UTF-8 Compat adds UTF-8 compatibility patches for CC:Tweaked on Minecraft 1.21.1 NeoForged.

The mod improves UTF-8 text handling in CC:Tweaked terminals and related peripherals. It is mainly intended for modpacks or servers that need non-ASCII text support, such as Russian, Chinese, Spanish accents, and other Unicode characters.

## Features

* UTF-8 text rendering in computer terminals
* UTF-8 input and paste support
* UTF-8 support for computer labels
* UTF-8 terminal synchronization between server and client
* UTF-8 rendering fixes for monitors
* UTF-8 support for pocket computers
* UTF-8 support for printers and printed pages
* Patched CraftOS Lua files for better UTF-8 behavior in selected programs and APIs

## Fixes compared to the original

* Fixes the issue where UTF-8 characters (especially Chinese characters) inside script programs were not rendered completely, so localized text now displays correctly.
* Users of languages other than English and Chinese can also use this mod to support their own Unicode characters.

## Contributions welcome

Maintenance and improvements from the community are welcome. If you fix a bug, port the mod to a newer CC:Tweaked version, or add new UTF-8 compatibility features, feel free to open a PR.

## Tested with

* Minecraft 1.21.1
* NeoForged 21.1.77
* CC:Tweaked 1.120.2

## Requirements

* Minecraft 1.21.1
* NeoForged
* CC:Tweaked 1.120.2

This mod is version-specific and depends on CC:Tweaked internals. Other CC:Tweaked versions are not guaranteed to work.

## Installation

Install the mod on both client and server.

Required files:

```text
mods/
  cc-tweaked-1.21.1-forge-1.120.2.jar
  cc_utf8_compat-1.1.0.jar
```

For singleplayer, place both mods in the client `mods` folder.

For multiplayer, the compat mod must be installed on both sides.

## Configuration

After the first launch, the config file will be created:

```text
config/cc_utf8_compat-common.toml
```

The UTF-8 compatibility layer can be enabled or disabled:

```toml
ccUtf8Compat = true
```

Set it to `false` to disable the patches without removing the mod.

## Known limitations

This mod patches internal CC:Tweaked classes using mixins. Because of that, it is only tested against CC:Tweaked 1.120.2.

Newer CC:Tweaked versions may change internal class names, method names, or behavior, which can break compatibility.

## License

This project is licensed under the MIT License.

## Other languages

* [简体中文](README.md)
