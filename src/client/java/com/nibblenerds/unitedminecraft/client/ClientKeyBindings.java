package com.nibblenerds.unitedminecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nibblenerds.unitedminecraft.UnitedMinecraft;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

/**
 * Keybindings for United Minecraft's accessibility features.
 *
 * <p>{@code C} defaults to unbound during normal gameplay - vanilla only wires it
 * up to "Save Toolbar Activator" while the creative inventory screen is open, so
 * there's no practical clash with narrating coordinates in-game (Minecraft's
 * controls menu may still show them as a cosmetic duplicate, since it compares
 * raw keys across contexts).
 *
 * <p>The arrow keys are entirely unbound by default in vanilla.
 */
public final class ClientKeyBindings {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(UnitedMinecraft.id("keys"));

	public static final KeyMapping NARRATE_COORDINATES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_coordinates", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

	public static final KeyMapping NARRATE_HEALTH = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_health", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

	public static final KeyMapping NARRATE_BEARING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_bearing", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));

	public static final KeyMapping SCAN_SURROUNDINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scan_surroundings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));

	public static final KeyMapping NARRATE_TIME = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_time", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

	public static final KeyMapping PLACE_MARKER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.place_marker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY));

	public static final KeyMapping TOGGLE_BUILD_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_build_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY));

	public static final KeyMapping BUILD_PLACE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_place", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));
	public static final KeyMapping BUILD_BREAK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_break", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

	public static final KeyMapping BUILD_WALK_TO_CURSOR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_walk_to_cursor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

	/** Cycles Build Mode's placement facing forward; Shift reverses it. */
	public static final KeyMapping BUILD_CYCLE_FACING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_cycle_facing", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));

	public static final KeyMapping TOGGLE_NAV_RADAR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_nav_radar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));

	public static final KeyMapping TOGGLE_MINING_RADAR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_mining_radar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));

	public static final KeyMapping TOGGLE_COMBAT_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_combat_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

	/** Narrates the nearest reachable way out of water; Shift instead swims there automatically. */
	public static final KeyMapping WATER_ESCAPE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.water_escape", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));

	/** Clears the recorded cave trail and starts recording fresh from the current position. */
	public static final KeyMapping MARK_TRAIL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.mark_trail", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY));

	/** Narrates the way back along the recorded trail; Shift instead walks it automatically. */
	public static final KeyMapping RETRACE_TRAIL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.retrace_trail", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));


	/**
	 * The arrow keys are dual-purpose: continuous camera turning normally (see
	 * {@code handleCameraLook}), but discrete one-block cursor steps while build
	 * mode is active (see {@link BuildModeController}).
	 *
	 * <p>Page Up/Down are triple-purpose: build mode's cursor Y level, or the
	 * scanner's item cycling (see {@link ScannerController}) when build mode
	 * isn't active. Home/End cycle the scanner's category instead.
	 */
	public static final KeyMapping LOOK_LEFT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY));
	public static final KeyMapping LOOK_RIGHT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY));
	public static final KeyMapping LOOK_UP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY));
	public static final KeyMapping LOOK_DOWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY));

	public static final KeyMapping PAGE_UP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.page_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, CATEGORY));
	public static final KeyMapping PAGE_DOWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.page_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, CATEGORY));

	public static final KeyMapping SCANNER_PREV_CATEGORY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_prev_category", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, CATEGORY));
	public static final KeyMapping SCANNER_NEXT_CATEGORY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_next_category", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, CATEGORY));

	/**
	 * Targets the Scanner's focused item normally - but while already locked onto a mob (where
	 * targeting doesn't apply, so this would otherwise do nothing), interacts with the locked
	 * entity directly instead, the same as right-clicking it but without needing an actual
	 * crosshair hit. Meant for when another entity is physically in the way of the one actually
	 * locked on (two chickens crowded together while trying to breed them, say), where camera
	 * aim alone can't tell them apart.
	 */
	public static final KeyMapping SCANNER_TARGET = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY));
	public static final KeyMapping SCANNER_STOP_LOCK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_stop_lock", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, CATEGORY));
	public static final KeyMapping SCANNER_COORDINATES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_coordinates", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY));

	public static final KeyMapping OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY));

	private ClientKeyBindings() {
	}

	/** No-op call that forces the static initializers above to run. */
	public static void register() {
	}

	public static boolean isShiftDown(Minecraft client) {
		return InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
	}
}
