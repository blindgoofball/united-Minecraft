package com.nibblenerds.unitedminecraft.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Edits a saved search's name and term (Shift+U while its category is selected). Two text fields,
 * then Confirm and Cancel; Enter in either field confirms, Escape cancels. A name or term left
 * empty, or a name another saved search already has, is said out loud and the screen stays open
 * so it can be fixed. Deleting is the Delete key on the category, not done here.
 */
final class SavedSearchEditScreen extends Screen {
	private static final int FIELD_WIDTH = 200;

	private final SavedSearchController.SavedSearch saved;
	private EditBox nameBox;
	private EditBox termBox;

	SavedSearchEditScreen(SavedSearchController.SavedSearch saved) {
		super(Component.translatable("united_minecraft.saved_search_screen.title"));
		this.saved = saved;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - FIELD_WIDTH / 2;
		int y = this.height / 2 - 40;
		nameBox = new EditBox(this.font, x, y, FIELD_WIDTH, 20, Component.translatable("united_minecraft.saved_search_screen.name"));
		nameBox.setMaxLength(SavedSearchController.MAX_TEXT);
		nameBox.setValue(nameBox.getValue().isEmpty() ? saved.name() : nameBox.getValue());
		addRenderableWidget(nameBox);
		termBox = new EditBox(this.font, x, y + 28, FIELD_WIDTH, 20, Component.translatable("united_minecraft.saved_search_screen.term"));
		termBox.setMaxLength(SavedSearchController.MAX_TEXT);
		termBox.setValue(termBox.getValue().isEmpty() ? saved.term() : termBox.getValue());
		addRenderableWidget(termBox);
		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.saved_search_screen.confirm"), button -> confirm())
				.bounds(x, y + 62, FIELD_WIDTH, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.saved_search_screen.cancel"), button -> onClose())
				.bounds(x, y + 86, FIELD_WIDTH, 20)
				.build());
		setInitialFocus(nameBox);
	}

	/** Vanilla Tabs on from the field focused in {@link #init} - see {@link MarkerNameScreen}'s same override. */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(nameBox);
	}

	@Override
	public void added() {
		super.added();
		this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.saved_search_edit_prompt"));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
				&& (getFocused() == nameBox || getFocused() == termBox)) {
			confirm();
			return true;
		}
		return super.keyPressed(event);
	}

	private void confirm() {
		SavedSearchController.SaveResult result = SavedSearchController.update(saved.id(), nameBox.getValue(), termBox.getValue());
		switch (result) {
			case UPDATED -> {
				ScannerController.onSavedSearchEdited(this.minecraft, this.minecraft.player, saved.id());
				this.minecraft.gui.setScreen(null);
			}
			case NAME_TAKEN -> say(Component.translatable("united_minecraft.narrate.saved_search_name_taken", nameBox.getValue().strip()));
			case NOT_FOUND -> {
				say(Component.translatable("united_minecraft.narrate.saved_search_gone"));
				this.minecraft.gui.setScreen(null);
			}
			default -> say(Component.translatable("united_minecraft.narrate.saved_search_blank"));
		}
	}

	private void say(Component message) {
		this.minecraft.getNarrator().saySystemNow(message);
	}

	@Override
	public void onClose() {
		say(Component.translatable("united_minecraft.narrate.saved_search_edit_cancelled"));
		this.minecraft.gui.setScreen(null);
	}
}
