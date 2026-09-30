package com.nibblenerds.unitedminecraft.client;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Build Mode action narration, Combat cue mode, mode on/off sounds, Eye of Ender cues, Auto-Walk auto-sprint, and Mount jump cue - see {@link SettingsScreen}. */
final class ModesMovementSettingsScreen extends SettingsListScreen {
	ModesMovementSettingsScreen() {
		super(Component.translatable("united_minecraft.modes_movement_screen.title"));
	}

	@Override
	protected void addRows() {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

		y = addToggle(x, y, "united_minecraft.settings_screen.build_mode_action_narration_enabled",
				config.buildModeActionNarrationEnabled, value -> config.buildModeActionNarrationEnabled = value);
		y = addCycle(x, y, "united_minecraft.settings_screen.combat_cue_mode",
				List.of(UnitedMinecraftConfig.CombatCueMode.values()), config.combatCueMode,
				mode -> Component.translatable("united_minecraft.settings_screen.combat_cue_mode." + mode.name().toLowerCase(Locale.ROOT)),
				value -> config.combatCueMode = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.mode_toggle_sounds_enabled",
				config.modeToggleSoundsEnabled, value -> config.modeToggleSoundsEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.eye_of_ender_cues_enabled",
				config.eyeOfEnderCuesEnabled, value -> config.eyeOfEnderCuesEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.auto_walk_auto_sprint",
				config.autoWalkAutoSprint, value -> config.autoWalkAutoSprint = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.mount_jump_cue_enabled",
				config.mountJumpCueEnabled, value -> config.mountJumpCueEnabled = value);

		addButton(x, y + ROW_SPACING, "united_minecraft.settings_screen.back",
				() -> Minecraft.getInstance().gui.setScreen(new SettingsScreen()));
	}

	@Override
	public void onClose() {
		UnitedMinecraftConfig.save();
		Minecraft.getInstance().gui.setScreen(new SettingsScreen());
	}
}
