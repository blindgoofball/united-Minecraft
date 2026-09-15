package com.nibblenerds.unitedminecraft.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;


/**
 * One rebindable chord: a primary {@link InputConstants} key plus an {@link InputConstants}
 * modifier bitmask (the same bitmask {@link net.minecraft.client.input.KeyEvent#modifiers()}
 * already produces - see {@link MenuAccessibilityController#handleKey} for another place in
 * this codebase that already reads {@code InputConstants.MOD_SHIFT}/{@code
 * InputConstants.MOD_CONTROL} off it the same way).
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

	/**
	 * Exact match against a raw key-press event - used by {@link KeybindContext#CONTAINER_SCREEN}
	 * actions, which are dispatched directly off a screen's own key events rather than polled
	 * through {@link ClientKeyBindings#updateAll()}. Exact modifier equality, not a subset check
	 * like {@code updateAll} uses: a container-screen chord like Shift+Enter is a fully separate
	 * action from plain Enter (see {@link ClientKeyBindings#CONTAINER_QUICK_MOVE}), not a more
	 * specific variant competing for the same primary key.
	 *
	 * <p>Enter and numpad Enter are treated as the same physical key here (matching every other
	 * vanilla and United Minecraft "Enter" binding) - a {@code Keybind} bound to either one
	 * accepts a press of both, rather than a player's numpad Enter silently not working just
	 * because the default happens to be recorded as the main Enter key.
	 */
	public boolean matches(KeyEvent event) {
		if (isUnbound() || modifiers != event.modifiers()) {
			return false;
		}
		if (key == event.key()) {
			return true;
		}
		boolean thisIsEnter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
		boolean eventIsEnter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
		return thisIsEnter && eventIsEnter;
	}

	/** Human-readable form for the rebind screen, e.g. "Ctrl+Shift+G" or "Unbound". */
	public Component describe() {
		if (isUnbound()) {
			return Component.translatable("united_minecraft.keybind_screen.unbound");
		}
		StringBuilder text = new StringBuilder();
		if ((modifiers & InputConstants.MOD_CONTROL) != 0) {
			text.append("Ctrl+");
		}
		if ((modifiers & InputConstants.MOD_SHIFT) != 0) {
			text.append("Shift+");
		}
		if ((modifiers & InputConstants.MOD_ALT) != 0) {
			text.append("Alt+");
		}
		if ((modifiers & InputConstants.MOD_SUPER) != 0) {
			text.append("Super+");
		}
		text.append(InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString());
		return Component.literal(text.toString());
	}
}
