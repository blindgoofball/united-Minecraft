package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Hostile Radar, Melee Range Alert, Fall Warning, Mining/Nav Radar range, and Scanner - see {@link SettingsScreen}. */
final class DetectionAlertsSettingsScreen extends SettingsListScreen {
	DetectionAlertsSettingsScreen() {
		super(Component.translatable("united_minecraft.detection_alerts_screen.title"));
	}

	@Override
	protected void addRows() {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

		y = addToggle(x, y, "united_minecraft.settings_screen.hostile_radar_enabled",
				config.hostileRadarEnabled, value -> config.hostileRadarEnabled = value);
		y = addSlider(x, y, 4.0, 32.0, 1.0, config.hostileRadarRange,
				"united_minecraft.settings_screen.hostile_radar_range", value -> config.hostileRadarRange = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.melee_range_alert_enabled",
				config.meleeRangeAlertEnabled, value -> config.meleeRangeAlertEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.fall_warning_enabled",
				config.fallWarningEnabled, value -> config.fallWarningEnabled = value);
		y = addSlider(x, y, 1.0, 10.0, 1.0, config.fallWarningThreshold,
				"united_minecraft.settings_screen.fall_warning_threshold", value -> config.fallWarningThreshold = value);
		y = addSlider(x, y, 0.5, 3.0, 0.5, config.fallWarningLookaheadSeconds,
				"united_minecraft.settings_screen.fall_warning_lookahead_seconds",
				value -> config.fallWarningLookaheadSeconds = value);
		y = addSlider(x, y, 4.0, 16.0, 1.0, config.miningRadarRange,
				"united_minecraft.settings_screen.mining_radar_range",
				value -> config.miningRadarRange = (int) Math.round(value));
		y = addSlider(x, y, 4.0, 16.0, 1.0, config.navRadarRange,
				"united_minecraft.settings_screen.nav_radar_range",
				value -> config.navRadarRange = (int) Math.round(value));
		y = addSlider(x, y, 8.0, 64.0, 4.0, config.scannerRange,
				"united_minecraft.settings_screen.scanner_range", value -> config.scannerRange = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.scanner_skip_empty_categories",
				config.scannerSkipEmptyCategories, value -> config.scannerSkipEmptyCategories = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.scanner_auto_lock_after_walk",
				config.scannerAutoLockAfterWalk, value -> config.scannerAutoLockAfterWalk = value);

		addButton(x, y + ROW_SPACING, "united_minecraft.settings_screen.back",
				() -> Minecraft.getInstance().gui.setScreen(new SettingsScreen()));
	}

	@Override
	public void onClose() {
		UnitedMinecraftConfig.save();
		Minecraft.getInstance().gui.setScreen(new SettingsScreen());
	}
}
