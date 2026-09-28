package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Durability/Tool Harvest awareness, Precise Coordinates, and Map Beacon - everything else, see {@link SettingsScreen}. */
final class GeneralSettingsScreen extends SettingsListScreen {
	GeneralSettingsScreen() {
		super(Component.translatable("united_minecraft.general_settings_screen.title"));
	}

	@Override
	protected void addRows() {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

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

		addButton(x, y + ROW_SPACING, "united_minecraft.settings_screen.back",
				() -> Minecraft.getInstance().gui.setScreen(new SettingsScreen()));
	}

	@Override
	public void onClose() {
		UnitedMinecraftConfig.save();
		Minecraft.getInstance().gui.setScreen(new SettingsScreen());
	}
}
