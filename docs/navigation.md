# Navigation and exploration

[Documentation index](README.md)

On this page: [Auto-Walk](#auto-walk) | [Water Exit](#water-exit) | [Elytra](#elytra) | [Boats](#boats) | [Cave Trail](#cave-trail) | [Eye of Ender](#eye-of-ender) | [Structure Voices](#structure-voices)

## Auto-Walk

Walks you to anything the Scanner found or Build Mode's cursor points at (use
Shift with the target key). It works on any server with no special permissions.

- Hold your sprint key to get there faster, or turn on Auto-Walk: Always Sprint
  in Settings to sprint the whole way.
- It climbs and descends ladders and vines, and swims across water when it can
  reach the target (Auto-Walk Swim in Settings, on by default). It will not leave
  you stranded in water.
- It will not walk you onto an open trapdoor and drop you through it.
- Cancel at any time with Backspace.

## Water Exit

Y reports the distance and direction to the nearest way out of water, and Shift+Y
swims you there. The route is traced through the water you are actually in, never
through walls or across open air, so it never points at a ledge you cannot reach.
Backspace cancels.

### Elytra

While gliding, Y reports the nearest safe place to land that you can glide to:
flat, solid ground (never water, lava, leaves or anything harmful) with a clear
line down, within the distance your height allows. If there is none, it says so.

Shift+Y flies you onto it. The mod takes the camera, steering and pitching to
touch down gently, circling down first if you are too high, and hands control back
when you land. Backspace cancels, and control is handed back if terrain is
unavoidable.

**Elytra flight alerts** (on by default, Settings > Modes & Movement) predict the
rest of your flight every tick, assuming you keep looking where you are, and check
it against the real blocks. A wall, a landing too fast to survive unhurt, harmful
ground (cactus, magma, a campfire) or lava is spoken a few seconds before it
happens ("Wall ahead", "Wall close", "Wall now"), then beeped at increasing urgency. A gentle
landing gets a chime, "Safe landing", and the height above ground at 50, 30, 20,
10 and 5 blocks. Only hits that would actually hurt count, and armor is ignored,
so it errs on the cautious side.

## Boats

While you drive a boat, the camera stays on the way the boat is facing, and the
keys that turn the camera turn the boat instead. The arrow keys turn it smoothly,
and Alt+arrows snap to the next compass direction, so turning to go west is one
press rather than a guess at how long to hold A. Your own A and D still work and
take over from a turn in progress. A boat only holds a heading to within about 5
degrees, because the smallest nudge it takes turns it ten.

A Scanner lock-on or Combat Mode takes the camera back, so you can still fight
from the boat; A and D steer as usual until you release it. Build Mode and the
auto-navigate modes do the same. Turn this off with Boat Steering Assist in
Settings > Modes & Movement.

**Boat alerts** (on by default, Settings > Modes & Movement) predict the next few
seconds of your course from the boat's speed and heading, and check it against the
real blocks. Two different cues tell you what is coming:

- **Land**: you will sail out of the water onto the bank. Low, thudding beeps and
  "Land ahead" with the distance.
- **Obstacle**: you will hit a block while still in the water - a cliff, a pier,
  a pillar, a wall of ice - and stop there. Higher, sharper beeps and "Obstacle
  ahead" with the distance.

Both are spoken a few seconds out and beep faster as they near, ending with "Land
now" or "Obstacle now". They only describe what is coming; what to do is up to you. Lily pads do not count.

## Cave Trail

Your positions are recorded as a breadcrumb trail as you walk, always on. X
reports the distance and direction back to the start of the trail, Shift+X walks
you back along it one recorded step at a time, and Alt+X clears the trail and
marks your current spot as a new start. Alt+X is useful right as you enter a cave.

Retracing follows the exact route you took, so it is always reachable. If it
reaches a spot where you fell or dropped down, it stops and tells you to place a
block there; place one and retrace again to continue. Backspace cancels.

## Eye of Ender

Throw an Eye of Ender and the mod follows it. Once it has flown a few blocks you
hear its heading ("Eye of Ender heading northeast") and a ping from the eye's
direction. When it ends you are told whether it dropped, so you can pick it up, or
shattered. Alt+B turns you to face the last eye's heading at any time afterwards.

An eye far from the stronghold always flies the same fixed distance. One that
stops noticeably short is near it, and the mod then says the stronghold is close,
with roughly how many blocks away and in which direction. This is estimated from
how the eye moved, since the game never tells the client where the stronghold is,
so treat it as a good estimate. The sounds can be switched off in Settings >
Modes & Movement.

## Structure Voices

When a village, ocean monument, ruined portal, shipwreck or any other structure
you could come across above ground comes within range, its name is spoken once,
from its direction: fly past a village on your left and you hear "Village" in your
left ear. It is passive, with no key; the Scanner's Biomes and Structures category
lists what is nearby on demand.

- **Range:** 96 blocks by default, adjustable up to 128, and held to your render
  distance when that is lower.
- **Underground structures** (strongholds, mineshafts, ancient cities, trial
  chambers, buried treasure) are never announced, the same no-x-ray rule the
  Scanner applies to ore. Dimensions with no surface, such as the Nether and End,
  are not filtered.
- Each structure is announced once per visit, and again only after you have gone
  well away and come back.
- **Direction** is fixed for the whole word: stereo balance is left/right (fully
  in one ear at 90 degrees), pitch is height relative to where you are looking,
  and a structure behind you sounds muffled and a little quieter.
- **The voice** is your system's own text-to-speech, rendered to audio so the game
  can pan and pitch it: OneCore or SAPI on Windows, AVSpeech (or `say`) on macOS,
  and `espeak-ng` on Linux. Where none can render audio, structures are narrated
  through your screen reader instead. Names come from the language file, so every
  village variant is "Village"; a modded structure is spoken from its id.
- **Server:** structure locations are known only to the server, so this works in
  single player out of the box, and in multiplayer when United Minecraft is also
  installed on the server (see [Installation](installation.md)).
- Settings has on/off, range, volume, and whether to also narrate distance and
  compass direction. To silence specific structures, add their ids (for example
  `"minecraft:mineshaft"`) to `structureVoiceMuted` in the config file.
