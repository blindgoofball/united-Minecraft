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
  facing direction in compass degrees with pitch, each on their own key. Shift on the
  coordinates key instead reads the light level (combined, plus block/sky split) -
  of the block under Build Mode's cursor if it's active, your own block otherwise.
- Automatic narration as things change: facing direction, hotbar slot,
  offhand/main-hand swaps, entering a new biome, and the time of day reaching
  sunrise, noon, sunset, night (when mobs can start spawning in the dark), or
  midnight. A dedicated key instead reads the full picture on demand - day
  count, current period, and a clock-style time.
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
  Ores, Liquids, Crops, Markers (see Map Markers below), and Players.
- Like Markers, Players ignores the Scanner's normal range - every other player in
  your current dimension shows up regardless of distance, since the game already
  tells your client about all of them no matter how far away they are.
- Crops covers farmland crops (wheat, carrots, potatoes, beetroot, torchflower),
  pumpkin and melon stems, nether wart, cocoa pods, and sweet berry bushes, and
  narrates "Ripe" once one's actually ready to harvest - silent otherwise, so it
  doesn't get in the way while you're just checking what's growing.
- Liquids covers water and lava, each clustered into whole connected bodies (a lake
  or ocean is one entry, not one per block) and reported at the nearest visible
  point - same "no x-ray" rule as Ores, so a lava pool sealed behind unmined stone
  won't show up until there's an actual way to see it.
- Ore detection (here and in the Mining Radar) only flags ore you could actually
  see - not ore sealed behind an unmined wall.
- Targeting a block aims at it. Targeting a mob starts a continuous lock-on that
  keeps facing it until released. Drawing a bow while locked on aims with a real
  ballistic arc, so shots land at real range instead of dropping short.
- Any bow shot that actually connects - locked on or not - gets a confirmation cue
  at full volume regardless of distance, since a hit at real range is easy to miss
  both by eye and by ear.
- Killing a locked-on hostile mob automatically re-locks onto the next nearest
  one, so tracking a fight doesn't mean re-scanning after every kill.
- Shift+target instead walks there automatically (see Auto-Walk below).
- A dedicated key announces the currently focused item's exact coordinates, for
  actually finding your way to it.
- Direction is narrated as a compass heading plus "above" or "below" whenever an
  item is more than 5 blocks above or below you, since a plain heading alone
  doesn't tell you which way to look vertically.

### Map Markers

Place a named waypoint at your current location, then reach it again any
time - from across the map, since unlike every other Scanner category,
Markers ignores distance entirely and always lists everything you've
placed in your current dimension. Persists to disk per-world, so your
markers are still there next time you load that world.

### Combat Mode

Toggleable continuous lock-on: keeps you facing whichever hostile mob is
nearest, switching target as a fight moves rather than sticking to one until
it dies - built for fighting more than one attacker, where re-targeting by
hand between hits isn't practical. Auto-disables Build Mode if it was on, and
you can still walk, strafe, and jump freely while your aim stays locked.

### Hostile Radar

Always-on warning for a hostile mob that's gotten close and has a clear line
of sight to you - a bell chime plus narration (name, distance, direction) the
moment one comes into range, so you know something's a threat before it's
already on top of you. No key or toggle - it just runs.

On top of that, a separate melee-range alert plays a quick click sound -
no narration, just the sound cue - every half-second while a hostile mob is
within actual melee reach, so you always know whether something's currently
close enough to hit (or be hit by) without another sentence competing with
the fight. Toggleable independently from the settings screen.

### Auto-Walk

Automatically walks you to anything the Scanner has found, or that Build Mode's
cursor is pointing at. Works on any server, no special permissions needed.
Hold your sprint key while it's walking to get there faster, same as walking
there yourself would. Cancel any time with the stop-lock key.

### Fall Warning

Always-on warning for a drop of more than 3 blocks coming up in whatever
direction you're actually walking (or sprint-strafing) - vanilla's own
damage-free threshold, so you'll never hear about a harmless step down. An
anvil-landing sound plus narration means it'll hurt; a lighter chime plus
narration means it's safe (a cave lake below, say) - useful information
either way, not just a hazard alert. No key or toggle - it just runs while
you're walking normally.

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

If the block under the cursor has a meaningful facing (repeaters, comparators,
dispensers, pistons, and the like), that's narrated too, and any block
currently receiving redstone power speaks up about it - silent otherwise, so
it doesn't get in the way. A dedicated key cycles a placement facing - North,
East, South, West, Up, Down, or back to automatic - so you can place a
dispenser with its output facing a specific direction, or attach a torch,
lever, or button to a specific side of
a block, without needing to physically turn to face that way first. There's a
brief pause between pressing place and the block actually appearing whenever a
facing is selected (getting the orientation right requires the choice to
genuinely reach the server first) - expected, not lag.

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
as you go. Enter picks an item up; Shift+Enter instead drops it straight into
the first empty hotbar slot (narrating if the hotbar's already full, rather
than overwriting anything), and Tab reaches your hotbar directly from any
item-picker tab, no need to switch to the Inventory tab just for that. The
Inventory tab itself works the same as your regular inventory screen, with
your full inventory and equipment included. Pressing Delete while carrying an
item discards it, the same as dragging it onto the trash slot would, without
having to navigate there - works from anywhere in the Creative screen, not
just the Inventory tab.

### Settings

A dedicated key opens a settings screen for the things worth tuning to
taste: on/off switches for Hostile Radar, its melee-range alert, and Fall
Warning (all otherwise always-on with no toggle of their own), and
range/threshold sliders for Hostile Radar, Fall Warning, Mining Radar,
Navigation Radar, and the Scanner. Saved to a config file shared across
every world and server, and
built from the same vanilla screen widgets as the rest of Minecraft's
Options menus rather than a third-party settings toolkit, so it narrates
exactly as reliably.

## Installation

You'll need three things: the matching version of Minecraft Java Edition,
Fabric Loader, and Fabric API. United Minecraft is a mod jar that goes
alongside Fabric API in your mods folder - it doesn't need anything installed
on the server you're playing on.

1. **Download United Minecraft.**
   Go to this repository's
   [Releases page](https://github.com/blindgoofball/united-Minecraft/releases)
   and download the latest `.jar` file. Note the Minecraft version listed in
   that release's title or notes - you'll use that same version for both
   steps below.

2. **Install Fabric Loader.**
   Go to the [Fabric installer page](https://fabricmc.net/use/installer/) and
   download the installer for your operating system. Run it, set the
   Minecraft version to the one from step 1, and click Install. This adds a
   new "Fabric Loader" profile to the official Minecraft Launcher - you don't
   need to touch anything else here.

3. **Download Fabric API.**
   Get the Fabric API release built for that same Minecraft version from
   [Modrinth](https://modrinth.com/mod/fabric-api) or
   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api).

4. **Put both jars in your mods folder.**
   That's a folder called `mods` inside your Minecraft folder:
   - Windows: `%appdata%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`

   If it doesn't exist yet, launch the Fabric profile once (step 5) to have
   it created automatically, then close the game and drop the two `.jar`
   files straight into it - not inside any subfolder.

5. **Launch the game.**
   Open the official Minecraft Launcher, choose the Fabric profile from the
   installations dropdown, and click Play.

6. **Turn on Minecraft's narrator**, if it isn't already. Either press
   Ctrl+B in-game to toggle it on, or go through Options > Accessibility
   Settings > Narrator. This is what actually triggers speech; United
   Minecraft narrates through it rather than replacing it.

If [NVDA](https://www.nvaccess.org/) is running, United Minecraft speaks
through it automatically for better screen-reader output - there's nothing
to set up for that part. Without NVDA running, it falls back to Minecraft's
normal narrator on its own.

## Default Key Bindings

All of these are rebindable from Options > Controls > United Minecraft, except
where noted.

| Key | Action |
| --- | --- |
| C (Shift = light level, of Build Mode's cursor if active) | Narrate coordinates, standing block, and biome |
| H | Narrate health and hunger |
| B (Shift+B resets facing to north) | Narrate facing direction and pitch |
| R | Read what's in front of me |
| V | Narrate time of day |
| U | Place a named map marker at your current location |
| I | Toggle Build Mode |
| Right Control | Build Mode: place block |
| Right Shift (hold) | Build Mode: break block |
| G | Build Mode: walk to cursor |
| J (Shift+J reverses) | Build Mode: cycle placement facing |
| N | Toggle Navigation Radar |
| M | Toggle Mining Radar |
| K | Toggle Combat Mode |
| Left/Right/Up/Down arrows | Turn camera, or move the Build Mode cursor (Shift = snap-turn 45 degrees when not in Build Mode) |
| Page Up/Down | Build Mode cursor up/down, or cycle the Scanner's nearest items |
| Home/End | Cycle the Scanner's category |
| Enter (Shift = walk there) | Target the Scanner's focused item |
| Delete | Stop Scanner lock-on / cancel Auto-Walk, or remove the focused marker in the Markers category |
| \ (backslash) | Announce the Scanner's focused item's coordinates |
| F6 | Open United Minecraft Settings |

Inside container menus, the Creative inventory, and settings/list screens, arrow
keys/Tab/Enter/Home/End/Delete take on the screen-specific meanings described
above - those are fixed, not part of the rebindable list, since they only
apply within that particular screen.

## Building From Source

This section is for building the mod itself, not for playing with it - see
Installation above for that. For IDE setup instructions, see the
[Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).
For cutting an actual release, see [RELEASING.md](RELEASING.md).

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
