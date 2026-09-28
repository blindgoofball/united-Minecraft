package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * United Minecraft's settings hub - a short list of category buttons (see {@link
 * DetectionAlertsSettingsScreen}, {@link WallToneSettingsScreen}, {@link
 * ModesMovementSettingsScreen}, {@link GeneralSettingsScreen}) rather than every setting the mod
 * has in one long, ever-growing list. Opened by a dedicated key ({@link
 * ClientKeyBindings#OPEN_SETTINGS}) rather than through Mod Menu, matching this mod's
 * keyboard-first design - nothing here needs another mod installed to reach it. See {@link
 * SettingsListScreen} for the row/scrolling machinery every one of these screens shares.
 */
public final class SettingsScreen extends SettingsListScreen {
	public SettingsScreen() {
		super(Component.translatable("united_minecraft.settings_screen.title"));
	}

	@Override
	protected void addRows() {
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

		y = addButton(x, y, "united_minecraft.settings_screen.category.detection_alerts",
				() -> Minecraft.getInstance().gui.setScreen(new DetectionAlertsSettingsScreen()));
		y = addButton(x, y, "united_minecraft.settings_screen.category.wall_tones",
				() -> Minecraft.getInstance().gui.setScreen(new WallToneSettingsScreen()));
		y = addButton(x, y, "united_minecraft.settings_screen.category.modes_movement",
				() -> Minecraft.getInstance().gui.setScreen(new ModesMovementSettingsScreen()));
		y = addButton(x, y, "united_minecraft.settings_screen.category.general",
				() -> Minecraft.getInstance().gui.setScreen(new GeneralSettingsScreen()));

		y = addButton(x, y, "united_minecraft.settings_screen.sound_glossary",
				() -> Minecraft.getInstance().gui.setScreen(new SoundGlossaryScreen()));
		y = addButton(x, y, "united_minecraft.settings_screen.keybindings",
				() -> Minecraft.getInstance().gui.setScreen(new KeybindScreen()));

		addButton(x, y + ROW_SPACING, "united_minecraft.settings_screen.done", this::onClose);
	}
}
