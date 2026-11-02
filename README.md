# United Minecraft

An accessibility mod for Minecraft, by NibbleNerds, built primarily for blind and
low-vision players.

It runs on the [Fabric](https://fabricmc.net/) and [NeoForge](https://neoforged.net/)
mod loaders. It is client-side: nothing needs to be installed on the server, and it
works on any vanilla server. One optional feature, Structure Voices, can also use
the same jar on the server.

## What it does

- **Narration.** Speaks through your screen reader (or a text-to-speech backend)
  via [Prism](https://github.com/ethindp/prism), with a fallback to Minecraft's
  narrator. Coordinates, health, time, what is in front of you, chat, books, signs,
  titles, the scoreboard and boss bars, all on demand or as they change.
  [Details](docs/narration.md)
- **The Scanner.** Cycle through nearby storage, workstations, mobs, items, ores,
  trees, crops, terrain, biomes, structures, players and your own map markers; hear
  what is there, lock on, or have the mod walk you to it. [Details](docs/scanner.md)
- **Combat.** A lock-on mode, a sound-based Beacon mode, bow aiming by ear, and a
  hostile radar. [Details](docs/combat.md)
- **Navigation.** Auto-Walk (including ladders and swimming), a way out of water,
  elytra landing help, a cave trail back to where you started, Eye of Ender
  tracking and Structure Voices. [Details](docs/navigation.md)
- **Audio awareness.** Fall warnings, durability and tool warnings, Wall Tones, and
  navigation and mining radars. [Details](docs/awareness.md)
- **Building and mining.** Build Mode's virtual cursor, Angle Assist for bridges,
  tunnels and staircases, and tree-chopping help. [Details](docs/building.md)
- **Menus and inventory.** Full keyboard navigation of containers, the Recipe Book
  and the Creative inventory. [Details](docs/menus.md)

## Getting started

1. On Windows, run the [installer](docs/installation.md#installing-on-windows-with-the-installer);
   it sets up Fabric, Fabric API and the mod for you, and updates them later. Otherwise follow the
   [installation guide](docs/installation.md). Fabric is the simpler default; NeoForge builds can lag
   a new Minecraft version by days or weeks.
2. The first time the mod runs it turns Minecraft's narrator on for you.
3. Press **F6** for Settings, and see the [default key bindings](docs/keybindings.md).
   Every binding can be changed in the mod's own Keybindings screen.

| Key | Does |
| --- | --- |
| F6 | Settings |
| Home / End | Change Scanner category |
| Page Up / Down | Change Scanner item |
| Enter (Shift = walk there) | Target the item |
| Backspace | Stop locking on or walking |
| I | Toggle Build Mode |
| K | Toggle Combat Mode |

## Documentation

- [Narration](docs/narration.md)
- [The Scanner and Map Markers](docs/scanner.md)
- [Combat](docs/combat.md)
- [Navigation and exploration](docs/navigation.md)
- [Audio awareness and warnings](docs/awareness.md)
- [Building and mining aids](docs/building.md)
- [Menus and inventory](docs/menus.md)
- [Settings](docs/settings.md)
- [Default key bindings](docs/keybindings.md)
- [Installation](docs/installation.md)

## Building from source

`./gradlew build` builds both loaders and runs the unit tests, producing one jar
per loader in `fabric/build/libs/` and `neoforge/build/libs/`. Launch a development
client with `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient`; each
has its own game directory. Building requires JDK 25. See
[CLAUDE.md](CLAUDE.md) for how the code is organized and
[RELEASING.md](RELEASING.md) for cutting a release.

For IDE setup, see the
[Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up)
or the [NeoForged documentation](https://docs.neoforged.net/).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
