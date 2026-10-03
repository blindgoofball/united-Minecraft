package com.nibblenerds.unitedminecraft.client;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;


/**
 * A minimal name-entry prompt: one text field, Enter confirms, Escape cancels. Reuses
 * vanilla's own {@link EditBox} rather than hand-rolling text editing, and gets reliable
 * narration of it "for free" from {@code ScreenNarratedWidgetMixin} - the same generic fix
 * that already covers every other vanilla-style screen in the mod.
 *
 * <p>Shared by {@link MapMarkerController} (placing a marker; a blank name falls back to an
 * auto-numbered one), {@link NamedBlockController} (naming/renaming a Scanner block), and
 * {@link ScannerController} (entering the Search category's term).
 *
 * <p>{@link NamedBlockController} also asks for an optional checkbox under the field ("Also show
 * in Markers") - see the toggle constructor. Every other caller gets no checkbox at all.
 */
final class MarkerNameScreen extends Screen {
	private final Consumer<String> onConfirm;
	private final Component prompt;
	private final Component cancelled;
	private final Component fieldLabel;
	private final String initialValue;
	private final Screen returnTo;
	private EditBox nameBox;
	// Only set by the toggle constructor; null means no checkbox is shown at all.
	private final Component toggleLabel;
	private final boolean toggleInitial;
	private final BiConsumer<String, Boolean> onConfirmWithToggle;
	private Checkbox toggleBox;

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
		this.toggleLabel = null;
		this.toggleInitial = false;
		this.onConfirmWithToggle = null;
	}

	/** Same prompt with a checkbox under the text field; {@code onConfirm} receives the name and the checkbox's state. */
	MarkerNameScreen(Component title, Component prompt, Component cancelled, Component fieldLabel, String initialValue,
			Component toggleLabel, boolean toggleInitial, BiConsumer<String, Boolean> onConfirm) {
		super(title);
		this.prompt = prompt;
		this.cancelled = cancelled;
		this.fieldLabel = fieldLabel;
		this.initialValue = initialValue;
		this.returnTo = null;
		this.onConfirm = null;
		this.toggleLabel = toggleLabel;
		this.toggleInitial = toggleInitial;
		this.onConfirmWithToggle = onConfirm;
	}

	@Override
	protected void init() {
		nameBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 10, 200, 20, fieldLabel);
		nameBox.setMaxLength(64);
		nameBox.setValue(initialValue);
		addRenderableWidget(nameBox);
		if (toggleLabel != null) {
			toggleBox = Checkbox.builder(toggleLabel, this.font)
					.pos(this.width / 2 - 100, this.height / 2 + 20)
					.selected(toggleBox != null ? toggleBox.selected() : toggleInitial)
					.build();
			addRenderableWidget(toggleBox);
			// Tab order is name field, checkbox, Clear name (only when there is one), Confirm, then
			// Cancel - Confirm and Cancel stay side by side at the end. Enter confirms from the name
			// field or while Confirm is focused; on the checkbox it does what vanilla's does and toggles.
			int buttonY = this.height / 2 + 50;
			if (!initialValue.isEmpty()) {
				// A blank name is what removes one (and its Markers flag), so this is just confirming
				// blank without having to empty the field first.
				addRenderableWidget(Button.builder(Component.translatable("united_minecraft.named_block_screen.clear"), button -> clear())
						.bounds(this.width / 2 - 100, buttonY, 200, 20)
						.build());
				buttonY += 24;
			}
			addRenderableWidget(Button.builder(Component.translatable("united_minecraft.named_block_screen.confirm"), button -> confirm())
					.bounds(this.width / 2 - 100, buttonY, 200, 20)
					.build());
			addRenderableWidget(Button.builder(Component.translatable("united_minecraft.named_block_screen.cancel"), button -> onClose())
					.bounds(this.width / 2 - 100, buttonY + 24, 200, 20)
					.build());
		}
		setInitialFocus(nameBox);
	}

	@Override
	public void added() {
		super.added();
		this.minecraft.getNarrator().saySystemNow(prompt);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Only while the text field has focus: the checkbox and the Confirm button handle Enter
		// themselves (toggle / press), so it must not confirm out from under them.
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
				&& getFocused() == nameBox) {
			confirm();
			return true;
		}
		return super.keyPressed(event);
	}

	private void clear() {
		onConfirmWithToggle.accept("", false);
		this.minecraft.gui.setScreen(returnTo);
	}

	private void confirm() {
		if (onConfirmWithToggle != null) {
			onConfirmWithToggle.accept(nameBox.getValue(), toggleBox.selected());
		} else {
			onConfirm.accept(nameBox.getValue());
		}
		this.minecraft.gui.setScreen(returnTo);
	}

	@Override
	public void onClose() {
		this.minecraft.getNarrator().saySystemNow(cancelled);
		this.minecraft.gui.setScreen(returnTo);
	}
}
