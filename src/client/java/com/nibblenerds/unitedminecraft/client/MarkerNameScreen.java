package com.nibblenerds.unitedminecraft.client;

import java.util.function.Consumer;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * A minimal name-entry prompt for placing a map marker: one text field, Enter confirms
 * (a blank name falls back to an auto-numbered one, handled by {@link MapMarkerController}),
 * Escape cancels without placing anything. Reuses vanilla's own {@link EditBox} rather than
 * hand-rolling text editing, and gets reliable narration of it "for free" from
 * {@code ScreenNarratedWidgetMixin} - the same generic fix that already covers every other
 * vanilla-style screen in the mod.
 */
final class MarkerNameScreen extends Screen {
	private final Consumer<String> onConfirm;
	private EditBox nameBox;

	MarkerNameScreen(Consumer<String> onConfirm) {
		super(Component.translatable("united_minecraft.marker_screen.title"));
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		nameBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 10, 200, 20,
				Component.translatable("united_minecraft.marker_screen.name"));
		nameBox.setMaxLength(64);
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);
	}

	@Override
	public void added() {
		super.added();
		this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.marker_prompt"));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			onConfirm.accept(nameBox.getValue());
			this.minecraft.gui.setScreen(null);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.marker_cancelled"));
		super.onClose();
	}
}
