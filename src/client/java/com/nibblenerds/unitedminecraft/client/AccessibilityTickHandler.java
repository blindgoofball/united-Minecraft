package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.nibblenerds.unitedminecraft.client.access.BossHealthOverlayAccess;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/**
 * Drives United Minecraft's per-tick accessibility features: coordinate/health/bearing/
 * time-of-day readouts on keypress, and facing-direction, hotbar-switch, and time-of-day
 * period narration as they change.
 */
public final class AccessibilityTickHandler {
	// Degrees the camera turns per tick while a look key is held.
	private static final float ROTATION_SPEED_DEG_PER_TICK = 4.5f;

	// Entity.turn(xo, yo) scales its input by 0.15 to emulate mouse-delta sensitivity;
	// undo that so ROTATION_SPEED_DEG_PER_TICK reads as an actual degrees-per-tick value.
	private static final float TURN_INPUT_SCALE = 1.0f / 0.15f;

	// Nudges snap-turn's floor/ceil so a press always moves at least one full 45-degree
	// step, even when already sitting exactly on a marker.
	private static final double SNAP_EPSILON = 1.0e-3;

	// Narrate Scoreboard's default cap - matching the sighted HUD's own on-screen row limit.
	// See ClientKeyBindings#NARRATE_SCOREBOARD_FULL for the full-list alternative.
	private static final int SCOREBOARD_DEFAULT_LIMIT = 10;

	// Ordered every 45 degrees starting at yaw 0 (south), matching Minecraft's yaw convention
	// (0 = south, 90 = west, 180 = north, 270 = east).
	private static final String[] DIRECTION_KEYS = {
			"united_minecraft.direction.south",
			"united_minecraft.direction.southwest",
			"united_minecraft.direction.west",
			"united_minecraft.direction.northwest",
			"united_minecraft.direction.north",
			"united_minecraft.direction.northeast",
			"united_minecraft.direction.east",
			"united_minecraft.direction.southeast",
	};

	// Vanilla's own named time-of-day ticks (matching /time set's presets, plus the point
	// mobs can start spawning in darkness - distinct from the merely visual sunset a bit
	// earlier, and the most actionable of these for knowing when it's gotten dangerous).
	// Ordered ascending, each paired with the narration key for the period it starts.
	private static final long[] TIME_PERIOD_TICKS = {0L, 6000L, 12000L, 13000L, 18000L};
	private static final String[] TIME_PERIOD_KEYS = {
			"united_minecraft.narrate.time_sunrise",
			"united_minecraft.narrate.time_noon",
			"united_minecraft.narrate.time_sunset",
			"united_minecraft.narrate.time_night",
			"united_minecraft.narrate.time_midnight",
	};
	private static final long TICKS_PER_DAY = 24000L;

	private static int lastOctant = -1;
	// Whether a rotation-owning mode (Auto-Walk, Build Mode, Scanner lock-on, etc.) was active
	// last tick - see the resync note where this is read, in onEndTick.
	private static boolean rotationOwnedLastTick;
	private static int lastHotbarSlot = -1;
	private static int lastTimePeriod = -1;
	private static Item lastOffhandItem = null;
	private static Holder<Biome> lastBiome = null;
	// Whether a screen was open as of the end of the previous tick - see the screen-just-closed
	// check in onEndTick for why this is tracked.
	private static boolean screenWasOpenLastTick;

	private AccessibilityTickHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AccessibilityTickHandler::onEndTick);
	}

	private static void onEndTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			// Reset so a fresh world/session starts without narrating stale changes.
			lastOctant = -1;
			rotationOwnedLastTick = false;
			lastHotbarSlot = -1;
			lastTimePeriod = -1;
			lastOffhandItem = null;
			lastBiome = null;
			screenWasOpenLastTick = false;
			ClientKeyBindings.resetPressState();
			BuildModeController.reset();
			CombatModeController.reset();
			MapMarkerController.reset();
			NamedBlockController.reset();
			ScannerController.reset();
			AutoWalkController.reset();
			WaterExitController.reset();
			TrailController.reset();
			MovementAssistController.reset();
			NavRadarController.reset();
			MiningRadarController.reset();
			HostileRadarController.reset();
			ArrowHitController.reset();
			FishingCatchController.reset();
			FallWarningController.reset();
			TreeChoppingAssist.reset();
			AutoCrosshairNarrationController.reset();
			DurabilityAwarenessController.reset();
			ToolHarvestAwarenessController.reset();
			return;
		}

		// Must run before anything below reads ClientKeyBindings.pressed() - see that method's
		// own doc for why the timing matters.
		ClientKeyBindings.updateAll();

		// A screen's own key handling (e.g. Chat's Enter, which sends the message and closes the
		// screen) runs synchronously off the raw key event, before updateAll() above ever polls
		// this tick - so the same keypress that just closed a screen would otherwise still read
		// as freshly pressed for any action gated on client.gui.screen() == null, in this exact
		// tick. See ClientKeyBindings#suppressJustPressedAfterScreenClose's own doc for the fix.
		boolean screenOpenNow = client.gui.screen() != null;
		if (screenWasOpenLastTick && !screenOpenNow) {
			ClientKeyBindings.suppressJustPressedAfterScreenClose();
		}
		screenWasOpenLastTick = screenOpenNow;

		// Every purely keybind-driven action (toggles, mode entry, keypress-triggered narration)
		// lives in handleKeybindActions - it needs no client.gui.screen() == null checks of its
		// own, since KeybindContext.isActive() already requires no screen be open for every
		// context it reads through (see that enum's own doc), unlike the mode-dispatch/ambient-
		// tick logic further below, which mixes real screen-dependent behavior with controller
		// tick() calls and isn't purely about keybind gating. Snap-turn's own press state comes
		// back from it because the mode-dispatch chain below still needs it, to call
		// handleSnapTurn at the right point in that chain.
		SnapTurnPresses snapTurn = handleKeybindActions(client, player);

		MapMarkerController.tick(client);
		NamedBlockController.tick(client);

		if (client.gui.screen() != null && AutoWalkController.isActive()) {
			// AutoWalkController.tick (and the cancel-key check inside it) only runs below,
			// under the screen == null branch - without this, opening any screen mid-walk
			// would leave the player's input permanently swapped to the auto-walk input and
			// make it unreachable/uncancellable until that screen closed again.
			AutoWalkController.cancel(client, player);
		}
		if (client.gui.screen() != null && WaterExitController.isActive()) {
			// Same reasoning as AutoWalkController just above - WaterExitController swaps
			// player.input the exact same way.
			WaterExitController.cancel(client, player);
		}
		if (client.gui.screen() != null && TrailController.isActive()) {
			// Same reasoning again - TrailController swaps player.input the exact same way.
			TrailController.cancel(client, player);
		}

		// Durability changes and break events must still be observed while a GUI is open.
		DurabilityAwarenessController.tick(client, player);

		if (client.gui.screen() == null) {
			if (AutoWalkController.isActive()) {
				// Owns rotation and movement input entirely until it finishes or is
				// cancelled - skip everything else that would otherwise fight it (camera
				// look, build mode, and the scanner's own key handling, which would
				// otherwise steal the Stop Lock/cancel keypress before this sees it).
				AutoWalkController.tick(client, player);
			} else if (WaterExitController.isActive()) {
				// Owns rotation and movement the same way Auto-Walk does, for the same reason.
				WaterExitController.tick(client, player);
			} else if (TrailController.isActive()) {
				// Owns rotation and movement the same way - mutually exclusive with the two
				// auto-navigate modes just above, only one should ever be walking at once.
				TrailController.tick(client, player);
			} else if (ScannerController.isLocked()) {
				ScannerController.tickLock(client, player);
				ScannerController.tick(client, player);
			} else if (CombatModeController.isActive()) {
				// Owns rotation entirely, the same as scanner lock-on above - deliberately not
				// also running build mode/normal camera look/scanner key handling here.
				CombatModeController.tick(client, player);
			} else if (BuildModeController.isActive()) {
				// Build Mode only repurposes arrow keys for the virtual cursor - it never
				// touches player movement input, so the player can still walk (and fall)
				// normally and still needs the same warning as ordinary walking below.
				BuildModeController.tick(client, player);
				ScannerController.tick(client, player);
				MiningRadarController.tick(client, player);
				FallWarningController.tick(client, player);
			} else {
				// Always safe to run both: ClientKeyBindings.updateAll() already resolved which
				// of LOOK_LEFT/RIGHT/UP/DOWN (GLOBAL, no modifiers) vs. SNAP_TURN_LEFT/RIGHT/UP/
				// DOWN (NORMAL_LOOK, Alt) wins a given tick when both are bound to the same key,
				// so handleCameraLook's isDown() reads are already false on any axis a snap-turn
				// just won - see ClientKeyBindings#updateAll's own doc.
				handleCameraLook(player);
				if (snapTurn.left() || snapTurn.right() || snapTurn.up() || snapTurn.down()) {
					handleSnapTurn(client, player, snapTurn.left(), snapTurn.right(), snapTurn.up(), snapTurn.down());
				}
				TreeChoppingAssist.tick(client, player);
				ScannerController.tick(client, player);
				MovementAssistController.tick(client, player);
				NavRadarController.tick(client, player);
				MiningRadarController.tick(client, player);
				FallWarningController.tick(client, player);
				AutoCrosshairNarrationController.tick(client, player);
			}
			// Runs no matter which mode owns rotation - the trail should keep recording real
			// movement regardless of what else is going on, except while it's driving that
			// movement itself (retracing), which would just record its own synthetic route.
			if (!TrailController.isActive()) {
				TrailController.recordTick(client, player);
			}
			// Runs no matter which mode owns rotation - a nearby, visible hostile mob is
			// worth a warning regardless of what else you're doing.
			HostileRadarController.tick(client, player);
			// Same reasoning - a fired arrow keeps flying, and needs watching for a hit,
			// regardless of what else the player is doing once it's loosed.
			ArrowHitController.tick(client, player);
			// Same reasoning - a cast line keeps waiting for a bite regardless of what else
			// the player is doing meanwhile.
			FishingCatchController.tick(client, player);
			// Same reasoning - the attack-strength meter keeps recharging regardless of what
			// else is going on, and CombatCueMode.ALWAYS needs it tracked outside Combat Mode too.
			CombatModeController.tickAttackCue(client, player);
			// Proactive tool mismatch and mining waste prevention
			ToolHarvestAwarenessController.tick(client, player);
		}

		boolean rotationOwned = BuildModeController.isActive() || ScannerController.isLocked() || AutoWalkController.isActive()
				|| CombatModeController.isActive() || WaterExitController.isActive() || TrailController.isActive();
		if (!rotationOwned) {
			if (rotationOwnedLastTick) {
				// One of those modes just handed rotation back this very tick - it may have
				// turned the player through several octants while narration was suppressed
				// (and can still re-aim the player once more on its way out, e.g. Auto-Walk
				// facing its target on arrival), so silently resync instead of narrating
				// whatever octant it happens to leave the player facing as if it were a
				// deliberate turn.
				lastOctant = Math.floorMod(Math.round(player.getYRot() / 45.0f), 8);
			} else {
				handleFacingNarration(client, player);
			}
		}
		rotationOwnedLastTick = rotationOwned;
		handleHotbarNarration(client, player);
		handleBiomeNarration(client, player);
		handleTimeOfDayNarration(client, player);
		if (client.gui.screen() == null) {
			// Only relevant in-world: the menu's own inventory-screen narration already covers
			// the offhand slot there, and vanilla's swap-hands key does nothing over a screen.
			handleOffhandNarration(client, player);
		}
	}

	/** Which of the four snap-turn keys ({@link ClientKeyBindings#SNAP_TURN_LEFT} etc.) were just pressed this tick - see {@link #handleKeybindActions}. */
	private record SnapTurnPresses(boolean left, boolean right, boolean up, boolean down) {
	}

	/**
	 * Every action this handler owns that's purely a reaction to a keypress - toggles, mode
	 * entry, and keypress-triggered narration - isolated here from {@link #onEndTick}'s own
	 * ambient/mode-dispatch logic, which mixes real screen-dependent behavior with continuous
	 * controller {@code tick()} calls and isn't purely about keybind gating the way everything in
	 * this method is. None of the {@code ClientKeyBindings.pressed(...)} reads below need their
	 * own {@code client.gui.screen() == null} check: every action read here is GLOBAL, BUILD_MODE,
	 * or NORMAL_LOOK context, and {@link KeybindContext#isActive()} already requires no screen be
	 * open for all three - see that method's own doc. Safe to call unconditionally every tick.
	 */
	private static SnapTurnPresses handleKeybindActions(Minecraft client, LocalPlayer player) {
		// Reset Rotation to North needs to release a Scanner lock-on *before* the
		// rotation-ownership chain in onEndTick runs this same tick - releasing it any later
		// (e.g. down where this key is actually handled, further below in this same method) means
		// ScannerController.tickLock would already have re-aimed at the locked entity this tick,
		// and would do so again next tick before resetRotationToNorth's effect is ever visible,
		// silently swallowing the reset entirely. isLocked() being false by the time that chain
		// reads it is what makes the reset actually stick. ClientKeyBindings.pressed is a plain
		// read (no consuming), so re-checking it again below, where it's normally handled, is safe
		// and unaffected by this early check.
		if (ScannerController.isLocked() && ClientKeyBindings.pressed(ClientKeyBindings.RESET_ROTATION_TO_NORTH)) {
			ScannerController.stopLock(client);
		}

		SnapTurnPresses snapTurn = new SnapTurnPresses(
				ClientKeyBindings.pressed(ClientKeyBindings.SNAP_TURN_LEFT),
				ClientKeyBindings.pressed(ClientKeyBindings.SNAP_TURN_RIGHT),
				ClientKeyBindings.pressed(ClientKeyBindings.SNAP_TURN_UP),
				ClientKeyBindings.pressed(ClientKeyBindings.SNAP_TURN_DOWN));

		// Blocked while locked: lock-on owns rotation until Stop Lock is pressed, and combining
		// it with the build cursor's own rotation-override would just fight it. Combat Mode owns
		// rotation the same way, so it blocks entering build mode too - toggling combat mode on
		// already turns build mode off if it was running. BUILD_MODE_RECENTER_CURSOR's own
		// KeybindContext already means it can only fire while Build Mode is active, so - unlike
		// the old Alt+I-while-active check this replaces - there's no need to guard against it
		// firing before Build Mode was ever turned on.
		if (!ScannerController.isLocked() && !CombatModeController.isActive()
				&& ClientKeyBindings.pressed(ClientKeyBindings.BUILD_MODE_RECENTER_CURSOR)) {
			BuildModeController.recenterCursor(client, player);
		} else if (!ScannerController.isLocked() && !CombatModeController.isActive()
				&& ClientKeyBindings.pressed(ClientKeyBindings.TOGGLE_BUILD_MODE)) {
			BuildModeController.toggle(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.TOGGLE_NAV_RADAR)) {
			NavRadarController.toggle(client);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.TOGGLE_MINING_RADAR)) {
			MiningRadarController.toggle(client);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.TOGGLE_COMBAT_MODE)) {
			CombatModeController.toggle(client, player);
		}
		// Same rotation-owning modes TOGGLE_BUILD_MODE is blocked against above - starting a
		// swim while one of them already owns rotation would just fight it. Auto-Walk itself
		// isn't in that list - starting a swim cancels it outright instead (see
		// WaterExitController#start), since the two are never useful to run at once anyway.
		boolean rotationFree = !ScannerController.isLocked() && !CombatModeController.isActive() && !BuildModeController.isActive();
		if (rotationFree && ClientKeyBindings.pressed(ClientKeyBindings.WATER_ESCAPE_AUTO_SWIM)) {
			WaterExitController.start(client, player);
		} else if (rotationFree && ClientKeyBindings.pressed(ClientKeyBindings.WATER_ESCAPE)) {
			WaterExitController.narrate(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.TRAIL_MARK_START)) {
			// Doesn't touch rotation or movement, so it isn't gated behind the rotation-owning
			// modes check below - marking was never gated before this key was merged either.
			TrailController.markStart(client, player);
		} else if (rotationFree && ClientKeyBindings.pressed(ClientKeyBindings.TRAIL_WALK_BACK)) {
			// Same rotation-owning modes WATER_ESCAPE is blocked against above, for the same reason.
			TrailController.start(client, player);
		} else if (rotationFree && ClientKeyBindings.pressed(ClientKeyBindings.TRAIL)) {
			TrailController.narrate(client, player);
		}

		if (ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_NAME_FOCUSED_ITEM)) {
			ScannerController.nameFocusedItem(client, player);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.PLACE_MARKER)) {
			MapMarkerController.openNameScreen(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.OPEN_SETTINGS)) {
			client.gui.setScreen(new SettingsScreen());
		}

		if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_LIGHT_LEVEL)) {
			narrateLightLevel(client, player);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_COORDINATES)) {
			narrateCoordinates(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_ARMOR_AND_EFFECTS)) {
			narrateArmorAndEffects(client, player);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_EXPERIENCE)) {
			narrateExperienceLevel(client, player);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_HEALTH)) {
			narrateHealth(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.RESET_ROTATION_TO_NORTH)) {
			// Build Mode locks the player's yaw to its own facing (see BuildModeController) -
			// snapping it to north here would silently desync the two and jerk the camera off
			// whatever facing the cursor is actually keyed to.
			if (!BuildModeController.isActive()) {
				resetRotationToNorth(client, player);
			}
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_BEARING)) {
			narrateBearing(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.TOGGLE_AUTO_CROSSHAIR_NARRATION)) {
			AutoCrosshairNarrationController.toggle(client);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.SCAN_SURROUNDINGS)) {
			SurroundingsScanner.narrateFront(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_WEATHER_AND_MOON)) {
			narrateWeatherAndMoon(client, player);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_TIME)) {
			narrateTimeOfDay(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_BOSS_BARS)) {
			narrateBossBars(client);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_SCOREBOARD_FULL)) {
			narrateScoreboard(client, player, true);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.NARRATE_SCOREBOARD)) {
			narrateScoreboard(client, player, false);
		}

		return snapTurn;
	}

	private static void handleCameraLook(LocalPlayer player) {
		float dYaw = 0.0f;
		float dPitch = 0.0f;
		if (ClientKeyBindings.LOOK_RIGHT.isDown()) {
			dYaw += ROTATION_SPEED_DEG_PER_TICK;
		}
		if (ClientKeyBindings.LOOK_LEFT.isDown()) {
			dYaw -= ROTATION_SPEED_DEG_PER_TICK;
		}
		if (ClientKeyBindings.LOOK_DOWN.isDown()) {
			dPitch += ROTATION_SPEED_DEG_PER_TICK;
		}
		if (ClientKeyBindings.LOOK_UP.isDown()) {
			dPitch -= ROTATION_SPEED_DEG_PER_TICK;
		}
		if (dYaw != 0.0f || dPitch != 0.0f) {
			player.turn(dYaw * TURN_INPUT_SCALE, dPitch * TURN_INPUT_SCALE);
		}
	}

	/**
	 * Alt+arrows snap-turn to the nearest 45 degree marker instead of turning smoothly:
	 * left/right step yaw to the previous/next compass octant (announced automatically by
	 * {@link #handleFacingNarration}, since it lands exactly on an octant boundary), and
	 * up/down step pitch through -90/-45/0/45/90 (announced here, since nothing else covers
	 * pitch). The epsilon nudge before flooring/ceiling means a press always moves at least
	 * one full step, even when already sitting exactly on a 45 degree marker.
	 *
	 * <p>Takes pre-computed rising-edge presses rather than reading the keys' click queues
	 * itself - see {@link ClientKeyBindings#pressed} for why.
	 */
	private static void handleSnapTurn(Minecraft client, LocalPlayer player,
			boolean leftPressed, boolean rightPressed, boolean upPressed, boolean downPressed) {
		boolean yawChanged = false;
		boolean pitchChanged = false;
		if (leftPressed) {
			player.setYRot(snapDown45(player.getYRot()));
			yawChanged = true;
		}
		if (rightPressed) {
			player.setYRot(snapUp45(player.getYRot()));
			yawChanged = true;
		}
		if (upPressed) {
			player.setXRot(snapDown45(player.getXRot()));
			pitchChanged = true;
		}
		if (downPressed) {
			player.setXRot(snapUp45(player.getXRot()));
			pitchChanged = true;
		}

		if (yawChanged || pitchChanged) {
			player.setOldRot();
			player.setYHeadRot(player.getYRot());
		}
		if (pitchChanged) {
			narrateBearing(client, player);
		}
	}

	private static float snapDown45(float degrees) {
		return (float) (Math.floor(degrees / 45.0 - SNAP_EPSILON) * 45.0);
	}

	private static float snapUp45(float degrees) {
		return (float) (Math.ceil(degrees / 45.0 + SNAP_EPSILON) * 45.0);
	}

	private static void handleFacingNarration(Minecraft client, LocalPlayer player) {
		int octant = Math.floorMod(Math.round(player.getYRot() / 45.0f), 8);
		if (octant != lastOctant) {
			if (lastOctant != -1) {
				client.getNarrator().saySystemNow(Component.translatable(DIRECTION_KEYS[octant]));
			}
			lastOctant = octant;
		}
	}

	private static void handleHotbarNarration(Minecraft client, LocalPlayer player) {
		int slot = player.getInventory().getSelectedSlot();
		if (slot != lastHotbarSlot) {
			if (lastHotbarSlot != -1) {
				narrateHotbarSlot(client, player, slot);
			}
			lastHotbarSlot = slot;
		}
	}

	private static void narrateHotbarSlot(Minecraft client, LocalPlayer player, int slot) {
		ItemStack stack = player.getInventory().getItem(slot);
		Component name = stack.isEmpty()
				? Component.translatable("united_minecraft.narrate.hotbar_empty")
				: ItemDescriptions.describe(stack, player);
		client.getNarrator().saySystemNow(name);
	}

	/**
	 * Edge-triggered on the offhand's {@link Item} identity alone, not the full stack - {@link
	 * ItemStack#matches} also compares count and components (durability, enchantments, etc.),
	 * which would announce a false "swap" for something as ordinary as eating one item off an
	 * offhand food stack or a shield/totem taking damage.
	 */
	private static void handleOffhandNarration(Minecraft client, LocalPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		Item offhandItem = offhand.getItem();
		if (lastOffhandItem != null && offhandItem != lastOffhandItem) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.hands_swapped",
					describeHand(player.getMainHandItem(), player), describeHand(offhand, player)));
		}
		lastOffhandItem = offhandItem;
	}

	private static Component describeHand(ItemStack stack, LocalPlayer player) {
		return stack.isEmpty()
				? Component.translatable("united_minecraft.narrate.hotbar_empty")
				: ItemDescriptions.describe(stack, player);
	}

	private static void handleBiomeNarration(Minecraft client, LocalPlayer player) {
		Holder<Biome> biome = player.level().getBiome(player.blockPosition());
		if (biome != lastBiome) {
			if (lastBiome != null) {
				client.getNarrator().saySystemNow(
						Component.translatable("united_minecraft.narrate.biome_entered", biomeName(biome)));
			}
			lastBiome = biome;
		}
	}

	/** Package-private - {@link ScannerController} reuses this for its Biomes category. */
	static Component biomeName(Holder<Biome> biome) {
		Identifier id = biome.unwrapKey().map(ResourceKey::identifier).orElse(null);
		return Component.translatable(Util.makeDescriptionId("biome", id));
	}

	/**
	 * Announces sunrise, noon, sunset, night (when mobs can start spawning in the dark -
	 * distinct from the merely visual sunset a bit earlier), and midnight as each begins.
	 * Wherever the player's current dimension has no day/night cycle at all (the Nether,
	 * say), its clock just always reads 0 and this narrates "sunrise" once on arrival and
	 * then never again, same as anywhere else that stops changing.
	 */
	private static void handleTimeOfDayNarration(Minecraft client, LocalPlayer player) {
		int period = timePeriodIndex(player);
		if (period != lastTimePeriod) {
			if (lastTimePeriod != -1) {
				client.getNarrator().saySystemNow(Component.translatable(TIME_PERIOD_KEYS[period]));
			}
			lastTimePeriod = period;
		}
	}

	private static int timePeriodIndex(LocalPlayer player) {
		long timeOfDay = Math.floorMod(player.level().getDefaultClockTime(), TICKS_PER_DAY);
		int period = 0;
		for (int i = TIME_PERIOD_TICKS.length - 1; i >= 0; i--) {
			if (timeOfDay >= TIME_PERIOD_TICKS[i]) {
				period = i;
				break;
			}
		}
		return period;
	}

	private static void narrateTimeOfDay(Minecraft client, LocalPlayer player) {
		long clockTicks = player.level().getDefaultClockTime();
		long timeOfDay = Math.floorMod(clockTicks, TICKS_PER_DAY);
		long day = Math.floorDiv(clockTicks, TICKS_PER_DAY) + 1;

		// Tick 0 is 6:00 AM (sunrise); the day/night cycle runs 24000 ticks = 24 in-game hours.
		int minutesFrom6am = (int) (timeOfDay * 24 * 60 / TICKS_PER_DAY);
		int hour24 = (6 + minutesFrom6am / 60) % 24;
		int minute = minutesFrom6am % 60;
		int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
		Component amPm = Component.translatable(hour24 < 12
				? "united_minecraft.narrate.time_am"
				: "united_minecraft.narrate.time_pm");

		Component message = Component.translatable("united_minecraft.narrate.time_day", day)
				.append(Component.literal(". "))
				.append(Component.translatable(TIME_PERIOD_KEYS[timePeriodIndex(player)]))
				.append(Component.literal(". "))
				.append(Component.translatable("united_minecraft.narrate.time_clock",
						hour12, String.format(Locale.ROOT, "%02d", minute), amPm));
		client.getNarrator().saySystemNow(message);
	}

	/** Shares the Narrate Time of Day key via Shift, same layering as the mod's other dual-purpose keys. */
	private static void narrateWeatherAndMoon(Minecraft client, LocalPlayer player) {
		Level level = player.level();
		String weatherKey;
		if (level.isThundering()) {
			weatherKey = "united_minecraft.narrate.weather_thunder";
		} else if (level.isRaining()) {
			BlockPos pos = player.blockPosition();
			Biome.Precipitation precipitation = level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel());
			weatherKey = precipitation == Biome.Precipitation.SNOW
					? "united_minecraft.narrate.weather_snow"
					: "united_minecraft.narrate.weather_rain";
		} else {
			weatherKey = "united_minecraft.narrate.weather_clear";
		}
		Component message = Component.translatable(weatherKey);

		// Moon phase only matters once it's actually visible - matches the same night threshold
		// used for the ambient "night" time-of-day narration.
		if (timePeriodIndex(player) >= 3) {
			MoonPhase phase = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.MOON_PHASE);
			message = message.copy().append(Component.literal(". ")).append(Component.translatable(moonPhaseKey(phase)));
		}
		client.getNarrator().saySystemNow(message);
	}

	private static String moonPhaseKey(MoonPhase phase) {
		return switch (phase) {
			case FULL_MOON -> "united_minecraft.narrate.moon_full";
			case WANING_GIBBOUS -> "united_minecraft.narrate.moon_waning_gibbous";
			case THIRD_QUARTER -> "united_minecraft.narrate.moon_third_quarter";
			case WANING_CRESCENT -> "united_minecraft.narrate.moon_waning_crescent";
			case NEW_MOON -> "united_minecraft.narrate.moon_new";
			case WAXING_CRESCENT -> "united_minecraft.narrate.moon_waxing_crescent";
			case FIRST_QUARTER -> "united_minecraft.narrate.moon_first_quarter";
			case WAXING_GIBBOUS -> "united_minecraft.narrate.moon_waxing_gibbous";
		};
	}

	private static void narrateCoordinates(Minecraft client, LocalPlayer player) {
		BlockPos pos = player.blockPosition();
		Component standingOn = player.level().getBlockState(pos.below()).getBlock().getName();
		Component message = Component.translatable("united_minecraft.narrate.coordinates", pos.getX(), pos.getY(), pos.getZ())
				.append(Component.literal(". "))
				.append(Component.translatable("united_minecraft.narrate.standing_on", standingOn))
				.append(Component.literal(". "))
				.append(Component.translatable("united_minecraft.narrate.biome_label", biomeName(player.level().getBiome(pos))));
		client.getNarrator().saySystemNow(message);
	}

	/**
	 * Shares the Narrate Coordinates key via Shift, the same layering {@link
	 * ClientKeyBindings} already uses elsewhere - light level is the same kind of "what's
	 * around me right now" fact as that key's normal readout. Reports the cursor's block
	 * while Build Mode is active (matching what that mode already treats as "here"),
	 * the player's own block otherwise.
	 */
	private static void narrateLightLevel(Minecraft client, LocalPlayer player) {
		BlockPos pos = BuildModeController.isActive() ? BuildModeController.getCursor() : player.blockPosition();
		int combined = player.level().getMaxLocalRawBrightness(pos);
		int blockLight = player.level().getBrightness(LightLayer.BLOCK, pos);
		int skyLight = player.level().getBrightness(LightLayer.SKY, pos);
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.light_level", combined, blockLight, skyLight));
	}

	/** Reports on the same 10-heart/10-shank scale the sighted HUD shows, half-point precision included. */
	private static void narrateHealth(Minecraft client, LocalPlayer player) {
		double hearts = player.getHealth() / 2.0;
		double maxHearts = player.getMaxHealth() / 2.0;
		double hunger = player.getFoodData().getFoodLevel() / 2.0;
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.health", formatHalf(hearts), formatHalf(maxHearts), formatHalf(hunger)));
	}

	private static String formatHalf(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	/** Shares the Narrate Health key via Shift, same layering as Narrate Coordinates' light-level readout. */
	private static void narrateExperienceLevel(Minecraft client, LocalPlayer player) {
		int level = player.experienceLevel;
		int progress = Math.round(player.experienceProgress * 100);
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.experience_level", level, progress));
	}

	/**
	 * Alt on the Narrate Health key: armor value (the same number the sighted armor bar shows),
	 * then every currently active status effect - name, level (only stated once it's actually
	 * above the base level, matching how a sighted player wouldn't see a roman numeral for a
	 * level 1 effect either), and remaining duration, or that it doesn't expire on its own
	 * (Bad Omen, most commonly) for one with no timer.
	 */
	private static void narrateArmorAndEffects(Minecraft client, LocalPlayer player) {
		MutableComponent message = Component.translatable(
				"united_minecraft.narrate.armor_value", player.getArmorValue()).copy();

		Collection<MobEffectInstance> effects = player.getActiveEffects();
		if (effects.isEmpty()) {
			message = message.append(Component.literal(". ")).append(
					Component.translatable("united_minecraft.narrate.status_no_effects"));
		} else {
			for (MobEffectInstance effect : effects) {
				message = message.append(Component.literal(". ")).append(describeStatusEffect(effect));
			}
		}
		client.getNarrator().saySystemNow(message);
	}

	private static Component describeStatusEffect(MobEffectInstance effect) {
		Component name = effect.getEffect().value().getDisplayName();
		if (effect.getAmplifier() > 0) {
			name = Component.translatable("united_minecraft.narrate.status_effect_level", name, effect.getAmplifier() + 1);
		}
		if (effect.isInfiniteDuration()) {
			return name.copy().append(Component.literal(", ")).append(
					Component.translatable("united_minecraft.narrate.status_effect_no_timer"));
		}
		int totalSeconds = effect.getDuration() / 20;
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return name.copy().append(Component.literal(", ")).append(Component.translatable(
				"united_minecraft.narrate.status_effect_duration", minutes, String.format(Locale.ROOT, "%02d", seconds)));
	}

	private static void narrateBearing(Minecraft client, LocalPlayer player) {
		// Minecraft's yaw is 0 at south, going towards west as it increases; shift it so
		// the reported bearing instead follows the usual compass convention (0 = north).
		int bearing = Math.floorMod(Math.round(player.getYRot()) + 180, 360);
		int pitch = Math.round(player.getXRot());
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.bearing", bearing, pitch));
	}

	private static void resetRotationToNorth(Minecraft client, LocalPlayer player) {
		// Bearing 0 (north) is yaw 180 in Minecraft's own convention (0 = south).
		player.setYRot(180.0f);
		player.setXRot(0.0f);
		player.setOldRot();
		player.setYHeadRot(180.0f);
		narrateBearing(client, player);
	}

	/** Narrates every currently active boss bar in one burst, via {@link BossHealthOverlayAccess}. */
	private static void narrateBossBars(Minecraft client) {
		BossHealthOverlay overlay = client.gui.hud.getBossOverlay();
		Collection<LerpingBossEvent> events = ((BossHealthOverlayAccess) overlay).unitedMinecraft$getEvents().values();
		if (events.isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.boss_bar_none"));
			return;
		}
		MutableComponent message = null;
		for (LerpingBossEvent event : events) {
			int percent = Math.round(event.getProgress() * 100.0f);
			Component line = Component.translatable("united_minecraft.narrate.boss_bar", event.getName(), percent);
			message = message == null ? line.copy() : message.append(Component.literal(". ")).append(line);
		}
		client.getNarrator().saySystemNow(message);
	}

	/**
	 * Narrates the sidebar scoreboard, sorted descending by score value with alphabetical
	 * tiebreak on owner name (vanilla's own sort comparator isn't public, so this reimplements
	 * its well-known behavior). Only the first {@link #SCOREBOARD_DEFAULT_LIMIT} entries are read
	 * by default; {@code full} (the mod's Alt modifier) reads every entry instead.
	 */
	private static void narrateScoreboard(Minecraft client, LocalPlayer player, boolean full) {
		Scoreboard scoreboard = player.level().getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scoreboard_none"));
			return;
		}
		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
				.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));

		int limit = full ? entries.size() : Math.min(SCOREBOARD_DEFAULT_LIMIT, entries.size());
		MutableComponent message = Component.translatable("united_minecraft.narrate.scoreboard_header", objective.getDisplayName()).copy();
		for (int i = 0; i < limit; i++) {
			PlayerScoreEntry entry = entries.get(i);
			message.append(Component.literal(". ")).append(Component.translatable(
					"united_minecraft.narrate.scoreboard_line", entry.ownerName(), entry.value()));
		}
		if (!full && entries.size() > limit) {
			message.append(Component.literal(". ")).append(Component.translatable(
					"united_minecraft.narrate.scoreboard_more", entries.size() - limit));
		}
		client.getNarrator().saySystemNow(message);
	}
}
