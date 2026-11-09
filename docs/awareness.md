# Audio awareness and warnings

[Documentation index](README.md)

Most of these run on their own; a few have a toggle key.

## Fall Warning

Always on. A drop of more than 3 blocks in the direction you are walking (or
sprint-strafing) is announced. 3 blocks is vanilla's damage-free limit, so you
never hear about a harmless step. An anvil-landing sound with narration means it
will hurt; a lighter chime with narration means it is safe (a cave lake below,
say). Settings controls the threshold and how many seconds ahead it looks.

## Powder Snow Warning

On by default (Settings > Detection & Alerts). Powder snow has no collision, so
you walk straight into it, sink and start to freeze. When it comes into your path
a few blocks ahead, a low tone and "Powder snow ahead, 3 blocks" warn you once,
and it stays quiet until you have stopped or got clear. Wearing leather boots lets
you walk across the top of powder snow, so powder snow underfoot is not a warning
for you, but a block of it at body height still is. Auto-Walk also routes around
powder snow, and only crosses the top of it if you are wearing leather boots.

## Durability and Tool Harvest Awareness

Always on, and toggleable independently in Settings.

- **Durability:** worn armor and hand items are narrated once when they cross a
  "getting low" threshold and again at an "about to break" threshold (both
  adjustable). Repairing past a threshold, with Mending or an anvil, re-arms it.
- **Tool Harvest:** starting to mine a block your held item cannot harvest
  (diamond ore with a stone pickaxe, stone with bare hands) narrates the tool or
  tier it needs, before you waste time on a block whose drop would be destroyed.

## Navigation Radar

N toggles an audio radar for front, left and right as you walk: a sound for an
obstruction (pitched differently if it is low enough to jump) and a chime for a
clear direction.

## Wall Tones

Shift+N toggles continuous looping sounds for ahead, behind, left and right,
relative to your facing. Each is louder the closer a wall is in that direction and
silent when nothing is in range, so you can feel the shape of the space around you
by ear. They are positioned in 3D around your head and work best with Minecraft's
Directional Audio setting on.

Inspired by the wall tones in
[Wrath Access](https://github.com/bradjrenshaw/wotr-access) (Pathfinder: Wrath of
the Righteous) and [Grimdark](https://github.com/ahicks92/grimdark) (Grim Dawn),
adapted for a 3D first-person world.

**What you are listening for**

- **Direction:** each has its own pitch. Ahead is highest, behind is lowest, and
  left and right sit between, right a little higher than left.
- **Distance:** louder means closer, rising sharply in the last couple of blocks.
  Silence means that direction is open for the full range (8 blocks by default).
- **Steady or pulsing:** steady is a wall, meaning anything at head or waist
  height you cannot walk through, fences included. Pulsing is a one-block step you
  could jump onto. You can hear both in one direction: a step close by with a wall
  farther behind it.
- **Common shapes:** a corridor holds both side sounds steady, and one dropping
  out as you walk is a doorway or side passage. A room plays all four, loudest
  toward the nearest wall. A corner is ahead plus one side; a dead end is ahead
  plus both sides. Outdoors is mostly silence, with pulses on hilly ground.
- **Not included:** drops and holes (Fall Warning covers those), water, lava, tall
  grass, flowers and mobs. Each direction checks one straight line from your
  center, so turning slowly in place sweeps the room.

Settings has the range, volume, a choice between **Tones** and **Noise**
(band-filtered pink noise in the same low-to-high order, which some people find
easier to place in 3D), the separate obstacle sound, and an optional ceiling sound
that gets louder as the ceiling gets lower, telling a tight tunnel from a cavern
from open sky. Every sound can be previewed from the Sound Glossary.

## Mining Radar

A switch in Settings > Detection & Alerts turns on a passive alert for valuable ore exposed nearby: a positional chime and
narration (name, distance, direction) the moment ore becomes visible. It only
flags ore you could actually see, not ore sealed behind unmined blocks.
