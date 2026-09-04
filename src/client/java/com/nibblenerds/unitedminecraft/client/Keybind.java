package com.nibblenerds.unitedminecraft.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * One rebindable chord: a primary GLFW key plus a GLFW modifier bitmask (the same bitmask
 * {@link net.minecraft.client.input.KeyEvent#modifiers()} already produces - see
 * {@link MenuAccessibilityController#handleKey} for another place in this codebase that
 * already reads {@code GLFW_MOD_SHIFT}/{@code GLFW_MOD_CONTROL} off it the same way).
 * {@code modifiers == 0} is just a plain key, the same as every one of this mod's existing
 * defaults - no special case needed anywhere else in the model for that, including a bare
 * modifier key itself (e.g. {@link ClientKeyBindings#BUILD_PLACE}'s default of bare Right
 * Ctrl).
 */
public record Keybind(int key, int modifiers) {
	public static final Keybind UNBOUND = new Keybind(-1, 0);

	public boolean isUnbound() {
		return key < 0;
	}

	/** Human-readable form for the rebind screen, e.g. "Ctrl+Shift+G" or "Unbound". */
	public Component describe() {
		if (isUnbound()) {
			return Component.translatable("united_minecraft.keybind_screen.unbound");
		}
		StringBuilder text = new StringBuilder();
		if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
			text.append("Ctrl+");
		}
		if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
			text.append("Shift+");
		}
		if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
			text.append("Alt+");
		}
		if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) {
			text.append("Super+");
		}
		text.append(InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString());
		return Component.literal(text.toString());
	}
}
