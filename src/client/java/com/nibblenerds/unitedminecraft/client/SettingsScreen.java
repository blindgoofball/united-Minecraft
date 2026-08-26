package com.nibblenerds.unitedminecraft.client;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * United Minecraft's settings screen - hand-rolled from vanilla's own widgets rather than a
 * third-party config toolkit (Cloth Config, etc.), so it benefits "for free" from {@code
 * ScreenNarratedWidgetMixin} and the rest of this mod's narration fixes, the same way {@link
 * MarkerNameScreen} already does - a toolkit not built for this mod's narration pipeline
 * would need its own accessibility auditing. Opened by a dedicated key ({@link
 * ClientKeyBindings#OPEN_SETTINGS}) rather than through Mod Menu, matching this mod's
 * keyboard-first design - nothing here needs another mod installed to reach it.
 *
 * <p>Every row is a single focusable widget whose own label includes its current value (a
 * toggle's built-in "Name: ON/OFF", or a slider's message rewritten on every change) -
 * deliberately not a separate label widget next to each control, so Tab visits exactly one
 * narrated element per setting instead of two.
 */
public final class SettingsScreen extends Screen {
	private static final int ROW_WIDTH = 240;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_SPACING = 4;
	// 9 original setting rows + the Attack Ready Cue cycle + the glossary button + Done.
	private static final int ROW_COUNT = 12;

	public SettingsScreen() {
		super(Component.translatable("united_minecraft.settings_screen.title"));
	}

	@Override
	protected void init() {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = this.height / 2 - (ROW_HEIGHT + ROW_SPACING) * (ROW_COUNT / 2) - ROW_HEIGHT / 2;

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
		y = addSlider(x, y, 4.0, 16.0, 1.0, config.miningRadarRange,
				"united_minecraft.settings_screen.mining_radar_range",
				value -> config.miningRadarRange = (int) Math.round(value));
		y = addSlider(x, y, 4.0, 16.0, 1.0, config.navRadarRange,
				"united_minecraft.settings_screen.nav_radar_range",
				value -> config.navRadarRange = (int) Math.round(value));
		y = addSlider(x, y, 8.0, 64.0, 4.0, config.scannerRange,
				"united_minecraft.settings_screen.scanner_range", value -> config.scannerRange = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.build_mode_action_narration_enabled",
				config.buildModeActionNarrationEnabled, value -> config.buildModeActionNarrationEnabled = value);
		y = addCycle(x, y, "united_minecraft.settings_screen.combat_cue_mode",
				List.of(UnitedMinecraftConfig.CombatCueMode.values()), config.combatCueMode,
				mode -> Component.translatable("united_minecraft.settings_screen.combat_cue_mode." + mode.name().toLowerCase(Locale.ROOT)),
				value -> config.combatCueMode = value);

		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.settings_screen.sound_glossary"),
				button -> Minecraft.getInstance().gui.setScreen(new SoundGlossaryScreen()))
				.bounds(x, y, ROW_WIDTH, ROW_HEIGHT)
				.build());
		y += ROW_HEIGHT + ROW_SPACING;

		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.settings_screen.done"),
				button -> onClose())
				.bounds(x, y + ROW_SPACING, ROW_WIDTH, ROW_HEIGHT)
				.build());
	}

	private <T> int addCycle(int x, int y, String labelKey, List<T> values, T initial,
			Function<T, Component> valueLabel, Consumer<T> onChange) {
		addRenderableWidget(CycleButton.<T>builder(valueLabel::apply, initial)
				.withValues(values)
				.create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
						(button, value) -> onChange.accept(value)));
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	private int addToggle(int x, int y, String labelKey, boolean initial, Consumer<Boolean> onChange) {
		addRenderableWidget(CycleButton.onOffBuilder(initial)
				.create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
						(button, value) -> onChange.accept(value)));
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	private int addSlider(int x, int y, double min, double max, double step, double initial,
			String labelKey, Consumer<Double> onChange) {
		addRenderableWidget(new RangeSlider(x, y, ROW_WIDTH, ROW_HEIGHT, min, max, step, initial,
				Component.translatable(labelKey), onChange));
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	@Override
	public void onClose() {
		UnitedMinecraftConfig.save();
		super.onClose();
	}

	/**
	 * A slider whose message is the setting's own label plus its current value (e.g. "Hostile
	 * Radar Range: 16 blocks"), rewritten every time the value changes - vanilla's own
	 * convention (see {@link CycleButton}) for keeping a row's narration to a single widget.
	 */
	private static final class RangeSlider extends AbstractSliderButton {
		private final double min;
		private final double max;
		private final double step;
		private final Component label;
		private final Consumer<Double> onChange;

		RangeSlider(int x, int y, int width, int height, double min, double max, double step,
				double initial, Component label, Consumer<Double> onChange) {
			super(x, y, width, height, Component.empty(), normalize(initial, min, max));
			this.min = min;
			this.max = max;
			this.step = step;
			this.label = label;
			this.onChange = onChange;
			updateMessage();
		}

		private double currentValue() {
			double raw = min + (max - min) * this.value;
			if (step > 0) {
				raw = Math.round(raw / step) * step;
			}
			return Mth.clamp(raw, min, max);
		}

		@Override
		protected void updateMessage() {
			double current = currentValue();
			String formatted = current == Math.rint(current)
					? String.valueOf((long) current)
					: String.valueOf(current);
			setMessage(Component.translatable("united_minecraft.settings_screen.slider_format", label, formatted));
		}

		@Override
		protected void applyValue() {
			onChange.accept(currentValue());
		}

		private static double normalize(double initial, double min, double max) {
			return (initial - min) / (max - min);
		}
	}
}
