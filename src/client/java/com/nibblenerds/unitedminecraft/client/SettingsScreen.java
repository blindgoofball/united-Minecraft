package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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
 *
 * <p>The rows are laid out in one fixed vertical column that keeps growing as settings are
 * added, so this screen also implements its own minimal scrolling rather than relying on a
 * list-widget framework (which would make each row two narrated elements - a label plus a
 * control - instead of one). {@link #rows} remembers each row's natural ("unscrolled") Y
 * position; {@link #applyScroll()} offsets every row by {@link #scrollOffset} and hides
 * ({@code visible = false}) any row that ends up entirely outside {@code [0, height]}. That
 * last part isn't cosmetic: on a small logical GUI resolution (e.g. a high-DPI display with
 * "Auto" GUI scale) this screen's rows can be taller than the screen itself, and vanilla's
 * 26.2 renderer throws {@code IllegalArgumentException: Scissor size must be >0} if it's ever
 * asked to draw a widget whose scissor rectangle doesn't intersect the screen at all -
 * {@link AbstractWidget#extractRenderState} already skips extraction entirely when
 * {@code visible} is false, which is exactly the guard we need. Tab/Shift+Tab
 * ({@link #keyPressed}) and the mouse wheel ({@link #mouseScrolled}) both funnel through
 * {@link #applyScroll()} so focus, narration, and rendering never disagree about what's
 * on screen.
 */
public final class SettingsScreen extends Screen {
	private static final int ROW_WIDTH = 240;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_SPACING = 4;
	private static final int SCROLL_STEP = ROW_HEIGHT + ROW_SPACING;
	// 9 original setting rows + the Attack Ready Cue cycle + the glossary button + Done
	// + 3 durability awareness rows (toggle + 2 sliders) + 1 tool harvest warning row
	// + 2 scanner rows (skip empty categories, auto-lock after walk)
	// + 1 fall warning lookahead row.
	private static final int ROW_COUNT = 19;

	/** Every row widget, in visual order, alongside its unscrolled ("base") Y position. */
	private final List<AbstractWidget> rows = new ArrayList<>();
	private final List<Integer> rowBaseY = new ArrayList<>();
	private int scrollOffset;
	private int maxScrollOffset;

	public SettingsScreen() {
		super(Component.translatable("united_minecraft.settings_screen.title"));
	}

	@Override
	protected void init() {
		rows.clear();
		rowBaseY.clear();
		scrollOffset = 0;

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
		y = addToggle(x, y, "united_minecraft.settings_screen.build_mode_action_narration_enabled",
				config.buildModeActionNarrationEnabled, value -> config.buildModeActionNarrationEnabled = value);
		y = addCycle(x, y, "united_minecraft.settings_screen.combat_cue_mode",
				List.of(UnitedMinecraftConfig.CombatCueMode.values()), config.combatCueMode,
				mode -> Component.translatable("united_minecraft.settings_screen.combat_cue_mode." + mode.name().toLowerCase(Locale.ROOT)),
				value -> config.combatCueMode = value);
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
		y = addToggle(x, y, "united_minecraft.settings_screen.scanner_skip_empty_categories",
				config.scannerSkipEmptyCategories, value -> config.scannerSkipEmptyCategories = value);
		y = addToggle(x, y, "united_minecraft.settings_screen.scanner_auto_lock_after_walk",
				config.scannerAutoLockAfterWalk, value -> config.scannerAutoLockAfterWalk = value);

		registerRow(addRenderableWidget(Button.builder(Component.translatable("united_minecraft.settings_screen.sound_glossary"),
				button -> Minecraft.getInstance().gui.setScreen(new SoundGlossaryScreen()))
				.bounds(x, y, ROW_WIDTH, ROW_HEIGHT)
				.build()), y);
		y += ROW_HEIGHT + ROW_SPACING;

		registerRow(addRenderableWidget(Button.builder(Component.translatable("united_minecraft.settings_screen.done"),
				button -> onClose())
				.bounds(x, y + ROW_SPACING, ROW_WIDTH, ROW_HEIGHT)
				.build()), y + ROW_SPACING);

		// scrollOffset is subtracted directly from each row's absolute base Y (see applyScroll),
		// not from a content-relative 0-based coordinate space - so the bound that brings the
		// last row's bottom edge exactly to the screen's bottom is just its own base Y plus its
		// height, minus the screen height. Also subtracting the first row's base Y here (an
		// earlier version of this fix did, from conflating this with the content's total span)
		// undercounts whenever that first row starts below y=0, permanently stranding the last
		// few rows above the visible area even at maximum scroll - exactly the "Done button and
		// Sound Glossary are missing" bug this replaces.
		maxScrollOffset = rowBaseY.isEmpty() ? 0
				: Math.max(0, (rowBaseY.get(rowBaseY.size() - 1) + ROW_HEIGHT) - this.height);
		applyScroll();
	}

	/** Records a row's widget and its unscrolled Y position so scrolling can find it later. */
	private <T extends AbstractWidget> T registerRow(T widget, int baseY) {
		rows.add(widget);
		rowBaseY.add(baseY);
		return widget;
	}

	private <T> int addCycle(int x, int y, String labelKey, List<T> values, T initial,
			Function<T, Component> valueLabel, Consumer<T> onChange) {
		registerRow(addRenderableWidget(CycleButton.<T>builder(valueLabel::apply, initial)
				.withValues(values)
				.create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
						(button, value) -> onChange.accept(value))), y);
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	private int addToggle(int x, int y, String labelKey, boolean initial, Consumer<Boolean> onChange) {
		registerRow(addRenderableWidget(CycleButton.onOffBuilder(initial)
				.create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
						(button, value) -> onChange.accept(value))), y);
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	private int addSlider(int x, int y, double min, double max, double step, double initial,
			String labelKey, Consumer<Double> onChange) {
		registerRow(addRenderableWidget(new RangeSlider(x, y, ROW_WIDTH, ROW_HEIGHT, min, max, step, initial,
				Component.translatable(labelKey), onChange)), y);
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	/**
	 * Offsets every row by {@link #scrollOffset} from its recorded base position, and hides
	 * (via {@code visible = false}) any row that ends up entirely above or below the screen so
	 * it's skipped by rendering instead of handed to the scissor-clipping renderer with a
	 * zero-area rectangle. Mouse focus/click hit-testing follows the same repositioned bounds,
	 * so an off-screen row is also unreachable by mouse until it's scrolled into view.
	 */
	private void applyScroll() {
		for (int i = 0; i < rows.size(); i++) {
			AbstractWidget widget = rows.get(i);
			int top = rowBaseY.get(i) - scrollOffset;
			widget.setY(top);
			widget.visible = top + widget.getHeight() > 0 && top < this.height;
		}
	}

	/**
	 * If Tab/Shift+Tab (handled by {@code super.keyPressed}) moved focus to a row that's
	 * currently scrolled out of view, scrolls just enough to bring it fully into
	 * {@code [0, height]} before this method returns - this mod's users navigate primarily by
	 * keyboard, so auto-scroll can't depend on a mouse wheel or scrollbar ever being touched.
	 *
	 * <p>Every row is temporarily forced visible before delegating to {@code super.keyPressed}:
	 * {@code AbstractWidget#nextFocusPath} - vanilla's own Tab-cycling target search - refuses
	 * any widget whose {@code isActive()} is false, and {@code isActive()} itself requires
	 * {@code visible} (confirmed via its bytecode), so a row {@link #applyScroll} had culled for
	 * being off-screen would otherwise be permanently unreachable by Tab, not merely reachable
	 * without auto-scrolling - Tab would silently skip straight past it to the next row vanilla
	 * still considers a valid target, exactly the "Done/Sound Glossary/the row before it went
	 * missing" bug this replaces. {@link #applyScroll} (called unconditionally below) restores
	 * correct culling for rendering immediately afterward, using whatever scroll position this
	 * method settles on.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		for (AbstractWidget row : rows) {
			row.visible = true;
		}
		boolean handled = super.keyPressed(event);
		GuiEventListener focused = getFocused();
		int index = rows.indexOf(focused);
		if (index >= 0) {
			int baseY = rowBaseY.get(index);
			int top = baseY - scrollOffset;
			int bottom = top + rows.get(index).getHeight();
			if (top < 0) {
				scrollOffset += top;
			} else if (bottom > this.height) {
				scrollOffset += bottom - this.height;
			}
			scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset);
		}
		applyScroll();
		return handled;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScrollOffset <= 0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		scrollOffset = Mth.clamp(scrollOffset - (int) Math.round(scrollY * SCROLL_STEP), 0, maxScrollOffset);
		applyScroll();
		return true;
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
