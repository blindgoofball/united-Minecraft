package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Type-ahead jump to a Scanner category: letters build up a prefix of the category's name, and
 * the moment exactly one category still matches the screen closes and jumps to it. A letter
 * that several categories share (Storage/Search, Mechanisms/Markers, Passive Mobs/Players,
 * Trees/Terrain) just narrows the list - it narrates the candidates and waits for the next
 * letter. Enter takes the first remaining match, Backspace undoes a letter, Escape cancels.
 *
 * <p>Typing goes into a real, focused {@link EditBox} - the same arrangement {@link
 * MarkerNameScreen} uses - rather than listening for raw key events, so focus, typing and
 * narration all work the way they already do on the Search prompt. Matching runs against the
 * translated category label, so it follows whatever language the labels are in.
 */
final class CategoryJumpScreen extends Screen {
	private EditBox input;
	// Set while this screen itself rewrites the box (rejecting a letter), so the responder
	// doesn't treat its own edit as the player typing.
	private boolean rewriting;

	CategoryJumpScreen() {
		super(Component.translatable("united_minecraft.category_jump.title"));
	}

	@Override
	protected void init() {
		input = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 10, 200, 20,
				Component.translatable("united_minecraft.category_jump.field"));
		input.setMaxLength(32);
		input.setResponder(this::onTextChanged);
		addRenderableWidget(input);
		setInitialFocus(input);
	}

	@Override
	public void added() {
		super.added();
		this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.category_jump_prompt"));
	}

	private void onTextChanged(String raw) {
		if (rewriting) {
			return;
		}
		String text = raw.strip().toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.category_jump_prompt"));
			return;
		}
		List<ScannerCategory> matches = matching(text);
		if (matches.isEmpty()) {
			this.minecraft.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.category_jump_none", text.toUpperCase(Locale.ROOT)));
			rewriting = true;
			input.setValue(text.substring(0, text.length() - 1));
			rewriting = false;
			return;
		}
		if (matches.size() == 1) {
			jumpTo(matches.get(0));
		} else {
			narrateCandidates(text, matches);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			String text = input.getValue().strip().toLowerCase(Locale.ROOT);
			List<ScannerCategory> matches = matching(text);
			if (!text.isEmpty() && !matches.isEmpty()) {
				jumpTo(matches.get(0));
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.category_jump_cancelled"));
		this.minecraft.gui.setScreen(null);
	}

	private void jumpTo(ScannerCategory category) {
		LocalPlayer player = this.minecraft.player;
		this.minecraft.gui.setScreen(null);
		if (player != null) {
			ScannerController.jumpToCategory(this.minecraft, player, category);
		}
	}

	private void narrateCandidates(String text, List<ScannerCategory> matches) {
		Component list = Component.empty();
		for (int i = 0; i < matches.size(); i++) {
			if (i > 0) {
				list = list.copy().append(Component.literal(", "));
			}
			list = list.copy().append(matches.get(i).label());
		}
		this.minecraft.getNarrator().saySystemNow(Component.literal(text.toUpperCase(Locale.ROOT) + ": ").append(list));
	}

	private static List<ScannerCategory> matching(String prefix) {
		List<ScannerCategory> matches = new ArrayList<>();
		for (ScannerCategory category : ScannerCategory.values()) {
			if (category.label().getString().toLowerCase(Locale.ROOT).startsWith(prefix)) {
				matches.add(category);
			}
		}
		return matches;
	}
}
