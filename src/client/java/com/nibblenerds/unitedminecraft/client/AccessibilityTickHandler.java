package com.nibblenerds.unitedminecraft.client;

import java.util.Locale;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

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
	private static int lastHotbarSlot = -1;
	private static int lastTimePeriod = -1;
	private static ItemStack lastOffhand = null;
	private static Holder<Biome> lastBiome = null;

	// Rising-edge state for snap-turn's arrow keys - see the note on pressedEdge() for why
	// this replaces KeyMapping's own click-queue tracking. Updated every tick regardless of
	// which mode is active (not just while snap-turn itself runs) so it stays accurate
	// across mode switches - e.g. an arrow key held while build mode is active shouldn't
	// misfire as a fresh press the moment build mode turns off.
	private static boolean snapLeftHeld;
	private static boolean snapRightHeld;
	private static boolean snapUpHeld;
	private static boolean snapDownHeld;

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
			lastHotbarSlot = -1;
			lastTimePeriod = -1;
			lastOffhand = null;
			lastBiome = null;
			snapLeftHeld = snapRightHeld = snapUpHeld = snapDownHeld = false;
			BuildModeController.reset();
			CombatModeController.reset();
			ScannerController.reset();
			AutoWalkController.reset();
			MovementAssistController.reset();
			NavRadarController.reset();
			MiningRadarController.reset();
			TreeChoppingAssist.reset();
			return;
		}

		boolean snapLeftPressed = pressedEdge(ClientKeyBindings.LOOK_LEFT, snapLeftHeld);
		boolean snapRightPressed = pressedEdge(ClientKeyBindings.LOOK_RIGHT, snapRightHeld);
		boolean snapUpPressed = pressedEdge(ClientKeyBindings.LOOK_UP, snapUpHeld);
		boolean snapDownPressed = pressedEdge(ClientKeyBindings.LOOK_DOWN, snapDownHeld);
		snapLeftHeld = ClientKeyBindings.LOOK_LEFT.isDown();
		snapRightHeld = ClientKeyBindings.LOOK_RIGHT.isDown();
		snapUpHeld = ClientKeyBindings.LOOK_UP.isDown();
		snapDownHeld = ClientKeyBindings.LOOK_DOWN.isDown();

		// Blocked while locked: lock-on owns rotation until Stop Lock is pressed, and
		// combining it with the build cursor's own rotation-override would just fight it.
		// Combat Mode owns rotation the same way, so it blocks entering build mode too -
		// toggling combat mode on already turns build mode off if it was running.
		if (!ScannerController.isLocked() && !CombatModeController.isActive()
				&& ClientKeyBindings.TOGGLE_BUILD_MODE.consumeClick()) {
			BuildModeController.toggle(client, player);
		}
		if (ClientKeyBindings.TOGGLE_NAV_RADAR.consumeClick()) {
			NavRadarController.toggle(client);
		}
		if (ClientKeyBindings.TOGGLE_MINING_RADAR.consumeClick()) {
			MiningRadarController.toggle(client);
		}
		if (ClientKeyBindings.TOGGLE_COMBAT_MODE.consumeClick()) {
			CombatModeController.toggle(client, player);
		}

		if (client.gui.screen() != null && AutoWalkController.isActive()) {
			// AutoWalkController.tick (and the cancel-key check inside it) only runs below,
			// under the screen == null branch - without this, opening any screen mid-walk
			// would leave the player's input permanently swapped to the auto-walk input and
			// make it unreachable/uncancellable until that screen closed again.
			AutoWalkController.cancel(client, player);
		}

		if (client.gui.screen() == null) {
			if (AutoWalkController.isActive()) {
				// Owns rotation and movement input entirely until it finishes or is
				// cancelled - skip everything else that would otherwise fight it (camera
				// look, build mode, and the scanner's own key handling, which would
				// otherwise steal the Stop Lock/cancel keypress before this sees it).
				AutoWalkController.tick(client, player);
			} else if (ScannerController.isLocked()) {
				ScannerController.tickLock(client, player);
				ScannerController.tick(client, player);
			} else if (CombatModeController.isActive()) {
				// Owns rotation entirely, the same as scanner lock-on above - deliberately not
				// also running build mode/normal camera look/scanner key handling here.
				CombatModeController.tick(client, player);
			} else if (BuildModeController.isActive()) {
				BuildModeController.tick(client, player);
				ScannerController.tick(client, player);
				MiningRadarController.tick(client, player);
			} else {
				if (ClientKeyBindings.isShiftDown(client)) {
					handleSnapTurn(client, player, snapLeftPressed, snapRightPressed, snapUpPressed, snapDownPressed);
				} else {
					handleCameraLook(player);
				}
				TreeChoppingAssist.tick(client, player);
				ScannerController.tick(client, player);
				MovementAssistController.tick(client, player);
				NavRadarController.tick(client, player);
				MiningRadarController.tick(client, player);
			}
		}

		if (!BuildModeController.isActive() && !ScannerController.isLocked() && !AutoWalkController.isActive()
				&& !CombatModeController.isActive()) {
			// Build mode, scanner lock-on, combat mode, and auto-walk all drive yaw
			// themselves; octant narration would just be noisy chatter racing their own.
			handleFacingNarration(client, player);
		}
		handleHotbarNarration(client, player);
		handleBiomeNarration(client, player);
		handleTimeOfDayNarration(client, player);
		if (client.gui.screen() == null) {
			// Only relevant in-world: the menu's own inventory-screen narration already covers
			// the offhand slot there, and vanilla's swap-hands key does nothing over a screen.
			handleOffhandNarration(client, player);
		}

		if (ClientKeyBindings.NARRATE_COORDINATES.consumeClick()) {
			narrateCoordinates(client, player);
		}
		if (ClientKeyBindings.NARRATE_HEALTH.consumeClick()) {
			narrateHealth(client, player);
		}
		if (ClientKeyBindings.NARRATE_BEARING.consumeClick()) {
			if (ClientKeyBindings.isShiftDown(client)) {
				resetRotationToNorth(client, player);
			} else {
				narrateBearing(client, player);
			}
		}
		if (ClientKeyBindings.SCAN_SURROUNDINGS.consumeClick()) {
			SurroundingsScanner.narrateFront(client, player);
		}
		if (ClientKeyBindings.NARRATE_TIME.consumeClick()) {
			narrateTimeOfDay(client, player);
		}
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
	 * Shift+arrows snap-turn to the nearest 45 degree marker instead of turning smoothly:
	 * left/right step yaw to the previous/next compass octant (announced automatically by
	 * {@link #handleFacingNarration}, since it lands exactly on an octant boundary), and
	 * up/down step pitch through -90/-45/0/45/90 (announced here, since nothing else covers
	 * pitch). The epsilon nudge before flooring/ceiling means a press always moves at least
	 * one full step, even when already sitting exactly on a 45 degree marker.
	 *
	 * <p>Takes pre-computed rising-edge presses rather than reading the keys' click queues
	 * itself - see {@link #pressedEdge} for why.
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

	/**
	 * True only on the tick a key transitions from up to down.
	 *
	 * <p>GLFW fires repeated key events for a key that's simply being held (not just the
	 * initial press), which keeps piling into {@link net.minecraft.client.KeyMapping}'s own
	 * click queue for as long as nothing drains it. Snap-turn used to read the arrow keys via
	 * {@code consumeClick()}, but ordinary camera-look (and build mode's cursor stepping) only
	 * ever read them via {@code isDown()} - neither drains the queue - so pressing arrows while
	 * either of those was active left a backlog that, the next time Shift was held, burned
	 * through as a burst of unwanted snap-turns all at once. Polling {@code isDown()} and
	 * tracking held-state ourselves sidesteps the click queue entirely.
	 */
	private static boolean pressedEdge(KeyMapping mapping, boolean wasHeld) {
		return mapping.isDown() && !wasHeld;
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
				: ItemDescriptions.describe(stack);
		client.getNarrator().saySystemNow(name);
	}

	private static void handleOffhandNarration(Minecraft client, LocalPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		if (lastOffhand != null && !ItemStack.matches(offhand, lastOffhand)) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.hands_swapped",
					describeHand(player.getMainHandItem()), describeHand(offhand)));
		}
		lastOffhand = offhand.copy();
	}

	private static Component describeHand(ItemStack stack) {
		return stack.isEmpty()
				? Component.translatable("united_minecraft.narrate.hotbar_empty")
				: ItemDescriptions.describe(stack);
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

	private static Component biomeName(Holder<Biome> biome) {
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

	private static void narrateHealth(Minecraft client, LocalPlayer player) {
		int health = Math.round(player.getHealth());
		int maxHealth = Math.round(player.getMaxHealth());
		int hunger = player.getFoodData().getFoodLevel();
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.health", health, maxHealth, hunger));
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
}
