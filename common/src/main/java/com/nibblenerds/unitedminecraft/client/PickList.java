package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.keybind.KeybindScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A list of things to pick between, for this mod's own screens: an {@link ObjectSelectionList}, the
 * same kind {@link KeybindScreen} uses, so it behaves and narrates the way that screen's list does.
 *
 * <p>The list's name is its widget message, which {@code ScreenNarratedWidgetMixin} speaks on
 * entering it. A list with nothing in it shows a single "none" row, since a list with no rows can't
 * take focus and Tab would skip it without a word. With any items there is always one picked.
 *
 * <p>Used by {@link MarkerShareScreen} and {@link ScannerFilterScreen}.
 */
final class PickList<T> extends ObjectSelectionList<PickList.Row<T>> {
	static final int ROW_HEIGHT = 20;

	private final Font font;
	private final Component emptyText;
	private final IntConsumer onIndexChanged;
	private final Runnable onSelectionChanged;
	private boolean rebuilding;

	/**
	 * @param onIndexChanged told the position of the picked item whenever it changes, however
	 *        (including a rebuild), so a screen can remember it across a resize
	 * @param onSelectionChanged run when the player moves the pick - not for a rebuild
	 */
	PickList(Minecraft minecraft, Font font, int x, int y, int width, int height, Component name, Component emptyText,
			IntConsumer onIndexChanged, Runnable onSelectionChanged) {
		super(minecraft, width, height, y, ROW_HEIGHT);
		setX(x);
		setMessage(name);
		this.font = font;
		this.emptyText = emptyText;
		this.onIndexChanged = onIndexChanged;
		this.onSelectionChanged = onSelectionChanged;
	}

	@Override
	public int getRowWidth() {
		return this.width - 12;
	}

	boolean hasItems() {
		for (Row<T> row : children()) {
			if (row.value != null) {
				return true;
			}
		}
		return false;
	}

	/** The picked item, or null if nothing is picked or the "none" row is. */
	T selectedValue() {
		Row<T> selected = getSelected();
		return selected == null ? null : selected.value;
	}

	/** Position of the picked item in the list; -1 if none (or only the "none" row) is picked. */
	private int selectedItemIndex() {
		Row<T> selected = getSelected();
		return selected == null || selected.value == null ? -1 : children().indexOf(selected);
	}

	/**
	 * Replaces the items, keeping the pick as close to {@code preferred} as the new list allows.
	 * With any items there is always something picked, so an action can use it without having to
	 * select first.
	 */
	void setItems(List<Component> labels, List<T> values, int preferred) {
		int previous = selectedItemIndex();
		int target = previous >= 0 ? previous : preferred;
		rebuilding = true;
		List<Row<T>> rows = new ArrayList<>();
		if (values.isEmpty()) {
			rows.add(new Row<>(font, emptyText, null));
		}
		for (int i = 0; i < values.size(); i++) {
			rows.add(new Row<>(font, labels.get(i), values.get(i)));
		}
		replaceEntries(rows);
		if (!values.isEmpty()) {
			int index = Mth.clamp(target, 0, values.size() - 1);
			setSelected(rows.get(index));
			onIndexChanged.accept(index);
		}
		rebuilding = false;
	}

	@Override
	public void setSelected(Row<T> entry) {
		Row<T> before = getSelected();
		super.setSelected(entry);
		if (!rebuilding && entry != before) {
			int index = selectedItemIndex();
			if (index >= 0) {
				onIndexChanged.accept(index);
			}
			onSelectionChanged.run();
		}
	}

	/**
	 * One row: either something to pick ({@code value}), or - when {@code value} is null - the
	 * "none" row shown in place of an empty list, which can't be acted on.
	 */
	static final class Row<T> extends ObjectSelectionList.Entry<Row<T>> {
		private final Font font;
		private final Component label;
		private final T value;

		Row(Font font, Component label, T value) {
			this.font = font;
			this.label = label;
			this.value = value;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int textX = getContentX() + 2;
			int textY = getContentY() + (getContentHeight() - font.lineHeight) / 2;
			guiGraphics.text(font, font.plainSubstrByWidth(label.getString(), getContentWidth() - 4), textX, textY, -1);
		}

		@Override
		public Component getNarration() {
			return label;
		}
	}
}
