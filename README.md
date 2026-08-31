# United Minecraft

An accessibility mod for Minecraft, by NibbleNerds, built primarily for blind and
low-vision players.

Built on the [Fabric](https://fabricmc.net/) mod loader. Everything is client-side -
no server-side install needed, and it works on any vanilla server.

## Features

### Narration

- Narration is routed through [Prism](https://github.com/ethindp/prism), which
  picks the best available screen reader (or, failing that, TTS backend) on your
  system, for better screen reader output than Minecraft's built-in narrator,
  including a connected braille display where your screen reader supports one.
  Falls back to the normal narrator automatically when no such backend is
  available. Works on Windows, macOS, and Linux (x86-64 and arm64); see
  [`src/client/resources/prism/README.md`](src/client/resources/prism/README.md)
  for details.
- Action-bar HUDs (health/mana readouts, cooldowns, etc.) that some servers
  keep alive by resending the same message every second or two are only
  narrated when the text actually changes, instead of vanilla's own behavior
  of re-narrating every resend even when nothing changed.
- On-demand readouts for coordinates/standing block/biome, health and hunger
  (on the same 10-heart/10-shank scale the sighted heart bar uses, half-point
  precision included), and facing direction in compass degrees with pitch,
  each on their own key. Shift on the coordinates key instead reads the light
  level (combined, plus block/sky split) - of the block under Build Mode's
  cursor if it's active, your own block otherwise.
- Automatic narration as things change: facing direction, hotbar slot,
  offhand/main-hand swaps, entering a new biome, and the time of day reaching
  sunrise, noon, sunset, night (when mobs can start spawning in the dark), or
  midnight. A dedicated key instead reads the full picture on demand - day
  count, current period, and a clock-style time. Shift on that same key
  instead reads current weather (clear, rain, snow, or thunderstorm) and, at
  night, the moon phase - useful since it affects spawn rates and mob gear
  drops.
- "Read what's in front of me": narrates the nearest block and/or entity ahead,
  with distance. For a block, also narrates which face your crosshair actually
  landed on (North/East/South/West/Top/Bottom) - the same raycast the vanilla
  crosshair itself uses, so it's exactly what you'd be interacting with if you
  clicked.
- Auto Crosshair Narration: toggleable with Shift+R, automatically narrates
  whatever block your crosshair is resting on as you look around - only when
  it changes to a different block, not for every step across the same one, so
  sweeping across a stone wall doesn't repeat "Stone" over and over. Air is
  never narrated.
- Reading a written book narrates the current page's text out loud - turning
  the page (buttons or Page Up/Down) narrates the new page automatically,
  since vanilla itself only ever includes the page text in a book's narration
  passively, with nothing that actually re-triggers it when you turn a page.
- Writing a book and quill narrates the current page's text when you open it
  or turn the page - vanilla doesn't narrate page text there at all, even
  passively. Typing, pasting, deleting, and moving the cursor within a page
  all narrate incrementally too, the same as any other text field.
- Reactive movement narration: if you're stuck walking into something, it tells
  you what (a wall, a wall you could jump, a drop-off, water, lava) and, if
  there's a clear way around it, which side to go.
- More reliable narration on settings and list screens - Key Binds, Options,
  World Selection, and similar - including cases where vanilla itself goes
  silent or only reads a bare position with no content.
- Chat history browsing: in the chat screen, Page Down/Up steps to a more
  recent/older message and narrates it (vanilla only scrolls the log
  silently, a whole page of lines at a time). Shift with either jumps
  straight to the newest or oldest message. The screen always opens scrolled
  to the most recent message regardless of where it was left last time.
- Fishing catches are narrated - vanilla gives no feedback at all about what
  a cast just reeled in, sighted or otherwise.
- Dedicated keys read the current boss bar(s) (name and percentage) and the
  sidebar scoreboard (objective name plus each line's score, highest first)
  on demand - vanilla shows both purely visually, with nothing narrating
  either one. The scoreboard reads the top 10 entries by default; hold Alt
  to hear the whole thing.

### The Scanner

Cycle through nearby things by category and get full narration, targeting, and
navigation without needing to see or aim at them.

- Categories: Interactables, Mechanisms, Items, Passive Mobs, Hostile Mobs, Trees,
  Ores, Liquids, Crops, Search, Biomes, Markers (see Map Markers below), Players,
  and Entities (minecarts, boats, armor stands, item frames and glow item frames -
  narrating what they're holding, if anything - paintings by their actual name
  rather than just "Painting", end crystals, and leash knots).
- An optional Settings toggle skips empty categories entirely when cycling with
  Home/End, instead of stopping on them to announce "empty" - off by default.
- Selecting a category announces how many items it found (e.g. "Trees, 13"),
  and cycling through them with Page Up/Down announces your position in that
  list (e.g. "3 of 13") alongside the item itself.
- Alt+Page Up/Down jumps to the next/previous item of the same kind as the one
  currently selected - the same entity type for mobs, the same block for
  everything else - instead of stepping through every item one at a time.
  Handy for skipping past a crowd of cows and pigs to reach the one sheep, or
  past a dozen spruce trees to find the one oak.
- Sheep narrate their wool color (e.g. "White Sheep"), including sheared
  ones, which still narrate their last color alongside "sheared".
- Mechanisms now also includes nether and end portals - clustered into one
  entry per portal, the same way Liquids clusters a lake into one entry
  instead of one per block - alongside doors, buttons, levers, and the rest
  of Mechanisms' usual redstone-adjacent contents.
- Like Markers, Players ignores the Scanner's normal range - every other player in
  your current dimension shows up regardless of distance, since the game already
  tells your client about all of them no matter how far away they are.
- Crops covers farmland crops (wheat, carrots, potatoes, beetroot, torchflower),
  pumpkin and melon stems, nether wart, cocoa pods, sweet berry bushes, saplings
  (including mangrove propagules and bamboo's own sapling stage), and cave vines
  actually bearing glow berries, narrating "Ripe" once one's actually ready to
  harvest - silent otherwise, so it doesn't get in the way while you're just
  checking what's growing. Grown bamboo stalks are here too, but clustered into
  whole clumps like Trees, not one entry per block.
- Liquids covers water and lava, each clustered into whole connected bodies (a lake
  or ocean is one entry, not one per block) and reported at the nearest visible
  point - same "no x-ray" rule as Ores, so a lava pool sealed behind unmined stone
  won't show up until there's an actual way to see it.
- Ore detection (here and in the Mining Radar) only flags ore you could actually
  see - not ore sealed behind an unmined wall.
- Search finds every visible block whose name matches a term you type - Shift+U
  while it's the selected category (instead of naming, which it does for every
  other category) opens a prompt for the term, then finds anything nearby whose
  name contains it, e.g. typing "glowstone" locates glowstone blocks. Same "no
  x-ray" rule as Ores and Liquids.
- Biomes covers nearby distinct biomes worth exploring toward - one entry per
  biome type, at the nearest surface point of it - out to a fixed 64 blocks
  regardless of your configured Scanner range, since it's meant for
  exploration-scale distances. Sampled at ground level, so a biome that only
  exists underground (dripstone caves, the deep dark) won't show up here.
- Targeting a block aims at it. Targeting a mob starts a continuous lock-on that
  keeps facing it until released. Drawing a bow while locked on aims with a real
  ballistic arc, so shots land at real range instead of dropping short.
- While locked onto a mob, the target key (Enter) doesn't target - there's
  nothing to target while already locked - so it interacts with the locked
  entity directly instead (feeding, trading, saddling, etc.), the same real
  interaction a right-click sends but targeting the locked entity by identity
  instead of whatever the crosshair's raycast happens to hit. Useful when
  another entity is physically in the way of the one you're locked onto (two
  chickens crowded together while trying to breed them, say), where camera aim
  alone can't tell them apart.
- The rest of the Scanner still works while locked on - Home/End and Page
  Up/Down keep cycling categories and items so you can look for something
  else without releasing the lock first. Targeting a *different* item drops
  the old lock and targets the new one normally; Shift+B (reset facing to
  north) also releases the lock.
- Walking to a mob (Shift+target) just faces it once you arrive, the same
  as walking to a block already does - turn on the auto-lock Settings
  toggle if you'd rather it lock on automatically instead.
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

A separate audio cue plays the moment your weapon's attack-strength meter
refills to full - the cooldown that reduces damage on an early swing - so you
know when a hit will land at full strength without watching for it.
Configurable from Settings to play only in Combat Mode, always, or never.

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

### Water Exit

For when you've fallen into a lake or river and can't find your way out - a
dedicated key reports the distance and direction to the nearest way out, and
Shift instead swims you there directly, both computed by tracing a real,
connected route through the water you're actually in (never through walls or
across open air), so it never points you at a ledge you can't actually reach.
Cancel a swim any time with the stop-lock key, same as Auto-Walk.

### Cave Trail

Solves the other half of "I'm lost underground": as you walk, your actual
positions are continuously recorded as a breadcrumb trail (no toggle needed -
it's always running). A dedicated key reports the distance and direction back
to the start of that trail, and Shift instead walks you back along it, one
recorded step at a time, retracing the exact route you actually took rather
than beelining through a wall. Since it's a literal reverse of ground you've
already covered, it's always reachable. If retracing hits a spot where you
fell or dropped down on the way in, it stops and tells you to place a block
there instead of endlessly walking into the wall below it - place one, then
retrace again to continue from there. A separate key clears the trail and
marks your current spot as a fresh start, useful right as you enter a cave.
Cancel a walk-back any time with the stop-lock key, same as Auto-Walk.

### Fall Warning

Always-on warning for a drop of more than 3 blocks coming up in whatever
direction you're actually walking (or sprint-strafing) - vanilla's own
damage-free threshold, so you'll never hear about a harmless step down. An
anvil-landing sound plus narration means it'll hurt; a lighter chime plus
narration means it's safe (a cave lake below, say) - useful information
either way, not just a hazard alert. No key or toggle - it just runs while
you're walking normally.

### Durability & Tool Harvest Awareness

Always-on warnings that head off the two most common ways to lose progress
without noticing: worn armor and hand items narrate once when they cross a
configurable "getting low" threshold and again at a more urgent "about to
break" threshold (both tunable from Settings), so a pick or a chestplate
never breaks as a surprise. Repairing past a threshold - Mending, an anvil -
re-arms it, so the same warning can fire again if it happens a second time.
Separately, starting to mine a block your held item can't actually harvest
(diamond ore with a stone pickaxe, stone with bare hands) narrates what tool
or tier it actually needs, before you waste the time mining a block whose
drop would've been destroyed anyway. Both are toggleable independently from
Settings.

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
physically turn to face them. Toggling it on snaps your camera to whichever
cardinal direction (north/east/south/west) you're already closest to facing,
narrates it, and locks the cursor's movement to that orientation: Up/Down
step forward/back, Left/Right strafe, and Page Up/Down move vertically -
narrating the block, coordinates, and whether it's placeable at each step,
across a 65x65 area centered on wherever you toggled it on. Alt+Left/Right
turns both the camera and that orientation a quarter turn at a time, the same
snap-turn mechanism used outside Build Mode, so you can reorient without
needing to leave Build Mode to physically turn. Place and break work reliably
no matter which way you're actually facing, independent of the cursor's own
orientation. If the cursor wanders out of reach, a dedicated key walks you to
it automatically; Alt+I instead snaps the cursor straight back to wherever
you're currently standing and re-centers the whole 65x65 area there, so you
don't have to walk back to keep exploring from your new position. Moving the
cursor onto the block you're actually standing on narrates that too.

If the block under the cursor has a meaningful facing (repeaters, comparators,
dispensers, pistons, and the like), that's narrated too, and any block
currently receiving redstone power speaks up about it - silent otherwise, so
it doesn't get in the way. Stairs narrate their corner as a compass direction
(e.g. "Northwest corner") when they're not a plain straight stair, and
"Upside down" when placed on the underside of the block above - both silent
for the plain, right-side-up case. A dedicated key cycles a placement facing -
North, East, South, West, Up, Down, or back to automatic - so you can place a
dispenser with its output facing a specific direction, or attach a torch,
lever, or button to a specific side of
a block, without needing to physically turn to face that way first. There's a
brief pause between pressing place and the block actually appearing whenever a
facing is selected (getting the orientation right requires the choice to
genuinely reach the server first) - expected, not lag.

When a block can't be placed, the reason is narrated specifically instead of
a generic "Can't place there" - you're standing in the way, something's
already occupying that space, there's no adjacent block to place against, or
something else (usually an entity) is blocking the spot.

Water and lava buckets work too, including pouring into a spot with no
directly clickable face - a narrow hole, say - the same way it works for a
sighted player looking straight down into one, since vanilla gives bucket
placement no other way in.

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
(Shift+Enter quick-moves). Hovering a slot and pressing 1-9 swaps its
contents into that hotbar slot, and Q (Ctrl+Q for the whole stack) drops it,
the same as they would with a real mouse cursor.

Wherever a crafting grid and Recipe Book are both present, Up/Down move
between recipe groups and Left/Right between variants within one (vanilla
bundles near-duplicates, like different wood colors, together), narrating
the result, whether it's currently craftable, and the ingredients it
actually needs - something vanilla's own recipe book never says out loud
anywhere. Home/End jump straight to the first/last recipe group, and Page
Up/Down cycle through vanilla's own recipe-book category tabs (Building
Blocks, Redstone, and so on), so you're not stuck paging through every
recipe one at a time to reach a specific kind. F toggles showing only
recipes you can currently craft. Space opens a search prompt - the term
also filters vanilla's own recipe book search box, if it's open, so you and
a sighted player looking over your shoulder see the same results. Enter
places the focused recipe; Shift+Enter fills the grid to the max stack size.

The enchanting table's three enchantment options aren't slots at all, so they
get their own Enchant Options section: Up/Down move between them, narrating
exactly what hovering with a mouse would - the enchantment itself, whether
your experience level meets the requirement, and its Lapis/XP cost - and
Enter selects the focused one.

The anvil's rename box gets its own Rename section too, ahead of its slots -
Tab reaches it and moves on from it like any other section, while every other
key (typing, Backspace, arrow keys) reaches the text field itself normally.

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

The Search tab's text field is fully keyboard-accessible: Tab reaches it,
typing filters the grid and narrates a result count as you go, and Up/Down
(or Page Up/Down) leave the field to jump straight into the first filtered
result - pressing Up again from the very first result returns to the field.

### Settings

A dedicated key opens a settings screen for the things worth tuning to
taste: on/off switches for Hostile Radar, its melee-range alert, Fall
Warning, Durability Awareness, Tool Harvest Awareness (all otherwise
always-on with no toggle of their own), skipping empty Scanner categories,
and auto-locking onto a mob after walking to it, a three-way switch for the
Combat Mode attack-ready cue (off, Combat Mode only, or always), and
range/threshold sliders for Hostile Radar, Fall Warning, Mining Radar,
Navigation Radar, the Scanner, and Durability Awareness's warning/critical
thresholds. Saved to a config file shared across every world and server, and
built from the same vanilla screen widgets as the rest of Minecraft's
Options menus rather than a third-party settings toolkit, so it narrates
exactly as reliably - including scrolling to keep whatever's focused on
screen as the list of settings grows past what fits.

A button on that screen opens the Sound and Cue Glossary - a scrollable,
keyboard-navigable list of every audio cue this mod plays and what it means,
since it's not always obvious the first time you hear one. Selecting an
entry (Enter or a click) replays its sound at the exact volume and pitch the
mod actually uses, so you can learn to recognize it on demand.

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

If a screen reader (or other Prism-supported speech backend) is available,
United Minecraft speaks through it automatically for better screen-reader
output - there's nothing to set up for that part. Otherwise, it falls back to
Minecraft's normal narrator on its own.

## Default Key Bindings

All of these are rebindable from Options > Controls > United Minecraft, except
where noted.

| Key | Action |
| --- | --- |
| C (Shift = light level, of Build Mode's cursor if active) | Narrate coordinates, standing block, and biome |
| H (Shift = experience level) | Narrate health and hunger |
| B (Shift+B resets facing to north) | Narrate facing direction and pitch |
| R (Shift = toggle Auto Crosshair Narration) | Read what's in front of me |
| V (Shift = weather and, at night, moon phase) | Narrate time of day |
| U (Shift = name the Scanner's focused item, or enter a Search term while Search is selected) | Place a named map marker at your current location |
| I (Alt+I while Build Mode is on) | Toggle Build Mode / recenter its cursor and movement area on your current position |
| Right Control | Build Mode: place block, or interact with it if it's something clickable (chest, door, lever, repeater, etc.) |
| Right Shift (hold) | Build Mode: break block |
| G | Build Mode: walk to cursor |
| J (Alt+J reverses) | Build Mode: cycle placement facing |
| N | Toggle Navigation Radar |
| M | Toggle Mining Radar |
| K | Toggle Combat Mode |
| Y (Shift = swim there) | Find the nearest way out of water |
| X | Mark cave trail start (clears and restarts the recorded trail here) |
| Z (Shift = walk there) | Find the way back along the recorded cave trail |
| Left/Right/Up/Down arrows | Turn camera (Alt = snap-turn 45 degrees), or move the Build Mode cursor relative to its own orientation (Alt+Left/Right = turn the cursor's orientation a quarter turn) |
| Page Up/Down (Alt = jump to the next/previous item of the same kind) | Build Mode cursor up/down, or cycle the Scanner's nearest items |
| Home/End | Cycle the Scanner's category |
| Enter (Shift = walk there) | Target the Scanner's focused item, or interact with it directly while locked on |
| Backspace | Stop Scanner lock-on / cancel Auto-Walk, swim, or trail retrace |
| Delete | Remove the focused marker in the Markers category |
| \ (backslash) | Announce the Scanner's focused item's coordinates |
| ; (semicolon) | Read the current boss bar(s) |
| ' (apostrophe, hold Alt for the whole board) | Read the sidebar scoreboard |
| F6 | Open United Minecraft Settings |

Inside container menus, the Creative inventory, and settings/list screens, arrow
keys/Tab/Enter/Home/End/Delete/Space/Page Up/Down take on the screen-specific
meanings described above - those are fixed, not part of the rebindable list,
since they only apply within that particular screen.

## Building From Source

This section is for building the mod itself, not for playing with it - see
Installation above for that. For IDE setup instructions, see the
[Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).
For cutting an actual release, see [RELEASING.md](RELEASING.md).

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
