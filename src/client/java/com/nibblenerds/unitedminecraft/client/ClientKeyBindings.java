package com.nibblenerds.unitedminecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nibblenerds.unitedminecraft.UnitedMinecraft;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

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

	public static final KeyMapping LOOK_LEFT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY));
	public static final KeyMapping LOOK_RIGHT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY));
	public static final KeyMapping LOOK_UP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY));
	public static final KeyMapping LOOK_DOWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY));

	private ClientKeyBindings() {
	}

	/** No-op call that forces the static initializers above to run. */
	public static void register() {
	}
}
