package com.nibblenerds.unitedminecraft.client.settings;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Arrow-key turn speed, Build Mode action narration, Combat cue mode, mode on/off sounds, elytra flight alerts, boat steering and alerts, Eye of Ender cues, Auto-Walk auto-sprint, and Mount jump cue - see {@link SettingsScreen}. */
final class ModesMovementSettingsScreen extends SettingsListScreen {
	ModesMovementSettingsScreen() {
		super(Component.translatable("united_minecraft.modes_movement_screen.title"));
	}

	@Override
	protected void addRows() {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

		y = addSlider(x, y, 50.0, 300.0, 10.0, config.turnSpeedPercent,
				"united_minecraft.settings_screen.turn_speed_percent", value -> config.turnSpeedPercent = (int) Math.round(value));
		y = addToggle(x, y, "united_minecraft.settings_screen.build_mode_action_narration_enabled",
				config.buildModeActionNarrationEnabled, value -> config.buildModeActionNarrationEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.build_mode_sound_cues_enabled",
				config.buildModeSoundCuesEnabled, value -> config.buildModeSoundCuesEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.build_mode_area_lock_height",
				config.buildModeAreaLockHeight, value -> config.buildModeAreaLockHeight = value);
		y = addCycle(x, y, "united_minecraft.settings_screen.combat_style",
				List.of(UnitedMinecraftConfig.CombatStyle.values()), config.combatStyle,
				style -> Component.translatable("united_minecraft.settings_screen.combat_style." + style.name().toLowerCase(Locale.ROOT)),
				value -> config.combatStyle = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.bow_beacon_enabled",
				config.bowBeaconEnabled, value -> config.bowBeaconEnabled = value);
		y = addCycle(x, y, "united_minecraft.settings_screen.combat_cue_mode",
				List.of(UnitedMinecraftConfig.CombatCueMode.values()), config.combatCueMode,
				mode -> Component.translatable("united_minecraft.settings_screen.combat_cue_mode." + mode.name().toLowerCase(Locale.ROOT)),
				value -> config.combatCueMode = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.mode_toggle_sounds_enabled",
				config.modeToggleSoundsEnabled, value -> config.modeToggleSoundsEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.elytra_awareness_enabled",
				config.elytraAwarenessEnabled, value -> config.elytraAwarenessEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.elytra_auto_walk_after_landing",
				config.elytraAutoWalkAfterLanding, value -> config.elytraAutoWalkAfterLanding = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.boat_assist_enabled",
				config.boatAssistEnabled, value -> config.boatAssistEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.boat_awareness_enabled",
				config.boatAwarenessEnabled, value -> config.boatAwarenessEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.eye_of_ender_cues_enabled",
				config.eyeOfEnderCuesEnabled, value -> config.eyeOfEnderCuesEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.auto_walk_auto_sprint",
				config.autoWalkAutoSprint, value -> config.autoWalkAutoSprint = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.auto_walk_open_doors",
				config.autoWalkOpenDoors, value -> config.autoWalkOpenDoors = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.auto_walk_swim",
				config.autoWalkSwim, value -> config.autoWalkSwim = value);
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
