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

	public static final KeyMapping TOGGLE_BUILD_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_build_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY));

	public static final KeyMapping BUILD_PLACE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_place", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));
	public static final KeyMapping BUILD_BREAK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_break", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

	public static final KeyMapping TOGGLE_NAV_RADAR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_nav_radar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));

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

	public static final KeyMapping SCANNER_TARGET = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY));
	public static final KeyMapping SCANNER_STOP_LOCK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_stop_lock", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, CATEGORY));

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
