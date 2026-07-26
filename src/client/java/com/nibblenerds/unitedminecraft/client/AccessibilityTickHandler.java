package com.nibblenerds.unitedminecraft.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Drives United Minecraft's per-tick accessibility features: coordinate/health/bearing
 * readouts on keypress, and facing-direction and hotbar-switch narration as they change.
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

	private static int lastOctant = -1;
	private static int lastHotbarSlot = -1;

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
			BuildModeController.reset();
			ScannerController.reset();
			TreeChoppingAssist.reset();
			return;
		}

		// Blocked while locked: lock-on owns rotation until Stop Lock is pressed, and
		// combining it with the build cursor's own rotation-override would just fight it.
		if (!ScannerController.isLocked() && ClientKeyBindings.TOGGLE_BUILD_MODE.consumeClick()) {
			BuildModeController.toggle(client, player);
		}

		if (client.screen == null) {
			if (ScannerController.isLocked()) {
				ScannerController.tickLock(client, player);
			} else if (BuildModeController.isActive()) {
				BuildModeController.tick(client, player);
			} else {
				if (ClientKeyBindings.isShiftDown(client)) {
					handleSnapTurn(client, player);
				} else {
					handleCameraLook(player);
				}
				TreeChoppingAssist.tick(client, player);
			}
			ScannerController.tick(client, player);
		}

		if (!BuildModeController.isActive() && !ScannerController.isLocked()) {
			// Build mode and scanner lock-on both drive yaw themselves; octant narration
			// would just be noisy chatter racing their own narration.
			handleFacingNarration(client, player);
		}
		handleHotbarNarration(client, player);

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
	 */
	private static void handleSnapTurn(Minecraft client, LocalPlayer player) {
		boolean yawChanged = false;
		boolean pitchChanged = false;
		if (ClientKeyBindings.LOOK_LEFT.consumeClick()) {
			player.setYRot(snapDown45(player.getYRot()));
			yawChanged = true;
		}
		if (ClientKeyBindings.LOOK_RIGHT.consumeClick()) {
			player.setYRot(snapUp45(player.getYRot()));
			yawChanged = true;
		}
		if (ClientKeyBindings.LOOK_UP.consumeClick()) {
			player.setXRot(snapDown45(player.getXRot()));
			pitchChanged = true;
		}
		if (ClientKeyBindings.LOOK_DOWN.consumeClick()) {
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
		if (stack.isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.hotbar_empty"));
			return;
		}

		MutableComponent name = stack.getCount() > 1
				? Component.literal(stack.getCount() + " ").append(stack.getHoverName())
				: stack.getHoverName().copy();

		if (stack.isDamageableItem()) {
			int remaining = stack.getMaxDamage() - stack.getDamageValue();
			name = name.append(Component.literal(", ")).append(Component.translatable(
					"united_minecraft.narrate.hotbar_durability", remaining, stack.getMaxDamage()));
		}

		client.getNarrator().saySystemNow(name);
	}

	private static void narrateCoordinates(Minecraft client, LocalPlayer player) {
		BlockPos pos = player.blockPosition();
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.coordinates", pos.getX(), pos.getY(), pos.getZ()));
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
