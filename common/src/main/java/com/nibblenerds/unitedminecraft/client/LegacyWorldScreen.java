package com.nibblenerds.unitedminecraft.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Asks which of an address's old saved worlds the player has just joined - see {@link
 * WorldMigration}. The list shows each set's marker count, a few of its marker names and when it
 * was last used. Use Selected moves that set's markers and block names into this world, This Is a
 * New World leaves them for another world, and Escape or Ask Me Later asks again next time.
 */
final class LegacyWorldScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int GAP = 6;
	private static final int BUTTON_HEIGHT = 20;
	private static final int MAX_WIDTH = 420;

	private final String worldId;
	private final List<LegacyWorldSets.LegacySet> sets;
	private int selectedIndex;
	private PickList<LegacyWorldSets.LegacySet> list;

	LegacyWorldScreen(String worldId, List<LegacyWorldSets.LegacySet> sets) {
		super(Component.translatable("united_minecraft.world_sets.title"));
		this.worldId = worldId;
		this.sets = sets;
	}

	@Override
	protected void init() {
		int fullWidth = Math.min(this.width - 2 * MARGIN, MAX_WIDTH);
		int left = (this.width - fullWidth) / 2;
		int buttonY = this.height - MARGIN - BUTTON_HEIGHT;
		int listHeight = buttonY - GAP - MARGIN;

		list = new PickList<>(this.minecraft, this.font, left, MARGIN, fullWidth, listHeight,
				Component.translatable("united_minecraft.world_sets.list"),
				Component.translatable("united_minecraft.world_sets.empty"),
				index -> selectedIndex = index, () -> {
				});
		addRenderableWidget(list);
		List<Component> labels = new ArrayList<>();
		for (LegacyWorldSets.LegacySet set : sets) {
			labels.add(describe(set));
		}
		list.setItems(labels, sets, selectedIndex);

		int third = (fullWidth - 2 * GAP) / 3;
		addButton(left, buttonY, third, "united_minecraft.world_sets.use", this::useSelected);
		addButton(left + third + GAP, buttonY, third, "united_minecraft.world_sets.new_world", this::newWorld);
		addButton(left + 2 * (third + GAP), buttonY, third, "united_minecraft.world_sets.later", this::onClose);
		setInitialFocus(list);
	}

	private void addButton(int x, int y, int width, String labelKey, Runnable onPress) {
		addRenderableWidget(Button.builder(Component.translatable(labelKey), button -> onPress.run())
				.bounds(x, y, width, BUTTON_HEIGHT)
				.build());
	}

	private static Component describe(LegacyWorldSets.LegacySet set) {
		String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
				.withLocale(Locale.getDefault())
				.format(Instant.ofEpochMilli(set.lastUsed()).atZone(ZoneId.systemDefault()));
		if (set.markerCount() == 0) {
			return Component.translatable("united_minecraft.world_sets.row_none", date);
		}
		return Component.translatable("united_minecraft.world_sets.row_markers",
				set.markerCount(), String.join(", ", set.markerNames()), date);
	}

	private void useSelected() {
		LegacyWorldSets.LegacySet set = list.selectedValue();
		if (set == null) {
			return;
		}
		LegacyWorldSets.claim(set, worldId);
		WorldScopedStore.reloadAll();
		this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.world_sets.restored"));
		this.minecraft.gui.setScreen(null);
	}

	private void newWorld() {
		LegacyWorldSets.startFresh(worldId);
		this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.world_sets.fresh"));
		this.minecraft.gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
