# United Minecraft

An accessibility mod for Minecraft, by NibbleNerds, built primarily for blind and
low-vision players.

Built on the [Fabric](https://fabricmc.net/) mod loader. Everything is client-side -
no server-side install needed, and it works on any vanilla server.

## Features

### Narration

- Narration is routed through [NVDA](https://www.nvaccess.org/) when it's running,
  for better screen reader output than Minecraft's built-in narrator. Falls back
  to the normal narrator automatically when NVDA isn't running. See
  [`src/client/resources/nvda/README.md`](src/client/resources/nvda/README.md)
  for setup details.
- On-demand readouts for coordinates/standing block/biome, health and hunger, and
  facing direction in compass degrees with pitch, each on their own key.
- Automatic narration as things change: facing direction, hotbar slot,
  offhand/main-hand swaps, and entering a new biome.
- "Read what's in front of me": narrates the nearest block and/or entity ahead,
  with distance.
- Reactive movement narration: if you're stuck walking into something, it tells
  you what (a wall, a wall you could jump, a drop-off, water, lava) and, if
  there's a clear way around it, which side to go.
- More reliable narration on settings and list screens - Key Binds, Options,
  World Selection, and similar - including cases where vanilla itself goes
  silent or only reads a bare position with no content.

### The Scanner

Cycle through nearby things by category and get full narration, targeting, and
navigation without needing to see or aim at them.

- Categories: Interactables, Mechanisms, Items, Passive Mobs, Hostile Mobs, Trees,
  and Ores.
- Ore detection (here and in the Mining Radar) only flags ore you could actually
  see - not ore sealed behind an unmined wall.
- Targeting a block aims at it. Targeting a mob starts a continuous lock-on that
  keeps facing it until released. Drawing a bow while locked on aims with a real
  ballistic arc, so shots land at real range instead of dropping short.
- Killing a locked-on hostile mob automatically re-locks onto the next nearest
  one, so tracking a fight doesn't mean re-scanning after every kill.
- Shift+target instead walks there automatically (see Auto-Walk below).
- A dedicated key announces the currently focused item's exact coordinates, for
  actually finding your way to it.

### Auto-Walk

Automatically walks you to anything the Scanner has found, or that Build Mode's
cursor is pointing at. Works on any server, no special permissions needed.
Cancel any time with the stop-lock key.

### Navigation Radar

Toggleable audio radar covering front, left, and right as you walk - a sound
for an obstruction (pitched differently if it's low enough to jump), a chime
for a clear direction - so you can navigate by ear.

### Mining Radar

Toggleable passive alert for valuable ore exposed nearby while mining - a
positional chime plus narration (name, distance, direction) the moment ore
becomes visible, without needing to run the Scanner yourself.

### Build Mode

A virtual cursor for exploring and targeting blocks without needing to
physically turn to face them. The arrow keys and Page Up/Down step the cursor
in true compass directions instead of turning the camera, narrating the block,
coordinates, and whether it's placeable at each step, across a 65x65 area
centered on wherever you toggled it on. Place and break work reliably no
matter which way you're actually facing. If the cursor wanders out of reach, a
dedicated key walks you to it automatically.

### Tree Chopping Assist

Whenever the log you're looking at gets broken, automatically re-aims at an
adjacent log, so felling a tree doesn't mean re-aiming after every single
block. Silent while it's working; only speaks up once the chain runs out.

### Menu & Inventory Accessibility

Full keyboard navigation for container menus - chests, furnaces, crafting
tables, anvils, brewing stands, enchanting tables, and your own inventory.
Tab/Shift+Tab cycle between sections (the container's own slots, equipment,
main inventory, hotbar, and a Recipe Book section where applicable); arrow
keys move within a section in a proper 2D grid; Enter picks up/places
(Shift+Enter quick-moves).

The Creative inventory gets full navigation too: Home/End cycle through every
creative tab, and arrow keys/Page Up/Down browse an entire tab's items - not
just whatever happens to be scrolled into view - narrating name and position
as you go. The ordinary Inventory tab works the same as your regular
inventory screen.

## Default Key Bindings

All of these are rebindable from Options > Controls > United Minecraft, except
where noted.

| Key | Action |
| --- | --- |
| C | Narrate coordinates, standing block, and biome |
| H | Narrate health and hunger |
| B (Shift+B resets facing to north) | Narrate facing direction and pitch |
| R | Read what's in front of me |
| I | Toggle Build Mode |
| Right Control | Build Mode: place block |
| Right Shift (hold) | Build Mode: break block |
| G | Build Mode: walk to cursor |
| N | Toggle Navigation Radar |
| M | Toggle Mining Radar |
| Left/Right/Up/Down arrows | Turn camera, or move the Build Mode cursor (Shift = snap-turn 45 degrees when not in Build Mode) |
| Page Up/Down | Build Mode cursor up/down, or cycle the Scanner's nearest items |
| Home/End | Cycle the Scanner's category |
| Enter (Shift = walk there) | Target the Scanner's focused item |
| Delete | Stop Scanner lock-on / cancel Auto-Walk |
| \ (backslash) | Announce the Scanner's focused item's coordinates |

Inside container menus, the Creative inventory, and settings/list screens, arrow
keys/Tab/Enter/Home/End take on the screen-specific meanings described above -
those are fixed, not part of the rebindable list, since they only apply within
that particular screen.

## Setup

For IDE setup instructions, see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
