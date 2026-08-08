package com.nibblenerds.unitedminecraft.client;

import java.util.function.Consumer;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * A minimal name-entry prompt: one text field, Enter confirms, Escape cancels. Reuses
 * vanilla's own {@link EditBox} rather than hand-rolling text editing, and gets reliable
 * narration of it "for free" from {@code ScreenNarratedWidgetMixin} - the same generic fix
 * that already covers every other vanilla-style screen in the mod.
 *
 * <p>Shared by {@link MapMarkerController} (placing a marker; a blank name falls back to an
 * auto-numbered one) and {@link NamedBlockController} (naming/renaming a Scanner block).
 */
final class MarkerNameScreen extends Screen {
	private final Consumer<String> onConfirm;
	private final Component prompt;
	private final Component cancelled;
	private final String initialValue;
	private EditBox nameBox;

	MarkerNameScreen(Consumer<String> onConfirm) {
		this(Component.translatable("united_minecraft.marker_screen.title"),
				Component.translatable("united_minecraft.narrate.marker_prompt"),
				Component.translatable("united_minecraft.narrate.marker_cancelled"),
				"", onConfirm);
	}

	MarkerNameScreen(Component title, Component prompt, Component cancelled, String initialValue, Consumer<String> onConfirm) {
		super(title);
		this.prompt = prompt;
		this.cancelled = cancelled;
		this.initialValue = initialValue;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		nameBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 10, 200, 20,
				Component.translatable("united_minecraft.marker_screen.name"));
		nameBox.setMaxLength(64);
		nameBox.setValue(initialValue);
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);
	}

	@Override
	public void added() {
		super.added();
		this.minecraft.getNarrator().saySystemNow(prompt);
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
		this.minecraft.getNarrator().saySystemNow(cancelled);
		super.onClose();
	}
}
