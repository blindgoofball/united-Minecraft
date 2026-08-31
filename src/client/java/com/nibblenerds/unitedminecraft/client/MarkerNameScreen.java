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
 * auto-numbered one), {@link NamedBlockController} (naming/renaming a Scanner block), and
 * {@link ScannerController} (entering the Search category's term).
 */
final class MarkerNameScreen extends Screen {
	private final Consumer<String> onConfirm;
	private final Component prompt;
	private final Component cancelled;
	private final Component fieldLabel;
	private final String initialValue;
	private final Screen returnTo;
	private EditBox nameBox;

	MarkerNameScreen(Consumer<String> onConfirm) {
		this(Component.translatable("united_minecraft.marker_screen.title"),
				Component.translatable("united_minecraft.narrate.marker_prompt"),
				Component.translatable("united_minecraft.narrate.marker_cancelled"),
				Component.translatable("united_minecraft.marker_screen.name"),
				"", onConfirm);
	}

	MarkerNameScreen(Component title, Component prompt, Component cancelled, Component fieldLabel, String initialValue, Consumer<String> onConfirm) {
		this(title, prompt, cancelled, fieldLabel, initialValue, null, onConfirm);
	}

	/**
	 * {@code returnTo} lets a caller reopen inside another screen instead of closing to the
	 * game world - e.g. the recipe book's own search prompt (see {@link MenuAccessibilityController})
	 * needs to reopen the crafting/furnace screen it was invoked from, not exit the container
	 * entirely the way the Scanner's world-space search or a Map Marker name does (both pass
	 * {@code null}, preserving the original behavior of closing to nothing).
	 */
	MarkerNameScreen(Component title, Component prompt, Component cancelled, Component fieldLabel, String initialValue, Screen returnTo, Consumer<String> onConfirm) {
		super(title);
		this.prompt = prompt;
		this.cancelled = cancelled;
		this.fieldLabel = fieldLabel;
		this.initialValue = initialValue;
		this.returnTo = returnTo;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		nameBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 10, 200, 20, fieldLabel);
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
			this.minecraft.gui.setScreen(returnTo);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.getNarrator().saySystemNow(cancelled);
		this.minecraft.gui.setScreen(returnTo);
	}
}
