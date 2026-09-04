package com.nibblenerds.unitedminecraft.client;

import net.minecraft.network.chat.Component;

/**
 * Groups {@link ClientKeyBindings} actions for display on {@link KeybindScreen} - purely a
 * presentation grouping, no effect on {@link KeybindContext} eligibility or conflict detection.
 * Kept deliberately small: a mode with only one or two actions (Combat Mode's toggle, marker
 * placement/removal) belongs under a broader neighboring category rather than getting its own,
 * so the category list stays a useful map of the keybind screen rather than one entry per mode.
 */
public enum KeybindCategory {
	NARRATION,
	SCANNER,
	MOVEMENT_AND_MODES,
	BUILD_MODE,
	INVENTORY,
	GENERAL;

	public Component label() {
		return Component.translatable("united_minecraft.keybind_category." + name().toLowerCase(java.util.Locale.ROOT));
	}
}
