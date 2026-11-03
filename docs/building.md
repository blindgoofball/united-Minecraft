# Building and mining aids

[Documentation index](README.md)

## Build Mode

I toggles a virtual cursor for exploring and targeting blocks without turning to
face them. Turning it on snaps your camera to the nearest cardinal direction,
narrates it, and locks the cursor's movement to that orientation.

- **Moving the cursor:** Up/Down step forward and back, Left/Right strafe, Page
  Up/Down move vertically. Each step narrates the block, coordinates and whether
  it is placeable. The cursor roams a 65x65 area centered where you turned it on.
- **Turning:** Alt+Left/Right turns both the camera and the cursor's orientation a
  quarter turn, the same snap-turn used outside Build Mode.
- **Recentering:** Alt+I snaps the cursor to where you are standing and recenters
  the area there.
- **Reach:** if the cursor wanders out of reach, G walks you to it. When it is
  already in reach, G walks you up to it anyway.
- **Placing and breaking** (Right Control and hold Right Shift) work no matter
  which way you are actually facing. Build Mode also works with hoes, axes and
  shovels on blocks, and with water and lava buckets, including pouring into a
  spot with no clickable face, such as a narrow hole.
- **Narration at the cursor:** the block you are standing on, and any entity
  sharing the cell (a mob, dropped item, minecart), named as the Scanner would.
  Blocks with a meaningful facing (repeaters, comparators, dispensers, pistons)
  narrate it. Blocks receiving redstone power say so, and are silent otherwise.
  Stairs narrate a corner as a compass direction ("Northwest corner") and "Upside
  down" when on the underside of a block, both silent for a plain stair.
  Waterlogged blocks are reported. Monster spawners and trial spawners say which mob
  they spawn ("Spawns Zombie").
  Flowing water and lava say which way they are heading ("Flowing North", or
  "Flowing Down" for a waterfall); source blocks say "Source".
- **Repeat after acting:** the cursor is re-narrated after a successful place,
  break or interact. This is on by default and adjustable in Settings.
- **Placement facing:** J cycles North, East, South, West, Up, Down or automatic
  (Alt+J reverses), so you can place a dispenser facing a specific way or attach a
  torch, lever or button to a specific side without turning. There is a brief
  pause between place and the block appearing while a facing is selected, since
  the choice must reach the server first. That is expected, not lag.
- **Upper or lower half:** Ctrl+J toggles upside-down stairs, top slabs, and a
  trapdoor that opens from the top.
- **When a block cannot be placed,** the reason is narrated: you are standing in
  the way, the space is occupied, there is no adjacent block to place against, or
  an entity is blocking it.

## Angle Assist

Alt+G cycles a mode that keeps your camera's pitch on the block a repetitive job
needs next: off, bridge, tunnel, staircase. You only keep your facing square
(Alt+arrow snap-turns do that) and hold the attack or use key. Only pitch is ever
touched. It always aims at the first block of the pattern still standing, so a
fast pickaxe that breaks a block at once just moves the crosshair to the next one
and never to something beyond it.

- **Tunnel:** a two-high tunnel straight ahead, head-height block then foot-height
  block, nearest first.
- **Staircase:** a staircase going down one block for every block forward. Each
  step clears three blocks: the two you walk through plus the one you drop onto.
- **Bridge:** aims at the side of the block you are bridging out from, ready for a
  block to be placed against it. That side faces the void, so face the block and
  back up until you are over its edge. It says "Back up to the edge" until you
  are, then "Ready to place".

It turns itself off, saying "Angle assist off", whenever something else takes over
where you are looking: turning on Build Mode or a camera-steering Combat Mode, a
Scanner lock or face-this action, Auto-Walk, Water Exit, Elytra Landing, the cave
trail, or turning to face the last Eye of Ender. Press Alt+G to turn it back on
afterwards.

## Tree Chopping Assist

When the log you are looking at breaks, the mod re-aims at an adjacent log, so
felling a tree does not mean re-aiming after every block. It is silent while
working and speaks only when the chain runs out.
