# Settings

[Documentation index](README.md)

F6 opens the Settings hub, which has four categories. Each is its own short,
scrollable list with a Back button (or Escape) returning to the hub. Settings are
saved to a config file shared across every world and server.

| Category | What it holds |
| --- | --- |
| Detection & Alerts | On/off switches for Hostile Radar's melee-range alert, Fall Warning and Powder Snow Warning; range and threshold sliders for Hostile Radar, Fall Warning (including how many seconds ahead it looks), Mining Radar, Navigation Radar and the Scanner; the Scanner's toggles for skipping empty categories and auto-locking onto a mob after walking to it; the Biomes and Structures range; Structure Voices (on/off, range, volume, and narrating distance and direction); Movement Assist, which controls the "wall ahead" and drop-off narration |
| Wall Tones | Range, volume, Tones or Noise style, the separate pulsing obstacle sound, and the ceiling sound |
| Modes & Movement | Arrow-key camera turn speed (50% to 300%; snap-turn is unaffected); re-narrating Build Mode's cursor after a place, break or interact; Combat Mode Style (Lock On or Beacon); Bow Beacon; the Combat Mode attack-ready cue (off, Combat Mode only, or always); elytra flight alerts; walking the rest of the way after an elytra flight to a Scanner target; boat steering assist and boat shore and obstacle alerts; Eye of Ender sounds; Auto-Walk's always-sprint and swim toggles; the mount jump charge cue |
| General | Audio Cue Volume (5-100%, scaling every short sound cue the mod plays so they can be balanced against the game's own sound; Wall Tones and Structure Voices keep their own volume); title narration; repeating Auto Crosshair Narration for the same block type; Durability Awareness and its warning and critical thresholds; Tool Harvest Awareness; Precise Coordinates; Map Beacon |

## Keybindings

Settings > Keybindings is United Minecraft's own screen for rebinding every
action to any key or modifier combination. It is separate from vanilla's Options >
Controls. Only bindings that differ from their default are saved, so default
changes in later versions reach you unless you rebound that action yourself.

## Recommended attack and use keys

On the first title screen after installing, the mod offers once to move vanilla's
Attack/Destroy to comma and Place/Use to period, its recommended setup. F7
swaps both between the mouse buttons and your other keys, remembering whatever
those keys were bound to when you left them.

## Sound and Cue Glossary

A button in Settings opens a scrollable, keyboard-navigable list of every audio
cue the mod plays and what it means. Selecting an entry (Enter or a click)
replays the sound at the exact volume and pitch the mod uses, so you can learn to
recognize it.

## Config files

Settings, keybindings and per-world data (map markers, named blocks) are written
atomically, so a crash cannot corrupt them, and a file that fails to parse is set
aside instead of overwritten.
