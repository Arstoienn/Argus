# Argus

A Fabric client mod for Minecraft 1.21.11 that opens a second window with a camera of its own.

The second camera can sit over your shoulder, follow another player, or ride an arrow, trident or
ender pearl you launched. It is drawn by the game's own renderer in a second pass after each frame,
with a culling state of its own, so it never disturbs what the main window shows, and it is held to
a share of frame time, so it cannot take the main frame rate down with it.

## What it does

- A second window with its own camera, position and size, remembered between sessions
- An over-the-shoulder camera, pulled in by walls so it never ends up inside a block
- Following another player, from behind or from their eyes, with their hands drawn
- Following your own arrows, tridents and ender pearls while they fly, then handing back
- Complementary view: first person in the second window while the main window is in third person
- Optionally the HUD, and open menus with a drawn pointer, so the second window can be recorded on
  its own
- A frame-time budget, a shorter render distance and a lower resolution for the second view
- Entity hitboxes in the second window when F3+B is on, including your own

## Requirements

| | |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | any version for 1.21.11 |
| [WalksyLib](https://modrinth.com/mod/walksylib) | 0.9.992+1.21.11, for the settings screen |
| Java | 21 or newer |

Client-side only; nothing needs to be installed on a server.

## Getting started

Build the jar with `./gradlew build`; it is written to `build/libs/`. Put it, Fabric API and
WalksyLib in `.minecraft/mods/` and join a world: the second window opens once a world is loaded.
The settings are in Mod Menu, and the keys are under "Argus" in the Controls screen.

| Key | Action |
|---|---|
| `\` | Follow the nearest player, lock on, then cycle through players |
| `Backspace` | Stop following |
| `]` | Switch a followed player between first and third person |
| `F11` | Fullscreen the second window, pressed while it has focus |

Every key and setting is described in [Using Argus](docs/USAGE.md).

## Compatibility

- **Sodium** is supported.
- **VulkanMod** is supported experimentally; following other players is disabled with it.
- **Ixeris** is incompatible: the second window cannot open while it is installed.

What each of these involves, and what has been tested, is in [Compatibility](docs/COMPATIBILITY.md).

## Documentation

| | |
|---|---|
| [Using Argus](docs/USAGE.md) | Controls, every setting, and troubleshooting |
| [Compatibility](docs/COMPATIBILITY.md) | Sodium, VulkanMod, zoom mods and Ixeris |
| [Engineering](docs/ENGINEERING.md) | Building, the development client, how the second view works, and the code layout |

## License

Released under the MIT License; see [LICENSE](LICENSE).
