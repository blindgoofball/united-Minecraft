package com.nibblenerds.unitedminecraft.client.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Audio Cue Volume, Title Narration, Hotbar Tooltips, Durability/Tool Harvest awareness, Precise Coordinates, Map Beacon, and Compass Beacon - everything else, see {@link SettingsScreen}. */
final class GeneralSettingsScreen extends SettingsListScreen {
	GeneralSettingsScreen() {
		super(Component.translatable("united_minecraft.general_settings_screen.title"));
	}

	@Override
	protected void addRows() {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

		y = addSlider(x, y, 5.0, 100.0, 5.0, config.cueVolume,
				"united_minecraft.settings_screen.cue_volume",
				value -> config.cueVolume = (int) Math.round(value));
		y = addToggle(x, y, "united_minecraft.settings_screen.title_narration_enabled",
				config.titleNarrationEnabled, value -> config.titleNarrationEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.hotbar_tooltips_enabled",
				config.hotbarTooltipsEnabled, value -> config.hotbarTooltipsEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.chat_sound_enabled",
				config.chatSoundEnabled, value -> config.chatSoundEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.ignore_marker_shares",
				config.ignoreMarkerShares, value -> config.ignoreMarkerShares = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.auto_crosshair_repeat_same_block",
				config.autoCrosshairRepeatSameBlock, value -> config.autoCrosshairRepeatSameBlock = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.durability_awareness_enabled",
				config.durabilityAwarenessEnabled, value -> config.durabilityAwarenessEnabled = value);
		y = addSlider(x, y, 1.0, 50.0, 1.0, config.durabilityWarningThreshold,
				"united_minecraft.settings_screen.durability_warning_threshold",
				value -> config.durabilityWarningThreshold = (int) Math.round(value));
		y = addSlider(x, y, 1.0, 50.0, 1.0, config.durabilityCriticalThreshold,
				"united_minecraft.settings_screen.durability_critical_threshold",
				value -> config.durabilityCriticalThreshold = (int) Math.round(value));
		y = addToggle(x, y, "united_minecraft.settings_screen.tool_harvest_warning_enabled",
				config.toolHarvestWarningEnabled, value -> config.toolHarvestWarningEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.precise_coordinates_enabled",
				config.preciseCoordinatesEnabled, value -> config.preciseCoordinatesEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.map_beacon_enabled",
				config.mapBeaconEnabled, value -> config.mapBeaconEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.compass_beacon_enabled",
				config.compassBeaconEnabled, value -> config.compassBeaconEnabled = value);

		addButton(x, y + ROW_SPACING, "united_minecraft.settings_screen.back",
				() -> Minecraft.getInstance().gui.setScreen(new SettingsScreen()));
	}

	@Override
	public void onClose() {
		UnitedMinecraftConfig.save();
		Minecraft.getInstance().gui.setScreen(new SettingsScreen());
	}
}
