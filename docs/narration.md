# Narration

[Documentation index](README.md)

On this page: [On-demand readouts](#on-demand-readouts) | [Automatic narration](#automatic-narration) | [Books](#books) | [Signs](#signs) | [Chat](#chat) | [F3 debug overlay](#f3-debug-overlay) | [Settings and list screens](#settings-and-list-screens)

United Minecraft speaks through [Prism](https://github.com/ethindp/prism), which
picks the best screen reader (or, failing that, text-to-speech backend) on your
system. That gives better output than Minecraft's built-in narrator, including a
connected braille display where your screen reader supports one. When no backend
is available it falls back to the normal narrator automatically. Prism works on
Windows, macOS and Linux (x86-64 and arm64); see
[`common/src/main/resources/prism/README.md`](../common/src/main/resources/prism/README.md)
for details.

## On-demand readouts

Each of these has its own key (see [Key bindings](keybindings.md)).

- **Coordinates, standing block and biome.** Shift reads the light level
  instead (combined, plus the block/sky split) - of the block under Build Mode's
  cursor if it is active, your own block otherwise.
- **Health and hunger,** on the same 10-heart / 10-shank scale as the sighted
  heart bar, half points included. While you ride a mount (a horse, camel,
  nautilus, strider, pig and so on), its health follows. Shift reads your
  experience level. Alt reads
  your armor value and every active status effect with its level and remaining
  duration.
- **Facing direction** in compass degrees, with pitch. Shift+B resets your
  facing to north.
- **Time of day:** the current period, a clock-style time, then the day count.
  Shift reads the weather (clear, rain, snow or thunderstorm) and, at night, the
  moon phase.
- **What's in front of you:** the nearest block and/or entity ahead, with
  distance. For a block it also says which face your crosshair landed on
  (North, East, South, West, Top or Bottom) - the same raycast the vanilla
  crosshair uses, so it is exactly what a click would hit.
- **Held items:** the grave accent key (`) reads what is in your main hand and
  off-hand.
- **Boss bars** (name and percentage) and the **sidebar scoreboard**. The
  scoreboard is read exactly as the sidebar shows it - your team's own sidebar,
  server lines built from team prefixes, and hidden or reformatted scores
  included. It reads the top 15 entries; hold Alt to hear the whole thing.

## Automatic narration

- Facing direction, hotbar slot, offhand and main-hand swaps, and entering a new
  biome are narrated as they change.
- The time of day reaching sunrise, noon, sunset, night (when mobs can start
  spawning in the dark) or midnight is narrated. This is silent in the Nether
  and the End, which have no day or night.
- **Auto Crosshair Narration** (Shift+R toggles it) narrates the block your
  crosshair rests on as you look around. It speaks only when the block changes,
  so sweeping across a stone wall does not repeat "Stone". Air is never
  narrated. A Settings option repeats the narration when you move to a different
  block of the same type.
- **Camera perspective** is announced when it changes (first person, third
  person behind, third person facing).
- **Movement:** if you are stuck walking into something, it says what (a wall, a
  wall you could jump, a drop-off, water, lava) and, if there is a clear way
  around, which side to take. This can be turned off in Settings.
- **Fishing catches** are narrated.
- **Titles and subtitles** that servers put in the middle of the screen
  (countdowns, area names, minigame announcements) are narrated. A title a server
  re-sends to hold it on screen is read once. This can be turned off in Settings.
- **Chat:** a sound plays when another player's chat message arrives.
- **Action-bar HUDs** that a server keeps alive by resending the same message are
  narrated only when the text changes. The same message returning after it has
  faded from the screen is read again.
- Narration of ambient and outcome events (biome, time of day, taming, fishing
  catches, Structure Voices, how an Auto-Walk or landing ended) waits its turn
  rather than interrupting what is already being said. Anything you asked for,
  danger warnings and the radars answer immediately.

## Books

- Reading a written book narrates the current page, and turning the page
  (buttons or Page Up/Down) narrates the new one.
- Writing in a book and quill narrates the page when you open it or turn the
  page. Typing, pasting, deleting and moving the cursor narrate incrementally,
  like any other text field.

## Signs

- Signs are read when you look at them, in surrounding scans, and by the Scanner,
  which reads the side you are facing.
- The sign editor announces opening and closing, narrates the line you move to,
  and narrates typing, deleting, cutting and cursor movement.

## Chat

In the chat screen, Page Down/Up steps to a more recent/older message and
narrates it. Shift with either jumps to the newest or oldest message. The screen
always opens at the most recent message.

Ctrl+Page Down/Up cycles through the clickable text in a message (links,
clickable commands) and narrates each; Ctrl+Enter activates the selected one,
including vanilla's confirmation prompt before opening a link.

## F3 debug overlay

With the overlay open, `]` and `[` step through its lines and read each line's
current value; Shift+`]` and Shift+`[` step between sections. Opening and closing
the overlay is announced, and the first line is read when it opens. The keys are
only active while F3 is showing, and they are rebindable.

## Settings and list screens

Key Binds, Options, World Selection and similar screens narrate reliably,
including cases where vanilla goes silent or reads only a bare position.
