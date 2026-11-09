# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

United Minecraft is a Minecraft Java Edition mod, built for both the Fabric and NeoForge
loaders, that adds accessibility features for blind and low-vision players — narration, a
"Scanner" for locating and targeting things without seeing them, auto-walk, audio radars,
build/combat modes, and full keyboard navigation of menus. Almost everything is
client-side and works against any vanilla server. The two exceptions need a server half:
Structure Voices (the `structure` package: `StructureScanner`, sending
`StructuresNearbyPayload`) and marker sharing (the `share` package: `ShareRelay` and
`SharePayloads`, which only relays offers between players and stores nothing, and
`ShareCommand`, the `/umshare` command that lets a player without the mod send one; its
replies are plain text rather than translation keys, since such a client has no copy of the
language file). Both run on
the integrated server in single player, and on a dedicated server only when the mod is
installed there too. Each loader module has a common entrypoint (`UnitedMinecraftFabricCommon`,
`UnitedMinecraftNeoForgeCommon`) for them, and the payloads are optional in both directions. See [README.md](README.md) and the feature pages in [docs/](docs/) for the full feature list
and default keybindings (both are useful context when touching related code).
When a change alters what a player can do or hear, update the matching page in `docs/` in the
same commit, describing current behavior only - the release notes carry what changed.

## Commands

```bash
./gradlew build                # build both loaders, one jar each
./gradlew :fabric:runClient    # launch a dev client on Fabric
./gradlew :neoforge:runClient  # launch a dev client on NeoForge
```

The jars land in each loader module's own `build/libs/`, as
`united-minecraft-{mod_version}+mc{minecraft_version}-fabric.jar` and the matching
`-neoforge.jar`. `build` is the whole CI pipeline (see `.github/workflows/build.yml`) and
includes the unit tests; `./gradlew :fabric:test` runs just those. `./gradlew :fabric:elytraSweep`
flies the elytra landing autopilot from ~900 starts (a couple of minutes, so not part of `build`) -
run it after touching `ElytraAutopilot` or `ElytraPhysics`. Requires JDK 25.

`./gradlew build -Pneoforge_enabled=false` builds Fabric alone. That flag (also settable
in `gradle.properties`) is for the window after a Minecraft release when NeoForge has not
caught up yet: Fabric ships day one regardless, and nothing may ever make it wait.

Each loader module keeps its own dev game directory — `fabric/run/` and `neoforge/run/` —
so their worlds and settings are independent.

Versioning and release steps (tagging, GitHub release notes format) are documented in
[RELEASING.md](RELEASING.md) — read it before cutting a release, not for normal
development.
### Inspecting vanilla Minecraft behavior

Never guess at vanilla's real behavior from memory — obfuscation mappings and internal
class shapes change between versions in ways that aren't documented anywhere
outward-facing. Two sources exist.

**Loom's remapped jars, via `javap` — the one that always works.** Loom downloads
remapped (named-mapping, so class/method/field names are readable) client and common
jars, gitignored under `.gradle/`:

```
.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-<mappings-id>/<mc-version>/minecraft-clientOnly-<mappings-id>-<mc-version>.jar
.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-<mappings-id>/<mc-version>/minecraft-common-<mappings-id>-<mc-version>.jar
```

`<mappings-id>` and `<mc-version>` vary by checkout/mapping refresh — glob for them
rather than assuming a fixed value. **Pick the jar whose `<mc-version>` matches
`minecraft_version` in `gradle.properties`.** Older versions' jars can linger in the cache
after a version bump, and a bare glob (or `head -1`) will happily return one of them and
give confidently wrong answers; delete stale ones when you notice them. Extract the single `.class` file you need and read it
with `javap -p -c`; there's no source, but the names are intact and the bytecode is
usually readable enough to confirm the real logic. Fabric Loom updates on the day a
Minecraft version releases, so these jars are always for the version being targeted —
which is why this is the baseline technique rather than the fallback.

**ModDevGradle's decompiled sources — better, when they're there and current.** Setting
the NeoForge module up decompiles and patches Minecraft, leaving real Java sources at:

```
neoforge/build/moddev/artifacts/minecraft-patched-<neoforge-version>-sources.jar
```

Readable sources rather than bytecode, with NeoForge's own patches inline — which also
makes this the way to answer questions about when a NeoForge event actually fires.

**Check the version in that filename before trusting it.** It is named after the NeoForge
version, not the Minecraft one, and the file is not removed when NeoForge falls behind.
During a version bump with `neoforge_enabled=false`, the `:neoforge:` tasks never run, so
what's sitting in that directory is the *previous* Minecraft version's sources — and
reading those while porting forward gives confidently wrong answers rather than no
answer. If the version doesn't match what's being targeted, or the jar isn't there at
all, use `javap` above.

`javap` also works against the NeoForge and FML jars under `~/.gradle/caches` for
questions about loader APIs rather than vanilla ones.

## Architecture

### Project layout

The mod builds for two loaders from one shared source tree. `common/` is *source*, not a
built artifact: each loader module compiles it in that module's own environment, with
that loader's own first-party Gradle plugin (Fabric Loom, ModDevGradle). That is what
lets the two modules build independently, which is what makes a day-one Fabric release
possible while NeoForge is still catching up.

- `common/src/main/java` — essentially everything, under
  `com.nibblenerds.unitedminecraft.client`, plus the `platform` package holding the
  loader seam. This is where nearly all feature work happens. The `client` package holds the
  shared hub (`AccessibilityTickHandler`, `ClientHooks`) and the smaller features; larger
  groups live in subpackages: `keybind`, `settings`, `audio`, `scanner`, `autowalk`,
  `travel` (elytra, boat, mount), `combat` and `menu` (container, Creative and advancements
  navigation), alongside `speech`, `mixin`, `access` and `duck`. Anything one package calls
  in another must be `public`; the rest stays package-private.
- `common/src/main/resources` — the client mixin config, the bundled Prism natives under
  `prism/<platform>/`, and the assets and lang files.
- `fabric/` and `neoforge/` — each loader's entrypoint, its `Platform` implementation and
  its event bridge. About a hundred lines apiece; nothing else belongs in them.

Shared code must not reference a loader API (import or fully-qualified name). The compiler cannot catch that on its own,
since each module compiles `common/` with its own loader on the classpath — so the root
build's `checkSharedSources` task does, and each module's `check` depends on it.

### The loader seam

`Platform` (in `com.nibblenerds.unitedminecraft.platform`) is how shared code asks for
the things it needs from a loader rather than from vanilla, which turned out to be
two directories and, for marker sharing, checking whether the server has a payload and
sending one to it. The loader module installs an implementation before anything
else runs.

`ClientHooks` (in the `client` package; the methods it calls on controllers in other
packages are `public`) is where the controllers' responses to game events live, as plain static calls.
Each loader's bridge subscribes that loader's events and calls into it; the bridge makes
no decisions of its own. Ordering inside each hook reproduces what the old
per-controller event registrations produced and is load-bearing — read the doc comments
there before changing it.

Adding a feature that needs a new game event means adding a hook to `ClientHooks` and
wiring it up in *both* bridges. Don't reach for a loader API from shared code; the seam
is the only place that knows which loader is running.

### Client bootstrap

Each loader's entrypoint (`UnitedMinecraftFabric`, `UnitedMinecraftNeoForge`) does the
same three things in the same order: install the `Platform`, call `ClientHooks.init()`,
register the bridge. Most of the actual per-frame logic is *not* wired up there —
it happens inside the single tick handler described next.

### The central tick loop

`AccessibilityTickHandler` (reached from `ClientHooks.onClientTick`, which each loader's
bridge calls at the end of every client tick) is the hub almost all gameplay-facing features run through. Read it first when touching
anything that reacts to player state each tick. Key points:

- Each toggleable mode or helper (Build Mode, Combat Mode, Scanner lock-on, Auto-Walk, Water
  Exit, elytra landing, Navigation Radar, Mining Radar, Angle Assist) is its own `XxxController` class with static state and a
  `tick(client, player)` method, plus a `reset()` called when the player unloads
  (world exit) — controllers must not carry stale state across sessions.
- Modes are mutually exclusive over rotation/input in a deliberate priority order
  (see the big if/else chain in `onEndTick`): Auto-Walk > Water Exit > Elytra Landing >
  Scanner lock-on > Combat Mode > Build Mode > normal camera look.
  Ask `ScannerController.ownsRotation()` / `CombatModeController.ownsRotation()` whether a mode
  holds the camera, not whether it is merely on: Combat Mode's Beacon style, and the Scanner
  lock while Bow Beacon is aiming, are on without steering. Read the inline comments there before adding a new
  mode — most new "owns rotation" features need to be slotted into this chain, not just
  bolted on alongside it.
- Arrow keys and Page Up/Down are intentionally overloaded (continuous camera turn vs.
  snap-turn vs. Build Mode cursor stepping vs. Scanner item cycling) depending on
  current mode and Shift state — see the doc comment on those keybindings in
  `ClientKeyBindings`.
- Ambient narration (facing octant, hotbar slot, offhand swap, biome change, time-of-day
  period) is edge-triggered off a `lastXxx` static field per feature, intentionally
  skipping the first narration after a reset so a fresh world/session doesn't immediately
  announce a "change" from nothing.

### The Scanner

`ScannerController` + `ScannerCategory` implement cycling through nearby
blocks/entities by category (the `ScannerCategory` enum is the authoritative list and cycling
order; Terrain, Biomes and Structures, Players and Search are the ones that need special
handling), targeting/locking on, and coordinate lookup. The player's saved searches follow
Search in that order (`SavedSearchController`), so the Scanner works in `CategoryRef`s - a
built-in category or a saved search - whose `kind` drives all behaviour: a saved search is
Search with a term already entered, plus a name and filters of its own. `FlatAreaFinder` backs Terrain's
flat-area search, and `SignNarrationHelper` is how the Scanner and the look-ahead read signs. Ore detection
(`OreDetection`) is shared with `MiningRadarController` and deliberately only flags ore
that's actually visible, not sealed behind unmined blocks. Map Markers
(`MapMarkerController`, `MarkerNameScreen`) are a Scanner category but are
distance-independent (always lists everything in the current dimension) and persist
per-world to disk.

### Narration / speech

- `PrismController` binds the C API of [Prism](https://github.com/ethindp/prism)
  (a cross-platform library that picks the best available screen reader/TTS
  backend) via the Java Foreign Function & Memory API (no JNI shim). Native
  libraries for Windows/macOS/Linux (x86-64 and arm64) are bundled under
  `common/src/main/resources/prism/<platform>/`; another platform/architecture just
  needs a native build dropped in a matching directory and a case added to
  `NativeLibrary#detect()`. Absence or failure to load falls back to vanilla
  narration transparently (see `tryLoad()`'s catch-all). The native is loaded eagerly at
  mod init by `PrismController.init()` — a no-op call that forces the static initializer —
  and released by `shutdownIfLoaded()`, both reached through `ClientHooks` rather than
  through any loader's own lifecycle event.
- `PrismNarrator` is the layer that picks Prism vs. vanilla narrator output.
- The `mixin` package (`GameNarratorMixin`, `ScreenNarratedWidgetMixin`,
  `ScreenNarrationDelayMixin`) patches vanilla's narration so settings/list screens
  (Key Binds, Options, World Selection, etc.) narrate reliably in cases where vanilla
  itself goes silent or only reads a bare position.
- `ItemDescriptions` centralizes how an `ItemStack` gets turned into narrated text —
  reuse it instead of formatting item names ad hoc.

### Menu/inventory accessibility

`MenuAccessibilityController` and `CreativeInventoryController` (plus their
`access`/`mixin` support classes, `CreativeModeInventoryScreenAccessorMixin` /
`CreativeSlotWrapperAccessorMixin`) implement keyboard-only navigation of container
screens and the Creative inventory — Tab/Shift+Tab between sections, 2D arrow-key
movement within a section, Enter/Shift+Enter to pick up/quick-move. These use mixins to
reach otherwise-inaccessible vanilla screen internals; check `access/` for the accessor
interfaces before adding a new one.

### Mixins

One mixin config, `united_minecraft.client.mixins.json`, lives in `common/` and serves
both loaders — referenced from `fabric.mod.json`'s `client`-environment entry and from
`neoforge.mods.toml`'s `[[mixins]]` block. It requires `requireAnnotations: true` and
`defaultRequire: 1` — new injectors need an explicit `@Mixin`/injector annotation
contract, not implicit application. Add new mixin classes to the `client` array or they
won't be picked up.

Every mixin targets a vanilla class; none target a loader's own classes. That is what
lets a single config serve both loaders, and it's worth keeping that way — a mixin
against loader internals would have to be duplicated per loader and would break the
shared-source arrangement.

### Coordinate conventions worth knowing

Minecraft yaw is 0 = south, increasing toward west; several places in this codebase
(bearing narration, snap-turn, `AccessibilityTickHandler.DIRECTION_KEYS`) convert
between that and ordinary compass bearing (0 = north) or an 8-octant index — check
existing conversions there rather than re-deriving the math.

### Persistence

Settings (`UnitedMinecraftConfig`), keybindings (`KeybindConfig`) and per-world data
(`WorldScopedStore`, behind Map Markers, named blocks and saved Scanner filters) all write through `SafeFiles`:
atomic writes, and a file that fails to parse is moved aside rather than overwritten by the
next save. Use it for anything new that persists. `KeybindConfig` stores only bindings that
differ from their default, so changing a default in `ClientKeyBindings` reaches every player
who hasn't rebound that action themselves.

### Unit tests

Tests live in `common/src/test/java`, in the same packages as the code they test (so they
can reach package-private methods; `TestPlatform` is public because tests in every
package install it), and run in the Fabric module only - both modules compile
the same shared sources, and Fabric is the one always built. JUnit 5.

They cover logic only, never the running game. The rules:

- Don't fake Minecraft classes or call a controller's `tick()`. Minecraft value types that are
  plain Java (`BlockPos`, `Vec3`, `Identifier`, `KeyEvent`, `InputConstants`,
  `Component.literal`/`translatable`) are fine; anything needing registries or bootstrap
  (items, blocks, `ItemStack`) is not.
- To test logic that reads live state, pull it into a small function that takes that state
  as parameters, and have the live code call it - e.g. `ClientKeyBindings.updateAll(IntPredicate,
  int, Predicate)`, `TitleNarrationController.update`, `OverlayRepeatFilter`,
  `StructureVoiceController.direction`. Only as far as the test needs; no broader refactoring.
- Anything touching config or data files installs `TestPlatform` over a JUnit `@TempDir`.
- Tests sharing static state (keybinds, config) reset it in `@BeforeEach`.

## Localization

User-facing strings are translation keys resolved through `en_us.json`
(`common/src/main/resources/assets/united_minecraft/lang/en_us.json`), not literals — use
`Component.translatable(...)` and add the key there, following existing
`united_minecraft.narrate.*` / `united_minecraft.direction.*` naming.

Translators add other languages as a `<locale>.json` beside `en_us.json` (`vi_vn.json` is the
existing one). Minecraft falls back to English for any key a translation lacks, so a new key only
has to exist in `en_us.json`.
