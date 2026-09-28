package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;

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
 * Shared machinery behind {@link SettingsScreen} and its category screens (a hub - list of
 * category buttons - leading to a short settings page per feature area, rather than one long
 * list of every setting the mod has) - hand-rolled from vanilla's own widgets rather than a
 * third-party config toolkit (Cloth Config, etc.), so every screen built on this benefits "for
 * free" from {@code ScreenNarratedWidgetMixin} and the rest of this mod's narration fixes, the
 * same way {@link MarkerNameScreen} already does - a toolkit not built for this mod's narration
 * pipeline would need its own accessibility auditing.
 *
 * <p>Every row is a single focusable widget whose own label includes its current value (a
 * toggle's built-in "Name: ON/OFF", or a slider's message rewritten on every change) -
 * deliberately not a separate label widget next to each control, so Tab visits exactly one
 * narrated element per setting instead of two.
 *
 * <p>A subclass's rows are laid out in one fixed vertical column via {@link #addRows()}, which
 * can keep growing as settings are added to it, so this class also implements its own minimal
 * scrolling rather than relying on a list-widget framework (which would make each row two
 * narrated elements - a label plus a control - instead of one). {@link #rows} remembers each
 * row's natural ("unscrolled") Y position; {@link #applyScroll()} offsets every row by {@link
 * #scrollOffset} and hides ({@code visible = false}) any row that isn't <em>fully</em> inside
 * {@code [0, height]}. That last part isn't cosmetic: on a small logical GUI resolution (e.g. a
 * high-DPI display with "Auto" GUI scale) a screen's rows can be taller than the screen itself,
 * and vanilla's renderer throws an {@code IllegalArgumentException} if it's ever asked to draw a
 * widget whose scissor rectangle isn't entirely contained in the screen - not merely a row
 * poking a few pixels off the bottom edge, confirmed against {@code
 * FrontendRenderPass#enableScissor} in the 26.3 sources, which is stricter than the "must be
 * non-zero-size" check an earlier version of this screen was written against. {@link
 * AbstractWidget#extractRenderState} already skips extraction entirely when {@code visible} is
 * false, which is exactly the guard we need. Tab/Shift+Tab ({@link #keyPressed}) and the mouse
 * wheel ({@link #mouseScrolled}) both funnel through {@link #applyScroll()} so focus, narration,
 * and rendering never disagree about what's on screen.
 */
abstract class SettingsListScreen extends Screen {
	protected static final int ROW_WIDTH = 240;
	protected static final int ROW_HEIGHT = 20;
	protected static final int ROW_SPACING = 4;
	private static final int SCROLL_STEP = ROW_HEIGHT + ROW_SPACING;

	/** Every row widget, in visual order, alongside its unscrolled ("base") Y position. */
	private final List<AbstractWidget> rows = new ArrayList<>();
	private final List<Integer> rowBaseY = new ArrayList<>();
	private int scrollOffset;
	private int maxScrollOffset;

	protected SettingsListScreen(Component title) {
		super(title);
	}

	/** Adds this screen's rows, starting from {@code y = 0} - {@link #init()} centers and scroll-bounds the result afterward. */
	protected abstract void addRows();

	@Override
	protected final void init() {
		rows.clear();
		rowBaseY.clear();
		scrollOffset = 0;

		addRows();

		// Now that every row has been added and its real total height is known, shift the whole
		// block (still anchored at y=0 in addRows()) down so it's actually centered vertically -
		// derived from the real row count rather than a hand-maintained one that would silently
		// go stale the moment a row is added or removed without updating it.
		if (!rowBaseY.isEmpty()) {
			int contentHeight = rowBaseY.get(rowBaseY.size() - 1) + ROW_HEIGHT;
			int shift = (this.height - contentHeight) / 2;
			if (shift != 0) {
				for (int i = 0; i < rows.size(); i++) {
					int shiftedY = rowBaseY.get(i) + shift;
					rows.get(i).setY(shiftedY);
					rowBaseY.set(i, shiftedY);
				}
			}
		}

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
	protected <T extends AbstractWidget> T registerRow(T widget, int baseY) {
		rows.add(widget);
		rowBaseY.add(baseY);
		return widget;
	}

	protected <T> int addCycle(int x, int y, String labelKey, List<T> values, T initial,
			Function<T, Component> valueLabel, Consumer<T> onChange) {
		registerRow(addRenderableWidget(CycleButton.<T>builder(valueLabel::apply, initial)
				.withValues(values)
				.create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
						(button, value) -> onChange.accept(value))), y);
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	protected int addToggle(int x, int y, String labelKey, boolean initial, Consumer<Boolean> onChange) {
		registerRow(addRenderableWidget(CycleButton.onOffBuilder(initial)
				.create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
						(button, value) -> onChange.accept(value))), y);
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	protected int addSlider(int x, int y, double min, double max, double step, double initial,
			String labelKey, Consumer<Double> onChange) {
		registerRow(addRenderableWidget(new RangeSlider(x, y, ROW_WIDTH, ROW_HEIGHT, min, max, step, initial,
				Component.translatable(labelKey), onChange)), y);
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	/** A plain navigation/action row - a category button, Back, Done, and so on. */
	protected int addButton(int x, int y, String labelKey, Runnable onPress) {
		registerRow(addRenderableWidget(Button.builder(Component.translatable(labelKey), button -> onPress.run())
				.bounds(x, y, ROW_WIDTH, ROW_HEIGHT)
				.build()), y);
		return y + ROW_HEIGHT + ROW_SPACING;
	}

	/**
	 * Offsets every row by {@link #scrollOffset} from its recorded base position, and hides
	 * (via {@code visible = false}) any row that isn't <em>fully</em> within {@code [0, height]}
	 * so it's skipped by rendering instead of handed to the scissor-clipping renderer with a
	 * rectangle that pokes past the screen's own edge - not just a zero-area one, which is all
	 * an earlier version of this check excluded (see the class doc). Mouse focus/click
	 * hit-testing follows the same repositioned bounds, so a row this hides is also unreachable
	 * by mouse until it's scrolled fully into view.
	 */
	private void applyScroll() {
		for (int i = 0; i < rows.size(); i++) {
			AbstractWidget widget = rows.get(i);
			int top = rowBaseY.get(i) - scrollOffset;
			widget.setY(top);
			widget.visible = top >= 0 && top + widget.getHeight() <= this.height;
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
				raw = min + Math.round((raw - min) / step) * step;
			}
			return Mth.clamp(raw, min, max);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT) {
				double direction = event.key() == InputConstants.KEY_RIGHT ? 1.0 : -1.0;
				double next = Mth.clamp(currentValue() + direction * step, min, max);
				this.value = (next - min) / (max - min);
				updateMessage();
				applyValue();
				return true;
			}
			return super.keyPressed(event);
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
			if (max <= min) {
				return 0.0;
			}
			return Mth.clamp((initial - min) / (max - min), 0.0, 1.0);
		}
	}
}
