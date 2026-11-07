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
- It goes through closed doors the way a villager does (Auto-Walk Opens Doors in
  Settings > Modes & Movement, on by default): it stops a couple of blocks short,
  opens the door, waits for it to swing open, and closes it again once you are
  through. A door that was already open is left open. Iron doors stay shut, since
  they need redstone, and a door that will not open stops the walk.
- It routes around powder snow, and only crosses the top of it if you are wearing
  leather boots.
- Riding a saddled horse, donkey, mule or similar, it rides you there instead,
  routing for the mount's own size. It charges and releases the mount's jump
  to climb ledges and clear fences, up to the height that mount can actually
  jump (your H key reports it). A mount is wider
  than you and cannot squeeze up to a doorway, so it counts as arrived within
  about three blocks of the target, still inside your reach.
- Riding a happy ghast wearing a harness, it flies you there through the air,
  steering by turning your view toward the next point on the way. The ghast
  turns slowly, so it eases round before pushing on, and it counts as arrived
  from a few blocks out since it cannot get close to walls.
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
touch down gently, diving steeply from a height and levelling off to shed speed
just above the ground (a wide circle only if it cannot reach the spot from where
you are), and hands control back when you land. Backspace cancels, and control
is handed back if terrain is unavoidable.

**Flying to a Scanner target:** while gliding, Shift+Enter on something in the
Scanner flies you toward it instead of walking (Build Mode's walk-to-cursor key
does the same). The target itself is rarely somewhere to land, so the mod lands on
the nearest flat, safe patch within 8 blocks of it, never using fireworks, and says
how close to the target it will land as you set off ("Flying toward Village,
landing 6 blocks southwest of it") and how far from it you came down ("Landed 6
blocks northeast of Village").
Landing within 3 blocks counts as arriving. If it cannot, it says why: there is no
safe place to land near the target, terrain is in the way, or you are too low ("Too
low to reach Village, you need about 40 more blocks of height"). Turn on Walk to
Target After Elytra Landing in Settings > Modes & Movement to have it walk the rest
of the way once you are down. Backspace cancels the flight.

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

## Maps and compasses

Holding a treasure or explorer map, or a compass, gives you a bell toward where it points. Its
pitch rises and it repeats faster as you close in, and every few seconds the mod says the
bearing and distance. Nothing is needed to turn it on beyond holding the item, and putting it
away stops it. If you hold both, the main hand's is the one that sounds.

- A normal compass points to world spawn, a lodestone compass to its lodestone, and a recovery
  compass to where you last died. The bearing says which. For a lodestone and your death spot it
  also says how many blocks above or below you the target is.
- A compass's target is exact, so it is reached within a few blocks, and not from far above or
  below it. A map's is only approximate, so the bell calls it reached from 16 blocks.
- A compass spins in the game when it has nothing to point at, and the mod stays quiet then,
  saying so once: a lodestone compass whose lodestone is gone, a recovery compass before you
  have died, or any compass whose target is in another dimension. That includes a normal
  compass in the Nether or the End.
- Map Beacon and Compass Beacon can each be switched off in the General settings.

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
