package com.nibblenerds.unitedminecraft.client;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Wall Tones' range, volume, style, obstacle sound, and ceiling sound - see {@link SettingsScreen}. */
final class WallToneSettingsScreen extends SettingsListScreen {
	WallToneSettingsScreen() {
		super(Component.translatable("united_minecraft.wall_tone_settings_screen.title"));
	}

	@Override
	protected void addRows() {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

		y = addSlider(x, y, 2.0, 16.0, 1.0, config.wallToneRange,
				"united_minecraft.settings_screen.wall_tone_range",
				value -> config.wallToneRange = (int) Math.round(value));
		y = addSlider(x, y, 5.0, 100.0, 5.0, config.wallToneVolume,
				"united_minecraft.settings_screen.wall_tone_volume",
				value -> config.wallToneVolume = (int) Math.round(value));
		y = addCycle(x, y, "united_minecraft.settings_screen.wall_tone_style",
				List.of(UnitedMinecraftConfig.WallToneStyle.values()), config.wallToneStyle,
				style -> Component.translatable("united_minecraft.settings_screen.wall_tone_style." + style.name().toLowerCase(Locale.ROOT)),
				value -> config.wallToneStyle = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.wall_tone_obstacles_enabled",
				config.wallToneObstaclesEnabled, value -> config.wallToneObstaclesEnabled = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.wall_tone_ceiling_enabled",
				config.wallToneCeilingEnabled, value -> config.wallToneCeilingEnabled = value);

		addButton(x, y + ROW_SPACING, "united_minecraft.settings_screen.back",
				() -> Minecraft.getInstance().gui.setScreen(new SettingsScreen()));
	}

	@Override
	public void onClose() {
		UnitedMinecraftConfig.save();
		Minecraft.getInstance().gui.setScreen(new SettingsScreen());
	}
}
