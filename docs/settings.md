# Settings

[Documentation index](README.md)

F6 opens the Settings hub, which has five categories. Each is its own short,
scrollable list with a Back button (or Escape) returning to the hub. Settings are
saved to a config file shared across every world and server.

| Category | What it holds |
| --- | --- |
| Detection & Alerts | On/off switches for Hostile Radar's melee-range alert, Fall Warning and Powder Snow Warning; the Mining Radar switch; range and threshold sliders for Hostile Radar, Fall Warning (including how many seconds ahead it looks), Mining Radar, Navigation Radar and the Scanner; the Scanner's toggles for skipping empty categories and auto-locking onto a mob after walking to it; the Biomes and Structures range; Structure Voices (on/off, range, volume, and narrating distance and direction); Movement Assist, which controls the "wall ahead" and drop-off narration |
| Wall Tones | Range, volume, Tones or Noise style, the separate pulsing obstacle sound, and the ceiling sound |
| Modes & Movement | Arrow-key camera turn speed (50% to 300%; snap-turn is unaffected); re-narrating Build Mode's cursor after a place, break or interact; whether Build Mode's area markers also limit height; Combat Mode Style (Lock On or Beacon); Bow Beacon; the Combat Mode attack-ready cue (off, Combat Mode only, or always); elytra flight alerts; walking the rest of the way after an elytra flight to a Scanner target; boat steering assist and boat shore and obstacle alerts; Eye of Ender sounds; Auto-Walk's always-sprint, open-doors and swim toggles; the mount jump charge cue |
| General | Audio Cue Volume (5-100%, scaling every short sound cue the mod plays so they can be balanced against the game's own sound; Wall Tones and Structure Voices keep their own volume); title narration; ignoring marker shares from other players; whether switching hotbar slots reads the item's tooltip; repeating Auto Crosshair Narration for the same block type; Durability Awareness and its warning and critical thresholds; Tool Harvest Awareness; Precise Coordinates; Map Beacon; Compass Beacon; the Attack and Use Keys swap |
| AI Description | Experimental. Which service describes your surroundings - Google Gemini, Anthropic Claude, Ollama running on your own machine, or any OpenAI-compatible service - with its API key (where it needs one), model and server address. Only the chosen service's fields are shown |

## Keybindings

Settings > Keybindings is United Minecraft's own screen for rebinding every
action to any key or modifier combination. It is separate from vanilla's Options >
Controls. Only bindings that differ from their default are saved, so default
changes in later versions reach you unless you rebound that action yourself.
A Category button at the top of the list narrows it to one group of actions
(Narration, Scanner and Markers, Movement and Modes, Build Mode, Inventory Screens
or General). It starts on All, and each press moves to the next category.

## Recommended attack and use keys

On the first title screen after installing, the mod offers once to move vanilla's
Attack/Destroy to comma and Place/Use to period, its recommended setup.
The Attack and Use Keys button in Settings > General swaps both between the mouse
buttons and your other keys, remembering whatever those keys were bound to when
you left them, and shows which setup is active.

## Sound and Cue Glossary

A button in Settings opens a scrollable, keyboard-navigable list of every audio
cue the mod plays and what it means. Selecting an entry (Enter or a click)
replays the sound at the exact volume and pitch the mod uses, so you can learn to
recognize it.

## AI Description

The Describe Surroundings with AI key (unassigned by default) reads the land around you, sends that
description to the service chosen here, and speaks the answer. Pressing it again while a request is
out says it is still describing, and a soft click every two seconds shows it is still working. What is sent is only a text summary of the terrain, water, walls and
ceiling, with positions relative to you: no player names, world name, server address or coordinates.
Gemini, Anthropic and OpenAI-compatible services are cloud services that use your own account and key; Ollama
runs locally and sends nothing off your machine. The key is stored in plain text in
`united_minecraft_ai.json` in the config folder, a separate file from your settings so that sharing
your settings does not share it.

## Config files

Settings, keybindings and per-world data (map markers, named blocks) are written
atomically, so a crash cannot corrupt them, and a file that fails to parse is set
aside instead of overwritten.
