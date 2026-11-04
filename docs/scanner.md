# The Scanner

[Documentation index](README.md)

On this page: [Using it](#using-it) | [Categories](#categories) | [No x-ray](#no-x-ray) | [Terrain](#terrain) | [Biomes and Structures](#biomes-and-structures) | [Search](#search) | [Targeting and lock-on](#targeting-and-lock-on) | [Map Markers](#map-markers)

The Scanner lets you cycle through nearby things by category and get full
narration, targeting and navigation without seeing or aiming at them.

## Using it

- **Home/End** cycle categories; **Page Up/Down** cycle the items in a category.
  **Z** jumps to a category by typing the first letters of its name (S asks
  Storage or Search, then St or Se; likewise M, P and T). Letters narrow the list
  and it jumps as soon as one category matches. Enter takes the first match and
  Escape cancels.
- Selecting a category announces how many items it found ("Trees, 13"), and
  cycling announces your position ("3 of 13") with the item.
- **Alt+Page Up/Down** jumps to the next or previous item of the same kind: the
  same entity type for mobs, the same block for everything else. Use it to skip a
  crowd of cows to reach the one sheep.
- **Enter** targets the focused item. **Shift+Enter** walks there first (see
  [Auto-Walk](navigation.md#auto-walk)).
- **\\** announces the focused item's exact coordinates.
- Direction is narrated as a compass heading, plus "above" or "below" when an
  item is more than 5 blocks off your height.
- A Settings option skips empty categories when cycling with Home/End. It is off
  by default, and Terrain is never skipped.

## Categories

In cycling order: Storage, Workstations, Mechanisms, Hostile Mobs, Passive Mobs,
Entities, Items, Ores, Trees, Crops, Terrain, Biomes and Structures, Players,
Markers and Search.

| Category | What it holds |
| --- | --- |
| Storage | Chests, barrels, shulker boxes, hoppers, droppers, dispensers, decorated pots, vaults, shelves |
| Workstations | Everything else that opens a menu (crafting tables, furnaces, anvils...), plus beds and command, structure, jigsaw and test blocks |
| Mechanisms | Doors, levers, buttons, pressure plates, repeaters, comparators, note blocks, bells, daylight sensors, jukeboxes, signs, and nether and end portals (one entry per portal) |
| Hostile Mobs, Passive Mobs | Mobs, narrated with their name; sheep include their wool color, sheared ones included; animals with variants include them (tabby cat, chestnut horse with white dots, snowy wolf, and likewise rabbits, foxes, llamas, axolotls, frogs, pigs, cows, chickens, mooshrooms, pandas, parrots, tropical fish); a mob in a boat, minecart or on another mob says what it is riding, and the boat, minecart or mount says who it is carrying; a shoulder parrot is narrated by color and shoulder |
| Entities | Minecarts, boats, armor stands, item frames (with what they hold), paintings by name, end crystals, leash knots |
| Items | Dropped items |
| Ores | Ore you could actually see (see below) |
| Trees | Trees, clustered into whole trees |
| Crops | Farmland crops, stems, nether wart, cocoa, sweet berry bushes, saplings (including mangrove propagules and bamboo), cave vines bearing glow berries, grown bamboo and chorus plants (clustered). "Ripe" is said when ready to harvest and nothing otherwise |
| Terrain | Liquids, climbables and flat areas (below) |
| Biomes and Structures | Places worth exploring toward (below) |
| Players | Every other player in your current dimension, at any distance |
| Markers | Your map markers, at any distance (see [Map Markers](#map-markers)) |
| Search | Any visible block whose name contains a term you type |

### No x-ray

Ores, Terrain's liquids and Search report only what you could actually see. Ore
sealed behind unmined stone, or a lava pool behind a wall, does not appear until
there is a way to see it. The Mining Radar follows the same rule. Buried
treasure and nether fossils are never reported, since they are hidden finds
rather than landmarks.

### Terrain

- **Liquids** are water and lava, each clustered into whole bodies (a lake is one
  entry, not one per block) and reported at the nearest visible point.
- **Climbables** are ladders, vines, scaffolding, and weeping, twisting and cave
  vines. Each vertical run is one entry at its base, narrated with how many
  blocks tall it is and whether it leads up, down or both from your height.
  Enter (or Shift+Enter to walk there first) faces you squarely at it, ready to
  climb.
- **Flat areas.** Shift+U while Terrain is selected asks for a size ("8" for 8 by
  8, "8x12" for a rectangle) and lists every level, open area at least that big
  within 64 blocks, nearest first. Each is narrated with its size and surface
  ("12 by 9 flat area, on Grass Block, Dirt"). Flat means one height all the way
  across: plants and snow do not break it, but trees, water, lava and steps do.
  Each is reported at its center block (the lower middle one when a side is
  even), and Shift+Enter walks you onto exactly that block. A blank size switches
  it off. Only terrain your game has loaded is searched.

### Biomes and Structures

One list, nearest first, out to the Biomes and Structures range (up to 128
blocks, 128 by default), separate from the general Scanner range and limited by
your render distance.

- **Biomes:** one entry per nearby biome type, at its nearest surface point. It
  samples at ground level, so a biome that exists only underground (dripstone
  caves, the deep dark) does not appear, and unloaded chunks are skipped.
- **Structures:** villages, ocean monuments, ruined portals and every other
  generated structure in range. This is the same list
  [Structure Voices](navigation.md#structure-voices) announces from, so
  structures appear only in single player or on a server that also has United
  Minecraft. Biomes work everywhere.
- Alt+Page Up/Down jumps between structures of the same kind, such as from one
  village to the next.

### Search

Shift+U while Search is selected opens a prompt for a term, then finds every
visible block nearby whose name contains it. "glowstone" locates glowstone blocks.

## Targeting and lock-on

- Targeting a **block** aims at it.
- Targeting a **mob** starts a continuous lock-on that keeps facing it until
  released with **Backspace** (or Shift+B, which resets facing to north).
- Alt+; locks onto the nearest Ender Dragon or Wither wherever it is. It does
  nothing under Build Mode or Combat Mode, which choose their own targets.
- The rest of the Scanner keeps working while locked on, so you can look for
  something else without releasing. Targeting a different item drops the old
  lock and takes the new one.
- While locked onto a mob, Enter interacts with it directly (feeding, trading,
  saddling...) using a real interaction aimed at that entity, not whatever the
  crosshair hits. This helps when another entity is in the way, such as two
  chickens crowded together.
- Shift+Enter on a mob walks you to it and faces it on arrival. A Settings
  toggle locks on automatically after the walk.
- Killing a locked-on hostile mob re-locks onto the next nearest one.
- Drawing a bow, holding a loaded crossbow, or holding a trident back while
  locked on aims with a real ballistic arc, so shots land at range. Riptide
  tridents and crossbow fireworks aim plain. With
  [Bow Beacon](combat.md#bow-beacon) on, the lock stops steering the camera while
  you hold a bow or crossbow.
- An arrow that connects, locked on or not, gets a confirmation cue at full
  volume regardless of distance, and a kill gets its own lower thud-and-ding.

## Map Markers

Shift+U places a named waypoint at your current location. Markers is a Scanner
category that ignores distance and always lists everything you have placed in
your current dimension. Markers are saved per world. On a multiplayer server a world is told apart by the server's
address together with the seed the server reports, so two worlds on one address keep separate
markers. Named blocks are kept the same way. Deleting a singleplayer world from the world
list also deletes its markers and block names.

- Shift+Enter on a marker walks you to its exact coordinates.
- You can also mark a block. Name it with Shift+U and tick "Also show in
  Markers", and it appears in the list under that name from any distance.
  Targeting it works like targeting the block: you walk up beside it and face it,
  with the same narration (open or closed, shelf contents, sign text).
- Delete removes a block from Markers but keeps its name.

### Sharing markers

Alt+U opens the Sharing screen, from anywhere. It works when both you and the other
player have United Minecraft and the server has it too (a single-player world opened to
LAN counts); on a server without the mod it tells you so. Use Tab to move between its parts
and Up/Down to move within a list. Escape or Close leaves.

- **Pending offers** lists what other players have sent you, with the sender, the name
  and how far away it is (or which dimension it is in). Accept saves it to your own
  markers; Decline discards it. Nothing is saved until you accept.
- If you already have a different name at that spot, the first Accept tells you so and
  keeps the offer; Accept again to replace it. A marker you already have exactly is not
  added twice.
- **Send to** lists the other players online who have the mod, and **Marker to send**
  lists your markers in every dimension, including named blocks that appear under Markers.
  Send to Selected Player sends that marker to that player. Send to All Players sends it
  to everyone on the list.
- A named block you share arrives as a named block, with its Markers setting, so it keeps
  its name wherever the receiver walks up to it.
- When an offer arrives you hear a bell and a short message with the sender and name.
  Offers wait in memory until you leave the world.
- "Ignore Marker Shares From Other Players" in the General settings drops incoming
  offers without a sound or message. The sender is not told.
