package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The filters for one Scanner category ({@link ClientKeyBindings#SCANNER_FILTERS}): a list of
 * what is being left out, with Remove for the picked one, Add Filter, and Close. Escape closes
 * too. Adding goes through {@link MarkerNameScreen} - a text field, a "keep across sessions"
 * checkbox, Confirm and Cancel - and comes back here.
 *
 * <p>See {@link ScannerFilterController} for what a filter is and how it is stored.
 */
final class ScannerFilterScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int GAP = 6;
	private static final int BUTTON_HEIGHT = 20;
	private static final int MAX_WIDTH = 420;

	private final ScannerCategory category;
	// Survives a resize, which rebuilds every widget.
	private int pickedIndex;
	private PickList<ScannerFilterController.Filter> list;

	ScannerFilterScreen(ScannerCategory category) {
		super(Component.translatable("united_minecraft.filter_screen.title", category.label()));
		this.category = category;
	}

	@Override
	protected void init() {
		int fullWidth = Math.min(this.width - 2 * MARGIN, MAX_WIDTH);
		int left = (this.width - fullWidth) / 2;
		int buttonY = this.height - MARGIN - BUTTON_HEIGHT;

		list = new PickList<>(this.minecraft, this.font, left, MARGIN, fullWidth,
				Math.max(PickList.ROW_HEIGHT * 2, buttonY - GAP - MARGIN),
				Component.translatable("united_minecraft.filter_screen.list", category.label()),
				Component.translatable("united_minecraft.filter_screen.none"),
				index -> pickedIndex = index, () -> {
				});
		addRenderableWidget(list);
		refreshList();

		int third = (fullWidth - 2 * GAP) / 3;
		addButton(left, buttonY, third, "united_minecraft.filter_screen.remove", this::removePicked);
		addButton(left + third + GAP, buttonY, third, "united_minecraft.filter_screen.add", this::openAdd);
		addButton(left + 2 * (third + GAP), buttonY, third, "united_minecraft.filter_screen.close", this::onClose);

		setInitialFocus(list);
	}

	private void addButton(int x, int y, int width, String labelKey, Runnable onPress) {
		addRenderableWidget(Button.builder(Component.translatable(labelKey), button -> onPress.run())
				.bounds(x, y, width, BUTTON_HEIGHT)
				.build());
	}

	private void refreshList() {
		List<ScannerFilterController.Filter> filters = ScannerFilterController.filtersFor(category);
		List<Component> labels = new ArrayList<>();
		for (ScannerFilterController.Filter filter : filters) {
			labels.add(Component.translatable(filter.persistent()
					? "united_minecraft.filter_screen.row_saved"
					: "united_minecraft.filter_screen.row_session", filter.text()));
		}
		list.setItems(labels, filters, pickedIndex);
	}

	private void removePicked() {
		ScannerFilterController.Filter filter = list.selectedValue();
		if (filter == null) {
			this.minecraft.getNarrator().saySystemNow(Component.translatable(list.hasItems()
					? "united_minecraft.narrate.filter_select_first"
					: "united_minecraft.narrate.filter_none"));
			return;
		}
		ScannerFilterController.remove(filter);
		ScannerController.onFiltersChanged(this.minecraft.player);
		refreshList();
		this.minecraft.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.filter_removed", filter.text(), category.label()));
	}

	private void openAdd() {
		Minecraft client = this.minecraft;
		client.gui.setScreen(new MarkerNameScreen(
				Component.translatable("united_minecraft.filter_add_screen.title", category.label()),
				Component.translatable("united_minecraft.narrate.filter_add_prompt", category.label()),
				Component.translatable("united_minecraft.narrate.filter_add_cancelled"),
				Component.translatable("united_minecraft.filter_add_screen.text"),
				"",
				Component.translatable("united_minecraft.filter_add_screen.persistent"),
				false,
				// Built now but only initialized when it is shown, after the filter below is added,
				// so the list it opens with already includes it.
				new ScannerFilterScreen(category),
				(text, persistent) -> add(client, text, persistent)));
	}

	private void add(Minecraft client, String text, boolean persistent) {
		ScannerFilterController.AddResult result = ScannerFilterController.add(category, text, persistent);
		String shown = text.strip();
		client.getNarrator().saySystemNow(switch (result) {
			case ADDED -> Component.translatable("united_minecraft.narrate.filter_added", shown, category.label());
			case MADE_PERSISTENT -> Component.translatable("united_minecraft.narrate.filter_made_persistent", shown);
			case ALREADY_FILTERED -> Component.translatable("united_minecraft.narrate.filter_already", shown);
			case BLANK -> Component.translatable("united_minecraft.narrate.filter_blank");
		});
		if (result == ScannerFilterController.AddResult.ADDED || result == ScannerFilterController.AddResult.MADE_PERSISTENT) {
			ScannerController.onFiltersChanged(client.player);
		}
	}
}
