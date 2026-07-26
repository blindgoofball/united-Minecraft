package com.nibblenerds.unitedminecraft.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
			return;
		}

		if (client.screen == null) {
			handleCameraLook(player);
		}

		handleFacingNarration(client, player);
		handleHotbarNarration(client, player);

		if (ClientKeyBindings.NARRATE_COORDINATES.consumeClick()) {
			narrateCoordinates(client, player);
		}
		if (ClientKeyBindings.NARRATE_HEALTH.consumeClick()) {
			narrateHealth(client, player);
		}
		if (ClientKeyBindings.NARRATE_BEARING.consumeClick()) {
			narrateBearing(client, player);
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
				: stack.getHoverName();
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
}
